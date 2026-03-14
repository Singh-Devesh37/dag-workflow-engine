# Workflow-Executors Module - Architecture Documentation

## Module Overview

- **Module Name:** workflow-executors
- **Artifact ID:** workflow-executors
- **Version:** 1.0-SNAPSHOT
- **Java Version:** 21

## Purpose

The **workflow-executors** module provides **concrete implementations of task executors** for the workflow orchestration engine. It implements the Strategy pattern, offering pluggable task execution logic for different task types:

- HTTP requests
- Kafka message publishing
- Email sending
- Script execution
- Custom user-defined tasks

This module focuses exclusively on **"how to execute this type of task"**, delegating retry logic, dependency management, persistence, and scheduling to other modules. It implements the `TaskExecutor` interface from workflow-core and provides a factory for creating executor instances.

## Directory Structure

```
workflow-executors/
├── pom.xml
└── src/
    ├── main/java/com/example/executor/
    │   ├── TaskExecutorFactoryImpl.java     # Factory implementation
    │   ├── HTTPTaskExecutor.java            # HTTP task executor
    │   ├── KafkaTaskExecutor.java           # Kafka task executor
    │   ├── EmailTaskExecutor.java           # Email task executor
    │   ├── ScriptTaskExecutor.java          # Script task executor
    │   └── CustomTaskExecutor.java          # Custom task executor
    └── test/java/com/example/executor/
        └── WorkflowEngineTest.java          # Example/test usage
```

## Core Components

### 1. TaskExecutorFactoryImpl (Factory)

**Location:** `TaskExecutorFactoryImpl.java`
**Implements:** `TaskExecutorFactory` from workflow-core
**Pattern:** Factory + Registry

#### Purpose
Centralized creation of task executors based on TaskType. Supports runtime registration of new executor types for extensibility.

#### Implementation

```java
public class TaskExecutorFactoryImpl implements TaskExecutorFactory {
    private final Map<TaskType, Function<Map<String, Object>, TaskExecutor>> registry;

    public TaskExecutorFactoryImpl() {
        registry = new HashMap<>();

        // Register built-in executors
        register(TaskType.HTTP, HTTPTaskExecutor::new);
        register(TaskType.KAFKA, KafkaTaskExecutor::new);
        register(TaskType.SCRIPT, ScriptTaskExecutor::new);
        register(TaskType.EMAIL, EmailTaskExecutor::new);
        register(TaskType.CUSTOM, CustomTaskExecutor::new);
    }

    @Override
    public TaskExecutor createExecutor(TaskType type, Map<String, Object> config) {
        Function<Map<String, Object>, TaskExecutor> builder = registry.get(type);
        if (builder == null) {
            throw new UnsupportedTaskTypeException("No executor for type: " + type);
        }
        return builder.apply(config);
    }

    @Override
    public void register(TaskType type, Function<Map<String, Object>, TaskExecutor> builder) {
        registry.put(type, builder);
    }
}
```

#### Key Features
- **Registry Pattern**: HashMap maps TaskType → constructor Function
- **Method References**: Uses constructor references (e.g., `HTTPTaskExecutor::new`)
- **Runtime Registration**: Supports adding new executors via `register()` method
- **Fail-Fast**: Throws exception for unknown task types

#### Usage Example
```java
TaskExecutorFactory factory = new TaskExecutorFactoryImpl();

// Create HTTP executor
Map<String, Object> config = Map.of("url", "https://api.example.com", "method", "POST");
TaskExecutor httpExecutor = factory.createExecutor(TaskType.HTTP, config);

// Register custom executor
factory.register(TaskType.CUSTOM, MyCustomExecutor::new);
```

---

### 2. HTTPTaskExecutor

**Location:** `HTTPTaskExecutor.java`
**Type:** HTTP Request Executor
**Status:** Partially implemented

#### Purpose
Executes HTTP requests (REST API calls) as part of workflow tasks.

#### Configuration
```java
Map<String, Object> config = Map.of(
    "url", "https://api.example.com/endpoint",
    "method", "POST"  // GET, POST, PUT, DELETE, etc.
);
```

#### Implementation

```java
public class HTTPTaskExecutor implements TaskExecutor {
    private final String url;
    private final String method;

    public HTTPTaskExecutor(Map<String, Object> config) {
        this.url = (String) config.getOrDefault("url", "");
        this.method = (String) config.getOrDefault("method", "GET");
    }

    @Override
    public TaskExecutionResult execute(ExecutionContext context) {
        log.info("Executing HTTP task: {} {}", method, url);

        // TODO: Implement actual HTTP call
        // - Use HttpClient or RestTemplate
        // - Set headers from context
        // - Send request body from context
        // - Parse response
        // - Return contextDelta with response data

        return TaskExecutionResult.success(
            "HTTP task executed",
            Map.of("responseStatus", 200)
        );
    }

    @Override
    public TaskType getType() {
        return TaskType.HTTP;
    }
}
```

