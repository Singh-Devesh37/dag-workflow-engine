# Workflow Engine - Architecture Documentation

## Project Overview

- **Project Name:** Workflow Engine
- **Version:** 1.0-SNAPSHOT
- **Type:** DAG (Directed Acyclic Graph) Executor
- **Technology Stack:** Java 21, Spring Boot 3.2.5/3.3.13, PostgreSQL, Quartz Scheduler

## Description

The Workflow Engine is a production-grade, distributed workflow orchestration system designed to execute complex business processes modeled as Directed Acyclic Graphs (DAGs). Inspired by modern workflow executors like Apache Airflow, Temporal, and AWS Step Functions, this engine provides:

- **Parallel Task Execution**: Executes independent tasks concurrently while respecting dependencies
- **Resilient Execution**: Built-in retry mechanisms with exponential backoff and jitter
- **Persistent State**: Full workflow and task execution history stored in PostgreSQL
- **Time-based Scheduling**: Cron-based workflow scheduling using Quartz
- **Real-time Updates**: WebSocket support for live workflow and task status updates
- **Extensible Executors**: Plugin architecture for custom task types
- **Observable**: Comprehensive metrics via Micrometer/Prometheus

## High-Level Architecture

### Module Structure

The project follows a **multi-module Maven architecture** with clear separation of concerns:

```
workflow-engine (parent)
├── workflow-core          # Core execution engine and domain models
├── workflow-persistence   # Data persistence layer (JPA/PostgreSQL)
├── workflow-executors     # Task executor implementations
├── workflow-scheduler     # Time-based scheduling (Quartz)
└── workflow-api          # REST API and WebSocket interface
```

### Module Dependency Graph

```
                    ┌─────────────────┐
                    │  workflow-api   │
                    │  (REST/WS API)  │
                    └────────┬────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
            ┌───────▼──────┐   ┌─────▼──────────┐
            │workflow-     │   │workflow-       │
            │scheduler     │   │core            │
            │(Quartz)      │   │(Engine)        │
            └──────────────┘   └────────────────┘
                                      ▲
                         ┌────────────┼────────────┐
                         │            │            │
                  ┌──────▼──────┐ ┌──▼──────┐ ┌──▼──────────┐
                  │workflow-    │ │workflow-│ │workflow-    │
                  │persistence  │ │executors│ │scheduler    │
                  │(JPA)        │ │(Tasks)  │ │(Quartz)     │
                  └─────────────┘ └─────────┘ └─────────────┘
```

**Dependency Direction:** Bottom modules depend on workflow-core; workflow-api depends on core + scheduler

### Key Architectural Patterns

| Pattern | Usage | Location |
|---------|-------|----------|
| **Layered Architecture** | Separation of API, business logic, and persistence | All modules |
| **Repository Pattern** | Abstract data access from business logic | workflow-core interfaces, workflow-persistence implementation |
| **Factory Pattern** | Create task executors by type | TaskExecutorFactory in workflow-executors |
| **Strategy Pattern** | Pluggable task execution strategies | TaskExecutor interface implementations |
| **Facade Pattern** | Simplified high-level API | WorkflowFacade in workflow-core |
| **Publisher-Subscriber** | Event-driven updates | WorkflowEventPublisher for WebSocket events |
| **Mapper Pattern** | Transform between layers (domain ↔ entity ↔ DTO) | Mappers in workflow-persistence and workflow-api |

## System Architecture

### Execution Flow

```
1. Client Request
   ↓
2. REST API (workflow-api)
   ├─ POST /api/workflows/start
   └─ POST /api/workflows/schedule
   ↓
3. WorkflowFacade (workflow-core)
   ├─ Validates workflow definition
   ├─ Creates WorkflowRun instance
   └─ Delegates to WorkflowEngine
   ↓
4. WorkflowEngine (workflow-core)
   ├─ Builds DAG from task dependencies
   ├─ Identifies tasks with no dependencies (entry points)
   ├─ Executes tasks via RetryingTaskRunner
   └─ Uses CompletableFuture for parallel execution
   ↓
5. RetryingTaskRunner (workflow-core)
   ├─ Clones ExecutionContext for isolation
   ├─ Calls TaskExecutor.execute()
   ├─ Retries on failure with exponential backoff
   └─ Returns TaskExecutionResult
   ↓
6. TaskExecutor (workflow-executors)
   ├─ HTTPTaskExecutor
   ├─ KafkaTaskExecutor
   ├─ EmailTaskExecutor
   ├─ ScriptTaskExecutor
   └─ CustomTaskExecutor
   ↓
7. Context Propagation
   ├─ Merge contextDelta into global ExecutionContext
   └─ Pass updated context to downstream tasks
   ↓
8. Event Publishing (workflow-api)
   ├─ Persist TaskRun to database
   ├─ Publish to WebSocket: /topic/workflows/{runId}/tasks/
   ├─ Persist WorkflowRun to database
   └─ Publish to WebSocket: /topic/workflows/{runId}
   ↓
9. Response
   └─ Return WorkflowRunResponse to client
```

