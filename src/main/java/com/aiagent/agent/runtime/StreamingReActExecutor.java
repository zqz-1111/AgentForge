package com.aiagent.agent.runtime;

import com.aiagent.dto.StreamEvent;
import com.aiagent.llm.StreamingResponseReader;
import com.aiagent.guardrails.ResponseValidator;
import org.springframework.beans.factory.annotation.Value;
import com.aiagent.observability.AgentTraceLogger;
import com.aiagent.observability.TokenMetrics;
import com.aiagent.tools.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 流式 ReAct 执行器。
 * 在整个推理过程中实时发送带类型的 SSE 事件。
 * 事件顺序：[STATUS] → [THOUGHT] → [TOOL_CALL] → [TOOL_RESULT] → [TOKEN] → [DONE]
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamingReActExecutor {

    private final StreamingResponseReader responseReader;
    private final ResponseValidator responseValidator;
    @Value("${ai.guardrails.max-output-length:5000}")
    private int maxOutputLength;
    private final ToolExecutor toolExecutor;
    private final AgentTraceLogger traceLogger;
    private final TokenMetrics tokenMetrics;

    private static final String FINAL_ANSWER_PREFIX = "Final Answer:";
    private static final String THOUGHT_PREFIX = "Thought:";
    private static final String ACTION_PREFIX = "Action:";
    private static final String ACTION_INPUT_PREFIX = "Action Input:";

    /**
     * 执行 ReAct 循环并发送结构化 SSE 事件。
     *
     * @param query 用户输入，用于路由和追踪
     * @param context 完整提示词上下文（系统提示词、历史、记忆及 RAG）
     * @param maxIterations 允许的最大 ReAct 步数
     * @param eventConsumer 接收结构化 StreamEvent 的回调
     * @return 完整回答文本
     */
    public String executeWithEvents(String query, String context, int maxIterations, Consumer<StreamEvent> eventConsumer) {
        long startTime = System.currentTimeMillis();
        AtomicLong tokenCounter = new AtomicLong(0);

        eventConsumer.accept(StreamEvent.status("正在解析意图并规划执行路径..."));

        String currentContext = context;
        String finalAnswer = "";

        for (int step = 1; step <= maxIterations; step++) {
            traceLogger.logStep("ReAct step " + step);
            eventConsumer.accept(StreamEvent.status("第 " + step + " 轮推理分析中..."));

            // 1. LLM 推理生成当前步决策
            boolean[] streamedAnswer = {false};
            AtomicLong outputLength = new AtomicLong();
            String response = responseReader.read(query, currentContext, delta -> {
                if (outputLength.addAndGet(delta.length()) > maxOutputLength) {
                    throw new com.aiagent.exception.GuardrailException("Stream answer exceeds maximum allowed length");
                }
                streamedAnswer[0] = true;
                eventConsumer.accept(StreamEvent.token(delta));
            });
            tokenMetrics.recordTokenUsage(response);
            tokenCounter.addAndGet(estimateTokenCount(response));

            // 2. 提取 Thought
            String thought = extractLine(response, THOUGHT_PREFIX);
            if (!thought.isBlank()) {
                eventConsumer.accept(StreamEvent.thought(step, thought));
            }

            // 3. 判断是否得出最终答案
            if (response.contains(FINAL_ANSWER_PREFIX)) {
                finalAnswer = extractFinalAnswer(response);
                responseValidator.validate(finalAnswer);
                if (!streamedAnswer[0]) {
                    eventConsumer.accept(StreamEvent.token(finalAnswer));
                }
                long duration = System.currentTimeMillis() - startTime;
                eventConsumer.accept(StreamEvent.done(step, duration, tokenCounter.get()));
                return finalAnswer;
            }

            // 4. 解析工具调用
            String toolName = extractLine(response, ACTION_PREFIX);
            String toolInput = extractLine(response, ACTION_INPUT_PREFIX);

            if (toolName.isBlank()) {
                // 没有指定工具，直接将回答作为最终结果
                finalAnswer = response.trim();
                responseValidator.validate(finalAnswer);
                eventConsumer.accept(StreamEvent.token(finalAnswer));
                long duration = System.currentTimeMillis() - startTime;
                eventConsumer.accept(StreamEvent.done(step, duration, tokenCounter.get()));
                return finalAnswer;
            }

            // 5. 执行工具并记录耗时
            eventConsumer.accept(StreamEvent.toolCall(step, toolName, toolInput));
            long toolStart = System.currentTimeMillis();
            String observation = toolExecutor.execute(toolName, toolInput);
            long toolDuration = System.currentTimeMillis() - toolStart;

            traceLogger.logStep("Tool [" + toolName + "] (" + toolDuration + "ms) -> " + observation);
            eventConsumer.accept(StreamEvent.toolResult(step, toolName, observation, toolDuration));

            // 6. 追加 Observation 推进 ReAct 上下文
            currentContext += "\n" + response + "\nObservation: " + observation + "\n";
        }

        finalAnswer = "任务在 " + maxIterations + " 轮推理内未能得出结论，已达到最大执行深度。";
        eventConsumer.accept(StreamEvent.token(finalAnswer));
        long duration = System.currentTimeMillis() - startTime;
        eventConsumer.accept(StreamEvent.done(maxIterations, duration, tokenCounter.get()));
        return finalAnswer;
    }

    /**
     * 兼容原有的简单 token 流式接口
     */
    public String execute(String context, int maxIterations, Consumer<String> tokenCallback) {
        return executeWithEvents("general", context, maxIterations, event -> {
            if (event.getType() == StreamEvent.EventType.TOKEN && event.getContent() != null) {
                tokenCallback.accept(event.getContent());
            }
        });
    }

    private String extractFinalAnswer(String response) {
        int idx = response.indexOf(FINAL_ANSWER_PREFIX);
        return response.substring(idx + FINAL_ANSWER_PREFIX.length()).trim();
    }

    private String extractLine(String response, String prefix) {
        for (String line : response.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private long estimateTokenCount(String text) {
        if (text == null) return 0;
        return Math.max(1, text.length() / 4); // 仅估算用量，实际计费以服务商为准。
    }
}
