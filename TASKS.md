# Workflow Engine - Task Breakdown

## Overview
Transform the workflow engine into a performance-focused learning project exploring Java concurrency patterns for DAG execution.

**Total Estimated Time:** 50-60 hours (~2-3 weeks)

---

## Phase 1: Complete Core Executors (12-16 hours)

**Goal:** Finish all 4 task executor implementations

### 1.1 HTTPTaskExecutor (2-3 hours) - PRIORITY 1
**File:** `workflow-executors/src/main/java/com/example/executor/HTTPTaskExecutor.java`

**Tasks:**
- [ ] Use Java 11+ HttpClient (built-in, async-capable)
- [ ] Support GET, POST, PUT, DELETE methods
- [ ] Extract URL and method from config
- [ ] Support request headers from config (optional)
- [ ] Support request body from context or config
- [ ] Parse HTTP response (status code, body)
- [ ] Return response data in contextDelta
- [ ] Handle HTTP errors gracefully
- [ ] Add timeout support (from taskNode.timeoutMillis)

**Implementation Outline:**
```java
HttpClient client = HttpClient.newHttpClient();
HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
    .uri(URI.create(url))
    .timeout(Duration.ofMillis(timeout));

// Add headers from config
// Add body if POST/PUT
// Send request
HttpResponse<String> response = client.send(request,
    HttpResponse.BodyHandlers.ofString());

// Return contextDelta with response
Map<String, Object> contextDelta = Map.of(
    "statusCode", response.statusCode(),
    "responseBody", response.body()
);
```

**Test Cases:**
- Successful GET request
- POST with JSON body
- Timeout handling
- HTTP error codes (404, 500)

---

### 1.2 KafkaTaskExecutor (3-4 hours) - PRIORITY 1
**File:** `workflow-executors/src/main/java/com/example/executor/KafkaTaskExecutor.java`

**Tasks:**
- [ ] Add spring-kafka dependency to workflow-executors/pom.xml
- [ ] Create KafkaTemplate bean or use direct Producer
- [ ] Extract topic and message from config
- [ ] Support message key (optional)
- [ ] Send message to Kafka topic
- [ ] Handle send result (metadata, offset)
- [ ] Return offset/partition in contextDelta
- [ ] Handle Kafka errors

**Dependencies:**
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
    <version>3.0.0</version>
</dependency>
```

**Implementation Outline:**
```java
KafkaTemplate<String, Object> kafkaTemplate = ...;
SendResult<String, Object> result = kafkaTemplate.send(
    topic,
    key,
    message
).get(); // or use async callback

Map<String, Object> contextDelta = Map.of(
    "partition", result.getRecordMetadata().partition(),
    "offset", result.getRecordMetadata().offset()
);
```

**Test Cases:**
- Send message successfully
- Handle broker unavailable
- Verify message received (consumer test)

---

### 1.3 EmailTaskExecutor (2-3 hours) - PRIORITY 2
**File:** `workflow-executors/src/main/java/com/example/executor/EmailTaskExecutor.java`

**Tasks:**
- [ ] Add spring-boot-starter-mail dependency
- [ ] Create JavaMailSender bean
- [ ] Extract to, subject, body from config
- [ ] Support CC, BCC (optional)
- [ ] Send simple text email
- [ ] Support HTML email (optional)
- [ ] Handle SMTP errors
- [ ] Return send status in contextDelta

**Dependencies:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

**Configuration (application.yml):**
```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

**Implementation Outline:**
```java
JavaMailSender mailSender = ...;
SimpleMailMessage message = new SimpleMailMessage();
message.setTo(to);
message.setSubject(subject);
message.setText(body);
mailSender.send(message);

Map<String, Object> contextDelta = Map.of(
    "emailSent", true,
    "recipient", to
);
```

**Test Cases:**
- Send email successfully (use Greenmail for testing)
- Handle invalid recipient
- Handle SMTP auth failure

---

### 1.4 ScriptTaskExecutor (3-4 hours) - PRIORITY 1
**File:** `workflow-executors/src/main/java/com/example/executor/ScriptTaskExecutor.java`

**Tasks:**
- [ ] Use ProcessBuilder for script execution
- [ ] Extract script/command from config
- [ ] Support bash, python, node scripts
- [ ] Pass parameters from config/context
- [ ] Capture stdout and stderr
- [ ] Handle exit codes
- [ ] Implement timeout (important!)
- [ ] Return stdout/stderr/exitCode in contextDelta
- [ ] Handle script execution errors

