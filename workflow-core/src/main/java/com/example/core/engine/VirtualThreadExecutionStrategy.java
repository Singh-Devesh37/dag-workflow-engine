package com.example.core.engine;

import com.example.core.model.TaskExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Java 21 Virtual Threads execution strategy (Project Loom).
 *
 * <p>This strategy leverages Java 21's Virtual Threads to achieve massive concurrency
 * with minimal resource overhead. Virtual threads are lightweight, user-mode threads
 * scheduled by the JVM rather than the OS.
 *
 * <p><b>Key Advantages:</b>
 * <ul>
 *   <li>Millions of virtual threads can run concurrently (vs thousands of platform threads)</li>
 *   <li>Minimal memory footprint (~1KB per thread vs ~1MB for platform threads)</li>
 *   <li>Blocking operations don't hold carrier threads (automatic yielding)</li>
 *   <li>Perfect for I/O-bound workloads (HTTP, Kafka, Email, etc.)</li>
 *   <li>No need to tune thread pool size - create a thread per task</li>
 * </ul>
 *
 * <p><b>How It Works:</b>
 * <ul>
 *   <li>Virtual threads are mounted on a small pool of carrier (platform) threads</li>
 *   <li>When a virtual thread blocks (I/O, sleep), it's unmounted and the carrier is freed</li>
 *   <li>Work-stealing scheduler ensures efficient carrier utilization</li>
 *   <li>JVM handles all scheduling automatically</li>
 * </ul>
 *
 * <p><b>Performance Expectations:</b>
 * <ul>
 *   <li>Wide DAG (100 parallel I/O tasks): 5-10x better throughput than ThreadPool</li>
 *   <li>Deep DAG (sequential): Similar to ThreadPool (no parallelism to exploit)</li>
 *   <li>Mixed DAG: 3-5x better throughput</li>
 *   <li>Lower latency under high load due to reduced context switching</li>
 * </ul>
 *
 * <p><b>Requirements:</b>
 * <ul>
 *   <li>Java 21 or later</li>
 *   <li>JVM flag: --enable-preview (if using preview features)</li>
 * </ul>
 */
public class VirtualThreadExecutionStrategy implements ExecutionStrategy {
    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadExecutionStrategy.class);

    private final ExecutorService executorService;

    /**
     * Creates a Virtual Threads strategy using Executors.newVirtualThreadPerTaskExecutor().
     *
     * <p>This creates an unbounded executor where each task runs on a new virtual thread.
     * Virtual threads are so lightweight that we don't need to pool them.
     */
    public VirtualThreadExecutionStrategy() {
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        logger.info("Initialized VirtualThreads execution strategy (Java 21 Project Loom)");
        logger.info("Virtual threads: unlimited concurrency, minimal memory overhead");
    }

    @Override
    public CompletableFuture<TaskExecutionResult> submitTask(Supplier<TaskExecutionResult> task) {
        return CompletableFuture.supplyAsync(task, executorService);
    }

    @Override
    public String getStrategyName() {
        return "VirtualThreads";
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down VirtualThreads execution strategy");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
}