#### Future Enhancements
- Integrate HTTP client library (Java HttpClient, OkHttp, or RestTemplate)
- Support request headers, body, query parameters
- Handle authentication (Basic, Bearer, OAuth)
- Parse and return response data in contextDelta
- Support timeout configuration
- Handle different content types (JSON, XML, form data)

---

### 3. KafkaTaskExecutor

**Location:** `KafkaTaskExecutor.java`
**Type:** Kafka Message Publisher
**Status:** Stub (minimal implementation)

#### Purpose
Publishes messages to Kafka topics as part of workflow tasks.

#### Configuration
```java
Map<String, Object> config = Map.of(
    "topic", "workflow-events",
    "message", "Task completed successfully"
);
```

#### Implementation

```java
public class KafkaTaskExecutor implements TaskExecutor {
    private final String topic;
    private final Object message;

    public KafkaTaskExecutor(Map<String, Object> config) {
        this.topic = (String) config.getOrDefault("topic", "");
        this.message = config.getOrDefault("message", "");
    }

    @Override
    public TaskExecutionResult execute(ExecutionContext context) {
        // TODO: Implement Kafka producer
        return null;
    }

    @Override
    public TaskType getType() {
        return TaskType.KAFKA;
    }
}
```

#### Future Enhancements
- Integrate Kafka client library (spring-kafka or kafka-clients)
- Support Kafka producer configuration (bootstrap servers, serializers)
- Allow message key specification
- Support headers and partitioning
- Handle producer acknowledgments
- Return message offset in contextDelta

---

### 4. EmailTaskExecutor

**Location:** `EmailTaskExecutor.java`
**Type:** Email Sender
**Status:** Stub (minimal implementation)

#### Purpose
Sends emails as part of workflow tasks (notifications, alerts, reports).

#### Configuration
```java
Map<String, Object> config = Map.of(
    "to", "user@example.com",
    "subject", "Workflow Completed",
    "body", "Your workflow has completed successfully"
);
```

#### Implementation

```java
public class EmailTaskExecutor implements TaskExecutor {
    private final String to;
    private final String subject;
    private final String body;

    public EmailTaskExecutor(Map<String, Object> config) {
        this.to = (String) config.getOrDefault("to", "");
        this.subject = (String) config.getOrDefault("subject", "");
        this.body = (String) config.getOrDefault("body", "");
    }

    @Override
    public TaskExecutionResult execute(ExecutionContext context) {
        // TODO: Implement email sending
        return null;
    }

    @Override
    public TaskType getType() {
        return TaskType.EMAIL;
    }
}
```

#### Future Enhancements
- Integrate email library (JavaMail or Spring Mail)
- Support SMTP configuration
- Handle CC, BCC recipients
- Support HTML email templates
- Support attachments
- Template variable substitution from context
- Handle email delivery errors

---

### 5. ScriptTaskExecutor

**Location:** `ScriptTaskExecutor.java`
**Type:** Script Executor
**Status:** Stub (minimal implementation)

#### Purpose
Executes scripts (bash, python, groovy, javascript) as part of workflow tasks.

#### Configuration
```java
Map<String, Object> config = Map.of(
    "script", "echo 'Hello World'",
    "language", "bash",  // bash, python, groovy, javascript
    "parameters", Map.of("param1", "value1")
);
```

#### Implementation

```java
public class ScriptTaskExecutor implements TaskExecutor {
    private final String script;
    private final String language;
    private final Map<String, Object> parameters;

    public ScriptTaskExecutor(Map<String, Object> config) {
        this.script = (String) config.getOrDefault("script", "");
        this.language = (String) config.getOrDefault("language", "bash");
        this.parameters = (Map) config.getOrDefault("parameters", Map.of());
    }

    @Override
    public TaskExecutionResult execute(ExecutionContext context) {
        // TODO: Implement script execution
        return null;
    }

    @Override
    public TaskType getType() {
        return TaskType.SCRIPT;
    }
}
```

#### Future Enhancements
- Support multiple scripting languages:
  - Bash: Use ProcessBuilder
  - Python: Invoke python interpreter
  - Groovy: Use GroovyShell
  - JavaScript: Use GraalVM or Nashorn
- Pass context variables as script parameters
- Capture script output (stdout, stderr)
- Return script results in contextDelta
- Handle script timeouts
- Sandbox script execution for security
- Support script file paths (not just inline scripts)

---

### 6. CustomTaskExecutor

**Location:** `CustomTaskExecutor.java`
**Type:** User-Defined Executor
**Status:** Stub (minimal implementation)

#### Purpose
Allows users to define custom task execution logic without modifying the engine.

