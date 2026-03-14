# Workflow-Persistence Module - Architecture Documentation

## Module Overview

- **Module Name:** workflow-persistence
- **Artifact ID:** workflow-persistence
- **Version:** 1.0-SNAPSHOT
- **Java Version:** 21
- **Spring Boot Version:** 3.3.13

## Purpose

The **workflow-persistence** module is the **data persistence layer** for the workflow orchestration engine. It provides:
- JPA entity mappings for workflow and task execution data
- Repository implementations (JPA-based and in-memory)
- Database schema management via SQL initialization scripts
- Data transformation between domain models and persistence entities
- Support for PostgreSQL with JSONB for flexible context storage
- Dual persistence strategy (production database + testing in-memory)

This module implements the repository interfaces defined in **workflow-core** and manages all data persistence concerns, keeping the core execution engine clean and storage-agnostic.

## Directory Structure

```
workflow-persistence/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/persistence/
    │   │   ├── entity/                         # JPA Entities
    │   │   │   ├── WorkflowRunEntity.java
    │   │   │   ├── TaskRunEntity.java
    │   │   │   └── WorkflowDefinitionEntity.java
    │   │   ├── repo/                           # Repository Implementations
    │   │   │   ├── WorkflowRunJpaRepository.java
    │   │   │   ├── WorkflowRunRepositoryImpl.java
    │   │   │   ├── TaskRunJpaRepository.java
    │   │   │   ├── TaskRunRepositoryImpl.java
    │   │   │   ├── WorkflowDefinitionJpaRepository.java
    │   │   │   ├── WorkflowDefinitionRepositoryImpl.java
    │   │   │   ├── InMemWorkflowRunRepository.java
    │   │   │   ├── InMemTaskRunRepository.java
    │   │   │   └── PersistenceConfig.java
    │   │   ├── mapper/                         # Entity Mappers
    │   │   │   ├── WorkflowEntityMapper.java
    │   │   │   └── WorkflowDefinitionMapper.java
    │   │   └── util/                           # Utilities
    │   │       └── JsonbConverter.java
    │   └── resources/
    │       └── db/migration/
    │           └── init.sql                    # Database schema
    └── test/ (no test files currently)
```

## Core Components

### 1. JPA Entities

#### WorkflowRunEntity

**Table:** `WORKFLOW_RUNS`
**Purpose:** Persists workflow execution instances

**Key Fields:**
```java
@Id
private String runId;              // Primary key (UUID)

@Column(nullable = false)
private String workflowId;         // Reference to workflow definition

@Enumerated(EnumType.STRING)
@Column(nullable = false)
private RunStatus status;          // PENDING/RUNNING/SUCCESS/FAILED/SKIPPED

private Instant startTime;         // Execution start timestamp
private Instant endTime;           // Execution end timestamp
private Long durationMillis;       // Total execution duration

@OneToMany(mappedBy = "workflowRunEntity", cascade = CascadeType.ALL,
           orphanRemoval = true, fetch = FetchType.LAZY)
private List<TaskRunEntity> taskRuns;  // Child task executions

@Convert(converter = JsonbConverter.class)
@Column(columnDefinition = "jsonb")
private Map<String, Object> mergedContext;  // Accumulated execution context

@Convert(converter = JsonbConverter.class)
@Column(columnDefinition = "jsonb")
private Map<String, Object> metadata;       // Custom metadata

@Column(updatable = false)
private Instant created;           // Record creation (auto-set via @PrePersist)

private Instant updated;           // Last update (auto-set via @PreUpdate)
```

**Relationships:**
- **One-to-Many** with TaskRunEntity (parent workflow → child tasks)
- **Cascade ALL**: All operations cascade to child tasks
- **Orphan Removal**: Deleted tasks removed from database
- **Lazy Fetch**: Task runs loaded on-demand

**Lifecycle Callbacks:**
```java
@PrePersist
protected void onCreate() {
    created = Instant.now();
    updated = Instant.now();
}

@PreUpdate
protected void onUpdate() {
    updated = Instant.now();
}
```

---

#### TaskRunEntity

**Table:** `TASK_RUNS`
**Purpose:** Persists individual task execution instances

