package com.aiagent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 智能体流式输出使用的结构化 SSE 事件模型。
 * 涵盖整个 ReAct 推理周期。
 * 事件顺序：STATUS → THOUGHT → TOOL_CALL → TOOL_RESULT → TOKEN → DONE 或 ERROR
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamEvent implements Serializable {

    public enum EventType {
        STATUS,       // 任务准备或路由状态
        THOUGHT,      // 智能体内部的推理或规划
        TOOL_CALL,    // 准备调用工具
        TOOL_RESULT,  // 工具执行结果与观察信息
        TOKEN,        // 实时逐段输出最终答案
        DONE,         // 流结束并附带执行指标
        ERROR         // 执行异常
    }

    private EventType type;
    private Integer step;
    private String content;
    private String toolName;
    private String toolInput;
    private String toolResult;
    private Long durationMs;
    private Long totalTokens;
    private String modelName;

    public static StreamEvent status(String message) {
        return StreamEvent.builder()
                .type(EventType.STATUS)
                .content(message)
                .build();
    }

    public static StreamEvent thought(int step, String thought) {
        return StreamEvent.builder()
                .type(EventType.THOUGHT)
                .step(step)
                .content(thought)
                .build();
    }

    public static StreamEvent toolCall(int step, String toolName, String toolInput) {
        return StreamEvent.builder()
                .type(EventType.TOOL_CALL)
                .step(step)
                .toolName(toolName)
                .toolInput(toolInput)
                .build();
    }

    public static StreamEvent toolResult(int step, String toolName, String result, long durationMs) {
        return StreamEvent.builder()
                .type(EventType.TOOL_RESULT)
                .step(step)
                .toolName(toolName)
                .toolResult(result)
                .durationMs(durationMs)
                .build();
    }

    public static StreamEvent token(String token) {
        return StreamEvent.builder()
                .type(EventType.TOKEN)
                .content(token)
                .build();
    }

    public static StreamEvent done(int totalSteps, long durationMs, long tokens) {
        return StreamEvent.builder()
                .type(EventType.DONE)
                .step(totalSteps)
                .durationMs(durationMs)
                .totalTokens(tokens)
                .build();
    }

    public static StreamEvent error(String errorMessage) {
        return StreamEvent.builder()
                .type(EventType.ERROR)
                .content(errorMessage)
                .build();
    }
}
