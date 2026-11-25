package com.aiagent.agent.workflow;

import com.aiagent.agent.config.AgentConfig;
import com.aiagent.agent.workflow.WorkflowPlanner.Plan;
import com.aiagent.agent.workflow.WorkflowPlanner.Step;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class WorkflowSupervisor {
    private final WorkflowPlanner planner;
    private final AgentConfig config;
    private final ExecutorService executor;

    public WorkflowSupervisor(WorkflowPlanner planner, AgentConfig config,
                              @Qualifier("agentTaskExecutor") ExecutorService executor) {
        this.planner = planner;
        this.config = config;
        this.executor = executor;
    }

    public Result coordinate(WorkflowRequest request) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.getSubTaskTimeoutSeconds());
        try {
            return execute(plan(request.query(), deadline), request, deadline);
        } catch (RejectedExecutionException ex) {
            return new Result(Status.BUSY, "编排线程池繁忙", null, List.of());
        } catch (TimeoutException ex) {
            return new Result(Status.TIMED_OUT, "规划超时，未执行任何步骤", null, List.of());
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof TimeoutException) {
                return new Result(Status.TIMED_OUT, "规划超时，未执行任何步骤", null, List.of());
            }
            if (ex.getCause() instanceof WorkflowPlanner.InvalidPlan) {
                return new Result(Status.INVALID_PLAN, "模型未给出可执行的计划", null, List.of());
            }
            log.warn("Workflow planning failed", ex.getCause());
            return new Result(Status.FAILED, "规划调用失败", null, List.of());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(Status.FAILED, "编排已中断", null, List.of());
        }
    }

    private Plan plan(String query, long deadline) throws InterruptedException, ExecutionException, TimeoutException {
        Future<Plan> planning = executor.submit(() -> {
            if (remaining(deadline) == 0) throw new TimeoutException();
            return planner.plan(query);
        });
        try {
            return planning.get(remaining(deadline), TimeUnit.NANOSECONDS);
        } finally {
            if (!planning.isDone()) planning.cancel(true);
        }
    }

    private Result execute(Plan plan, WorkflowRequest request, long deadline) throws InterruptedException {
        var completion = new ExecutorCompletionService<StepResult>(executor);
        var waiting = new LinkedHashMap<String, Step>();
        plan.steps().forEach(step -> waiting.put(step.id(), step));
        var running = new HashMap<Future<StepResult>, Step>();
        var completed = new HashMap<String, StepResult>();

        try {
            while (!waiting.isEmpty() || !running.isEmpty()) {
                scheduleReady(request, deadline, completion, waiting, running, completed);
                if (running.isEmpty()) {
                    if (remaining(deadline) == 0) break;
                    continue;
                }
                Future<StepResult> finished = completion.poll(remaining(deadline), TimeUnit.NANOSECONDS);
                if (finished == null) break;
                collect(finished, running, completed);
            }
            Future<StepResult> finished;
            while ((finished = completion.poll()) != null) collect(finished, running, completed);
        } finally {
            running.keySet().forEach(future -> future.cancel(true));
        }

        List<StepResult> results = plan.steps().stream()
                .map(step -> completed.getOrDefault(step.id(), failure(step, StepStatus.TIMED_OUT, "请求时间预算已用完")))
                .toList();
        StepResult target = completed.get(plan.resultStep());
        if (target != null && target.status() == StepStatus.SUCCESS) {
            return new Result(Status.COMPLETED, "已取得所需结果", target.output(), results);
        }
        if (results.stream().anyMatch(step -> step.status() == StepStatus.SUCCESS)) {
            return new Result(Status.PARTIAL, "只完成部分步骤，未取得最终结果", null, results);
        }
        Status status = results.stream().anyMatch(step -> step.status() == StepStatus.TIMED_OUT)
                ? Status.TIMED_OUT : Status.FAILED;
        return new Result(status, "未取得可用结果", null, results);
    }

    private void scheduleReady(WorkflowRequest request, long deadline, ExecutorCompletionService<StepResult> completion,
                               Map<String, Step> waiting, Map<Future<StepResult>, Step> running,
                               Map<String, StepResult> completed) {
        var iterator = waiting.values().iterator();
        while (iterator.hasNext() && remaining(deadline) > 0) {
            Step step = iterator.next();
            var dependencies = step.dependencies();
            if (!completed.keySet().containsAll(dependencies)) continue;
            iterator.remove();
            if (dependencies.stream().anyMatch(id -> completed.get(id).status() != StepStatus.SUCCESS)) {
                completed.put(step.id(), failure(step, StepStatus.SKIPPED_DEPENDENCY, "上游步骤未提供所需结果"));
                continue;
            }
            var inputs = new HashMap<String, Object>();
            step.inputs().forEach((key, input) -> inputs.put(key,
                    input.from() == null ? input.value().textValue() : completed.get(input.from()).output()));
            try {
                Future<StepResult> future = completion.submit(() -> run(step, inputs, request, deadline));
                running.put(future, step);
            } catch (RejectedExecutionException ex) {
                completed.put(step.id(), failure(step, StepStatus.REJECTED, "编排线程池繁忙"));
            }
        }
    }

    private StepResult run(Step step, Map<String, Object> inputs, WorkflowRequest request, long deadline) {
        if (remaining(deadline) == 0) return failure(step, StepStatus.TIMED_OUT, "请求时间预算已用完");
        var worker = planner.worker(step.worker());
        try {
            Object output = worker.execute(Map.copyOf(inputs), request);
            if (remaining(deadline) == 0) return failure(step, StepStatus.TIMED_OUT, "请求时间预算已用完");
            if (!worker.outputType().isInstance(output)) return failure(step, StepStatus.FAILED, "Worker 返回了无效结果");
            return new StepResult(step.id(), step.worker(), StepStatus.SUCCESS, output, null);
        } catch (BillingWorkflow.UnavailableData ex) {
            return failure(step, StepStatus.FAILED, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Workflow worker {} failed", step.worker(), ex);
            return failure(step, StepStatus.FAILED, "Worker 执行失败");
        }
    }

    private void collect(Future<StepResult> finished, Map<Future<StepResult>, Step> running,
                         Map<String, StepResult> completed) throws InterruptedException {
        Step step = running.remove(finished);
        try {
            completed.put(step.id(), finished.get());
        } catch (ExecutionException ex) {
            log.warn("Workflow worker {} terminated unexpectedly", step.worker(), ex.getCause());
            completed.put(step.id(), failure(step, StepStatus.FAILED, "Worker 执行失败"));
        }
    }

    private static long remaining(long deadline) {
        return Math.max(0L, deadline - System.nanoTime());
    }

    private static StepResult failure(Step step, StepStatus status, String detail) {
        return new StepResult(step.id(), step.worker(), status, null, detail);
    }

    public enum Status { COMPLETED, PARTIAL, INVALID_PLAN, TIMED_OUT, BUSY, FAILED }
    public enum StepStatus { SUCCESS, FAILED, SKIPPED_DEPENDENCY, TIMED_OUT, REJECTED }
    public record StepResult(String id, String worker, StepStatus status, Object output, String detail) {}
    public record Result(Status status, String message, Object output, List<StepResult> steps) {}
}
