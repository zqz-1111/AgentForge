package com.aiagent.agent.orchestrator;

import com.aiagent.llm.LLMClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Enterprise Supervisor Agent (Multi-Agent Coordinator).
 * Manages Supervisor-Worker hierarchy:
 * 1. Analyzes user intent & breaks down complex compound requests into sub-tasks.
 * 2. Dynamically routes sub-tasks to specialized SubAgents (ResearchAgent, DataAnalystAgent, etc.).
 * 3. Aggregates multi-agent execution results and synthesizes the unified final response.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupervisorAgent {

    private final List<SubAgent> subAgents;
    private final LLMClient llmClient;

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

        List<SubTaskExecution> taskExecutions = new ArrayList<>();

        // 1. Identify matched worker agents based on query
        for (SubAgent agent : subAgents) {
            if (agent.canHandle(query)) {
                log.info("[SupervisorAgent] Dispatching sub-task to [{}]", agent.getName());
                long agentStart = System.currentTimeMillis();
                String agentResult = agent.execute(query, context);
                long agentDuration = System.currentTimeMillis() - agentStart;

                taskExecutions.add(new SubTaskExecution(agent.getName(), agentResult, agentDuration));
            }
        }

        // 2. Synthesize results
        String finalAnswer;
        if (taskExecutions.isEmpty()) {
            // Fallback to direct LLM response if no specialized worker matched
            finalAnswer = llmClient.generate(context + "\nUser: " + query + "\nAnswer:");
        } else {
            // Synthesize multi-agent outputs using LLM synthesis prompt
            StringBuilder synthesisPrompt = new StringBuilder();
            synthesisPrompt.append("你是一个多智能体协同主管（Supervisor Agent）。以下是各个专能子智能体的执行成果：\n\n");

            for (SubTaskExecution exec : taskExecutions) {
                synthesisPrompt.append("【智能体 ").append(exec.agentName()).append(" 输出】：\n")
                        .append(exec.output()).append("\n\n");
            }

            synthesisPrompt.append("用户原始问题：").append(query).append("\n\n")
                    .append("请整合上述各智能体的产出，给出条理清晰、专业完整的最终回答：");

            finalAnswer = llmClient.generate(synthesisPrompt.toString());
        }

        long totalDuration = System.currentTimeMillis() - start;
        return new MultiAgentResult(finalAnswer, taskExecutions, totalDuration);
    }

    public record SubTaskExecution(String agentName, String output, long durationMs) {}
    public record MultiAgentResult(String finalAnswer, List<SubTaskExecution> subTasks, long durationMs) {}
}
