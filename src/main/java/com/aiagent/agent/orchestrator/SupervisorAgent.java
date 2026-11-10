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
 * 负责协调多个智能体的 Supervisor。
 * 按任务匹配、并行处理和结果汇总来编排 Supervisor 与 Worker。
 * 1. 按规则将原始问题与 Worker 的能力匹配。
 * 2. 将问题交给匹配到的 Worker，在专用线程池中并行执行。
 *    隔离各 Worker 的异常，并共享同一截止时间（含排队时间）。
 * 3. 汇总各 Worker 的执行结果，生成统一回答。
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
     * 针对给定问题协调多个智能体并执行任务。
     *
     * @param query 用户的原始问题
     * @param context 已有对话上下文
     * @return 汇总后的完整回答
     */
    public MultiAgentResult coordinate(String query, String context) {
        log.info("[SupervisorAgent] Coordinating multi-agent plan for: '{}'", query);
        long start = System.currentTimeMillis();

        // 1. 匹配与路由：能力匹配开销较低，直接在调用线程执行。
        List<SubAgent> matched = subAgents.stream()
                .filter(agent -> safeCanHandle(agent, query))
                .toList();

        // 没有匹配到专用 Worker 时，由大语言模型直接回答。
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

        // 3. 限时等待：到达全局截止时间后取消未完成任务，结果保留提交顺序。
        List<SubTaskExecution> taskExecutions = joinAll(futures, matched, deadline);

        // 如果所有 Worker 都被取消或超时，则由大语言模型直接回答。
        if (taskExecutions.isEmpty()) {
            log.warn("[SupervisorAgent] All workers unfinished, fallback to direct LLM");
            String fallback = llmClient.generate(context + "\nUser: " + query + "\nAnswer:");
            return new MultiAgentResult(fallback, List.of(), System.currentTimeMillis() - start);
        }

        // 4. 汇总：用专门的提示词让大语言模型整合各 Worker 的输出。
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
     * 执行单个 Worker，记录耗时并隔离异常。
     * 单个 Worker 失败时返回失败的 SubTaskExecution，其余协作继续。
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
     * 保留提交顺序，排队和等待共用同一个截止时间。
     * Future.cancel(true) 发出中断请求，不响应中断的 I/O 可能仍会继续。
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

    /** 某个 Worker 的 canHandle 失败时，继续为其他 Worker 执行路由。 */
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
