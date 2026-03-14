# Workflow-API Module - Architecture Documentation

## Module Overview

- **Module Name:** workflow-api
- **Artifact ID:** workflow-api
- **Version:** 1.0-SNAPSHOT
- **Java Version:** 21
- **Spring Boot Version:** 3.2.5

## Purpose

The **workflow-api** module is the **presentation and communication layer** for the workflow orchestration engine. It provides:

- **REST API**: HTTP endpoints for workflow lifecycle operations
- **WebSocket/STOMP**: Real-time workflow and task status updates
- **Data Transformation**: DTOs for clean API contracts
- **Event Publishing**: Dual-mode (persistence + WebSocket) event notifications
- **API Documentation**: Auto-generated OpenAPI/Swagger documentation
- **Global Exception Handling**: Centralized error responses

This module serves as the **gateway** between external clients (web UIs, API consumers) and the internal workflow engine, exposing a clean, RESTful interface while abstracting internal complexity.

## Directory Structure

```
workflow-api/
├── pom.xml
└── src/
    └── main/java/com/example/api/
        ├── ApiResponse.java                        # Generic response wrapper
        ├── app/                                    # Application Objects (DTOs)
        │   ├── TaskRunAO.java
        │   ├── WorkflowRunAO.java
        │   └── WorkflowDefinitionAO.java
        ├── config/                                 # Configuration
        │   ├── WebConfig.java                     # CORS configuration
        │   └── WebSocketConfig.java               # WebSocket/STOMP setup
        ├── controller/                             # REST Controllers
        │   ├── WorkflowController.java
        │   ├── WorkflowDefinitionController.java
        │   ├── WorkflowRunController.java
        │   └── SchedulerController.java
        ├── exception/                              # Error Handling
        │   └── GlobalExceptionHandler.java
        ├── facade/                                 # (Reserved for future use)
        ├── mapper/                                 # DTO Mappers
        │   ├── WorkflowDefinitionMapper.java
        │   └── WorkflowResultMapper.java
        ├── model/                                  # Request/Response Models
        │   ├── StartWorkflowRequest.java
        │   ├── ScheduleWorkflowRequest.java
        │   ├── WorkflowRunResponse.java
        │   ├── WorkflowScheduleResponse.java
        │   ├── WorkflowEventMessage.java
        │   └── TaskEventMessage.java
        └── pub/                                    # Event Publishers
            ├── StompWorkflowEventPublisher.java   # WebSocket-only
            ├── PersistWorkflowEventPublisher.java # Persistence + WebSocket
            └── NoOpWorkflowEventPublisher.java    # No-op (testing)
```

## Core Components

### 1. REST API Controllers

#### WorkflowController

**Location:** `controller/WorkflowController.java`
**Base Path:** `/api/workflows`
**Purpose:** Workflow lifecycle operations

**Endpoints:**

**POST /api/workflows/start** - Start workflow execution
```java
@PostMapping("/start")
public ResponseEntity<ApiResponse<WorkflowRun>> startWorkflow(
    @RequestBody StartWorkflowRequest request
) {
    CompletableFuture<WorkflowRun> future = workflowFacade.startWorkflow(
        request.workflowId(),
        request.tasks(),
        request.initialContext()
    );

    WorkflowRun result = future.get();
    return ResponseEntity.ok(ApiResponse.success(result));
}
```

**POST /api/workflows/schedule** - Schedule workflow with cron
```java
@PostMapping("/schedule")
public ResponseEntity<ApiResponse<String>> scheduleWorkflow(
    @RequestBody ScheduleWorkflowRequest request
) {
    workflowFacade.scheduleWorkflow(
        request.workflowId(),
        request.initialContext(),
        request.cron()
    );

    return ResponseEntity.ok(ApiResponse.success("Workflow scheduled successfully"));
}
```

**GET /api/workflows/runs** - Get all workflow runs
```java
@GetMapping("/runs")
public ResponseEntity<ApiResponse<List<WorkflowRun>>> getAllWorkflowRuns() {
    List<WorkflowRun> runs = workflowFacade.getAllWorkflowRuns();
    return ResponseEntity.ok(ApiResponse.success(runs));
}
```

