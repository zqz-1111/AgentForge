package com.aiagent.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 记录智能体 ReAct 循环的每一步。
 * 日志格式自动注入 Micrometer/Zipkin 的追踪 ID。
 */
@Slf4j
@Component
public class AgentTraceLogger {

    public void logStep(String step) {
        log.info("Agent step: {}", step);
    }
}