**Key Fields:**
```java
@Id
private String id;                 // Primary key (UUID)

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "workflow_id", nullable = false)
private WorkflowRunEntity workflowRunEntity;  // Parent workflow

@Column(nullable = false)
private String taskName;           // Task identifier

@Enumerated(EnumType.STRING)
@Column(nullable = false)
private RunStatus status;          // Task execution status

private Integer attempt;           // Current retry attempt
private Integer maxRetries;        // Maximum allowed retries

private Instant startTime;         // Task start timestamp
private Instant endTime;           // Task end timestamp
private Long durationMillis;       // Task execution duration

private String errorMessage;       // Error summary
@Column(columnDefinition = "TEXT")
private String errorStackTrace;    // Full stack trace

@Convert(converter = JsonbConverter.class)
@Column(columnDefinition = "jsonb")
private Map<String, Object> contextDelta;  // Data produced by this task

@Convert(converter = JsonbConverter.class)
@Column(columnDefinition = "jsonb")
private Map<String, Object> metaData;      // Custom metadata

@Column(updatable = false)
private Instant created;           // Record creation

private Instant updated;           // Last update
```

**Relationships:**
- **Many-to-One** with WorkflowRunEntity (child task → parent workflow)
- **Lazy Fetch**: Parent workflow loaded on-demand

---

#### WorkflowDefinitionEntity

**Table:** `WORKFLOW_DEFINITION`
**Purpose:** Persists workflow blueprints/templates

**Key Fields:**
```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private String id;                 // Primary key (auto-generated UUID)

@Column(nullable = false)
private String name;               // Workflow name

@Column(nullable = false)
private Integer version;           // Version number

private String description;        // Human-readable description

@Column(columnDefinition = "jsonb", nullable = false)
private String taskDefinitions;    // JSON serialized task definitions

private Instant created;           // Definition creation timestamp
private Instant updated;           // Definition update timestamp
```

---

### 2. JPA Repository Interfaces

#### WorkflowRunJpaRepository

```java
@Repository
public interface WorkflowRunJpaRepository extends JpaRepository<WorkflowRunEntity, String> {
    // Inherits:
    // - save(entity)
    // - findById(id)
    // - findAll()
    // - delete(entity)
    // - etc.
}
```

**Provided by Spring Data JPA:** All basic CRUD operations

---

#### TaskRunJpaRepository

```java
@Repository
public interface TaskRunJpaRepository extends JpaRepository<TaskRunEntity, String> {
    List<TaskRunEntity> findByWorkflowId(String workflowId);
}
```

**Custom Query:** Find all tasks for a specific workflow

---

#### WorkflowDefinitionJpaRepository

```java
@Repository
public interface WorkflowDefinitionJpaRepository extends JpaRepository<WorkflowDefinitionEntity, String> {
    Optional<WorkflowDefinitionEntity> findByName(String name);
}
```

**Custom Query:** Find definition by workflow name

---

### 3. Repository Implementations

#### WorkflowRunRepositoryImpl

**Implements:** `WorkflowRunRepository` from workflow-core
**Type:** Spring @Repository

```java
@Repository
public class WorkflowRunRepositoryImpl implements WorkflowRunRepository {
    private final WorkflowRunJpaRepository jpaRepository;
    private final WorkflowEntityMapper mapper;

    @Override
    public WorkflowRun save(WorkflowRun workflowRun) {
        WorkflowRunEntity entity = mapper.toEntity(workflowRun);
        WorkflowRunEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public WorkflowRun findById(String id) {
        return jpaRepository.findById(id)
            .map(mapper::toDomain)
            .orElse(null);
    }

    @Override
    public List<WorkflowRun> findAll() {
        return jpaRepository.findAll().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<WorkflowRun> findByStatus(RunStatus status) {
        // Note: Not yet implemented in JPA repository
        return List.of();
    }
}
```

**Responsibilities:**
- Transform domain models to entities via mapper
- Delegate to JPA repository for persistence
- Transform entities back to domain models
- Provide abstraction over JPA implementation

---

#### TaskRunRepositoryImpl

**Implements:** `TaskRunRepository` from workflow-core
**Status:** STUB (minimal implementation)

```java
@Repository
public class TaskRunRepositoryImpl implements TaskRunRepository {
    @Override
    public TaskRun save(TaskRun taskRun) { return null; }  // TODO

    @Override
    public TaskRun findById(String id) { return null; }  // TODO

    // Other methods also return empty/null
}
```

**Note:** Currently incomplete - needs full implementation

---

#### WorkflowDefinitionRepositoryImpl

**Implements:** `WorkflowDefinitionRepository` from workflow-core

