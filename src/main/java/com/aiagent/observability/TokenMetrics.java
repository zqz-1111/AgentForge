package com.aiagent.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Records token usage and LLM call counts as Micrometer metrics.
 * Exposed at /actuator/prometheus for Grafana dashboards.
 *
 * Metrics:
 *   ai.agent.llm.calls.total     - total LLM invocations
 *   ai.agent.tokens.estimated    - estimated token count (chars / 4)
 */
@Slf4j
@Component
public class TokenMetrics {

    private final Counter llmCallCounter;
    private final Counter tokenCounter;

    public TokenMetrics(MeterRegistry registry) {
        this.llmCallCounter = Counter.builder("ai.agent.llm.calls")
                .description("Total LLM calls made by the agent")
                .register(registry);

        this.tokenCounter = Counter.builder("ai.agent.tokens.estimated")
                .description("Estimated tokens consumed (response chars / 4)")
                .register(registry);
    }

    public void recordTokenUsage(String response) {
        llmCallCounter.increment();
        int estimatedTokens = response.length() / 4;
        tokenCounter.increment(estimatedTokens);
        log.debug("Estimated tokens this call: {}", estimatedTokens);
    }
}
