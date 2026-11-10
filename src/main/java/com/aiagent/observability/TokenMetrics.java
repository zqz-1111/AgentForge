package com.aiagent.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通过 Micrometer 记录模型调用次数和 token 用量。
 * 通过 /actuator/prometheus 暴露指标，供 Grafana 仪表板使用。
 *
 * 指标如下。
 *   ai.agent.llm.calls.total：大语言模型调用总次数
 *   ai.agent.tokens.estimated：估算 token 数（字符数除以 4）
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