#### Configuration
```java
Map<String, Object> config = Map.of(
    "handler", "com.mycompany.CustomHandler",
    "customParam", "value"
);
```

#### Implementation

```java
public class CustomTaskExecutor implements TaskExecutor {
    private final Map<String, Object> config;

    public CustomTaskExecutor(Map<String, Object> config) {
        this.config = config;
    }

    @Override
    public TaskExecutionResult execute(ExecutionContext context) {
        // TODO: Implement custom task handler invocation
        // - Load handler class dynamically
        // - Invoke handler with config and context
        // - Return handler result
        return null;
    }

    @Override
    public TaskType getType() {
        return TaskType.CUSTOM;
    }
}
```

#### Future Enhancements
- Support handler class loading via reflection
- Define handler interface for consistent API
- Support Spring bean lookup for handlers
- Allow handler registration at runtime
- Support dependency injection in handlers
- Provide base handler class for common functionality

---

## Integration with Workflow-Core

### Interface Contract

**TaskExecutor Interface** (from workflow-core):
```java
public interface TaskExecutor {
    TaskExecutionResult execute(ExecutionContext context);
    TaskType getType();
}
```

**All executors implement this interface**, ensuring consistent interaction with the engine.

### Execution Flow

```
1. TaskDefinitionConverter (workflow-core)
    ├─ Uses TaskExecutorFactory to create executors
    └─ Stores executors in TaskNode objects

2. WorkflowEngine (workflow-core)
    └─ Passes TaskNode to RetryingTaskRunner

3. RetryingTaskRunner (workflow-core)
    ├─ Clones ExecutionContext
    ├─ Calls taskExecutor.execute(context)
    ├─ Retries on failure with exponential backoff
    └─ Returns TaskExecutionResult

4. TaskExecutor Implementation (this module)
    ├─ Reads configuration from constructor
    ├─ Reads input data from ExecutionContext
    ├─ Performs task-specific operation
    ├─ Returns success/failure with contextDelta
    └─ Does NOT handle retries (delegated)

5. WorkflowEngine (workflow-core)
    └─ Merges contextDelta into global context
```

### What Executors DO NOT Do

Executors are **single-purpose, stateless execution units**. They do NOT:
- Handle retry logic (delegated to RetryingTaskRunner)
- Manage dependencies (delegated to WorkflowEngine)
- Persist results (delegated to workflow-persistence)
- Schedule execution (delegated to workflow-scheduler)
- Publish events (delegated to workflow-api)
- Manage transactions
- Track metrics (delegated to RetryingTaskRunner)

### What Executors DO

Executors:
- Parse task configuration
- Read input from ExecutionContext
- Execute task-specific operation (HTTP call, Kafka publish, etc.)
- Return TaskExecutionResult with:
  - Success/failure status
  - Status message
  - Error (if failed)
  - Context delta (output data for downstream tasks)

---

## Design Patterns

| Pattern | Implementation | Purpose |
|---------|----------------|---------|
| **Strategy** | TaskExecutor interface | Pluggable execution strategies |
| **Factory** | TaskExecutorFactoryImpl | Centralized executor creation |
| **Registry** | HashMap in factory | Runtime executor registration |
| **Configuration Object** | Map<String, Object> config | Flexible task configuration |

---

## Dependencies

### Maven Dependencies

```xml
<dependency>
    <groupId>com.example.workflow</groupId>
    <artifactId>workflow-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Only depends on workflow-core** for:
- TaskExecutor interface
- TaskExecutorFactory interface
- TaskType enum
- ExecutionContext class
- TaskExecutionResult class
- Custom exceptions

### Future Dependencies

When implementations are completed, will need:
- **HTTP Client**: `java.net.http.HttpClient` (built-in) or OkHttp
- **Kafka Client**: `spring-kafka` or `kafka-clients`
- **Email**: `spring-boot-starter-mail` or JavaMail
- **Script Engines**: Groovy, GraalVM for JavaScript

---

## Error Handling

### Exception Strategy

Executors throw exceptions for errors, which are caught by RetryingTaskRunner:
```java
@Override
public TaskExecutionResult execute(ExecutionContext context) {
    try {
        // Perform operation
        return TaskExecutionResult.success("...", contextDelta);
    } catch (Exception e) {
        return TaskExecutionResult.failure("Operation failed", e);
    }
}
```

### Available Exceptions (from workflow-core)
- `TaskExecutionException` - General task execution error
- `TaskTimeoutException` - Task exceeded timeout
- `TaskConfigurationException` - Invalid task configuration
- `UnsupportedTaskTypeException` - Unknown task type

---

## Configuration Pattern

All executors follow the same configuration pattern:

```java
public TaskExecutor(Map<String, Object> config) {
    this.field1 = (String) config.getOrDefault("field1", "default");
    this.field2 = (Integer) config.getOrDefault("field2", 0);
    // Validate configuration
    if (field1.isEmpty()) {
        throw new TaskConfigurationException("field1 is required");
    }
}
```

**Benefits:**
- Flexible: Any key-value pairs
- Type-safe: Cast to expected type
- Defaults: Use `getOrDefault()` for optional params
- Validated: Check required params in constructor

---

## Extensibility

### Adding a New Executor

**Step 1:** Create executor class
```java
public class DatabaseTaskExecutor implements TaskExecutor {
    private final String query;

