package com.aiagent.agent.runtime;

import com.aiagent.agent.config.AgentConfig;
import com.aiagent.dto.AgentRequest;
import com.aiagent.dto.AgentResponse;
import com.aiagent.guardrails.InputFilter;
import com.aiagent.guardrails.ResponseValidator;
import com.aiagent.llm.PromptTemplates;
import com.aiagent.memory.ConversationMemory;
import com.aiagent.memory.SessionScope;
import com.aiagent.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentRuntime {

    private final AgentPlanner planner;
    private final ReActLoopExecutor reactLoop;
    private final ConversationMemory memory;
    private final InputFilter inputFilter;
    private final ResponseValidator responseValidator;
    private final AgentConfig agentConfig;
    private final ToolRegistry toolRegistry;
    private final SessionScope sessionScope;

    public AgentResponse execute(AgentRequest request) {
        String sessionId = sessionScope.resolve(request.getSessionId());
        String memoryKey = sessionScope.storageKey(request.getUserId(), sessionId);

        inputFilter.validate(request.getQuery()); // throws on violation

        // Build dynamic system prompt with all registered tools
        String systemPrompt = PromptTemplates.buildReactSystemPrompt(toolRegistry.allTools());

        String context = systemPrompt
                + "\n\n"
                + planner.buildContext(memoryKey, request.getQuery());

        ReActLoopExecutor.ExecutionResult execution = reactLoop.executeWithResult(context, agentConfig.getMaxIterations());
        String answer = execution.answer();

        responseValidator.validate(answer); // throws on violation

        memory.store(memoryKey, "User: " + request.getQuery());
        memory.store(memoryKey, "Agent: " + answer);

        return AgentResponse.builder()
                .answer(answer)
                .sessionId(sessionId)
                .iterationsUsed(execution.iterationsUsed())
                .build();
    }

}
