package com.example.core.engine;

import com.example.core.model.TaskExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Fork/Join work-stealing execution strategy.
 *
 * <p>This strategy uses the {@link ForkJoinPool} framework, which implements a work-stealing
 * algorithm. Idle threads "steal" tasks from busy threads' queues, improving load balancing.
 *
 * <p><b>How Work-Stealing Works:</b>
 * <ul>
 *   <li>Each thread has its own double-ended queue (deque) of tasks</li>
 *   <li>Threads push/pop tasks from their own queue (LIFO for locality)</li>
 *   <li>Idle threads steal tasks from other threads' queues (FIFO to minimize contention)</li>
 *   <li>Reduces synchronization overhead compared to shared task queue</li>
 * </ul>
 *
 * <p><b>Characteristics:</b>
 * <ul>
 *   <li>Default parallelism: number of CPU cores</li>
 *   <li>Optimized for compute-intensive tasks with subtask spawning</li>
 *   <li>Better load balancing than fixed thread pool for recursive workloads</li>
 *   <li>Uses asynchronous mode (queue-based, not recursive fork/join)</li>
 * </ul>
 *
 * <p><b>Performance Expectations:</b>
 * <ul>
 *   <li>Similar to ThreadPool for I/O-bound tasks (limited by thread count)</li>
 *   <li>Better than ThreadPool for CPU-bound tasks with dynamic parallelism</li>
 *   <li>Work-stealing reduces idle time when tasks have uneven durations</li>
 *   <li>Lower overhead than ThreadPool for short-lived tasks</li>
 * </ul>
 *
 * <p><b>Best For:</b>
 * <ul>
 *   <li>CPU-bound workflows with variable task durations</li>
 *   <li>Mixed workloads where some tasks spawn subtasks</li>
 *   <li>Scenarios requiring efficient thread utilization</li>
 * </ul>
 */
public class ForkJoinExecutionStrategy implements ExecutionStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ForkJoinExecutionStrategy.class);

    private final ForkJoinPool forkJoinPool;
    private final int parallelism;

    /**
     * Creates a ForkJoin strategy with default parallelism (CPU core count).
     */
    public ForkJoinExecutionStrategy() {
        this(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates a ForkJoin strategy with specified parallelism level.
     *
     * @param parallelism Target parallelism level (number of worker threads)
     */
    public ForkJoinExecutionStrategy(int parallelism) {
        this.parallelism = parallelism;
        this.forkJoinPool = new ForkJoinPool(
                parallelism,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                true  // asyncMode = true for FIFO queue-based processing
        );

        logger.info("Initialized ForkJoin execution strategy with parallelism={}", parallelism);
        logger.info("Work-stealing enabled for improved load balancing");
    }

    @Override
    public CompletableFuture<TaskExecutionResult> submitTask(Supplier<TaskExecutionResult> task) {
        return CompletableFuture.supplyAsync(task, forkJoinPool);
    }

    @Override
    public String getStrategyName() {
        return "ForkJoin";
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down ForkJoin execution strategy");
        forkJoinPool.shutdown();
        try {
            if (!forkJoinPool.awaitTermination(30, TimeUnit.SECONDS)) {
                forkJoinPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            forkJoinPool.shutdownNow();
        }
    }

    /**
     * Returns the configured parallelism level.
     *
     * @return Parallelism level (number of worker threads)
     */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * Returns current statistics about the ForkJoin pool.
     *
     * @return String with pool statistics (running threads, queued tasks, etc.)
     */
    public String getPoolStats() {
        return String.format(
                "ForkJoinPool[parallelism=%d, activeThreads=%d, queuedTasks=%d, stealCount=%d]",
                forkJoinPool.getParallelism(),
                forkJoinPool.getActiveThreadCount(),
                forkJoinPool.getQueuedTaskCount(),
                forkJoinPool.getStealCount()
        );
    }
}