```java
@Repository
public class WorkflowDefinitionRepositoryImpl implements WorkflowDefinitionRepository {
    private final WorkflowDefinitionJpaRepository jpaRepository;
    private final WorkflowDefinitionMapper mapper;

    @Override
    public WorkflowDefinition save(WorkflowDefinition definition) {
        WorkflowDefinitionEntity entity = mapper.toEntity(definition);
        WorkflowDefinitionEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public WorkflowDefinition findByName(String name) {
        return jpaRepository.findByName(name)
            .map(mapper::toDomain)
            .orElse(null);
    }

    // Other methods similar
}
```

---

### 4. In-Memory Implementations

#### InMemWorkflowRunRepository

**Purpose:** Testing and lightweight scenarios without database
**Storage:** ConcurrentHashMap<String, WorkflowRun>

```java
@Repository
@Profile("test")  // Only active in test profile
public class InMemWorkflowRunRepository implements WorkflowRunRepository {
    private final Map<String, WorkflowRun> storage = new ConcurrentHashMap<>();

    @Override
    public WorkflowRun save(WorkflowRun workflowRun) {
        storage.put(workflowRun.getRunId(), workflowRun);
        return workflowRun;
    }

    @Override
    public WorkflowRun findById(String id) {
        return storage.get(id);
    }

    // Other methods operate on in-memory map
}
```

**Benefits:**
- No database setup required for tests
- Fast operation
- Thread-safe (ConcurrentHashMap)

---

#### InMemTaskRunRepository

**Similar to InMemWorkflowRunRepository** but for TaskRun objects

---

### 5. Mappers (Domain ↔ Entity Transformation)

#### WorkflowEntityMapper

**Purpose:** Convert between WorkflowRun (domain) and WorkflowRunEntity (JPA)

```java
@Component
public class WorkflowEntityMapper {
    public WorkflowRunEntity toEntity(WorkflowRun workflowRun) {
        WorkflowRunEntity entity = new WorkflowRunEntity();
        entity.setRunId(workflowRun.getRunId());
        entity.setWorkflowId(workflowRun.getWorkflowId());
        entity.setStatus(workflowRun.getStatus());
        entity.setStartTime(workflowRun.getStartTime());
        entity.setEndTime(workflowRun.getEndTime());
        entity.setDurationMillis(workflowRun.getDurationMillis());
        entity.setMergedContext(workflowRun.getMergedContextSnapshot());
        entity.setMetadata(workflowRun.getMetaData());

        // Convert TaskRuns
        List<TaskRunEntity> taskRunEntities = workflowRun.getTaskRuns().values()
            .stream()
            .map(this::toTaskRunEntity)
            .collect(Collectors.toList());
        entity.setTaskRuns(taskRunEntities);

        return entity;
    }

    public WorkflowRun toDomain(WorkflowRunEntity entity) {
        // Reverse transformation
        // Converts entity fields back to domain object
    }

    private TaskRunEntity toTaskRunEntity(TaskRun taskRun) {
        // Convert individual TaskRun to TaskRunEntity
    }
}
```

**Key Responsibilities:**
- Bidirectional conversion (domain ↔ entity)
- Handle nested TaskRun collections
- Copy all fields including JSONB maps
- Set parent-child relationships

---

#### WorkflowDefinitionMapper

**Purpose:** Convert WorkflowDefinition and serialize/deserialize task definitions

```java
@Component
public class WorkflowDefinitionMapper {
    private final TaskExecutorFactory taskExecutorFactory;
    private final ObjectMapper objectMapper;  // Jackson JSON

    public WorkflowDefinitionEntity toEntity(WorkflowDefinition definition) {
        // Serialize taskDefinitions to JSON string
        String taskDefJson = objectMapper.writeValueAsString(
            definition.getTasks()
        );

        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity();
        entity.setId(definition.getId());
        entity.setName(definition.getName());
        entity.setVersion(definition.getVersion());
        entity.setTaskDefinitions(taskDefJson);
        // ...
        return entity;
    }

    public WorkflowDefinition toDomain(WorkflowDefinitionEntity entity) {
        // Deserialize JSON string back to TaskNode objects
        // Use TaskExecutorFactory to recreate executors
        // Rebuild task dependency graph
    }
}
```

**Challenges:**
- TaskNode contains TaskExecutor instances (not directly serializable)
- Must recreate executors using TaskExecutorFactory during deserialization
- Preserve task dependencies (DAG structure)

