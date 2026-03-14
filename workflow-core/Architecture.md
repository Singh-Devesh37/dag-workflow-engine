# Workflow-Core Module - Architecture Documentation

## Module Overview

- **Module Name:** workflow-core
- **Artifact ID:** workflow-core
- **Version:** 1.0-SNAPSHOT
- **Java Version:** 21
- **Spring Boot Version:** 3.2.5

## Purpose

The **workflow-core** module is the foundational execution engine for the DAG-based workflow orchestration system. It provides the core business logic for:
- DAG execution with concurrent task processing
- Dependency resolution and topological sorting
- Resilient retry logic with exponential backoff
- Thread-safe context propagation between tasks
- High-level workflow orchestration API
- Workflow validation (cycle detection, configuration validation)
- Observability via comprehensive metrics

This module defines the domain models, interfaces, and execution engine but remains **agnostic of persistence, scheduling, and API concerns** — those are handled by other modules.

## Directory Structure

```
workflow-core/
├── pom.xml
└── src/
    └── main/java/com/example/core/
        ├── WorkflowFacade.java                  # High-level orchestration API
        ├── engine/                              # Core execution components
        │   ├── WorkflowEngine.java             # DAG executor
        │   ├── ExecutionContext.java           # Thread-safe context
        │   ├── RetryingTaskRunner.java         # Retry logic
        │   ├── TaskExecutor.java               # Task executor interface
        │   ├── TaskExecutorFactory.java        # Factory interface
        │   └── WorkflowSchedulerService.java   # Scheduler interface
        ├── model/                               # Domain models
        │   ├── WorkflowDefinition.java         # Workflow blueprint
        │   ├── WorkflowRun.java                # Workflow execution instance
        │   ├── TaskNode.java                   # DAG node
        │   ├── TaskRun.java                    # Task execution instance
        │   ├── TaskExecutionResult.java        # Task result
        │   ├── TaskDefinition.java             # Task metadata
        │   └── TaskDefinitionConverter.java    # Converter utility
        ├── repo/                                # Repository interfaces
        │   ├── WorkflowRunRepository.java
        │   ├── WorkflowDefinitionRepository.java
        │   └── TaskRunRepository.java
        ├── enums/                               # Enumerations
        │   ├── RunStatus.java                  # Execution status
        │   └── TaskType.java                   # Task types
        ├── exception/                           # Custom exceptions (12 classes)
        │   ├── WorkflowEngineException.java
        │   ├── RetryExhaustException.java
        │   ├── TaskExecutionException.java
        │   ├── TaskTimeoutException.java
        │   ├── WorkflowDefinitionException.java
        │   ├── TaskConfigurationException.java
        │   ├── EngineInitializationException.java
        │   ├── ExecutorNotFoundException.java
        │   ├── ContextPropagationException.java
        │   ├── UnsupportedTaskTypeException.java
        │   ├── PersistenceException.java
        │   └── WorkflowSchedulerException.java
        ├── pub/                                 # Event publishing
        │   └── WorkflowEventPublisher.java     # Publisher interface
        └── validation/
            └── WorkflowValidator.java          # Workflow validation
```

## Core Components

### 1. WorkflowEngine (DAG Executor)

**Location:** `engine/WorkflowEngine.java`
**Type:** Spring @Service
**Responsibility:** Orchestrates parallel execution of tasks in a DAG

#### Key Features
- **Concurrent Execution**: Uses `CompletableFuture` for parallel task execution
- **Dependency Management**: Builds dependency graph (in-degree map + adjacency list)
- **Smart Scheduling**: Executes independent tasks immediately, waits for dependencies
- **Failure Handling**: Skips downstream tasks when upstream tasks fail
- **Context Merging**: Aggregates contextDeltas from completed tasks
- **Event Publishing**: Publishes task and workflow status updates

#### Execution Algorithm

