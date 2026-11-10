package com.aiagent.agent.orchestrator;

import com.aiagent.agent.runtime.AgentRuntime;
import com.aiagent.dto.AgentRequest;
import com.aiagent.dto.AgentResponse;
import com.aiagent.observability.AgentTraceLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 根据提问意图决定调用哪个智能体。
 * 可扩展此类，加入支持、运维或知识检索智能体。
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