---

### 6. JsonbConverter (Hibernate Converter)

**Purpose:** Convert Map<String, Object> to/from PostgreSQL JSONB

```java
@Converter
public class JsonbConverter implements AttributeConverter<Map<String, Object>, String> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) return null;
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert Map to JSON", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return objectMapper.readValue(dbData, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert JSON to Map", e);
        }
    }
}
```

**Usage:** Applied to entity fields with `@Convert(converter = JsonbConverter.class)`

---

## Database Schema

### Schema File Location

`/Users/deveshsingh/Project/workflow-engine/workflow-engine/src/main/resources/db/migration/init.sql`

### Table Definitions

#### WORKFLOW_RUNS

```sql
CREATE TABLE WORKFLOW_RUNS (
    RUN_ID TEXT PRIMARY KEY,
    WORKFLOW_ID TEXT NOT NULL,
    STATUS VARCHAR(32) NOT NULL,
    START_TIME TIMESTAMP WITH TIME ZONE,
    END_TIME TIMESTAMP WITH TIME ZONE,
    DURATION_MILLIS BIGINT,
    MERGED_CONTEXT JSONB,
    METADATA JSONB,
    CREATED TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UPDATED TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IDX_WORKFLOW_RUNS_STATUS ON WORKFLOW_RUNS(STATUS);
CREATE INDEX IDX_WORKFLOW_RUNS_WORKFLOW_ID ON WORKFLOW_RUNS(WORKFLOW_ID);
```

**Key Features:**
- Text primary key (UUID strings)
- JSONB columns for flexible context/metadata storage
- Timestamp with timezone support
- Indexed status and workflow_id for fast queries

---

#### TASK_RUNS

```sql
CREATE TABLE TASK_RUNS (
    ID TEXT PRIMARY KEY,
    WORKFLOW_ID TEXT NOT NULL REFERENCES WORKFLOW_RUNS(RUN_ID) ON DELETE CASCADE,
    TASK_NAME TEXT NOT NULL,
    STATUS VARCHAR(32) NOT NULL,
    ATTEMPT INT,
    MAX_RETRIES INT,
    START_TIME TIMESTAMP WITH TIME ZONE,
    END_TIME TIMESTAMP WITH TIME ZONE,
    DURATION_MILLIS BIGINT,
    ERROR TEXT,
    ERROR_DETAILS TEXT,
    MERGED_CONTEXT JSONB,
    METADATA JSONB,
    CREATED TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UPDATED TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IDX_TASK_RUNS_RUN_ID ON TASK_RUNS(RUN_ID);
CREATE INDEX IDX_TASK_RUNS_STATUS ON TASK_RUNS(STATUS);
CREATE INDEX IDX_TASK_RUNS_TASK_NAME ON TASK_RUNS(TASK_NAME);
```

**Key Features:**
- Foreign key constraint to WORKFLOW_RUNS
- CASCADE DELETE: Tasks deleted when parent workflow deleted
- Retry tracking (ATTEMPT, MAX_RETRIES)
- Separate ERROR (summary) and ERROR_DETAILS (stack trace)
- Indexes on workflow_id, status, task_name

---

#### WORKFLOW_DEFINITION

```sql
CREATE TABLE WORKFLOW_DEFINITION (
    ID TEXT PRIMARY KEY,
    NAME VARCHAR(255) NOT NULL,
    VERSION INT NOT NULL,
    DESCRIPTION TEXT,
    TASK_DEFINITION JSONB NOT NULL,
    CREATED TIMESTAMP NOT NULL DEFAULT NOW(),
    UPDATED TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**Key Features:**
- Stores task definitions as JSONB
- Supports workflow versioning
- Lookup by name capability

---

### Quartz Scheduler Tables

The schema also includes 40+ tables for Quartz scheduler:
- QRTZ_JOB_DETAILS
- QRTZ_TRIGGERS
- QRTZ_CRON_TRIGGERS
- QRTZ_SIMPLE_TRIGGERS
- QRTZ_FIRED_TRIGGERS
- QRTZ_LOCKS
- etc.

**Purpose:** Persistent storage for scheduled workflows

---

## Persistence Strategy

### Dual Strategy

**1. JPA/PostgreSQL (Production)**
- Full ACID guarantees
- Complex queries via JPA
- JSONB support for flexible context storage
- Relationship management (cascade, lazy loading)
- Transaction support

**2. In-Memory (Testing/Development)**
- No database setup required
- Fast operation
- Perfect for unit tests
- Thread-safe ConcurrentHashMap storage
- Profile-based activation

### Strategy Selection

**Via Spring Profiles:**
```java
@Repository
@Profile("prod")
public class WorkflowRunRepositoryImpl implements WorkflowRunRepository { }

