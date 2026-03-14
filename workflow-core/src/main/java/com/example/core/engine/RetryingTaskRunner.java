package com.example.core.engine;

import com.example.core.exception.WorkflowEngineException;
import com.example.core.model.TaskExecutionResult;
import com.example.core.model.TaskNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RetryingTaskRunner {
  private static final Logger logger = LoggerFactory.getLogger(RetryingTaskRunner.class);

  private final ScheduledExecutorService scheduler;
  private final ExecutionStrategy executionStrategy;
  private final int defaultMaxRetries;
  private final long defaultInitialDelay;
  private final MeterRegistry meterRegistry;
  private final Counter taskSuccess;
  private final Counter taskFailure;
  private final Counter taskRetry;
  private final Timer taskDuration;
  private final AtomicInteger runningTasks = new AtomicInteger(0);

  public RetryingTaskRunner(
      ScheduledExecutorService scheduler,
      ExecutionStrategy executionStrategy,
      MeterRegistry meterRegistry,
      TaskExecutorFactory taskExecutorFactory,
      int defaultMaxRetries,
      long defaultInitialDelay) {
    this.scheduler = scheduler;
    this.executionStrategy = executionStrategy;
    this.meterRegistry = meterRegistry;
    this.defaultMaxRetries = defaultMaxRetries;
    this.defaultInitialDelay = Math.max(10L, defaultInitialDelay);

    logger.info("RetryingTaskRunner initialized with {} execution strategy",
        executionStrategy.getStrategyName());
    this.taskSuccess =
        Counter.builder("task_total")
            .tag("status", "success")
            .description("Total successful task executions")
            .register(meterRegistry);

    this.taskFailure =
        Counter.builder("task_total")
            .tag("status", "failed")
            .description("Total failed task executions")
            .register(meterRegistry);

    this.taskRetry =
        Counter.builder("task_retry_total")
            .description("Total retry attempts across all tasks")
            .register(meterRegistry);

    this.taskDuration =
        Timer.builder("task_duration_seconds")
            .description("Time taken to execute a task")
            .publishPercentileHistogram()
            .register(meterRegistry);

    Gauge.builder("task_active_gauge", runningTasks, AtomicInteger::get)
        .description("Currently active tasks")
        .register(meterRegistry);
  }

  public CompletableFuture<TaskExecutionResult> runWithRetry(
      TaskNode node, ExecutionContext context, int initialAttempt) {
    CompletableFuture<TaskExecutionResult> future = new CompletableFuture<>();
    int maxRetries =
        Math.max(0, node.getMaxRetries() == 0 ? defaultMaxRetries : node.getMaxRetries());
    long[] delay = {
      Math.max(
          10L,
          node.getInitialDelayMillis() == 0 ? defaultInitialDelay : node.getInitialDelayMillis())
    };
    AtomicInteger attempt = new AtomicInteger(Math.max(1, initialAttempt));

    Runnable task =
        new Runnable() {
          @Override
          public void run() {
            ExecutionContext snap = context.clone();
            runningTasks.incrementAndGet();
            Timer.Sample sample = Timer.start();

            // Submit task using the configured execution strategy
            executionStrategy.submitTask(
                    () -> {
                      Instant s = Instant.now();
                      try {
                        TaskExecutionResult result = node.getTaskExecutor().execute(snap);
                        if (result == null) {
                          taskFailure.increment();
                          return TaskExecutionResult.failure(
                                  "NULL Task Run Result",
                                  new WorkflowEngineException("NULL Task Run Result"),
                                  s, Instant.now());
                        }
                        if (result.isSuccess()) {
                          taskSuccess.increment();
                          sample.stop(taskDuration);
                          runningTasks.decrementAndGet();
                          return result;
                        }
                        taskFailure.increment();
                        sample.stop(taskDuration);
                        runningTasks.decrementAndGet();
                        return TaskExecutionResult.failure(
                                "Task Run Failed",
                                (result.getError() == null)
                                    ? new WorkflowEngineException("Task Run Failed with no Errors")
                                    : result.getError(),
                                s, Instant.now());
                      } catch (Throwable ex) {
                        taskFailure.increment();
                        sample.stop(taskDuration);
                        runningTasks.decrementAndGet();
                        return TaskExecutionResult.failure(
                                ex.getMessage() != null ? ex.getMessage() : "Task execution failed",
                                ex, s, Instant.now());
                      }
                    })
                .whenComplete(
                    (result, ex) -> {
                      if (ex != null || (result != null && !result.isSuccess())) {
                        int a = attempt.getAndIncrement();
                        if (a > maxRetries) {
                          // Max retries exceeded, complete with failure
                          if (ex != null) {
                            future.complete(
                                TaskExecutionResult.failure(
                                    ex.getMessage() != null ? ex.getMessage() : "Task failed after retries",
                                    ex, Instant.now(), Instant.now()));
                          } else {
                            future.complete(result);
                          }
                        } else {
                          // Schedule retry with exponential backoff + jitter
                          long j = jitter(delay[0]);
                          delay[0] = delay[0] * 2;
                          taskRetry.increment();
                          logger.debug("Retrying task {} - attempt {}/{}",
                              node.getName(), a, maxRetries);
                          scheduler.schedule(this, j, TimeUnit.MILLISECONDS);
                        }
                      } else {
                        // Task succeeded, complete the future
                        future.complete(result);
                      }
                    });
          }
        };

    // Start the task execution
    task.run();
    return future;
  }

  private static long jitter(long base) {
    double f = 0.8 + Math.random() * 0.4;
    return Math.max(1L, (long) (base * f));
  }
}