```java
1. Build DAG Structure
   - Create dependency count map (in-degree for each task)
   - Create downstream map (adjacency list)
   - Initialize TaskRun for each task

2. Identify Entry Points
   - Find tasks with zero dependencies
   - Submit for immediate execution

3. Execute Tasks with Dependencies
   - For each task with dependencies:
     * Wait for all dependencies via CompletableFuture.allOf()
     * Check if any dependency failed
     * If failed: mark SKIPPED, skip execution
     * If success: execute task via RetryingTaskRunner

4. Context Propagation
   - Each completed task produces contextDelta
   - Merge delta into global ExecutionContext
   - Pass updated context to downstream tasks

5. Workflow Completion
   - Wait for all tasks to complete
   - Determine final status (SUCCESS if all succeeded, FAILED otherwise)
   - Record timing and publish workflow update
```

#### Metrics Published
- `workflow_total{status=started|completed|failed}` - Counter
- `workflow_duration_seconds` - Timer
- `workflow_running_gauge` - Active workflow count

**Key Methods:**
- `execute(runId, workflowId, tasks, globalContext): CompletableFuture<WorkflowRun>`

---

### 2. RetryingTaskRunner

**Location:** `engine/RetryingTaskRunner.java`
**Responsibility:** Resilient task execution with exponential backoff

#### Key Features
- **Exponential Backoff**: Delay doubles after each retry (initialDelay × 2^attempt)
- **Jitter**: Adds randomness (0-20%) to prevent thundering herd
- **Context Isolation**: Clones ExecutionContext for each attempt
- **Timeout Support**: Configurable per-task timeout (default: 30s)
- **Metrics Tracking**: Success/failure/retry counts and durations

#### Retry Logic

```java
1. Clone ExecutionContext for isolation
2. Call TaskExecutor.execute(context)
3. If success:
   - Record metrics
   - Return TaskExecutionResult
4. If failure:
   - Increment attempt counter
   - If attempts < maxRetries:
     * Calculate delay: initialDelay × 2^attempt
     * Add jitter: delay × (0.8 to 1.2)
     * Schedule retry on ScheduledExecutorService
   - If retries exhausted:
     * Throw RetryExhaustException
```

#### Metrics Published
- `task_total{status=success|failed}` - Counter
- `task_retry_total` - Retry counter
- `task_duration_seconds` - Timer
- `task_active_gauge` - Active task count

**Key Methods:**
- `runWithRetry(taskExecutor, context, taskNode, taskRun): TaskExecutionResult`

---

### 3. WorkflowFacade (High-Level API)

**Location:** `WorkflowFacade.java`
**Type:** Spring @Service
**Responsibility:** Simplified high-level API for workflow operations

#### Operations Provided

**Workflow Execution:**
- `startWorkflow(workflowId, tasks, initialContext): CompletableFuture<WorkflowRun>`
- `startWorkflowByDefinitionName(name, context): CompletableFuture<WorkflowRun>`

**Workflow Definition Management:**
- `saveWorkflowDefinition(definition): WorkflowDefinition`
- `getWorkflowDefinition(id): WorkflowDefinition`
- `getWorkflowDefinitionByName(name): WorkflowDefinition`
- `getAllWorkflowDefinitions(): List<WorkflowDefinition>`

**Workflow Run Queries:**
- `getWorkflowRun(runId): WorkflowRun`
- `getAllWorkflowRuns(): List<WorkflowRun>`

**Scheduling:**
- `scheduleWorkflow(workflowId, context, cronExpression): void`
- `unscheduleWorkflow(workflowId): void`
- `listScheduledJobs(): List<String>`

#### Design Pattern
- **Facade Pattern**: Simplifies complex subsystem interactions
- **Async Execution**: All executions return `CompletableFuture`
- **ExecutorService**: Uses thread pool for async workflow execution

---

### 4. ExecutionContext (Context Propagation)

**Location:** `engine/ExecutionContext.java`
**Responsibility:** Thread-safe context for sharing data between tasks

#### Key Features
- **Thread-Safe**: Backed by `ConcurrentHashMap`
- **Generic Types**: Type-safe getters with generic parameter
- **Cloneable**: Deep copy support for retry isolation
- **Mergeable**: Merge contextDeltas from task results

