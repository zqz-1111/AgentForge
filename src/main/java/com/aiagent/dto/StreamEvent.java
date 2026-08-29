package com.aiagent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Structured SSE event model for Agent streaming output.
 * Encapsulates the entire ReAct reasoning cycle:
 * STATUS -> THOUGHT -> TOOL_CALL -> TOOL_RESULT -> TOKEN -> DONE / ERROR
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamEvent implements Serializable {

    public enum EventType {
        STATUS,       // Task preparation / routing status
        THOUGHT,      // Agent internal reasoning / planning thought
        TOOL_CALL,    // Tool execution intent
        TOOL_RESULT,  // Tool execution output & observation
        TOKEN,        // Real-time typewriter output of final answer
        DONE,         // Stream completion with metrics
        ERROR         // Execution error
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