**GET /api/workflows/runs/{runId}** - Get specific workflow run
```java
@GetMapping("/runs/{runId}")
public ResponseEntity<ApiResponse<WorkflowRun>> getWorkflowRun(
    @PathVariable String runId
) {
    WorkflowRun run = workflowFacade.getWorkflowRun(runId);
    return ResponseEntity.ok(ApiResponse.success(run));
}
```

---

#### WorkflowDefinitionController

**Location:** `controller/WorkflowDefinitionController.java`
**Base Path:** `/api/workflow-definitions`
**Purpose:** Workflow definition CRUD operations

**Endpoints:**

**POST /api/workflow-definitions** - Create workflow definition
```java
@PostMapping
public ResponseEntity<ApiResponse<WorkflowDefinition>> createDefinition(
    @RequestBody WorkflowDefinitionAO definitionAO
) {
    // Convert AO to domain model
    WorkflowDefinition definition = mapper.toDomain(definitionAO);

    // Save definition
    WorkflowDefinition saved = workflowFacade.saveWorkflowDefinition(definition);

    return ResponseEntity.ok(ApiResponse.success("Workflow definition created", saved));
}
```

**GET /api/workflow-definitions/definitions/{name}** - Get definition by name
```java
@GetMapping("/definitions/{name}")
public ResponseEntity<ApiResponse<WorkflowDefinition>> getDefinitionByName(
    @PathVariable String name
) {
    WorkflowDefinition definition = workflowFacade.getWorkflowDefinitionByName(name);
    return ResponseEntity.ok(ApiResponse.success(definition));
}
```

**GET /api/workflow-definitions/definitions** - List all definitions
```java
@GetMapping("/definitions")
public ResponseEntity<ApiResponse<List<WorkflowDefinition>>> getAllDefinitions() {
    List<WorkflowDefinition> definitions = workflowFacade.getAllWorkflowDefinitions();
    return ResponseEntity.ok(ApiResponse.success(definitions));
}
```

---

#### WorkflowRunController

**Location:** `controller/WorkflowRunController.java`
**Base Path:** `/api/workflow-runs`
**Purpose:** Alternative workflow run endpoints

**Endpoints:**

**POST /api/workflow-runs/start** - Start workflow with response wrapper
```java
@PostMapping("/start")
public ApiResponse<WorkflowRunResponse> startWorkflow(
    @RequestBody StartWorkflowRequest request
) {
    CompletableFuture<WorkflowRun> future = workflowFacade.startWorkflow(...);
    WorkflowRun result = future.get();

    WorkflowRunResponse response = new WorkflowRunResponse(
        result.getRunId(),
        result.getStatus().toString(),
        "Workflow started successfully"
    );

    return ApiResponse.success(response);
}
```

**GET /api/workflow-runs/{runId}** - Get workflow run details
**GET /api/workflow-runs** - List all workflow runs

---

#### SchedulerController

**Location:** `controller/SchedulerController.java`
**Base Path:** `/api/schedules`
**Purpose:** Workflow scheduling operations

**Endpoints:**

**POST /api/schedules** - Create schedule
```java
@PostMapping
public ApiResponse<WorkflowScheduleResponse> createSchedule(
    @RequestBody ScheduleWorkflowRequest request
) {
    workflowFacade.scheduleWorkflow(
        request.workflowId(),
        request.initialContext(),
        request.cron()
    );

    WorkflowScheduleResponse response = new WorkflowScheduleResponse(
        "SUCCESS",
        "Workflow scheduled successfully"
    );

    return ApiResponse.success(response);
}
```

**GET /api/schedules** - List all scheduled jobs
```java
@GetMapping
public ApiResponse<List<String>> listSchedules() {
    List<String> schedules = workflowFacade.listScheduledJobs();
    return ApiResponse.success(schedules);
}
```

**DELETE /api/schedules/{workflowId}** - Remove schedule
```java
@DeleteMapping("/{workflowId}")
public ApiResponse<String> deleteSchedule(@PathVariable String workflowId) {
    workflowFacade.unscheduleWorkflow(workflowId);
    return ApiResponse.success("Schedule removed successfully");
}
```

---

### 2. WebSocket Configuration

#### WebSocketConfig

**Location:** `config/WebSocketConfig.java`
**Implements:** `WebSocketMessageBrokerConfigurer`

