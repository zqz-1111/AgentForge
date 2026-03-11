package com.aiagent.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Logs each step of the agent's ReAct loop.
 * Trace ID from Micrometer/Zipkin is automatically injected into the log pattern.
 */
@Slf4j
@Component
public class AgentTraceLogger {

    public void logStep(String step) {
        log.info("Agent step: {}", step);
    }
}