**Key Methods:**
```java
- set(String key, Object value): void
- <T> T get(String key): T
- merge(Map<String, Object> delta): void
- clone(): ExecutionContext
- deepCopy(): ExecutionContext
- asMap(): Map<String, Object> (unmodifiable view)
```

#### Usage Pattern
```java
// Task A produces data
ExecutionContext context = new ExecutionContext();
context.set("userId", "123");
context.set("amount", 100.0);

// Task B consumes data
String userId = context.get("userId");
Double amount = context.get("amount");

// Merge delta from task result
context.merge(taskResult.getContextDelta());
```

---

## Domain Models

### WorkflowDefinition

**Purpose:** Blueprint/template for a workflow

**Key Fields:**
- `id` (String, UUID) - Unique identifier
- `name` (String) - Workflow name
- `version` (Integer) - Version number
- `description` (String) - Human-readable description
- `tasks` (Map<String, TaskNode>) - DAG task definitions
- `created`, `updated` (Instant) - Timestamps

---

### WorkflowRun

**Purpose:** Single execution instance of a workflow

**Key Fields:**
- `runId` (String, UUID) - Unique run identifier
- `workflowId` (String) - Reference to WorkflowDefinition
- `status` (RunStatus) - Current status (PENDING/RUNNING/SUCCESS/FAILED/SKIPPED)
- `startTime`, `endTime` (Instant) - Execution timeline
- `durationMillis` (Long) - Total execution duration
- `taskRuns` (Map<String, TaskRun>) - Task execution instances
- `mergedContextSnapshot` (Map<String, Object>) - Accumulated context (thread-safe ConcurrentHashMap)
- `metaData` (Map<String, Object>) - Custom metadata

**Status Transitions:**
```
PENDING → RUNNING → SUCCESS
PENDING → RUNNING → FAILED
```

---

### TaskNode

**Purpose:** Represents a node in the DAG

**Key Fields:**
- `name` (String) - Task identifier
- `taskExecutor` (TaskExecutor) - Executor instance
- `config` (Map<String, Object>) - Task configuration
- `maxRetries` (int, default: 0) - Maximum retry attempts
- `initialDelayMillis` (long, default: 1000) - Initial retry delay
- `timeoutMillis` (long, default: 30000) - Execution timeout
- `dependencies` (List<TaskNode>) - Upstream dependencies (DAG edges)

**Builder Pattern:**
```java
TaskNode task = TaskNode.builder()
    .name("send-email")
    .taskExecutor(emailExecutor)
    .config(Map.of("to", "user@example.com"))
    .maxRetries(3)
    .initialDelayMillis(2000)
    .timeoutMillis(60000)
    .dependencies(List.of(validateTask))
    .build();
```

---

### TaskRun

**Purpose:** Execution instance of a specific task

**Key Fields:**
- `id` (String, UUID) - Unique task run identifier
- `taskName` (String) - Reference to task definition
- `workflowId` (String) - Parent workflow run ID
- `status` (RunStatus) - Execution status
- `attempt` (AtomicInteger) - Current retry attempt
- `maxRetries` (int) - Maximum allowed retries
- `startTime`, `endTime` (Instant) - Execution timeline
- `durationMillis` (Long) - Execution duration
- `errorMessage`, `errorStackTrace` (String) - Failure details
- `contextDelta` (Map<String, Object>) - Data produced by this task
- `metaData` (Map<String, Object>) - Custom metadata

**Status Transitions:**
```
PENDING → RUNNING → SUCCESS
PENDING → RUNNING → FAILED
PENDING → SKIPPED (upstream failure)
```

---

### TaskExecutionResult

**Purpose:** Encapsulates task execution outcome

**Key Fields:**
- `success` (boolean) - Success/failure indicator
- `message` (String) - Status message
- `error` (Throwable) - Exception if failed
- `contextDelta` (Map<String, Object>) - Data to propagate
- `startTime`, `endTime` (Instant) - Execution timeline

**Factory Methods:**
```java
TaskExecutionResult.success(message, contextDelta)
TaskExecutionResult.failure(message, error)
```

---

## Interfaces (Extension Points)

