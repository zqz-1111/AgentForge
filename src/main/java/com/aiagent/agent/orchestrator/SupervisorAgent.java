package com.aiagent.agent.orchestrator;

import com.aiagent.agent.config.AgentConfig;
import com.aiagent.llm.LLMClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * Enterprise Supervisor Agent (Multi-Agent Coordinator).
 * Manages Supervisor-Worker hierarchy with divide-and-conquer orchestration:
 * 1. Matches the original query to worker capabilities using rules.
 * 2. Routes that query to matched workers in parallel on a dedicated pool,
 *    with exception isolation and a shared worker deadline (including queue time).
 * 3. Aggregates multi-agent execution results and synthesizes the unified final response.
 */
@Slf4j
@Component
public class SupervisorAgent {

    private final List<SubAgent> subAgents;
    private final LLMClient llmClient;
    private final AgentConfig agentConfig;
    private final ExecutorService agentExecutor;

    public SupervisorAgent(List<SubAgent> subAgents,
                           LLMClient llmClient,
                           AgentConfig agentConfig,
                           @Qualifier("agentTaskExecutor") ExecutorService agentExecutor) {
        this.subAgents = subAgents;
        this.llmClient = llmClient;
        this.agentConfig = agentConfig;
        this.agentExecutor = agentExecutor;
    }

    /**
     * Coordinate multi-agent collaborative execution for a given query.
     *
     * @param query   User's original query
     * @param context Pre-existing conversation context
     * @return Synthesized comprehensive response
     */
    public MultiAgentResult coordinate(String query, String context) {
        log.info("[SupervisorAgent] Coordinating multi-agent plan for: '{}'", query);
        long start = System.currentTimeMillis();

        // 1. DIVIDE & ROUTE: capability matching is cheap and stays on the calling thread.
        List<SubAgent> matched = subAgents.stream()
                .filter(agent -> safeCanHandle(agent, query))
                .toList();

        // Fallback to direct LLM response if no specialized worker matched
        if (matched.isEmpty()) {
            log.info("[SupervisorAgent] No worker matched, fallback to direct LLM");
            String fallback = llmClient.generate(context + "\nUser: " + query + "\nAnswer:");
            return new MultiAgentResult(fallback, List.of(), System.currentTimeMillis() - start);
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(agentConfig.getSubTaskTimeoutSeconds());
        List<Future<SubTaskExecution>> futures = new ArrayList<>();
        for (SubAgent agent : matched) {
            try {
                futures.add(agentExecutor.submit(() -> runSubAgent(agent, query, context)));
            } catch (RejectedExecutionException ex) {
                futures.add(CompletableFuture.completedFuture(new SubTaskExecution(
                        agent.getName(), "Worker rejected: executor is busy", 0)));
            }
        }

        // 3. BOUNDED JOIN: global timeout, cancel unfinished workers, keep submission order.
        List<SubTaskExecution> taskExecutions = joinAll(futures, matched, deadline);

        // If every worker was cancelled or timed out, degrade to a direct LLM answer.
        if (taskExecutions.isEmpty()) {
            log.warn("[SupervisorAgent] All workers unfinished, fallback to direct LLM");
            String fallback = llmClient.generate(context + "\nUser: " + query + "\nAnswer:");
            return new MultiAgentResult(fallback, List.of(), System.currentTimeMillis() - start);
        }

        // 4. COMBINE: synthesize multi-agent outputs using LLM synthesis prompt.
        StringBuilder synthesisPrompt = new StringBuilder();
        synthesisPrompt.append("你是一个多智能体协同主管（Supervisor Agent）。以下是各个专能子智能体的执行成果：\n\n");

        for (SubTaskExecution exec : taskExecutions) {
            synthesisPrompt.append("【智能体 ").append(exec.agentName()).append(" 输出】：\n")
                    .append(exec.output()).append("\n\n");
        }

        synthesisPrompt.append("用户原始问题：").append(query).append("\n\n")
                .append("请整合上述各智能体的产出，给出条理清晰、专业完整的最终回答：");

        String finalAnswer = llmClient.generate(synthesisPrompt.toString());

        long totalDuration = System.currentTimeMillis() - start;
        log.info("[SupervisorAgent] {} worker(s) coordinated in {}ms", taskExecutions.size(), totalDuration);
        return new MultiAgentResult(finalAnswer, taskExecutions, totalDuration);
    }

    /**
     * Execute one worker with timing and exception isolation: a single broken worker
     * yields an error SubTaskExecution instead of failing the whole coordination.
     */
    private SubTaskExecution runSubAgent(SubAgent agent, String query, String context) {
        long agentStart = System.currentTimeMillis();
        try {
            log.info("[SupervisorAgent] Dispatching to [{}] on thread {}",
                    agent.getName(), Thread.currentThread().getName());
            String output = agent.execute(query, context);
            return new SubTaskExecution(agent.getName(), output, System.currentTimeMillis() - agentStart);
        } catch (Exception e) {
            log.error("[SupervisorAgent] Sub-agent [{}] failed: {}", agent.getName(), e.getMessage(), e);
            return new SubTaskExecution(agent.getName(),
                    "子智能体执行异常，已被 Supervisor 隔离：" + e.getMessage(),
                    System.currentTimeMillis() - agentStart);
        }
    }

    /**
     * Preserve submission order; queueing and waiting consume the same deadline.
     * Future.cancel(true) requests interruption; uncooperative I/O may still continue.
     */
    private List<SubTaskExecution> joinAll(List<Future<SubTaskExecution>> futures, List<SubAgent> agents, long deadline) {
        List<SubTaskExecution> results = new ArrayList<>(futures.size());
        for (int i = 0; i < futures.size(); i++) {
            Future<SubTaskExecution> future = futures.get(i);
            try {
                long remaining = Math.max(0, deadline - System.nanoTime());
                results.add(future.isDone() ? future.get() : future.get(remaining, TimeUnit.NANOSECONDS));
            } catch (InterruptedException ex) {
                futures.forEach(f -> f.cancel(true));
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Worker coordination interrupted", ex);
            } catch (Exception ex) {
                future.cancel(true);
                results.add(new SubTaskExecution(agents.get(i).getName(), "Worker timed out or failed", 0));
            }
        }
        return results;
    }

    /** A broken canHandle must not break routing for the remaining workers. */
    private boolean safeCanHandle(SubAgent agent, String query) {
        try {
            return agent.canHandle(query);
        } catch (Exception e) {
            log.warn("[SupervisorAgent] canHandle of [{}] threw, treated as unmatched: {}",
                    agent.getName(), e.getMessage());
            return false;
        }
    }

    public record SubTaskExecution(String agentName, String output, long durationMs) {}
    public record MultiAgentResult(String finalAnswer, List<SubTaskExecution> subTasks, long durationMs) {}
}