**Implementation Outline:**
```java
ProcessBuilder pb = new ProcessBuilder(script.split(" "));
pb.directory(new File(workingDir));
Process process = pb.start();

// Capture output
BufferedReader reader = new BufferedReader(
    new InputStreamReader(process.getInputStream()));
String stdout = reader.lines().collect(Collectors.joining("\n"));

// Wait with timeout
boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
if (!finished) {
    process.destroyForcibly();
    throw new TaskTimeoutException("Script timeout");
}

int exitCode = process.exitValue();
Map<String, Object> contextDelta = Map.of(
    "exitCode", exitCode,
    "stdout", stdout,
    "stderr", stderr
);
```

**Test Cases:**
- Execute simple bash script
- Capture output correctly
- Handle timeout
- Handle script failure (non-zero exit code)

---

### 1.5 Remove CustomTaskExecutor (15 mins) - PRIORITY 1
**Tasks:**
- [ ] Delete CustomTaskExecutor.java
- [ ] Remove from TaskExecutorFactoryImpl registry
- [ ] Remove CUSTOM from TaskType enum (or mark deprecated)
- [ ] Update documentation

---

## Phase 2: Fix Persistence Layer (1-2 hours)

### 2.1 Complete TaskRunRepositoryImpl - PRIORITY 1
**File:** `workflow-persistence/src/main/java/com/example/persistence/repo/TaskRunRepositoryImpl.java`

**Tasks:**
- [ ] Implement `save(TaskRun)` method
- [ ] Implement `findById(String)` method
- [ ] Implement `findByWorkflowId(String)` method
- [ ] Implement `findByStatus(RunStatus)` method
- [ ] Implement `findAll()` method
- [ ] Add proper entity ↔ domain mapping
- [ ] Test all methods

**Implementation:**
Similar pattern to WorkflowRunRepositoryImpl:
```java
@Override
public TaskRun save(TaskRun taskRun) {
    TaskRunEntity entity = mapper.toEntity(taskRun);
    TaskRunEntity saved = jpaRepository.save(entity);
    return mapper.toDomain(saved);
}
```

---

## Phase 3: Implement Execution Strategies (8-12 hours)

**Goal:** Add 3 execution strategies to WorkflowEngine

### 3.1 Refactor WorkflowEngine for Strategy Pattern (2-3 hours) - PRIORITY 1
**File:** `workflow-core/src/main/java/com/example/core/engine/WorkflowEngine.java`

**Tasks:**
- [ ] Create ExecutionStrategy enum (THREAD_POOL, VIRTUAL_THREADS, FORK_JOIN)
- [ ] Refactor WorkflowEngine to accept ExecutorService as parameter
- [ ] Create factory method to create ExecutorService based on strategy
- [ ] Add configuration property for default strategy
- [ ] Allow per-workflow strategy override (optional)

**New Code Structure:**
```java
public enum ExecutionStrategy {
    THREAD_POOL,
    VIRTUAL_THREADS,
    FORK_JOIN
}

@Service
public class WorkflowEngine {
    private ExecutorService createExecutor(ExecutionStrategy strategy) {
        return switch (strategy) {
            case THREAD_POOL -> Executors.newFixedThreadPool(100);
            case VIRTUAL_THREADS -> Executors.newVirtualThreadPerTaskExecutor();
            case FORK_JOIN -> ForkJoinPool.commonPool();
        };
    }

    public CompletableFuture<WorkflowRun> execute(
        String runId,
        String workflowId,
        List<TaskNode> tasks,
        ExecutionContext globalContext,
        ExecutionStrategy strategy  // NEW parameter
    ) {
        ExecutorService executor = createExecutor(strategy);
        // ... rest of execution logic
    }
}
```

---

### 3.2 Strategy 1: Traditional Thread Pool (1-2 hours) - PRIORITY 1
**Tasks:**
- [ ] Implement fixed-size thread pool (100 threads)
- [ ] Add metrics for thread pool (active threads, queue size)
- [ ] Test with different DAG shapes
- [ ] This is the baseline for comparison

**Configuration:**
```java
ExecutorService executor = Executors.newFixedThreadPool(
    100,  // Fixed pool size
    new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("workflow-thread-pool-" + counter.incrementAndGet());
            return t;
        }
    }
);
```

---

### 3.3 Strategy 2: Virtual Threads (2-3 hours) - PRIORITY 1 ⭐
**Tasks:**
- [ ] Implement Virtual Threads executor (Java 21+)
- [ ] Test scalability (10K+ tasks)
- [ ] Measure memory usage vs thread pool
- [ ] Add metrics specific to virtual threads
- [ ] Document when to use virtual threads

**Configuration:**
```java
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
```