### TaskExecutor

**Purpose:** Abstract task execution logic

```java
public interface TaskExecutor {
    TaskExecutionResult execute(ExecutionContext context);
    TaskType getType();
}
```

**Implementations:** Provided by workflow-executors module
- HTTPTaskExecutor
- KafkaTaskExecutor
- EmailTaskExecutor
- ScriptTaskExecutor
- CustomTaskExecutor

---

### TaskExecutorFactory

**Purpose:** Create TaskExecutor instances by type

```java
public interface TaskExecutorFactory {
    TaskExecutor createExecutor(TaskType type, Map<String, Object> config);
    void register(TaskType type, Function<Map<String,Object>, TaskExecutor> builder);
}
```

**Implementation:** workflow-executors module (TaskExecutorFactoryImpl)

---

### Repository Interfaces

**WorkflowRunRepository:**
```java
WorkflowRun save(WorkflowRun workflowRun);
WorkflowRun findById(String id);
List<WorkflowRun> findByStatus(RunStatus status);
List<WorkflowRun> findAll();
```

**WorkflowDefinitionRepository:**
```java
WorkflowDefinition save(WorkflowDefinition definition);
WorkflowDefinition findById(String id);
WorkflowDefinition findByName(String name);
List<WorkflowDefinition> findAll();
```

**TaskRunRepository:**
```java
TaskRun save(TaskRun taskRun);
TaskRun findById(String id);
List<TaskRun> findByWorkflowId(String workflowId);
List<TaskRun> findByStatus(RunStatus status);
List<TaskRun> findAll();
```

**Implementations:** Provided by workflow-persistence module

---

### WorkflowEventPublisher

**Purpose:** Event notification for workflow/task status changes

```java
public interface WorkflowEventPublisher {
    void publishTaskUpdate(String runId, TaskRun taskRun);
    void publishWorkflowUpdate(String runId, WorkflowRun workflowRun);
}
```

**Implementations:** Provided by workflow-api module
- PersistWorkflowEventPublisher (persistence + WebSocket)
- StompWorkflowEventPublisher (WebSocket only)
- NoOpWorkflowEventPublisher (no-op for testing)

---

### WorkflowSchedulerService

**Purpose:** Time-based workflow scheduling

```java
public interface WorkflowSchedulerService {
    void scheduleWorkflow(String workflowId, String cronExpression, Map<String, Object> context);
    void unscheduleWorkflow(String workflowId);
    List<String> listScheduledJobs();
}
```

**Implementation:** Provided by workflow-scheduler module (WorkflowSchedulerServiceImpl)

---

## Enumerations

### RunStatus
```java
public enum RunStatus {
    PENDING,   // Not yet started
    RUNNING,   // Currently executing
    SUCCESS,   // Completed successfully
    FAILED,    // Execution failed
    SKIPPED    // Skipped due to upstream failure
}
```

### TaskType
```java
public enum TaskType {
    HTTP,      // HTTP request task
    KAFKA,     // Kafka message task
    SCRIPT,    // Script execution task
    EMAIL,     // Email sending task
    CUSTOM     // Custom implementation
}
```

---

## Exception Hierarchy

All exceptions extend `WorkflowEngineException` (RuntimeException):

```
WorkflowEngineException (base)
├── RetryExhaustException          # All retries exhausted
├── TaskExecutionException         # Task execution error
├── TaskTimeoutException           # Task timeout exceeded
├── WorkflowDefinitionException    # Invalid workflow definition
├── TaskConfigurationException     # Invalid task configuration
├── EngineInitializationException  # Engine initialization error
├── ExecutorNotFoundException      # TaskExecutor not found
├── ContextPropagationException    # Context propagation error
├── UnsupportedTaskTypeException   # Unknown task type
├── PersistenceException           # Persistence error
└── WorkflowSchedulerException     # Scheduler error
```

---

## Validation

### WorkflowValidator

**Location:** `validation/WorkflowValidator.java`
**Type:** Spring @Service

