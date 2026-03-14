package com.example.core.config;

import com.example.core.engine.ExecutionStrategy;
import com.example.core.engine.ForkJoinExecutionStrategy;
import com.example.core.engine.ThreadPoolExecutionStrategy;
import com.example.core.engine.VirtualThreadExecutionStrategy;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Configuration for workflow execution strategies.
 *
 * <p>Configures the execution strategy used by the WorkflowEngine based on
 * application properties. Supports three strategies:
 *
 * <ul>
 *   <li><b>threadpool</b> - Traditional fixed thread pool (baseline)</li>
 *   <li><b>virtual</b> - Java 21 Virtual Threads (Project Loom)</li>
 *   <li><b>forkjoin</b> - Work-stealing Fork/Join pool</li>
 * </ul>
 *
 * <p><b>Configuration Properties:</b>
 * <pre>
 * # application.yml or application.properties
 * workflow.execution.strategy=virtual          # Strategy type (threadpool|virtual|forkjoin)
 * workflow.execution.threadpool.size=16        # ThreadPool: number of threads
 * workflow.execution.forkjoin.parallelism=8    # ForkJoin: parallelism level
 * </pre>
 *
 * <p><b>Default Values:</b>
 * <ul>
 *   <li>Strategy: threadpool</li>
 *   <li>ThreadPool size: 2x CPU cores</li>
 *   <li>ForkJoin parallelism: CPU core count</li>
 * </ul>
 */
@Configuration
public class ExecutionStrategyConfig {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionStrategyConfig.class);

    @Value("${workflow.execution.strategy:threadpool}")
    private String strategyType;

    @Value("${workflow.execution.threadpool.size:0}")
    private int threadPoolSize;

    @Value("${workflow.execution.forkjoin.parallelism:0}")
    private int forkJoinParallelism;

    private ExecutionStrategy executionStrategy;

    /**
     * Creates the configured execution strategy.
     *
     * @return ExecutionStrategy instance based on configuration
     */
    @Bean
    public ExecutionStrategy executionStrategy() {
        logger.info("Configuring execution strategy: {}", strategyType);

        executionStrategy = switch (strategyType.toLowerCase()) {
            case "virtual", "virtualthreads" -> {
                logger.info("Using VirtualThreads execution strategy (Java 21)");
                yield new VirtualThreadExecutionStrategy();
            }
            case "forkjoin", "fork-join" -> {
                int parallelism = forkJoinParallelism > 0
                        ? forkJoinParallelism
                        : Runtime.getRuntime().availableProcessors();
                logger.info("Using ForkJoin execution strategy with parallelism={}", parallelism);
                yield new ForkJoinExecutionStrategy(parallelism);
            }
            case "threadpool", "thread-pool" -> {
                int poolSize = threadPoolSize > 0
                        ? threadPoolSize
                        : Runtime.getRuntime().availableProcessors() * 2;
                logger.info("Using ThreadPool execution strategy with size={}", poolSize);
                yield new ThreadPoolExecutionStrategy(poolSize);
            }
            default -> {
                logger.warn("Unknown strategy '{}', falling back to ThreadPool", strategyType);
                int poolSize = Runtime.getRuntime().availableProcessors() * 2;
                yield new ThreadPoolExecutionStrategy(poolSize);
            }
        };

        logger.info("Execution strategy initialized: {}", executionStrategy.getStrategyName());
        return executionStrategy;
    }

    /**
     * Creates a scheduled executor for periodic tasks (e.g., health checks, metrics).
     *
     * @return ScheduledExecutorService with single thread
     */
    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        return Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r, "workflow-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Creates a general-purpose executor service for WorkflowFacade and other components.
     * Uses virtual threads if Java 21+ is available, otherwise uses a cached thread pool.
     *
     * @return ExecutorService for general async operations
     */
    @Bean
    public ExecutorService executorService() {
        try {
            // Try to use virtual threads if available (Java 21+)
            return Executors.newVirtualThreadPerTaskExecutor();
        } catch (UnsupportedOperationException | NoSuchMethodError e) {
            // Fall back to cached thread pool for Java < 21
            logger.info("Virtual threads not available, using cached thread pool for general executor");
            return Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r, "workflow-general");
                thread.setDaemon(false);
                return thread;
            });
        }
    }

    /**
     * Shuts down the execution strategy on application shutdown.
     */
    @PreDestroy
    public void shutdown() {
        if (executionStrategy != null) {
            logger.info("Shutting down execution strategy: {}", executionStrategy.getStrategyName());
            executionStrategy.shutdown();
        }
    }
}