package com.aiagent.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated thread pool for Supervisor-Worker parallel orchestration.
 * Deliberately isolated from ForkJoinPool.commonPool():
 * - bounded work queue to prevent resource exhaustion,
 * - named daemon threads for observability in thread dumps,
 * - CallerRunsPolicy as back-pressure (degrade to caller-thread execution instead of dropping tasks),
 * - graceful shutdown together with the Spring context via destroyMethod.
 */
@Configuration
public class AgentExecutorConfig {

    @Bean(name = "agentTaskExecutor", destroyMethod = "shutdown")
    public ExecutorService agentTaskExecutor(AgentConfig config) {
        AtomicInteger threadSeq = new AtomicInteger(1);
        ThreadFactory namedDaemonFactory = r -> {
            Thread t = new Thread(r, "agent-worker-" + threadSeq.getAndIncrement());
            t.setDaemon(true);
            return t;
        };

        return new ThreadPoolExecutor(
                config.getSupervisorCorePoolSize(),
                config.getSupervisorMaxPoolSize(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getSupervisorQueueCapacity()),
                namedDaemonFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