**Validations Performed:**
1. **Unique Task Names**: No duplicate names in workflow
2. **Cycle Detection**: DFS-based algorithm to detect cycles in DAG
3. **Configuration Validation**:
   - maxRetries >= 0
   - timeoutMillis > 0
   - dependencies reference valid tasks

**Method:**
```java
public void validate(WorkflowDefinition definition) throws WorkflowDefinitionException
```

**Cycle Detection Algorithm:**
```java
1. Track visited nodes (WHITE/GRAY/BLACK states)
2. For each task node:
   - Mark as GRAY (in progress)
   - Visit all dependencies recursively
   - If dependency is GRAY: cycle detected!
   - Mark as BLACK (completed)
```

---

## Concurrency Model

### Thread Safety Guarantees

**ConcurrentHashMap:**
- ExecutionContext.data
- WorkflowRun.taskRuns
- WorkflowRun.mergedContextSnapshot
- WorkflowEngine.downstream map

**AtomicInteger:**
- TaskRun.attempt (retry counter)
- RetryingTaskRunner metrics counters
- WorkflowEngine metrics counters

**volatile:**
- WorkflowRun.status
- TaskRun.status

**CompletableFuture:**
- Task execution coordination
- Dependency waiting
- Parallel task execution

### Threading Model

```
Main Thread
    ↓
WorkflowFacade.startWorkflow()
    ↓
ExecutorService.submit() → Async Thread Pool
    ↓
WorkflowEngine.execute()
    ├─→ Task A (CompletableFuture) → Thread 1
    ├─→ Task B (CompletableFuture) → Thread 2
    └─→ Task C (CompletableFuture) → Thread 3
         ↓
    RetryingTaskRunner
         ↓
    ScheduledExecutorService (for retries)
```

---

## Dependencies

### Maven Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
    <version>3.2.5</version>
</dependency>

<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
    <version>6.2.7</version>
</dependency>
```

**No external workflow dependencies** - This is the foundation module.

---

## Design Principles

1. **Separation of Concerns**: Engine logic separate from persistence, API, scheduling
2. **Interface-Driven**: All extension points defined as interfaces
3. **Thread-Safe**: Careful use of concurrent data structures
4. **Observable**: Comprehensive metrics for monitoring
5. **Resilient**: Built-in retry logic with exponential backoff
6. **Testable**: Clean interfaces enable easy mocking
7. **Extensible**: Factory pattern for pluggable executors

---

## Usage Example

```java
@Service
public class MyWorkflowService {
    @Autowired
    private WorkflowFacade workflowFacade;

    @Autowired
    private TaskExecutorFactory factory;

    public void executeWorkflow() {
        // Create task executors
        TaskExecutor httpTask = factory.createExecutor(
            TaskType.HTTP,
            Map.of("url", "https://api.example.com", "method", "POST")
        );

        TaskExecutor emailTask = factory.createExecutor(
            TaskType.EMAIL,
            Map.of("to", "user@example.com", "subject", "Workflow Complete")
        );

        // Build task nodes
        TaskNode task1 = TaskNode.builder()
            .name("fetch-data")
            .taskExecutor(httpTask)
            .maxRetries(3)
            .build();

        TaskNode task2 = TaskNode.builder()
            .name("send-notification")
            .taskExecutor(emailTask)
            .dependencies(List.of(task1))  // Depends on task1
            .build();

        // Execute workflow
        CompletableFuture<WorkflowRun> future = workflowFacade.startWorkflow(
            "workflow-123",
            List.of(task1, task2),
            Map.of("userId", "user-456")
        );

        // Handle result
        future.thenAccept(workflowRun -> {
            System.out.println("Workflow Status: " + workflowRun.getStatus());
        });
    }
}
```

---

## Testing Considerations

- Use in-memory repository implementations for unit tests
- Mock TaskExecutor implementations for isolated engine testing
- Use NoOpWorkflowEventPublisher for tests without event publishing
- Test cycle detection with various DAG structures
- Test retry logic with controlled failure scenarios
- Test concurrent execution with multiple independent tasks

---

**Last Updated:** January 2026
**Module Version:** 1.0-SNAPSHOT