**Configuration:**
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for topic-based messaging
        config.enableSimpleBroker("/topic");

        // Application destination prefix for client messages
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register WebSocket endpoint with SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // CORS: allow all origins
                .withSockJS();  // Enable SockJS for browsers without WebSocket
    }
}
```

**Key Features:**
- **STOMP Protocol**: Messaging protocol over WebSocket
- **Simple Broker**: In-memory message broker for `/topic` destinations
- **SockJS Fallback**: Supports browsers without native WebSocket
- **CORS**: Allows all origins (configurable for production)

**Connection Details:**
- **Endpoint**: `ws://localhost:8080/ws` (or https in production)
- **Protocol**: STOMP over WebSocket (or SockJS)
- **Destinations**:
  - Task updates: `/topic/workflows/{runId}/tasks/`
  - Workflow updates: `/topic/workflows/{runId}`

---

### 3. Event Publishers

#### PersistWorkflowEventPublisher (Primary Implementation)

**Location:** `pub/PersistWorkflowEventPublisher.java`
**Type:** Spring @Service
**Strategy:** Dual-mode (persistence + WebSocket)

**Purpose:**
1. Persist task/workflow runs to database
2. Publish events to WebSocket subscribers

**Implementation:**

```java
@Service
public class PersistWorkflowEventPublisher implements WorkflowEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;
    private final WorkflowRunRepository workflowRunRepository;
    private final TaskRunRepository taskRunRepository;
    private final WorkflowResultMapper mapper;

    @Override
    public void publishTaskUpdate(String runId, TaskRun taskRun) {
        // 1. Persist to database
        taskRunRepository.save(taskRun);

        // 2. Transform to event message
        TaskEventMessage event = mapper.toTaskEventMessage(taskRun);

        // 3. Publish to WebSocket
        String destination = "/topic/workflows/" + runId + "/tasks/";
        messagingTemplate.convertAndSend(destination, event);
    }

    @Override
    public void publishWorkflowUpdate(String runId, WorkflowRun workflowRun) {
        // 1. Persist to database
        workflowRunRepository.save(workflowRun);

        // 2. Transform to event message
        WorkflowEventMessage event = mapper.toWorkflowEventMessage(workflowRun);

        // 3. Publish to WebSocket
        String destination = "/topic/workflows/" + runId;
        messagingTemplate.convertAndSend(destination, event);
    }
}
```

**Key Features:**
- **Persistence First**: Always saves to database before publishing
- **Event Transformation**: Converts domain models to lightweight event messages
- **Topic-Based Routing**: Each workflow run has dedicated WebSocket topics
- **Spring Integration**: Uses SimpMessagingTemplate for WebSocket messaging

---

#### StompWorkflowEventPublisher (WebSocket-Only)

**Location:** `pub/StompWorkflowEventPublisher.java`
**Strategy:** WebSocket-only (no persistence)

**Use Case:** When persistence is handled elsewhere or not needed

---

#### NoOpWorkflowEventPublisher (Testing)

**Location:** `pub/NoOpWorkflowEventPublisher.java`
**Strategy:** No-op implementation

**Use Case:** Testing environments without WebSocket support

---

### 4. DTOs and API Models

#### Application Objects (AO)

**WorkflowRunAO** - Workflow execution data transfer
```java
public record WorkflowRunAO(
    String runId,
    String workflowId,
    RunStatus status,
    Instant startTime,
    Instant endTime,
    Map<String, TaskRunAO> taskRuns,
    Map<String, Object> mergedContextSnapshot
) {}
```

**TaskRunAO** - Task execution data transfer
```java
public record TaskRunAO(
    String id,
    String taskName,
    RunStatus status,
    int attempt,
    int maxRetries,
    Instant startTime,
    Instant endTime,
    Long durationMillis,
    String errorMessage,
    Map<String, Object> contextDelta
) {}
```

**WorkflowDefinitionAO** - Workflow definition with nested task definitions
```java
public record WorkflowDefinitionAO(
    String id,
    String name,
    String description,
    List<TaskDefinitionAO> tasks
) {
    public record TaskDefinitionAO(
        String name,
        String type,
        Map<String, Object> config,
        int maxRetries,
        long initialDelayMillis,
        long timeoutMillis
    ) {}
}
```

---

#### Request Models

