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
        return new SubAgent() {
            public String getName() { return "fixture-worker"; }
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
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AgentConfig config = new AgentConfig(); config.setSubTaskTimeoutSeconds(1);
        try {
            SupervisorAgent supervisor = new SupervisorAgent(List.of(worker(() -> {
                try { Thread.sleep(10000); } catch (InterruptedException ex) {
                    interrupted.countDown(); Thread.currentThread().interrupt();
                }
                return "stopped";
            })), client(), config, pool);
            long started = System.nanoTime();
            var result = supervisor.coordinate("q", "");
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3000);
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
            assertTrue(result.subTasks().getFirst().output().contains("timed out"));
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
            pool.submit(() -> {}); // Fill queue.
            var supervisor = new SupervisorAgent(List.of(worker(() -> { fail("Worker escaped saturated pool"); return ""; })),
                    client(), new AgentConfig(), pool);
            assertTrue(supervisor.coordinate("q", "").subTasks().getFirst().output().contains("rejected"));
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