@Repository
@Profile("test")
public class InMemWorkflowRunRepository implements WorkflowRunRepository { }
```

**Activation:**
```properties
spring.profiles.active=prod  # Use JPA
spring.profiles.active=test  # Use in-memory
```

---

## Configuration

### PersistenceConfig

```java
@Configuration
@EnableJpaRepositories(basePackages = "com.example.persistence.repo")
@EntityScan(basePackages = "com.example.persistence.entity")
public class PersistenceConfig {
    // Additional configuration beans if needed
}
```

**Purpose:**
- Enable JPA repositories scanning
- Enable entity scanning
- Configure JPA settings

---

## Dependencies

### Maven Dependencies

```xml
<dependency>
    <groupId>com.example.workflow</groupId>
    <artifactId>workflow-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
    <version>3.3.13</version>
</dependency>

<!-- PostgreSQL driver (runtime) -->
<!-- Jackson (for JSON conversion) -->
<!-- Hibernate (via JPA starter) -->
```

---

## Data Flow

### Save Workflow Run

```
1. WorkflowEngine creates WorkflowRun (domain model)
    ↓
2. Call workflowRunRepository.save(workflowRun)
    ↓
3. WorkflowRunRepositoryImpl.save()
    ├─ WorkflowEntityMapper.toEntity(workflowRun)
    │   ├─ Convert WorkflowRun → WorkflowRunEntity
    │   ├─ Convert each TaskRun → TaskRunEntity
    │   └─ Set parent-child relationships
    ├─ JsonbConverter.convertToDatabaseColumn(context)
    │   └─ Serialize Map to JSON string
    ├─ JpaRepository.save(entity)
    │   └─ Hibernate INSERT INTO WORKFLOW_RUNS
    └─ WorkflowEntityMapper.toDomain(savedEntity)
        └─ Return domain object to caller
```

### Load Workflow Run

```
1. Call workflowRunRepository.findById(runId)
    ↓
2. WorkflowRunRepositoryImpl.findById()
    ├─ JpaRepository.findById(runId)
    │   └─ Hibernate SELECT FROM WORKFLOW_RUNS
    ├─ Lazy load taskRuns (if accessed)
    │   └─ SELECT FROM TASK_RUNS WHERE WORKFLOW_ID = ?
    ├─ JsonbConverter.convertToEntityAttribute(jsonString)
    │   └─ Deserialize JSON to Map
    └─ WorkflowEntityMapper.toDomain(entity)
        └─ Return WorkflowRun (domain model)
```

---

## Design Patterns

| Pattern | Implementation | Purpose |
|---------|----------------|---------|
| **Repository** | WorkflowRunRepository interface + impl | Abstract data access |
| **Mapper** | WorkflowEntityMapper | Transform domain ↔ entity |
| **Converter** | JsonbConverter | Map ↔ JSONB conversion |
| **Strategy** | JPA vs In-Memory implementations | Swappable persistence |
| **Dependency Injection** | Spring @Repository, @Component | Loose coupling |

---

## Known Issues / TODOs

1. **TaskRunRepositoryImpl**: Currently a stub - needs full implementation
2. **Query Methods**: Missing findByStatus() implementation in JPA repository
3. **Transactions**: No explicit @Transactional annotations (may need for complex operations)
4. **Error Handling**: Limited error handling in mappers
5. **Performance**: No query optimization or caching configured

---

## Testing Recommendations

- Use in-memory implementations for fast unit tests
- Use Testcontainers with PostgreSQL for integration tests
- Test JSONB serialization/deserialization edge cases
- Test cascade delete behavior
- Test lazy loading scenarios
- Verify transaction boundaries

---

## Future Enhancements

- Add database migration tool (Flyway or Liquibase)
- Implement query result caching
- Add database indexes based on query patterns
- Implement soft delete instead of hard delete
- Add audit logging for all changes
- Implement optimistic locking with @Version

---

**Last Updated:** January 2026
**Module Version:** 1.0-SNAPSHOT