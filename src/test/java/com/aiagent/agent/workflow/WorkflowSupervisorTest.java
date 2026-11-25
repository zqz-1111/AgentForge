package com.aiagent.agent.workflow;

import com.aiagent.agent.config.AgentConfig;
import com.aiagent.controller.AIController;
import com.aiagent.guardrails.InputFilter;
import com.aiagent.llm.LLMClient;
import com.aiagent.rag.HybridRetrievalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkflowSupervisorTest {
    private static final String BILLING_PLAN = """
            {"steps":[
              {"id":"cost","worker":"calculate_cost","inputs":{"rate":{"from":"rate"},"usage":{"from":"usage"}}},
              {"id":"rate","worker":"lookup_rate","inputs":{"product":{"value":"storage"}}},
              {"id":"usage","worker":"lookup_usage","inputs":{"product":{"value":"storage"},"month":{"value":"2026-11"}}}
            ],"resultStep":"cost"}
            """;
    private static final String QUERY = "计算 storage 在 2026-11 的费用";
    private static final WorkflowRequest REQUEST = new WorkflowRequest(QUERY,
            List.of(new WorkflowRequest.UsageRow("storage", "2026-11", "GB", BigDecimal.valueOf(14))));

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final LLMClient model = mock(LLMClient.class);
    private final HybridRetrievalService retrieval = mock(HybridRetrievalService.class);
    private final AgentConfig config = new AgentConfig();
    private final BillingWorkflow billing = new BillingWorkflow();
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach void stopExecutor() {
        executor.shutdownNow();
    }

    private WorkflowSupervisor supervisor(WorkflowWorker... workers) {
        return new WorkflowSupervisor(new WorkflowPlanner(model, mapper, List.of(workers)), config, executor);
    }

    private WorkflowSupervisor billingSupervisor() {
        return supervisor(billing.lookupRate(retrieval), billing.lookupUsage(), billing.calculateCost());
    }

    @Test void endpointUsesRegisteredWorkersAndReturnsExactCalculatedAmount() throws Exception {
        when(model.generate(anyString())).thenReturn(BILLING_PLAN);
        when(retrieval.retrieve("billing-rate storage", 5))
                .thenReturn(List.of("Policy\nbilling-rate|storage|GB|CNY|7.00\nbilling-rate|other|GB|CNY|99"));
        var filter = mock(InputFilter.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AIController(null, null, billingSupervisor(), null, filter)).build();
        mvc.perform(post("/ai/multi-agent/workflow").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(REQUEST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.output.amount").value(98))
                .andExpect(jsonPath("$.output.currency").value("CNY"))
                .andExpect(jsonPath("$.steps[0].id").value("cost"))
                .andExpect(jsonPath("$.steps[2].output.quantity").value(14));
        verify(filter).validate(QUERY);
        verify(model).generate(argThat(prompt -> prompt.contains("lookup_rate") && prompt.contains("calculate_cost")));
    }

    @Test void missingRateSkipsDependentStepAndPreservesUsage() {
        when(model.generate(anyString())).thenReturn(BILLING_PLAN);
        when(retrieval.retrieve(anyString(), anyInt())).thenReturn(List.of("billing-rate|other|GB|CNY|7"));
        var result = billingSupervisor().coordinate(REQUEST);
        assertEquals(WorkflowSupervisor.Status.PARTIAL, result.status());
        assertNull(result.output());
        assertEquals(List.of(WorkflowSupervisor.StepStatus.SKIPPED_DEPENDENCY, WorkflowSupervisor.StepStatus.FAILED,
                        WorkflowSupervisor.StepStatus.SUCCESS),
                result.steps().stream().map(WorkflowSupervisor.StepResult::status).toList());
    }

    @Test void invalidReferenceIsRejectedBeforeAnyWorkerRuns() {
        when(model.generate(anyString())).thenReturn(BILLING_PLAN.replace("\"from\":\"rate\"", "\"from\":\"usage\""));
        assertEquals(WorkflowSupervisor.Status.INVALID_PLAN, billingSupervisor().coordinate(REQUEST).status());
        verifyNoInteractions(retrieval);
    }

    @Test void cyclicPlanIsRejectedBeforeAnyWorkerRuns() {
        WorkflowWorker copy = mock(WorkflowWorker.class);
        when(copy.name()).thenReturn("copy");
        when(copy.inputs()).thenReturn(Map.of("text", String.class));
        when(copy.outputType()).thenAnswer(call -> String.class);
        when(model.generate(anyString())).thenReturn("""
                {"steps":[{"id":"a","worker":"copy","inputs":{"text":{"from":"b"}}},
                          {"id":"b","worker":"copy","inputs":{"text":{"from":"a"}}}],"resultStep":"b"}
                """);
        assertEquals(WorkflowSupervisor.Status.INVALID_PLAN, supervisor(copy).coordinate(REQUEST).status());
        verify(copy, never()).execute(anyMap(), any());
    }

    @Test void independentWorkersOverlapAndACustomFourthWorkerReceivesTheirFinalResult() throws Exception {
        CountDownLatch usageRunning = new CountDownLatch(1);
        when(retrieval.retrieve(anyString(), anyInt())).thenAnswer(call -> {
            assertTrue(usageRunning.await(2, TimeUnit.SECONDS), "usage should run while rate lookup waits");
            return List.of("billing-rate|storage|GB|CNY|7");
        });
        WorkflowWorker lookupUsage = billing.lookupUsage();
        WorkflowWorker usage = mock(WorkflowWorker.class);
        when(usage.name()).thenReturn(lookupUsage.name());
        when(usage.description()).thenReturn(lookupUsage.description());
        when(usage.inputs()).thenReturn(lookupUsage.inputs());
        when(usage.outputType()).thenAnswer(call -> lookupUsage.outputType());
        when(usage.execute(anyMap(), any())).thenAnswer(call -> {
            usageRunning.countDown();
            return lookupUsage.execute(call.getArgument(0), call.getArgument(1));
        });
        var format = mock(WorkflowWorker.class);
        when(format.name()).thenReturn("format");
        when(format.inputs()).thenReturn(Map.of("cost", BillingWorkflow.Cost.class));
        when(format.outputType()).thenAnswer(call -> String.class);
        when(format.execute(anyMap(), any())).thenAnswer(call -> {
            Map<?, ?> inputs = call.getArgument(0);
            BillingWorkflow.Cost cost = (BillingWorkflow.Cost) inputs.get("cost");
            return cost.amount().toPlainString() + " " + cost.currency();
        });
        when(model.generate(anyString())).thenReturn(BILLING_PLAN.replace("\"resultStep\":\"cost\"", "\"resultStep\":\"formatted\"")
                .replace("{\"steps\":[", """
                        {"steps":[{"id":"formatted","worker":"format","inputs":{"cost":{"from":"cost"}}},
                        """));
        var result = supervisor(billing.lookupRate(retrieval), usage, billing.calculateCost(), format).coordinate(REQUEST);
        assertEquals(WorkflowSupervisor.Status.COMPLETED, result.status());
        assertEquals("98 CNY", result.output());
        assertEquals(4, result.steps().size());
    }

    @Test void planningTimeoutDoesNotStartWorkers() throws Exception {
        config.setSubTaskTimeoutSeconds(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(model.generate(anyString())).thenAnswer(call -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException ex) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return BILLING_PLAN;
        });
        var result = billingSupervisor().coordinate(REQUEST);
        assertEquals(WorkflowSupervisor.Status.TIMED_OUT, result.status());
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        verifyNoInteractions(retrieval);
    }

    @Test void workerCannotStartADependentStepAfterTheSharedBudgetExpires() {
        config.setSubTaskTimeoutSeconds(1);
        when(model.generate(anyString())).thenAnswer(call -> {
            Thread.sleep(550);
            return BILLING_PLAN;
        });
        when(retrieval.retrieve(anyString(), anyInt())).thenAnswer(call -> {
            Thread.sleep(700);
            return List.of("billing-rate|storage|GB|CNY|7");
        });
        var result = billingSupervisor().coordinate(REQUEST);
        assertEquals(WorkflowSupervisor.Status.PARTIAL, result.status());
        assertNull(result.output());
        assertEquals(List.of(WorkflowSupervisor.StepStatus.TIMED_OUT, WorkflowSupervisor.StepStatus.TIMED_OUT,
                        WorkflowSupervisor.StepStatus.SUCCESS),
                result.steps().stream().map(WorkflowSupervisor.StepResult::status).toList());
    }
}
