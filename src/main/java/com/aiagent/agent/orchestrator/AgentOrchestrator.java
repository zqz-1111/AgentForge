package com.aiagent.agent.orchestrator;

import com.aiagent.agent.runtime.AgentRuntime;
import com.aiagent.dto.AgentRequest;
import com.aiagent.dto.AgentResponse;
import com.aiagent.observability.AgentTraceLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Decides which agent to invoke based on query intent.
 * Extend this class to add: support agent, devops agent, knowledge agent.
 */
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final AgentRuntime agentRuntime;
    private final AgentTraceLogger traceLogger;

    public AgentResponse runAgent(AgentRequest request) {
        traceLogger.logStep("Orchestrator received query: " + request.getQuery());
        long start = System.currentTimeMillis();

        AgentResponse response = agentRuntime.execute(request);

        long latency = System.currentTimeMillis() - start;
        traceLogger.logStep("Orchestrator completed in " + latency + "ms");

        response.setLatencyMs(latency);
        return response;
    }
}
