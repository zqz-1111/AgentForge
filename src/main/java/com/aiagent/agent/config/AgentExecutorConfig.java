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
 * 供 Supervisor 和 Worker 并行编排使用的专用线程池。
 * 与 ForkJoinPool.commonPool() 隔离，具体配置如下。
 * - 使用有界任务队列，避免资源耗尽。
 * - 给守护线程命名，方便在线程转储中定位。
 * - 饱和时显式拒绝任务，避免 Worker 转到请求线程执行。
 * - 通过 destroyMethod 随 Spring 容器一起平滑关闭。
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
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
