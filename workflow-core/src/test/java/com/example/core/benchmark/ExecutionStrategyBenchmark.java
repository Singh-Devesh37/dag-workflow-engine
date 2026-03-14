package com.example.core.benchmark;

import com.example.core.engine.*;
import com.example.core.model.TaskExecutionResult;
import com.example.core.model.TaskNode;
import com.example.core.model.WorkflowRun;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * JMH Benchmark comparing execution strategies for DAG workloads.
 *
 * <p>Benchmarks three execution strategies:
 * <ul>
 *   <li>ThreadPool (baseline) - Fixed thread pool</li>
 *   <li>VirtualThreads - Java 21 Project Loom</li>
 *   <li>ForkJoin - Work-stealing pool</li>
 * </ul>
 *
 * <p><b>How to run:</b>
 * <pre>
 * mvn clean package
 * java -jar target/benchmarks.jar
 * </pre>
 *
 * <p><b>Benchmark scenarios:</b>
 * <ul>
 *   <li>Wide DAG: 100 parallel I/O tasks</li>
 *   <li>Deep DAG: 50 sequential tasks</li>
 *   <li>Mixed DAG: Realistic workflow pattern</li>
 *   <li>CPU-bound: Compute-intensive tasks</li>
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(1)
public class ExecutionStrategyBenchmark {

    @Param({"ThreadPool", "VirtualThreads", "ForkJoin"})
    private String strategyType;

    @Param({"50", "100", "200"})
    private int taskCount;

    private WorkflowEngine engine;
    private ExecutionStrategy strategy;
    private ScheduledExecutorService scheduler;

    @Setup(Level.Trial)
    public void setup() {
        // Create execution strategy based on parameter
        strategy = switch (strategyType) {
            case "VirtualThreads" -> new VirtualThreadExecutionStrategy();
            case "ForkJoin" -> new ForkJoinExecutionStrategy();
            default -> new ThreadPoolExecutionStrategy(Runtime.getRuntime().availableProcessors() * 2);
        };

        scheduler = Executors.newScheduledThreadPool(1);

        // Create mock task executor factory
        TaskExecutorFactory factory = (type, config) -> new TaskExecutor() {
            @Override
            public TaskExecutionResult execute(ExecutionContext context) {
                // Simulate I/O-bound work (e.g., HTTP call)
                try {
                    Thread.sleep(10); // 10ms simulated I/O
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return TaskExecutionResult.success(
                    "Success",
                    Map.of("result", "done"),
                    java.time.Instant.now(),
                    java.time.Instant.now()
                );
            }

            @Override
            public com.example.core.enums.TaskType getType() {
                return com.example.core.enums.TaskType.HTTP;
            }
        };

        RetryingTaskRunner taskRunner = new RetryingTaskRunner(
            scheduler,
            strategy,
            new SimpleMeterRegistry(),
            factory,
            0, // no retries for benchmark
            100
        );

        engine = new WorkflowEngine(
            Executors.newCachedThreadPool(),
            null, // no event publisher for benchmark
            scheduler,
            taskRunner,
            new SimpleMeterRegistry()
        );
    }

    @TearDown(Level.Trial)
    public void teardown() {
        strategy.shutdown();
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Wide DAG: All tasks run in parallel (no dependencies).
     * This tests maximum concurrency and should favor VirtualThreads.
     */
    @Benchmark
    public void wideDAG() throws Exception {
        List<TaskNode> tasks = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            TaskNode task = new TaskNode("task-" + i, createMockTaskExecutor(10), Map.of());
            tasks.add(task);
        }

        ExecutionContext context = new ExecutionContext();
        CompletableFuture<WorkflowRun> future = engine.execute(
            "run-" + System.nanoTime(),
            "workflow-wide",
            tasks,
            context
        );

        future.get(30, TimeUnit.SECONDS);
    }

    /**
     * Deep DAG: All tasks in a chain (each depends on previous).
     * This tests sequential execution - all strategies should be similar.
     */
    @Benchmark
    public void deepDAG() throws Exception {
        List<TaskNode> tasks = new ArrayList<>();
        TaskNode previous = null;

        for (int i = 0; i < taskCount; i++) {
            TaskNode task = new TaskNode("task-" + i,createMockTaskExecutor(10), Map.of());

            if (previous != null) {
                task.addDependency(previous);
            }

            tasks.add(task);
            previous = task;
        }

        ExecutionContext context = new ExecutionContext();
        CompletableFuture<WorkflowRun> future = engine.execute(
            "run-" + System.nanoTime(),
            "workflow-deep",
            tasks,
            context
        );

        future.get(30, TimeUnit.SECONDS);
    }

    /**
     * Mixed DAG: Realistic workflow with branches and joins.
     * Tests mixed parallelism - VirtualThreads should still win.
     */
    @Benchmark
    public void mixedDAG() throws Exception {
        List<TaskNode> tasks = new ArrayList<>();

        // Root task
        TaskNode root = new TaskNode("root", createMockTaskExecutor(10), Map.of());
        tasks.add(root);

        // Create branches (50% of tasks)
        List<TaskNode> branchTasks = new ArrayList<>();
        for (int i = 0; i < taskCount / 2; i++) {
            TaskNode branch = new TaskNode("branch-" + i, createMockTaskExecutor(10), Map.of());
            branch.addDependency(root);
            tasks.add(branch);
            branchTasks.add(branch);
        }

        // Join task depends on all branches
        TaskNode join = new TaskNode("join", createMockTaskExecutor(10), Map.of());
        for (TaskNode branch : branchTasks) {
            join.addDependency(branch);
        }
        tasks.add(join);

        // Final tasks in parallel
        for (int i = 0; i < taskCount / 2; i++) {
            TaskNode finalTask = new TaskNode("final-" + i, createMockTaskExecutor(10), Map.of());
            finalTask.addDependency(join);
            tasks.add(finalTask);
        }

        ExecutionContext context = new ExecutionContext();
        CompletableFuture<WorkflowRun> future = engine.execute(
            "run-" + System.nanoTime(),
            "workflow-mixed",
            tasks,
            context
        );

        future.get(60, TimeUnit.SECONDS);
    }

    private TaskExecutor createMockTaskExecutor(long sleepMs) {
        return new TaskExecutor() {
            @Override
            public TaskExecutionResult execute(ExecutionContext context) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return TaskExecutionResult.success(
                    "Success",
                    Map.of("result", "done"),
                    java.time.Instant.now(),
                    java.time.Instant.now()
                );
            }

            @Override
            public com.example.core.enums.TaskType getType() {
                return com.example.core.enums.TaskType.HTTP;
            }
        };
    }
}