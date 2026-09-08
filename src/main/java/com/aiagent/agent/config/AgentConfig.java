package com.aiagent.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.agent")
public class AgentConfig {

    private int maxIterations = 5;

    /** Supervisor-Worker 并行编排专用线程池 */
    private int supervisorCorePoolSize = 4;
    private int supervisorMaxPoolSize = 8;
    private int supervisorQueueCapacity = 32;
    /** 子智能体并行执行的整体超时（秒） */
    private long subTaskTimeoutSeconds = 30;
}