### Scheduling Flow

```
1. Schedule Request
   ↓
2. POST /api/schedules (workflow-api)
   ↓
3. WorkflowFacade.scheduleWorkflow() (workflow-core)
   ↓
4. WorkflowSchedulerServiceImpl (workflow-scheduler)
   ├─ Creates Quartz JobDetail (WorkflowTriggerJob)
   ├─ Creates CronTrigger with provided expression
   └─ Persists to PostgreSQL (QRTZ_* tables)
   ↓
5. Quartz Scheduler (at scheduled time)
   ├─ Fires trigger
   └─ Executes WorkflowTriggerJob.execute()
   ↓
6. WorkflowTriggerJob
   ├─ Retrieves workflowId and context from JobDataMap
   └─ Calls WorkflowFacade.startWorkflowByDefinitionName()
   ↓
7. Standard Execution Flow (see above)
```

## Core Domain Models

### WorkflowDefinition
- Blueprint/template for a workflow
- Contains task definitions and their dependencies
- Supports versioning
- Stored as JSONB in PostgreSQL

### WorkflowRun
- Single execution instance of a workflow
- Tracks status (PENDING → RUNNING → SUCCESS/FAILED)
- Contains collection of TaskRuns
- Maintains merged context snapshot from all tasks
- Records timing and duration metrics

### TaskNode
- Represents a node in the DAG
- Contains TaskExecutor instance
- Defines task configuration, retries, timeout
- Lists dependencies (edges in the graph)

### TaskRun
- Execution instance of a specific task
- Tracks attempt count for retries
- Stores error details (message + stack trace)
- Contains contextDelta (data produced by this task)
- Records timing information

### ExecutionContext
- Thread-safe (ConcurrentHashMap) context for sharing data
- Cloned per retry attempt for isolation
- Supports merging of contextDeltas
- Propagates data between dependent tasks

## Technology Stack

### Backend
- **Java**: 21 (LTS)
- **Spring Boot**: 3.2.5 / 3.3.13
- **Spring Framework**: 6.2.7
- **Quartz Scheduler**: 4.0.0-M3
- **PostgreSQL Driver**: 42.7.7
- **Lombok**: 1.18.30

### API & Communication
- **Spring Web**: REST API endpoints
- **Spring WebSocket**: Real-time updates via STOMP
- **SpringDoc OpenAPI**: 2.5.0 (Swagger UI)

### Persistence
- **Spring Data JPA**: Repository abstraction
- **Hibernate**: ORM framework
- **PostgreSQL**: Primary database with JSONB support
- **Quartz JDBC JobStore**: Persistent job scheduling

### Observability
- **Micrometer**: 1.16.0-M3 (metrics)
- **Prometheus**: Registry for metrics export
- **Logback**: Logging framework
- **Logstash Encoder**: 7.4 (structured logging)

## Database Schema

### Core Workflow Tables

**WORKFLOW_RUNS**
- Stores workflow execution instances
- Columns: runId (PK), workflowId, status, startTime, endTime, durationMillis, mergedContext (JSONB), metadata (JSONB)
- Indexes: status, workflowId

**TASK_RUNS**
- Stores task execution instances
- Columns: id (PK), workflowId (FK), taskName, status, attempt, maxRetries, startTime, endTime, durationMillis, error, errorDetails, mergedContext (JSONB), metadata (JSONB)
- Foreign Key: CASCADE DELETE on workflowId
- Indexes: workflowId, status, taskName

**WORKFLOW_DEFINITION**
- Stores workflow blueprints
- Columns: id (PK), name, version, description, taskDefinition (JSONB)
- Supports versioning and lookup by name

### Quartz Scheduler Tables
- **QRTZ_JOB_DETAILS**: Job definitions
- **QRTZ_TRIGGERS**: Trigger configurations
- **QRTZ_CRON_TRIGGERS**: Cron expressions
- **QRTZ_FIRED_TRIGGERS**: Currently executing triggers
- **QRTZ_LOCKS**: Distributed locking for clustering

## Concurrency and Threading

### Thread Safety Measures
- **ConcurrentHashMap**: Task runs map, execution context, downstream dependencies
- **AtomicInteger**: Attempt counters, running task/workflow gauges
- **volatile**: Status fields for visibility across threads
- **CompletableFuture**: Async task coordination and dependency management
- **ExecutorService**: Thread pool for parallel task execution
- **ScheduledExecutorService**: Delayed retry scheduling

### Concurrency Model
- Each workflow execution runs in its own CompletableFuture chain
- Independent tasks within a workflow execute in parallel
- Dependent tasks wait via CompletableFuture.allOf()
- Retry delays scheduled asynchronously
- Context cloned per retry for isolation
- Thread pool configurable (default: 10 threads for Quartz)

## API Endpoints

