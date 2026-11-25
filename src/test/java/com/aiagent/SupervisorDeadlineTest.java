package com.aiagent;

import com.aiagent.agent.config.AgentConfig;
import com.aiagent.agent.orchestrator.SubAgent;
import com.aiagent.agent.orchestrator.SupervisorAgent;
import com.aiagent.llm.LLMClient;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SupervisorDeadlineTest {
    SubAgent worker(java.util.function.Supplier<String> action) {
        return worker("fixture-worker", action);
    }

    SubAgent worker(String name, java.util.function.Supplier<String> action) {
        return new SubAgent() {
            public String getName() { return name; }
            public String getDescription() { return "fixture"; }
            public boolean canHandle(String query) { return true; }
            public String execute(String query, String context) { return action.get(); }
        };
    }
    LLMClient client() {
        LLMClient client = mock(LLMClient.class);
        when(client.generate(anyString())).thenReturn("fixture summary");
        return client;
    }

    @Test void timedOutInterruptibleWorkerActuallyReceivesInterruption() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AgentConfig config = new AgentConfig(); config.setSubTaskTimeoutSeconds(1);
        try {
            LLMClient llmClient = client();
            SupervisorAgent supervisor = new SupervisorAgent(List.of(worker(() -> {
                try { Thread.sleep(10000); } catch (InterruptedException ex) {
                    interrupted.countDown(); Thread.currentThread().interrupt();
                }
                return "stopped";
            }), worker("failing-worker", () -> {
                throw new IllegalStateException("fixture failure");
            })), llmClient, config, pool);
            long started = System.nanoTime();
            var result = supervisor.coordinate("q", "");
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3000);
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
            assertEquals(SupervisorAgent.WorkerStatus.TIMED_OUT, result.subTasks().getFirst().status());
            assertEquals(SupervisorAgent.WorkerStatus.FAILED, result.subTasks().get(1).status());
            assertTrue(result.finalAnswer().contains("本次未取得可用的 Worker 结果"));
            verifyNoInteractions(llmClient);
        } finally { pool.shutdownNow(); }
    }

    @Test void saturatedPoolRejectsWithoutRunningWorkerOnCaller() throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            pool.submit(() -> { occupied.countDown(); try { release.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } });
            assertTrue(occupied.await(1, TimeUnit.SECONDS));
            pool.submit(() -> {}); // 填满任务队列。
            var supervisor = new SupervisorAgent(List.of(worker(() -> { fail("Worker escaped saturated pool"); return ""; })),
                    client(), new AgentConfig(), pool);
            assertEquals(SupervisorAgent.WorkerStatus.REJECTED, supervisor.coordinate("q", "").subTasks().getFirst().status());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void completedWorkerStillContributesWhenAnotherWorkerTimesOut() {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch waitForTimeout = new CountDownLatch(1);
        AgentConfig config = new AgentConfig();
        config.setSubTaskTimeoutSeconds(1);
        LLMClient llmClient = client();
        when(llmClient.generate(anyString())).thenReturn("总价为 98 元");
        try {
            SubAgent research = worker("Research", () -> {
                try {
                    waitForTimeout.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return "late research";
            });
            SubAgent analyst = worker("DataAnalyst", () -> "总价为 98 元");
            var result = new SupervisorAgent(List.of(research, analyst), llmClient, config, pool)
                    .coordinate("单价 7 元，买 14 件", "");

            assertEquals(List.of(SupervisorAgent.WorkerStatus.TIMED_OUT, SupervisorAgent.WorkerStatus.SUCCESS),
                    result.subTasks().stream().map(SupervisorAgent.SubTaskExecution::status).toList());
            verify(llmClient).generate(argThat(prompt -> prompt.contains("总价为 98 元") && !prompt.contains("Research")));
            assertTrue(result.finalAnswer().contains("总价为 98 元"));
            assertTrue(result.finalAnswer().contains("Research（超时）"));
        } finally {
            waitForTimeout.countDown();
            pool.shutdownNow();
        }
    }
}
