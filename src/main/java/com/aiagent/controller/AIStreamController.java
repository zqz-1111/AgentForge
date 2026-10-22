package com.aiagent.controller;

import com.aiagent.agent.config.AgentConfig;
import com.aiagent.agent.runtime.AgentPlanner;
import com.aiagent.agent.runtime.StreamingReActExecutor;
import com.aiagent.dto.StreamEvent;
import com.aiagent.guardrails.InputFilter;
import com.aiagent.llm.PromptTemplates;
import com.aiagent.memory.ConversationMemory;
import com.aiagent.memory.SessionScope;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private final SessionScope sessionScope;
    private final ThreadPoolTaskExecutor streamExecutor;

    /**
     * Standard SSE streaming endpoint for AI reasoning & generation.
     *
     * Usage: GET /ai/stream?query=查一下2026最新汇率&sessionId=user_123
     * Returns: text/event-stream with structured JSON events
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String query,
                             @RequestParam(required = false) String sessionId, @RequestAttribute String userId) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 min timeout

        String resolvedSession = sessionScope.resolve(sessionId);
        String memoryKey = sessionScope.storageKey(userId, resolvedSession);

        try {
            inputFilter.validate(query);
        } catch (Exception e) {
            sendEventSafe(emitter, "error", StreamEvent.error(e.getMessage()));
            emitter.complete();
            return emitter;
        }

        final String finalSession = resolvedSession;
        AtomicBoolean active = new AtomicBoolean(true);
        emitter.onTimeout(() -> { active.set(false); emitter.complete(); });
        emitter.onCompletion(() -> active.set(false));
        emitter.onError(error -> active.set(false));
        CompletableFuture.runAsync(() -> {
            try {
                AtomicReference<StreamEvent> completion = new AtomicReference<>();
                // 1. 发送 session 初始化事件
                sendEventSafe(emitter, "session", StreamEvent.status("会话已初始化: " + finalSession));
                String systemPrompt = PromptTemplates.buildReactSystemPrompt(toolRegistry.allTools());
                String context = systemPrompt + "\n\n" + planner.buildContext(memoryKey, query);

                // 2. 启动结构化流式推理
                String answer = streamingExecutor.executeWithEvents(
                        query,
                        context,
                        agentConfig.getMaxIterations(),
                        event -> {
                            if (!active.get()) throw new java.util.concurrent.CancellationException("SSE closed");
                            if (event.getType() == StreamEvent.EventType.DONE) {
                                completion.set(event);
                                return;
                            }
                            String eventName = event.getType().name().toLowerCase();
                            sendEventSafe(emitter, eventName, event);
                        }
                );

                // 3. 存储会话记忆
                if (!active.get()) return;
                memory.store(memoryKey, "User: " + query);
                memory.store(memoryKey, "Agent: " + answer);
                if (completion.get() != null) sendEventSafe(emitter, "done", completion.get());

                emitter.complete();
            } catch (Exception e) {
                log.warn("Streaming execution failed", e);
                if (active.get()) sendEventSafe(emitter, "error", StreamEvent.error("Agent stream failed"));
                emitter.complete();
            }
        }, streamExecutor);

        return emitter;
    }

    private void sendEventSafe(SseEmitter emitter, String eventName, Object data) {
        try {
            String json = (data instanceof String str) ? str : objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(json, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new java.util.concurrent.CancellationException("Client disconnected during SSE push");
        } catch (Exception e) {
            log.warn("Failed to serialize SSE event [{}]: {}", eventName, e.getMessage());
        }
    }
}