### Workflow Operations
- `POST /api/workflows/start` - Start workflow execution
- `POST /api/workflows/schedule` - Schedule workflow with cron
- `GET /api/workflows/runs` - List all workflow runs
- `GET /api/workflows/runs/{runId}` - Get specific workflow run

### Workflow Definitions
- `POST /api/workflow-definitions` - Create workflow definition
- `GET /api/workflow-definitions/definitions/{name}` - Get by name
- `GET /api/workflow-definitions/definitions` - List all definitions

### Scheduling
- `POST /api/schedules` - Create schedule
- `GET /api/schedules` - List scheduled jobs
- `DELETE /api/schedules/{workflowId}` - Remove schedule

### WebSocket
- **Endpoint**: `/ws` (SockJS fallback enabled)
- **Task Updates**: `/topic/workflows/{runId}/tasks/`
- **Workflow Updates**: `/topic/workflows/{runId}`
- **Protocol**: STOMP over WebSocket

## Extension Points

### Custom Task Executors
1. Implement `TaskExecutor` interface
2. Define execution logic in `execute(ExecutionContext)` method
3. Register with `TaskExecutorFactory` using `register()` method
4. Add to TaskType enum (or use CUSTOM type)

### Custom Event Publishers
1. Implement `WorkflowEventPublisher` interface
2. Define `publishTaskUpdate()` and `publishWorkflowUpdate()` methods
3. Mark as Spring @Service with appropriate profile

### Custom Persistence
1. Implement repository interfaces from workflow-core
2. Choose between JPA (WorkflowRunRepositoryImpl) or in-memory implementations
3. Configure via Spring profiles

## Metrics and Observability

### Workflow Metrics
- `workflow_total{status=started|completed|failed}` - Counter
- `workflow_duration_seconds` - Timer/Histogram
- `workflow_running_gauge` - Active workflows

### Task Metrics
- `task_total{status=success|failed}` - Counter
- `task_retry_total` - Retry attempts counter
- `task_duration_seconds` - Timer/Histogram
- `task_active_gauge` - Active tasks

### Exposure
- Prometheus endpoint: `/actuator/prometheus`
- Metrics registry: Micrometer with Prometheus format

## Configuration

### Database Configuration
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/workflowdb
spring.datasource.username=workflow_user
spring.datasource.password=workflow_pass
```

### Quartz Configuration
```properties
org.quartz.scheduler.instanceName=WorkflowQuartzScheduler
org.quartz.threadPool.threadCount=10
org.quartz.jobStore.class=JobStoreTX
org.quartz.jobStore.driverDelegateClass=PostgreSQLDelegate
```

### CORS Configuration
- Allows all origins (configurable in WebConfig)
- Methods: GET, POST, PUT, DELETE, OPTIONS

## Deployment Considerations

### Clustering Support
- Quartz supports clustering via JDBC JobStore
- Distributed locking via QRTZ_LOCKS table
- Auto instance ID generation
- Multiple scheduler instances can share job load

### Scalability
- Horizontal scaling: Multiple API instances behind load balancer
- Vertical scaling: Increase thread pool size in Quartz configuration
- Database connection pooling (default: 10 connections)

### High Availability
- Stateless API layer for easy replication
- Database serves as single source of truth
- Quartz handles scheduler failover automatically
- WebSocket sessions need sticky sessions or Redis-backed sessions

## Security Considerations

### Current State
- No authentication/authorization implemented
- All endpoints publicly accessible
- CORS allows all origins

### Recommendations for Production
- Add Spring Security for authentication
- Implement JWT or OAuth2 for API access
- Restrict CORS to known domains
- Add role-based access control (RBAC)
- Encrypt sensitive workflow context data
- Enable HTTPS/TLS for API and WebSocket

## Module Documentation

For detailed module-level architecture, see:
- [workflow-core/Architecture.md](workflow-core/Architecture.md) - Core execution engine
- [workflow-persistence/Architecture.md](workflow-persistence/Architecture.md) - Data persistence layer
- [workflow-executors/Architecture.md](workflow-executors/Architecture.md) - Task executor implementations
- [workflow-scheduler/Architecture.md](workflow-scheduler/Architecture.md) - Quartz scheduling
- [workflow-api/Architecture.md](workflow-api/Architecture.md) - REST and WebSocket API

## Future Enhancements

### Planned Features
- **Conditional Branching**: Support for if/else logic in workflows
- **Dynamic DAG**: Runtime task generation based on results
- **Sub-workflows**: Nested workflow execution
- **Workflow Templates**: Reusable workflow patterns
- **Advanced Scheduling**: Time windows, dependencies between schedules
- **Workflow Versioning**: Safe upgrades of running workflows
- **Admin UI**: Web interface for workflow monitoring (workflow-ui in progress)

### Performance Optimizations
- Task result caching
- Workflow execution graph optimization
- Lazy loading of task runs
- Database query optimization with specific indexes
- Connection pool tuning

## License

[Specify license here]

## Contributors

[List contributors here]

---

**Last Updated:** January 2026
**Version:** 1.0-SNAPSHOT