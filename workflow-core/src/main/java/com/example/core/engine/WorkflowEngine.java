package com.example.core.engine;

import com.example.core.enums.RunStatus;
import com.example.core.exception.WorkflowEngineException;
import com.example.core.model.*;
import com.example.core.pub.WorkflowEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service("WorkflowEngine")
public class WorkflowEngine {
  private static final Logger logger = LoggerFactory.getLogger(WorkflowEngine.class);

  private final ExecutorService executorService;
  private final WorkflowEventPublisher publisher;
  private final ScheduledExecutorService scheduler;
  private final RetryingTaskRunner retryingTaskRunner;
  private final MeterRegistry meterRegistry;
  private final Counter workflowsStarted;
  private final Counter workflowsCompleted;
  private final Counter workflowsFailed;
  private final Timer workflowDuration;
  private final AtomicInteger runningWorkflowCount = new AtomicInteger(0);

  public WorkflowEngine(
      ExecutorService executorService,
      WorkflowEventPublisher publisher,
      ScheduledExecutorService scheduler,
      RetryingTaskRunner retryingTaskRunner,
      MeterRegistry meterRegistry) {
    this.executorService = executorService;
    this.publisher = publisher;
    this.scheduler = scheduler;
    this.retryingTaskRunner = retryingTaskRunner;
    this.meterRegistry = meterRegistry;
    this.workflowsStarted =
        Counter.builder("workflow_total")
            .tag("status", "started")
            .description("Total workflows started")
            .register(meterRegistry);

    this.workflowsCompleted =
        Counter.builder("workflow_total")
            .tag("status", "completed")
            .description("Total workflows completed successfully")
            .register(meterRegistry);

    this.workflowsFailed =
        Counter.builder("workflow_total")
            .tag("status", "failed")
            .description("Total workflows failed")
            .register(meterRegistry);

    this.workflowDuration =
        Timer.builder("workflow_duration_seconds")
            .description("Workflow total duration")
            .publishPercentileHistogram()
            .register(meterRegistry);

    Gauge.builder("workflow_running_gauge", runningWorkflowCount, AtomicInteger::get)
        .description("Currently running workflows")
        .register(meterRegistry);
  }