**StartWorkflowRequest** - Workflow execution request
```java
public record StartWorkflowRequest(
    @NotBlank String workflowId,
    @NotEmpty List<TaskNode> tasks,
    Map<String, Object> initialContext
) {}
```

**ScheduleWorkflowRequest** - Workflow scheduling request
```java
public record ScheduleWorkflowRequest(
    @NotBlank String workflowId,
    Map<String, Object> initialContext,
    @NotBlank String cron
) {}
```

**Validation:** Uses JSR-303 annotations (@NotBlank, @NotEmpty)

---

#### Response Models

**WorkflowRunResponse** - Simplified workflow run response
```java
public record WorkflowRunResponse(
    String runId,
    String status,
    String message
) {}
```

**WorkflowScheduleResponse** - Scheduling operation response
```java
public record WorkflowScheduleResponse(
    String status,
    String message
) {}
```

**ApiResponse<T>** - Generic API response wrapper
```java
public record ApiResponse<T>(
    boolean success,
    String message,
    T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
```

---

#### Event Messages (WebSocket)

**TaskEventMessage** - WebSocket event for task updates
```java
public record TaskEventMessage(
    String id,
    String workflowId,
    String taskName,
    RunStatus runStatus,
    Instant timestamp,
    int attempt,
    Map<String, Object> contextDelta
) {}
```

**WorkflowEventMessage** - WebSocket event for workflow updates
```java
public record WorkflowEventMessage(
    String runId,
    String workflowId,
    RunStatus runStatus,
    Instant timestamp,
    Map<String, Object> details  // Contains: startTime, endTime, taskCount
) {}
```

---

### 5. Mappers (Transformation Layer)

#### WorkflowDefinitionMapper

**Location:** `mapper/WorkflowDefinitionMapper.java`
**Purpose:** Convert between WorkflowDefinitionAO (API) and WorkflowDefinition (domain)

**Key Methods:**
```java
@Component
public class WorkflowDefinitionMapper {
    private final TaskExecutorFactory taskExecutorFactory;

    public WorkflowDefinition toDomain(WorkflowDefinitionAO ao) {
        // Convert each TaskDefinitionAO to TaskNode
        Map<String, TaskNode> tasks = ao.tasks().stream()
            .map(this::toTaskNode)
            .collect(Collectors.toMap(TaskNode::getName, t -> t));

        return WorkflowDefinition.builder()
            .id(ao.id())
            .name(ao.name())
            .description(ao.description())
            .tasks(tasks)
            .build();
    }

    private TaskNode toTaskNode(TaskDefinitionAO taskAO) {
        // Create TaskExecutor using factory
        TaskType taskType = TaskType.valueOf(taskAO.type());
        TaskExecutor executor = taskExecutorFactory.createExecutor(
            taskType,
            taskAO.config()
        );

        return TaskNode.builder()
            .name(taskAO.name())
            .taskExecutor(executor)
            .config(taskAO.config())
            .maxRetries(taskAO.maxRetries())
            .initialDelayMillis(taskAO.initialDelayMillis())
            .timeoutMillis(taskAO.timeoutMillis())
            .build();
    }

    public WorkflowDefinitionAO toAO(WorkflowDefinition definition) {
        // Reverse conversion
    }
}
```

**Challenge:** Recreating TaskExecutor instances from configuration

---

#### WorkflowResultMapper

**Location:** `mapper/WorkflowResultMapper.java`
**Purpose:** Convert domain models to event messages

**Key Methods:**
```java
@Component
public class WorkflowResultMapper {
    public TaskEventMessage toTaskEventMessage(TaskRun taskRun) {
        return new TaskEventMessage(
            taskRun.getId(),
            taskRun.getWorkflowId(),
            taskRun.getTaskName(),
            taskRun.getStatus(),
            Instant.now(),
            taskRun.getAttempt().get(),
            taskRun.getContextDelta()
        );
    }

    public WorkflowEventMessage toWorkflowEventMessage(WorkflowRun workflowRun) {
        Map<String, Object> details = Map.of(
            "startTime", workflowRun.getStartTime(),
            "endTime", workflowRun.getEndTime(),
            "taskCount", workflowRun.getTaskRuns().size()
        );

        return new WorkflowEventMessage(
            workflowRun.getRunId(),
            workflowRun.getWorkflowId(),
            workflowRun.getStatus(),
            Instant.now(),
            details
        );
    }

    public WorkflowRunAO toWorkflowRunAO(WorkflowRun workflowRun) {
        // Convert to application object
    }
}
```

