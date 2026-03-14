# Workflow Engine Performance Benchmarks

This document explains how to run and interpret the JMH benchmarks comparing the three execution strategies.

## Execution Strategies

1. **ThreadPool** (Baseline)
   - Fixed thread pool with 2x CPU cores
   - Traditional Java concurrency model
   - Limited by platform thread count

2. **VirtualThreads** (Java 21)
   - Unlimited virtual threads via Project Loom
   - Lightweight, user-mode threads
   - Automatic yielding on blocking operations

3. **ForkJoin**
   - Work-stealing pool
   - Good load balancing
   - CPU core count parallelism

## Benchmark Scenarios

### Wide DAG (100-200 parallel I/O tasks)
- **Pattern:** All tasks run in parallel
- **Workload:** I/O-bound (simulated 10ms HTTP calls)
- **Expected:** VirtualThreads wins by 5-10x
- **Why:** Can create unlimited threads, no blocking

### Deep DAG (50-200 sequential tasks)
- **Pattern:** Each task depends on the previous one
- **Workload:** I/O-bound (simulated 10ms HTTP calls)
- **Expected:** All strategies similar
- **Why:** No parallelism to exploit

### Mixed DAG (realistic workflow)
- **Pattern:** Root → Parallel branches → Join → Parallel final tasks
- **Workload:** I/O-bound (simulated 10ms HTTP calls)
- **Expected:** VirtualThreads wins by 3-5x
- **Why:** Exploits available parallelism without thread limits

## Running Benchmarks

### Quick Run (Single Scenario)
```bash
cd workflow-core
mvn clean package
java -jar target/benchmarks.jar wideDAG -p taskCount=100
```

### Full Benchmark Suite
```bash
# Build the benchmark JAR
mvn clean package

# Run all benchmarks
java -jar target/benchmarks.jar

# This will take ~30-45 minutes (3 warmup + 5 measurement iterations per config)
```

### Custom Configuration
```bash
# Run specific benchmark
java -jar target/benchmarks.jar mixedDAG

# Run with specific strategy
java -jar target/benchmarks.jar -p strategyType=VirtualThreads

# Run with specific task count
java -jar target/benchmarks.jar -p taskCount=200

# Quick test run (fewer iterations)
java -jar target/benchmarks.jar -wi 1 -i 2
```

### JMH Options
- `-wi <count>` : Warmup iterations (default: 3)
- `-i <count>` : Measurement iterations (default: 5)
- `-f <count>` : Number of forks (default: 1)
- `-t <count>` : Number of threads
- `-p <param>=<value>` : Set parameter value

## Expected Results

### Wide DAG (100 tasks, I/O-bound)
```
Benchmark                    (strategyType)  (taskCount)   Mode  Cnt   Score   Error  Units
wideDAG                      ThreadPool            100   thrpt    5   5.234 ± 0.342  ops/s
wideDAG                      VirtualThreads        100   thrpt    5  48.127 ± 2.156  ops/s  ⭐ 9x faster
wideDAG                      ForkJoin              100   thrpt    5   5.891 ± 0.421  ops/s
```

**Why VirtualThreads wins:**
- ThreadPool limited to 16 threads (2x8 cores) → only 16 tasks run concurrently
- VirtualThreads creates 100 threads → all tasks run concurrently
- Virtual threads don't block carrier threads during sleep

### Deep DAG (50 tasks, sequential)
```
Benchmark                    (strategyType)  (taskCount)   Mode  Cnt   Score   Error  Units
deepDAG                      ThreadPool             50   thrpt    5   1.987 ± 0.123  ops/s
deepDAG                      VirtualThreads         50   thrpt    5   1.992 ± 0.098  ops/s
deepDAG                      ForkJoin               50   thrpt    5   1.976 ± 0.134  ops/s
```

**Why all strategies similar:**
- No parallelism to exploit (sequential chain)
- Total time = 50 tasks × 10ms = 500ms
- Execution strategy doesn't matter

### Mixed DAG (realistic, ~100 tasks)
```
Benchmark                    (strategyType)  (taskCount)   Mode  Cnt   Score   Error  Units
mixedDAG                     ThreadPool            100   thrpt    5   8.234 ± 0.456  ops/s
mixedDAG                     VirtualThreads        100   thrpt    5  32.567 ± 1.234  ops/s  ⭐ 4x faster
mixedDAG                     ForkJoin              100   thrpt    5   9.123 ± 0.567  ops/s
```

**Why VirtualThreads wins:**
- Exploits all available parallelism in branches
- No thread pool contention

## Interpreting Results

### Throughput (ops/s)
- **Higher is better**
- Measures workflows completed per second
- VirtualThreads should win on I/O-bound workloads

### Latency (ms per operation)
```bash
# Run with latency measurement
java -jar target/benchmarks.jar -bm avgt
```
- **Lower is better**
- Measures time to complete one workflow
- Shows p50, p99, p999 percentiles

### Memory Usage
VirtualThreads use less memory than you'd expect:
- Platform thread: ~1MB stack
- Virtual thread: ~1KB stack
- 100 virtual threads: ~100KB (vs 100MB for platform threads)

## System Requirements

- **Java 21+** (for VirtualThreads)
- **8+ CPU cores** recommended for meaningful comparison
- **4GB RAM** minimum
- **Linux/Mac** preferred (better thread scheduling)

## Benchmark Gotchas

1. **JVM Warmup:** First few iterations may be slower (JIT compilation)
2. **GC Pauses:** Can skew results - use `-XX:+UseZGC` for low latency
3. **CPU Throttling:** Disable power saving mode for consistent results
4. **Background Processes:** Close unnecessary applications

## Next Steps

After running benchmarks:
1. Record results in README.md
2. Create charts/graphs for visualization
3. Write blog post explaining findings
4. Use results in interviews to demonstrate understanding

## Further Reading

- [JMH Samples](https://github.com/openjdk/jmh/tree/master/jmh-samples)
- [Virtual Threads JEP](https://openjdk.org/jeps/444)
- [Project Loom](https://wiki.openjdk.org/display/loom)