  public CompletableFuture<WorkflowRun> execute(
      String runId, String workflowId, List<TaskNode> tasks, ExecutionContext globalContext) {
    Instant workflowStart = Instant.now();
    WorkflowRun workflowRun = new WorkflowRun(runId, workflowId);
    workflowRun.setStartTime(workflowStart);
    workflowRun.setStatus(RunStatus.RUNNING);
    workflowsStarted.increment();
    runningWorkflowCount.incrementAndGet();
    Timer.Sample sample = Timer.start();

    Map<String, AtomicInteger> dependencyCount = new ConcurrentHashMap<>();
    Map<String, List<String>> downstreamMap = new ConcurrentHashMap<>();

    for (TaskNode t : tasks) {
      dependencyCount.put(t.getName(), new AtomicInteger(t.getDependencies().size()));
      for (TaskNode dep : t.getDependencies()) {
        downstreamMap
            .computeIfAbsent(dep.getName(), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(t.getName());
      }

      TaskRun taskRun = new TaskRun(t.getName(), workflowId);
      taskRun.setMaxRetries(t.getMaxRetries());
      workflowRun.putTaskRun(taskRun);
    }

    publishWorkflowUpdate(workflowRun);

    Map<String, CompletableFuture<TaskExecutionResult>> futureMap = new ConcurrentHashMap<>();

    for (TaskNode t : tasks) {
      if (t.getDependencies().isEmpty()) {
        CompletableFuture<TaskExecutionResult> future = startTask(t, workflowRun, globalContext);
        futureMap.put(t.getName(), future);
      } else {
        List<CompletableFuture<TaskExecutionResult>> futures =
            t.getDependencies().stream()
                .map(
                    dep -> futureMap.computeIfAbsent(dep.getName(), k -> new CompletableFuture<>()))
                .toList();
        CompletableFuture<Void> allDependencies =
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        CompletableFuture<TaskExecutionResult> future =
            allDependencies.thenComposeAsync(
                v -> {
                  boolean anyFailed =
                      futures.stream()
                          .anyMatch(
                              f -> {
                                TaskExecutionResult result = f.getNow(null);
                                return result == null || !result.isSuccess();
                              });
                  if (anyFailed) {
                    TaskRun tr = workflowRun.getTaskRuns().get(t.getName());
                    tr.setStatus(RunStatus.SKIPPED);
                    tr.setStartTime(null);
                    tr.setEndTime(Instant.now());
                    tr.setDurationMillis(0L);
                    tr.setErrorMessage("Task skipped due to upstream failure");
                    publishTaskUpdate(workflowRun, tr);
                    return CompletableFuture.completedFuture(
                        TaskExecutionResult.failure(
                                "Task skipped due to upstream failure",
                                new WorkflowEngineException("Task skipped due to upstream failure"),
                                Instant.now(), Instant.now()));
                  } else {
                    return startTask(t, workflowRun, globalContext);
                  }
                },
                executorService);
      }
    }

    CompletableFuture<Void> all =
        CompletableFuture.allOf(futureMap.values().toArray(new CompletableFuture[0]));

    return all.handleAsync(
        (v, ex) -> {
          Instant end = Instant.now();
          workflowRun.setEndTime(end);
          workflowRun.setDurationMillis(Duration.between(workflowStart, end).toMillis());

          boolean anyFailed =
              workflowRun.getTaskRuns().values().stream()
                  .anyMatch(tr -> tr.getStatus() == RunStatus.FAILED);
          boolean anySkipped =
              workflowRun.getTaskRuns().values().stream()
                  .anyMatch(tr -> tr.getStatus() == RunStatus.SKIPPED);

          if (!anyFailed && !anySkipped) {
            workflowRun.setStatus(RunStatus.SUCCESS);
            workflowsCompleted.increment();
          } else if (anyFailed) {
            workflowRun.setStatus(RunStatus.FAILED);
            workflowsFailed.increment();
          } else {
            workflowRun.setStatus(RunStatus.FAILED);
            workflowsFailed.increment();
          }
          sample.stop(workflowDuration);
          runningWorkflowCount.decrementAndGet();
          publishWorkflowUpdate(workflowRun);
          if (ex != null)
            logger.warn("Workflow {} finished with exception: {}", runId, ex.toString());
          return workflowRun;
        },
        executorService);
  }

  private CompletableFuture<TaskExecutionResult> startTask(
      TaskNode node, WorkflowRun workflowRun, ExecutionContext globalContext) {
    TaskRun taskRun = workflowRun.getTaskRuns().get(node.getName());
    taskRun.setStartTime(Instant.now());
    taskRun.incrementAttempt();
    taskRun.setStatus(RunStatus.RUNNING);
    publisher.publishTaskUpdate(workflowRun.getRunId(), taskRun);

    CompletableFuture<TaskExecutionResult> future =
        retryingTaskRunner.runWithRetry(node, globalContext, taskRun.getAttempt().get());

    future.whenCompleteAsync(
        (result, ex) -> {
          if (result != null && result.isSuccess()) {
            taskRun.setStatus(RunStatus.SUCCESS);
            taskRun.setEndTime(Instant.now());
            taskRun.setDurationMillis(
                Duration.between(taskRun.getStartTime(), taskRun.getEndTime()).toMillis());
            taskRun.setContextDelta(result.getContextDelta());
            Map<String, Object> contextDelta = result.getContextDelta();
            if (contextDelta != null) {
              globalContext.merge(contextDelta);
              contextDelta.forEach((k, v) -> workflowRun.getMergedContextSnapshot().put(k, v));
            }
          } else {
            taskRun.setStatus(RunStatus.FAILED);
            taskRun.setEndTime(Instant.now());
            taskRun.setDurationMillis(
                Duration.between(taskRun.getStartTime(), taskRun.getEndTime()).toMillis());
            taskRun.setErrorMessage(
                truncate(
                    ex == null
                        ? Optional.ofNullable(result == null ? null : result.getError())
                            .map(Throwable::getMessage)
                            .orElse("error")
                        : ex.getMessage(),
                    500));
            taskRun.setErrorStackTrace(
                truncate(
                    ex == null
                        ? Optional.ofNullable(result == null ? null : result.getError())
                            .map(Throwable::getMessage)
                            .orElse("error")
                        : ex.getMessage(),
                    2000));
            publishTaskUpdate(workflowRun, taskRun);
          }
        },
        executorService);

    return future;
  }



  private void publishTaskUpdate(WorkflowRun wr, TaskRun tr) {
    if (publisher == null) return;
    publisher.publishTaskUpdate(wr.getRunId(), tr);
  }

  private void publishWorkflowUpdate(WorkflowRun wr) {
    publisher.publishWorkflowUpdate(wr.getRunId(), wr);
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
  }
}