---

### 6. Exception Handling

#### GlobalExceptionHandler

**Location:** `exception/GlobalExceptionHandler.java`
**Type:** @ControllerAdvice

**Purpose:** Centralized exception handling for all REST endpoints

**Implementation:**

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WorkflowEngineException.class)
    public ResponseEntity<ApiResponse<Void>> handleWorkflowEngineException(
        WorkflowEngineException ex
    ) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(WorkflowDefinitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleWorkflowDefinitionException(
        WorkflowDefinitionException ex
    ) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
        MethodArgumentNotValidException ex
    ) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Validation failed: " + errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred"));
    }
}
```

**Handled Exceptions:**
- `WorkflowEngineException` → 500 Internal Server Error
- `WorkflowDefinitionException` → 400 Bad Request
- `MethodArgumentNotValidException` → 400 Bad Request (validation errors)
- `Exception` → 500 Internal Server Error (catch-all)

---

### 7. Configuration

#### WebConfig (CORS)

**Location:** `config/WebConfig.java`

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

**Configuration:**
- Allows all origins (configurable for production)
- Allows common HTTP methods
- Supports credentials (cookies, authorization headers)

---

## API Documentation (OpenAPI/Swagger)

### SpringDoc OpenAPI Integration

**Dependency:**
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.5.0</version>
</dependency>
```

**Auto-Generated Endpoints:**
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`
- **JSON Format**: `http://localhost:8080/v3/api-docs.json`

**Features:**
- Auto-discovers all @RestController classes
- Generates API documentation from method signatures
- Interactive API explorer
- Request/response examples
- Schema definitions

**Future Enhancement:** Add OpenAPI annotations for richer documentation
```java
@Operation(summary = "Start workflow execution", description = "...")
@ApiResponse(responseCode = "200", description = "Workflow started successfully")
```

---

## Dependencies

### Maven Dependencies

```xml
<!-- Workflow Core -->
<dependency>
    <groupId>com.example.workflow</groupId>
    <artifactId>workflow-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- Workflow Scheduler -->
<dependency>
    <groupId>com.example.workflow</groupId>
    <artifactId>workflow-scheduler</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- Spring Boot Web (REST API) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Spring Boot WebSocket (Real-time updates) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<!-- SpringDoc OpenAPI (API Documentation) -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.5.0</version>
</dependency>
```

---

## Complete Request/Response Flow

### Workflow Execution Flow

```
1. Client HTTP Request
   ↓
   POST /api/workflows/start
   {
     "workflowId": "data-pipeline",
     "tasks": [...],
     "initialContext": {"input": "data"}
   }
   ↓
2. WorkflowController.startWorkflow()
   ├─ Validate request (JSR-303)
   └─ Call workflowFacade.startWorkflow()
   ↓
3. WorkflowFacade (workflow-core)
   ├─ Create WorkflowRun instance
   ├─ Submit to ExecutorService (async)
   └─ Return CompletableFuture<WorkflowRun>
   ↓
4. HTTP Response (immediate)
   {
     "success": true,
     "data": {
       "runId": "uuid-123",
       "status": "PENDING",
       ...
     }
   }
   ↓
5. WorkflowEngine (workflow-core - async)
   ├─ Execute tasks in DAG order
   ├─ For each task completion:
   │   └─ Call workflowEventPublisher.publishTaskUpdate()
   └─ On workflow completion:
       └─ Call workflowEventPublisher.publishWorkflowUpdate()
   ↓
6. PersistWorkflowEventPublisher (this module)
   ├─ Save TaskRun to database
   ├─ Transform to TaskEventMessage
   └─ Publish to WebSocket: /topic/workflows/uuid-123/tasks/
   ↓
7. WebSocket Clients (subscribed to /topic/workflows/uuid-123/tasks/)
   ├─ Receive real-time task update
   └─ Update UI with task status
   ↓
8. Final Workflow Update
   ├─ Save WorkflowRun to database
   ├─ Transform to WorkflowEventMessage
   └─ Publish to WebSocket: /topic/workflows/uuid-123
   ↓
9. WebSocket Clients (subscribed to /topic/workflows/uuid-123)
   ├─ Receive workflow completion event
   └─ Update UI with final workflow status
```

