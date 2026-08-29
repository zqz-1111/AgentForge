package com.aiagent.controller;

import com.aiagent.agent.config.AgentConfig;
import com.aiagent.agent.runtime.AgentPlanner;
import com.aiagent.agent.runtime.StreamingReActExecutor;
import com.aiagent.dto.StreamEvent;
import com.aiagent.guardrails.InputFilter;
import com.aiagent.llm.PromptTemplates;
import com.aiagent.memory.ConversationMemory;
import com.aiagent.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise-grade Server-Sent Events (SSE) Streaming Controller.
 * Delivers full-lifecycle typed events across the ReAct reasoning chain:
 * - status: System status & routing
 * - thought: Step-by-step reasoning thought
 * - tool_call: Tool name & arguments
 * - tool_result: Tool execution observation
 * - token: Real-time typewriter output
 * - done: Final completion with execution metrics
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIStreamController {

    private final StreamingReActExecutor streamingExecutor;
    private final AgentPlanner planner;
    private final InputFilter inputFilter;
    private final ConversationMemory memory;
    private final ToolRegistry toolRegistry;
    private final AgentConfig agentConfig;
    private final ObjectMapper objectMapper;

    /**
     * Standard SSE streaming endpoint for AI reasoning & generation.
     *
     * Usage: GET /ai/stream?query=查一下2026最新汇率&sessionId=user_123
     * Returns: text/event-stream with structured JSON events
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String query,
                             @RequestParam(required = false) String sessionId) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 min timeout

        String resolvedSession = (sessionId != null && !sessionId.isBlank())
                ? sessionId : UUID.randomUUID().toString();

        try {
            inputFilter.validate(query);
        } catch (Exception e) {
            sendEventSafe(emitter, "error", StreamEvent.error(e.getMessage()));
            emitter.complete();
            return emitter;
        }

        String systemPrompt = PromptTemplates.buildReactSystemPrompt(toolRegistry.allTools());
        String context = systemPrompt + "\n\n" + planner.buildContext(resolvedSession, query);

        final String finalSession = resolvedSession;
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 发送 session 初始化事件
                sendEventSafe(emitter, "session", StreamEvent.status("会话已初始化: " + finalSession));

                // 2. 启动结构化流式推理
                String answer = streamingExecutor.executeWithEvents(
                        query,
                        context,
                        agentConfig.getMaxIterations(),
                        event -> {
                            String eventName = event.getType().name().toLowerCase();
                            sendEventSafe(emitter, eventName, event);
                        }
                );

                // 3. 存储会话记忆
                memory.store(finalSession, "User: " + query);
                memory.store(finalSession, "Agent: " + answer);

                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming execution failed: {}", e.getMessage(), e);
                sendEventSafe(emitter, "error", StreamEvent.error("执行异常: " + e.getMessage()));
                emitter.complete();
            }
        });

        emitter.onTimeout(() -> {
            log.warn("SSE timeout for session {}", finalSession);
            emitter.complete();
        });
        emitter.onCompletion(() -> log.debug("SSE stream completed for session {}", finalSession));

        return emitter;
    }

    private void sendEventSafe(SseEmitter emitter, String eventName, Object data) {
        try {
            String json = (data instanceof String str) ? str : objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(json, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.debug("Client disconnected during SSE push: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to serialize SSE event [{}]: {}", eventName, e.getMessage());
        }
    }
}