    public DatabaseTaskExecutor(Map<String, Object> config) {
        this.query = (String) config.get("query");
    }

    @Override
    public TaskExecutionResult execute(ExecutionContext context) {
        // Execute database query
        // Return results in contextDelta
    }

    @Override
    public TaskType getType() {
        return TaskType.DATABASE;  // Add to enum
    }
}
```

**Step 2:** Add to TaskType enum (workflow-core)
```java
public enum TaskType {
    HTTP, KAFKA, SCRIPT, EMAIL, CUSTOM,
    DATABASE  // New type
}
```

**Step 3:** Register in factory
```java
public TaskExecutorFactoryImpl() {
    // ... existing registrations
    register(TaskType.DATABASE, DatabaseTaskExecutor::new);
}
```

**Step 4:** Use in workflows
```java
TaskNode dbTask = TaskNode.builder()
    .name("fetch-users")
    .taskExecutor(factory.createExecutor(
        TaskType.DATABASE,
        Map.of("query", "SELECT * FROM users")
    ))
    .build();
```

---

## Current Implementation Status

| Executor | Status | Completion |
|----------|--------|------------|
| HTTPTaskExecutor | Partial | 30% - Logs execution, needs HTTP client |
| KafkaTaskExecutor | Stub | 10% - Constructor only |
| EmailTaskExecutor | Stub | 10% - Constructor only |
| ScriptTaskExecutor | Stub | 10% - Constructor only |
| CustomTaskExecutor | Stub | 10% - Constructor only |
| TaskExecutorFactoryImpl | Complete | 100% - Fully functional |

---

## Testing Recommendations

### Unit Tests
- Test each executor in isolation
- Mock external dependencies (HTTP, Kafka, SMTP)
- Test configuration parsing
- Test error scenarios
- Verify contextDelta output

### Integration Tests
- Test with actual external systems
- Use testcontainers for Kafka, databases
- Use mock SMTP server for email
- Test end-to-end workflow execution

### Example Test
```java
@Test
public void testHTTPTaskExecutor() {
    Map<String, Object> config = Map.of(
        "url", "http://localhost:8080/api",
        "method", "POST"
    );

    HTTPTaskExecutor executor = new HTTPTaskExecutor(config);
    ExecutionContext context = new ExecutionContext();
    context.set("payload", Map.of("key", "value"));

    TaskExecutionResult result = executor.execute(context);

    assertTrue(result.isSuccess());
    assertNotNull(result.getContextDelta());
}
```

---

## Future Enhancements

### Additional Executors
- **Database Executor**: Execute SQL queries
- **File Executor**: Read/write files
- **FTP Executor**: Upload/download via FTP
- **SSH Executor**: Execute remote commands
- **AWS Lambda Executor**: Invoke Lambda functions
- **Docker Executor**: Run containers
- **Webhook Executor**: Call webhooks with retry

### Enhanced Features
- **Template Support**: Jinja2-style templates for configuration
- **Secret Management**: Integrate with Vault, AWS Secrets Manager
- **Circuit Breaker**: Prevent cascading failures
- **Rate Limiting**: Control external API usage
- **Result Caching**: Cache expensive operation results
- **Async Execution**: Support long-running tasks with callbacks

---

## Best Practices

1. **Stateless**: Executors should be stateless (all state in ExecutionContext)
2. **Idempotent**: Design for safe retries (same input → same output)
3. **Validation**: Validate configuration in constructor
4. **Logging**: Use structured logging with contextual information
5. **Error Messages**: Provide clear, actionable error messages
6. **Resource Cleanup**: Close connections, streams properly
7. **Timeouts**: Respect timeout configuration
8. **Context Delta**: Return only relevant data for downstream tasks

---

## Security Considerations

- **Script Execution**: Sandbox scripts to prevent code injection
- **HTTP Calls**: Validate URLs, prevent SSRF attacks
- **Credentials**: Never log sensitive configuration (passwords, API keys)
- **Input Validation**: Sanitize all inputs from ExecutionContext
- **Resource Limits**: Enforce memory/CPU limits for script execution
- **Network Access**: Restrict outbound network access if needed

---

**Last Updated:** January 2026
**Module Version:** 1.0-SNAPSHOT