**Key Learnings to Document:**
- Virtual threads are lightweight (millions possible)
- Perfect for I/O-bound tasks (HTTP, Kafka, Email)
- Memory footprint comparison
- Pinning issues with synchronized blocks

---

### 3.4 Strategy 3: ForkJoin Pool (2-3 hours) - PRIORITY 2
**Tasks:**
- [ ] Implement ForkJoin pool executor
- [ ] Configure parallelism level
- [ ] Test with CPU-bound tasks
- [ ] Add work-stealing metrics
- [ ] Document when to use ForkJoin

**Configuration:**
```java
ForkJoinPool executor = new ForkJoinPool(
    Runtime.getRuntime().availableProcessors(),
    ForkJoinPool.defaultForkJoinWorkerThreadFactory,
    null,
    true  // asyncMode
);
```

---

### 3.5 Add Strategy Selection API (1 hour) - PRIORITY 2
**Tasks:**
- [ ] Add executionStrategy field to StartWorkflowRequest
- [ ] Update WorkflowController to pass strategy
- [ ] Update WorkflowFacade to accept strategy parameter
- [ ] Add default strategy in application.yml
- [ ] Document API usage

---

## Phase 4: Testing (10-14 hours)

### 4.1 Unit Tests for Executors (8 hours)
**Tasks:**
- [ ] HTTPTaskExecutor tests (2 hrs) - use WireMock for HTTP mocking
- [ ] KafkaTaskExecutor tests (2 hrs) - use Embedded Kafka
- [ ] EmailTaskExecutor tests (2 hrs) - use Greenmail
- [ ] ScriptTaskExecutor tests (2 hrs) - use actual scripts

**Test Coverage Goal:** 70%+ for each executor

---

### 4.2 Unit Tests for Core Engine (4-6 hours)
**Tasks:**
- [ ] WorkflowEngine tests (3-4 hrs)
  - Test DAG execution order
  - Test parallel execution
  - Test failure handling
  - Test context propagation
- [ ] RetryingTaskRunner tests (2 hrs)
  - Test retry logic
  - Test exponential backoff
  - Test max retries exhaustion

---

### 4.3 Integration Tests (2-3 hours)
**Tasks:**
- [ ] End-to-end workflow execution test
- [ ] Test with real database (Testcontainers)
- [ ] Test scheduler integration
- [ ] Test WebSocket events

---

## Phase 5: Benchmarking (10-12 hours)

### 5.1 Setup JMH Infrastructure (2 hours) - PRIORITY 1
**File:** `workflow-core/pom.xml`

**Tasks:**
- [ ] Add JMH dependencies
- [ ] Create benchmark module/package
- [ ] Configure JMH Maven plugin
- [ ] Create base benchmark class

**Dependencies:**
```xml
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.37</version>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.37</version>
</dependency>
```

---

### 5.2 Create Benchmark Scenarios (6-8 hours)

#### Wide DAG Benchmark (2 hrs) - PRIORITY 1
**File:** `workflow-core/src/test/java/com/example/benchmark/WideDagBenchmark.java`

**Tasks:**
- [ ] Create 100 parallel HTTP tasks (no dependencies)
- [ ] Benchmark all 3 strategies
- [ ] Measure throughput (tasks/sec)
- [ ] Measure latency (p50, p95, p99)
- [ ] Measure memory usage

**Expected Result:** Virtual Threads should win here

---

#### Deep DAG Benchmark (2 hrs) - PRIORITY 1
**File:** `workflow-core/src/test/java/com/example/benchmark/DeepDagBenchmark.java`

**Tasks:**
- [ ] Create 50 sequential HTTP tasks (chain)
- [ ] Benchmark all 3 strategies
- [ ] Measure total execution time
- [ ] Measure context propagation overhead

**Expected Result:** Thread pool should be competitive

---

#### Mixed DAG Benchmark (2 hrs) - PRIORITY 2
**File:** `workflow-core/src/test/java/com/example/benchmark/MixedDagBenchmark.java`

**Tasks:**
- [ ] Create realistic DAG (mix of parallel and sequential)
- [ ] Benchmark all 3 strategies
- [ ] Test with different task types (HTTP + Script)

---

#### CPU-Bound Benchmark (2 hrs) - PRIORITY 2
**File:** `workflow-core/src/test/java/com/example/benchmark/CpuBoundBenchmark.java`

**Tasks:**
- [ ] Create CPU-intensive script tasks
- [ ] Benchmark all 3 strategies
- [ ] Measure CPU utilization

**Expected Result:** ForkJoin should excel here

---

