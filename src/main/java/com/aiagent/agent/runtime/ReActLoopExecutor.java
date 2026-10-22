package com.aiagent.agent.runtime;

import com.aiagent.llm.LLMClient;
import com.aiagent.observability.AgentTraceLogger;
import com.aiagent.observability.TokenMetrics;
import com.aiagent.tools.ToolExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implements the ReAct (Reasoning + Acting) loop.
 *
 * Loop:
 *   1. Send context to LLM
 *   2. If LLM returns "Final Answer:" → done
 *   3. Otherwise parse tool call → execute → append observation → repeat
 */
@Component
@RequiredArgsConstructor
public class ReActLoopExecutor {

    private final LLMClient llmClient;
    private final ToolExecutor toolExecutor;
    private final AgentTraceLogger traceLogger;
    private final TokenMetrics tokenMetrics;

    private static final String FINAL_ANSWER_PREFIX = "Final Answer:";
    private static final String ACTION_PREFIX       = "Action:";
    private static final String ACTION_INPUT_PREFIX = "Action Input:";

    public String execute(String context, int maxIterations) {
        return executeWithResult(context, maxIterations).answer();
    }

    public ExecutionResult executeWithResult(String context, int maxIterations) {
        for (int step = 0; step < maxIterations; step++) {
            traceLogger.logStep("ReAct step " + (step + 1));

            String response = llmClient.generate(context);
            tokenMetrics.recordTokenUsage(response);

            if (response.contains(FINAL_ANSWER_PREFIX)) {
                return new ExecutionResult(extractFinalAnswer(response), step + 1);
            }

            String toolName  = extractLine(response, ACTION_PREFIX);
            String toolInput = extractLine(response, ACTION_INPUT_PREFIX);

            if (toolName.isEmpty()) {
                return new ExecutionResult(response, step + 1);
            }

            String observation = toolExecutor.execute(toolName, toolInput);
            traceLogger.logStep("Tool [" + toolName + "] → " + observation);

            context += "\n" + response + "\nObservation: " + observation + "\n";
        }

        return new ExecutionResult("Unable to complete task within " + maxIterations + " iterations.", maxIterations);
    }

    public record ExecutionResult(String answer, int iterationsUsed) {}

    private String extractFinalAnswer(String response) {
        int idx = response.indexOf(FINAL_ANSWER_PREFIX);
        return response.substring(idx + FINAL_ANSWER_PREFIX.length()).trim();
    }

    private String extractLine(String response, String prefix) {
        for (String line : response.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "";
    }
}
