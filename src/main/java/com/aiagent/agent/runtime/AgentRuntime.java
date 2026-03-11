package com.aiagent.agent.runtime;

import com.aiagent.agent.config.AgentConfig;
import com.aiagent.dto.AgentRequest;
import com.aiagent.dto.AgentResponse;
import com.aiagent.guardrails.InputFilter;
import com.aiagent.guardrails.ResponseValidator;
import com.aiagent.llm.PromptTemplates;
import com.aiagent.memory.ConversationMemory;
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

    public AgentResponse execute(AgentRequest request) {
        String sessionId = resolveSession(request.getSessionId());

        inputFilter.validate(request.getQuery()); // throws on violation

        String context = PromptTemplates.REACT_SYSTEM_PROMPT
                + "\n\n"
                + planner.buildContext(sessionId, request.getQuery());

        String answer = reactLoop.execute(context, agentConfig.getMaxIterations());

        responseValidator.validate(answer); // throws on violation

        memory.store(sessionId, "User: " + request.getQuery());
        memory.store(sessionId, "Agent: " + answer);

        return AgentResponse.builder()
                .answer(answer)
                .sessionId(sessionId)
                .build();
    }

    private String resolveSession(String sessionId) {
        return (sessionId != null && !sessionId.isBlank())
                ? sessionId
                : UUID.randomUUID().toString();
    }
}