---

## WebSocket Client Example

### JavaScript Client (SockJS + STOMP)

```javascript
// Connect to WebSocket
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);

    // Subscribe to workflow updates
    stompClient.subscribe('/topic/workflows/uuid-123', function(message) {
        const workflowEvent = JSON.parse(message.body);
        console.log('Workflow status:', workflowEvent.runStatus);
        // Update UI...
    });

    // Subscribe to task updates
    stompClient.subscribe('/topic/workflows/uuid-123/tasks/', function(message) {
        const taskEvent = JSON.parse(message.body);
        console.log('Task', taskEvent.taskName, ':', taskEvent.runStatus);
        // Update UI...
    });
});
```

---

## Security Considerations

### Current State
- **No Authentication**: All endpoints publicly accessible
- **No Authorization**: No role-based access control
- **CORS**: Allows all origins (development mode)
- **WebSocket**: No authentication on WebSocket connections

### Production Recommendations

**1. Add Spring Security**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

**2. Implement JWT Authentication**
- Issue JWT tokens on login
- Validate tokens in SecurityFilterChain
- Protect endpoints with @PreAuthorize

**3. Secure WebSocket**
```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new AuthChannelInterceptor());
}
```

**4. Restrict CORS**
```java
.allowedOriginPatterns("https://myapp.com", "https://admin.myapp.com")
```

**5. Rate Limiting**
- Use Spring Cloud Gateway or API Gateway
- Implement request throttling

---

## Testing Recommendations

### Controller Tests
```java
@WebMvcTest(WorkflowController.class)
class WorkflowControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowFacade workflowFacade;

    @Test
    void testStartWorkflow() throws Exception {
        mockMvc.perform(post("/api/workflows/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workflowId\":\"test\",\"tasks\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}
```

### WebSocket Tests
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketTest {
    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;

    @Test
    void testWebSocketConnection() throws Exception {
        // Connect and subscribe to topic
        // Trigger workflow
        // Verify WebSocket message received
    }
}
```

---

## Monitoring and Observability

### Metrics Exposure

**Spring Boot Actuator** (if configured):
```
GET /actuator/prometheus  # Prometheus metrics
GET /actuator/health      # Health check
GET /actuator/info        # Application info
```

### Logging

Structured logging for all API requests:
```java
@Slf4j
@RestController
public class WorkflowController {
    @PostMapping("/start")
    public ResponseEntity<?> startWorkflow(@RequestBody StartWorkflowRequest request) {
        log.info("Starting workflow: workflowId={}", request.workflowId());
        // ...
    }
}
```

---

## Future Enhancements

### API Enhancements
- **Pagination**: Support for paginated workflow run queries
- **Filtering**: Filter workflows by status, date range
- **Sorting**: Sort results by creation time, status
- **Partial Updates**: PATCH endpoints for workflow updates
- **Bulk Operations**: Start/stop multiple workflows

### WebSocket Enhancements
- **Authentication**: JWT-based WebSocket authentication
- **Selective Subscriptions**: Subscribe to specific task types
- **Binary Messages**: Support for binary payloads
- **Compression**: Message compression for large payloads

### Documentation Enhancements
- **OpenAPI Annotations**: Richer API documentation
- **Examples**: Request/response examples in docs
- **Postman Collection**: Auto-generate Postman collections
- **Client SDKs**: Auto-generate client libraries

---

## Best Practices

1. **Async Operations**: Always return immediately from REST endpoints
2. **Validation**: Validate all inputs with JSR-303
3. **Error Handling**: Use GlobalExceptionHandler for consistent errors
4. **Versioning**: Consider API versioning (/api/v1/workflows)
5. **DTOs**: Never expose internal domain models directly
6. **Documentation**: Keep OpenAPI docs updated
7. **CORS**: Restrict origins in production
8. **Rate Limiting**: Implement API rate limits
9. **Logging**: Log all API operations for audit trail
10. **Testing**: Test all endpoints with integration tests

---

**Last Updated:** January 2026
**Module Version:** 1.0-SNAPSHOT