### 5.3 Run and Analyze Benchmarks (2-3 hours)
**Tasks:**
- [ ] Run all benchmarks multiple times
- [ ] Collect results (CSV/JSON format)
- [ ] Create comparison charts
- [ ] Analyze tradeoffs
- [ ] Document findings in README

**Benchmark Command:**
```bash
cd workflow-core
mvn clean install
java -jar target/benchmarks.jar -rf json -rff results.json
```

---

## Phase 6: Documentation & Polish (4-6 hours)

### 6.1 Update README with Results (2 hours)
**Tasks:**
- [ ] Add actual benchmark numbers to "Key Findings" section
- [ ] Create performance comparison tables
- [ ] Add charts/graphs (optional)
- [ ] Update "Expected insights" with actual learnings

---

### 6.2 JavaDoc and Code Comments (2 hours)
**Tasks:**
- [ ] Add JavaDoc to WorkflowEngine
- [ ] Add JavaDoc to all executors
- [ ] Add JavaDoc to execution strategies
- [ ] Document key design decisions

---

### 6.3 Testing Documentation (1 hour)
**Tasks:**
- [ ] Document how to run tests
- [ ] Document how to run benchmarks
- [ ] Document how to interpret results
- [ ] Add troubleshooting section

---

### 6.4 Code Coverage Setup (1 hour)
**File:** `pom.xml` (parent)

**Tasks:**
- [ ] Add JaCoCo plugin
- [ ] Configure coverage thresholds
- [ ] Generate coverage report
- [ ] Add coverage badge to README (optional)

**Plugin:**
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.10</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

## Phase 7: Verification & Final Touches (2-3 hours)

### 7.1 Verify Scheduler Works (1 hour)
**Tasks:**
- [ ] Test cron scheduling end-to-end
- [ ] Verify job persistence
- [ ] Test job deletion
- [ ] Add scheduler test cases

---

### 7.2 Clean Up (1 hour)
**Tasks:**
- [ ] Remove unused imports
- [ ] Format code consistently
- [ ] Remove commented-out code
- [ ] Check for TODOs and resolve them

---

### 7.3 Final Review (1 hour)
**Tasks:**
- [ ] Review README for clarity
- [ ] Review Architecture.md files
- [ ] Test all API endpoints
- [ ] Verify all tests pass
- [ ] Build and run application end-to-end

---

## Priority Summary

### Week 1 Focus (High Priority)
1. ✅ Complete all 4 executors (HTTP, Kafka, Email, Script)
2. ✅ Fix TaskRunRepositoryImpl
3. ✅ Implement 3 execution strategies
4. ✅ Unit tests for executors
5. ✅ Setup JMH benchmarks

### Week 2 Focus (Medium Priority)
6. ✅ Create benchmark scenarios (Wide, Deep, Mixed, CPU)
7. ✅ Run benchmarks and collect data
8. ✅ Unit tests for core engine
9. ✅ Integration tests
10. ✅ Update README with results

### Week 3 Focus (Polish)
11. ✅ JavaDoc and documentation
12. ✅ Code coverage setup
13. ✅ Final review and cleanup

---

## Time Estimates Summary

| Phase | Estimated Time |
|-------|----------------|
| Phase 1: Executors | 12-16 hours |
| Phase 2: Persistence | 1-2 hours |
| Phase 3: Strategies | 8-12 hours |
| Phase 4: Testing | 10-14 hours |
| Phase 5: Benchmarks | 10-12 hours |
| Phase 6: Docs | 4-6 hours |
| Phase 7: Polish | 2-3 hours |
| **TOTAL** | **47-65 hours** |

**Realistic Timeline:** 2-3 weeks of focused work (15-20 hrs/week)

---

## Success Criteria

**Must Have:**
- ✅ All 4 executors fully implemented with tests
- ✅ 3 execution strategies working
- ✅ Benchmarks comparing all strategies
- ✅ Test coverage >60%
- ✅ README updated with real results
- ✅ Clean, documented code

**Nice to Have:**
- ⭐ Test coverage >70%
- ⭐ Performance charts/graphs
- ⭐ Blog post about learnings
- ⭐ CI/CD pipeline (GitHub Actions)

---

## Getting Started

**Suggested Order:**
1. Start with **HTTPTaskExecutor** (easiest, most important for benchmarks)
2. Then **ScriptTaskExecutor** (CPU-bound for comparison)
3. Then **Execution Strategies** (the core differentiation)
4. Then **Benchmarks** (to validate the work)
5. Then **KafkaTaskExecutor** and **EmailTaskExecutor** (nice-to-haves)
6. Finally **Tests and Documentation**

**Let's begin!** 🚀