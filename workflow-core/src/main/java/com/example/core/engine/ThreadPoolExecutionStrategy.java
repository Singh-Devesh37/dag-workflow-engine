package com.example.core.engine;

import com.example.core.model.TaskExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Traditional thread pool execution strategy using a fixed-size thread pool.
 *
 * <p>This is the baseline strategy that uses {@link ThreadPoolExecutor} with a fixed
 * number of threads. Each task submitted is executed on one of the pool threads.
 *
 * <p><b>Characteristics:</b>
 * <ul>
 *   <li>Fixed number of platform threads (default: 2x CPU cores)</li>
 *   <li>Bounded task queue to prevent memory overflow</li>
 *   <li>Suitable for CPU-bound tasks and moderate I/O workloads</li>
 *   <li>Thread reuse reduces context switching overhead</li>
 * </ul>
 *
 * <p><b>Performance Considerations:</b>
 * <ul>
 *   <li>Limited by number of platform threads (expensive resources)</li>
 *   <li>Blocking I/O operations hold threads, reducing throughput</li>
 *   <li>Context switching overhead increases with high concurrency</li>
 * </ul>
 */
public class ThreadPoolExecutionStrategy implements ExecutionStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolExecutionStrategy.class);

    private final ExecutorService executorService;
    private final int poolSize;

    /**
     * Creates a ThreadPool strategy with default pool size (2x available processors).
     */
    public ThreadPoolExecutionStrategy() {
        this(Runtime.getRuntime().availableProcessors() * 2);
    }

    /**
     * Creates a ThreadPool strategy with specified pool size.
     *
     * @param poolSize Number of threads in the pool
     */
    public ThreadPoolExecutionStrategy(int poolSize) {
        this.poolSize = poolSize;
        this.executorService = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10000),
                new ThreadFactory() {
                    private int counter = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "workflow-thread-pool-" + counter++);
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        logger.info("Initialized ThreadPool execution strategy with {} threads", poolSize);
    }

    @Override
    public CompletableFuture<TaskExecutionResult> submitTask(Supplier<TaskExecutionResult> task) {
        return CompletableFuture.supplyAsync(task, executorService);
    }

    @Override
    public String getStrategyName() {
        return "ThreadPool";
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down ThreadPool execution strategy");
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

    /**
     * Returns the configured pool size.
     *
     * @return Number of threads in the pool
     */
    public int getPoolSize() {
        return poolSize;
    }
}