package com.example.core.engine;

import com.example.core.model.TaskExecutionResult;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Strategy interface for different task execution approaches.
 *
 * <p>Implementations provide different concurrency models for executing workflow tasks:
 * <ul>
 *   <li>{@link ThreadPoolExecutionStrategy} - Traditional thread pool (baseline)</li>
 *   <li>{@link VirtualThreadExecutionStrategy} - Java 21 Virtual Threads (Project Loom)</li>
 *   <li>{@link ForkJoinExecutionStrategy} - Work-stealing Fork/Join framework</li>
 * </ul>
 *
 * <p>Each strategy handles task submission and asynchronous execution differently,
 * enabling performance comparisons across concurrency models.
 */
public interface ExecutionStrategy {

    /**
     * Submits a task for asynchronous execution.
     *
     * @param task The task to execute, wrapped as a Supplier
     * @return A CompletableFuture representing the asynchronous task execution
     */
    CompletableFuture<TaskExecutionResult> submitTask(Supplier<TaskExecutionResult> task);

    /**
     * Returns the name of this execution strategy.
     *
     * @return Strategy name (e.g., "ThreadPool", "VirtualThreads", "ForkJoin")
     */
    String getStrategyName();

    /**
     * Shuts down the underlying executor, releasing all resources.
     * Should be called during application shutdown.
     */
    void shutdown();
}