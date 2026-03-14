# Workflow-Scheduler Module - Architecture Documentation

## Module Overview

- **Module Name:** workflow-scheduler
- **Artifact ID:** workflow-scheduler
- **Version:** 1.0-SNAPSHOT
- **Java Version:** 21
- **Quartz Version:** 4.0.0-M3

## Purpose

The **workflow-scheduler** module provides **time-based workflow scheduling capabilities** using Apache Quartz Scheduler. It enables:

- Cron-based workflow scheduling (e.g., daily, weekly, hourly)
- Persistent job storage in PostgreSQL
- Distributed scheduling with clustering support
- Job lifecycle management (create, delete, list)
- Automatic workflow trigger at scheduled times
- Integration with the workflow engine for seamless execution

This module implements the `WorkflowSchedulerService` interface from workflow-core and handles all scheduling concerns, delegating actual workflow execution back to the workflow engine.

## Directory Structure

```
workflow-scheduler/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/scheduler/
    │   │   ├── config/
    │   │   │   └── QuartzConfig.java              # Quartz configuration
    │   │   ├── job/
    │   │   │   └── WorkflowTriggerJob.java        # Quartz job implementation
    │   │   ├── model/
    │   │   │   └── WorkflowScheduleRequest.java   # Request DTO
    │   │   ├── repo/
    │   │   │   └── SchedulerJobEntity.java        # (Stub) Future enhancement
    │   │   └── service/
    │   │       ├── QuartzJobBuilder.java          # (Stub) Future enhancement
    │   │       └── WorkflowSchedulerServiceImpl.java  # Core scheduling logic
    │   └── resources/
    │       └── quartz.properties                   # Quartz configuration file
    └── test/ (no test files currently)
```

## Core Components

### 1. QuartzConfig (Spring Configuration)

**Location:** `config/QuartzConfig.java`
**Type:** Spring @Configuration

#### Purpose
Configures Quartz Scheduler as a Spring bean with database persistence and Spring integration.

#### Implementation

```java
@Configuration
public class QuartzConfig {

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean() {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();

        // Enable Spring dependency injection in Quartz jobs
        factory.setJobFactory(springBeanJobFactory());

        // Load Quartz properties
        factory.setQuartzProperties(quartzProperties());

        // Wait for running jobs before shutdown
        factory.setWaitForJobsToCompleteOnShutdown(true);

        return factory;
    }

    @Bean
    public Properties quartzProperties() {
        Properties properties = new Properties();
        // Load from quartz.properties file
        return properties;
    }

    @Bean
    public SpringBeanJobFactory springBeanJobFactory() {
        return new SpringBeanJobFactory();
    }
}
```

#### Key Features
- **Spring Integration**: `SpringBeanJobFactory` enables @Autowired in jobs
- **Properties Loading**: Loads configuration from `quartz.properties`
- **Graceful Shutdown**: Waits for running jobs to complete before shutting down
- **Database Persistence**: Configured via properties (JobStoreTX)

---

### 2. WorkflowSchedulerServiceImpl (Core Service)

**Location:** `service/WorkflowSchedulerServiceImpl.java`
**Implements:** `WorkflowSchedulerService` from workflow-core
**Type:** Spring @Service

#### Purpose
Implements scheduling operations using Quartz Scheduler APIs.

#### Dependencies
```java
@Service
public class WorkflowSchedulerServiceImpl implements WorkflowSchedulerService {
    private final Scheduler scheduler;
    private final WorkflowFacade workflowFacade;
    private final ObjectMapper objectMapper;

    // Constructor injection
}
```

#### Method: scheduleWorkflow()

```java
@Override
public void scheduleWorkflow(String workflowId, String cronExpression,
                             Map<String, Object> ctx) {
    try {
        // 1. Create JobDataMap with workflowId and serialized context
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("workflowId", workflowId);
        jobDataMap.put("context", objectMapper.writeValueAsString(ctx));

        // 2. Build JobDetail
        JobDetail jobDetail = JobBuilder.newJob(WorkflowTriggerJob.class)
            .withIdentity("workflow-" + workflowId, "workflow-jobs")
            .usingJobData(jobDataMap)
            .storeDurably()  // Persist even without triggers
            .build();

        // 3. Build CronTrigger
        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("trigger-" + workflowId, "workflow-triggers")
            .forJob(jobDetail)
            .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
            .build();

        // 4. Schedule the job
        scheduler.scheduleJob(jobDetail, trigger);

    } catch (SchedulerException | JsonProcessingException e) {
        throw new WorkflowSchedulerException("Failed to schedule workflow", e);
    }
}
```

**Key Steps:**
1. Serialize execution context to JSON
2. Create Quartz JobDetail with WorkflowTriggerJob class
3. Create CronTrigger with provided cron expression
4. Schedule job with trigger in Quartz
5. Job and trigger persisted to PostgreSQL

---

#### Method: unscheduleWorkflow()

```java
@Override
public void unscheduleWorkflow(String workflowId) {
    try {
        JobKey jobKey = JobKey.jobKey("workflow-" + workflowId, "workflow-jobs");
        scheduler.deleteJob(jobKey);  // Removes job and all associated triggers
    } catch (SchedulerException e) {
        throw new WorkflowSchedulerException("Failed to unschedule workflow", e);
    }
}
```

**Behavior:**
- Deletes job from Quartz
- Automatically removes all associated triggers
- Removes from database (QRTZ_JOB_DETAILS, QRTZ_TRIGGERS)

---

#### Method: listScheduledJobs()

```java
@Override
public List<String> listScheduledJobs() {
    try {
        List<String> jobNames = new ArrayList<>();
        List<String> groupNames = scheduler.getJobGroupNames();

        for (String groupName : groupNames) {
            Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName));
            for (JobKey jobKey : jobKeys) {
                jobNames.add(jobKey.getName());
            }
        }

        return jobNames;
    } catch (SchedulerException e) {
        throw new WorkflowSchedulerException("Failed to list scheduled jobs", e);
    }
}
```

**Behavior:**
- Iterates through all job groups
- Collects job names from each group
- Returns list of scheduled job names

---

### 3. WorkflowTriggerJob (Quartz Job)

**Location:** `job/WorkflowTriggerJob.java`
**Implements:** Quartz `Job` interface
**Type:** Spring @Component

#### Purpose
Executes when Quartz fires a trigger. Responsible for:
1. Extracting workflow ID and context from job data
2. Deserializing JSON context
3. Invoking WorkflowFacade to execute the workflow

#### Implementation

```java
@Component
public class WorkflowTriggerJob implements Job {
    @Autowired
    private WorkflowFacade workflowFacade;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            // 1. Extract job data
            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            String workflowId = dataMap.getString("workflowId");
            String contextJson = dataMap.getString("context");

            // 2. Deserialize context
            Map<String, Object> executionContext = objectMapper.readValue(
                contextJson,
                new TypeReference<Map<String, Object>>() {}
            );

            // 3. Start workflow execution
            workflowFacade.startWorkflowByDefinitionName(workflowId, executionContext);

            log.info("Workflow {} triggered successfully", workflowId);

        } catch (Exception e) {
            log.error("Failed to execute workflow trigger job", e);
            throw new WorkflowEngineException("Job execution failed", e);
        }
    }
}
```

**Key Features:**
- **Spring Dependency Injection**: Uses @Autowired for WorkflowFacade
- **JSON Deserialization**: Converts stored JSON context back to Map
- **Async Execution**: WorkflowFacade executes workflow asynchronously
- **Error Handling**: Logs errors and wraps in WorkflowEngineException

---

### 4. WorkflowScheduleRequest (DTO)

**Location:** `model/WorkflowScheduleRequest.java`
**Type:** Java Record

#### Purpose
Data transfer object for scheduling requests.

#### Definition

```java
public record WorkflowScheduleRequest(
    String workflowId,
    String cronExpression,
    Map<String, Object> context
) {}
```

**Fields:**
- `workflowId`: Identifier of workflow to schedule
- `cronExpression`: Cron expression for schedule (e.g., "0 0 * * * ?")
- `context`: Initial execution context as key-value map

---

## Quartz Configuration

### Configuration File: quartz.properties

**Location:** `src/main/resources/quartz.properties`

#### Scheduler Configuration

```properties
# Scheduler Instance
org.quartz.scheduler.instanceName=WorkflowQuartzScheduler
org.quartz.scheduler.instanceId=AUTO
org.quartz.scheduler.skipUpdateCheck=true
```

**Key Settings:**
- `instanceName`: Identifies this scheduler instance
- `instanceId=AUTO`: Auto-generates unique instance ID (enables clustering)
- `skipUpdateCheck`: Skips Quartz version check at startup

---

#### Thread Pool Configuration

```properties
# Thread Pool
org.quartz.threadPool.class=org.quartz.simpl.SimpleThreadPool
org.quartz.threadPool.threadCount=10
org.quartz.threadPool.threadPriority=5
```

**Key Settings:**
- `threadCount=10`: Maximum 10 concurrent job executions
- `threadPriority=5`: Normal thread priority (1-10 scale)

---

#### Job Store Configuration (PostgreSQL)

```properties
# Job Store
org.quartz.jobStore.class=org.quartz.impl.jdbcjobstore.JobStoreTX
org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.PostgreSQLDelegate
org.quartz.jobStore.dataSource=myDS
org.quartz.jobStore.tablePrefix=QRTZ_
```

**Key Settings:**
- `JobStoreTX`: JDBC-based job store with transaction support
- `PostgreSQLDelegate`: PostgreSQL-specific SQL delegate
- `tablePrefix=QRTZ_`: All Quartz tables prefixed with QRTZ_

---

#### DataSource Configuration

```properties
# DataSource
org.quartz.dataSource.myDS.driver=org.postgresql.Driver
org.quartz.dataSource.myDS.URL=jdbc:postgresql://localhost:5432/workflowdb
org.quartz.dataSource.myDS.user=workflow_user
org.quartz.dataSource.myDS.password=workflow_pass
org.quartz.dataSource.myDS.maxConnections=10
org.quartz.dataSource.myDS.validationQuery=select 1
```

**Key Settings:**
- PostgreSQL connection to `workflowdb` database
- Connection pool: maximum 10 connections
- Validation query: `select 1` (tests connection health)

---

## Database Schema (Quartz Tables)

### Quartz Tables (40+ tables)

The database schema includes comprehensive Quartz tables for job persistence:

#### Core Tables

**QRTZ_JOB_DETAILS**
- Stores job definitions
- Columns: `SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`, `JOB_CLASS_NAME`, `JOB_DATA`, etc.
- Purpose: Persistent storage of JobDetail instances

**QRTZ_TRIGGERS**
- Stores trigger definitions
- Columns: `SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`, `JOB_NAME`, `TRIGGER_TYPE`, `START_TIME`, `END_TIME`, etc.
- Purpose: Persistent storage of Trigger instances

**QRTZ_CRON_TRIGGERS**
- Stores cron trigger details
- Columns: `SCHED_NAME`, `TRIGGER_NAME`, `CRON_EXPRESSION`, `TIME_ZONE_ID`
- Purpose: Stores cron expressions for CronTrigger instances

**QRTZ_FIRED_TRIGGERS**
- Tracks currently executing triggers
- Columns: `SCHED_NAME`, `ENTRY_ID`, `TRIGGER_NAME`, `FIRED_TIME`, `STATE`
- Purpose: Tracks which triggers are currently firing

**QRTZ_LOCKS**
- Distributed locking for clustering
- Columns: `SCHED_NAME`, `LOCK_NAME`
- Purpose: Ensures only one scheduler instance processes a trigger in clustered environments

#### Additional Tables
- QRTZ_SIMPLE_TRIGGERS
- QRTZ_SIMPROP_TRIGGERS
- QRTZ_BLOB_TRIGGERS
- QRTZ_CALENDARS
- QRTZ_PAUSED_TRIGGER_GRPS
- QRTZ_SCHEDULER_STATE

---

## Cron Expression Support

### Cron Format

Quartz uses standard cron format with 6-7 fields:

```
┌───────────── second (0-59)
│ ┌───────────── minute (0-59)
│ │ ┌───────────── hour (0-23)
│ │ │ ┌───────────── day of month (1-31)
│ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
│ │ │ │ │ ┌───────────── day of week (0-6 or SUN-SAT)
│ │ │ │ │ │ ┌───────────── year (optional, 1970-2099)
│ │ │ │ │ │ │
* * * * * * *
```

### Common Examples

| Expression | Meaning |
|------------|---------|
| `0 0 * * * ?` | Every day at midnight |
| `0 */15 * * * ?` | Every 15 minutes |
| `0 0 */6 * * ?` | Every 6 hours |
| `0 0 9 * * MON-FRI` | 9 AM on weekdays |
| `0 0 0 1 * ?` | First day of every month at midnight |
| `0 30 10 * * ?` | Every day at 10:30 AM |
| `0 0 12 ? * WED` | Every Wednesday at noon |

### Special Characters
- `*` - All values
- `?` - No specific value (day of month or day of week)
- `-` - Range (e.g., MON-FRI)
- `,` - List (e.g., MON,WED,FRI)
- `/` - Increments (e.g., */15 = every 15 units)
- `L` - Last (e.g., L in day of month = last day of month)
- `W` - Weekday (e.g., 15W = nearest weekday to 15th)
- `#` - Nth occurrence (e.g., FRI#2 = second Friday of month)

---

## Integration Flow

### Complete Scheduling Flow

```
1. Client Request
   ↓
   POST /api/schedules
   {
     "workflowId": "daily-report",
     "cronExpression": "0 0 9 * * ?",
     "context": {"reportType": "summary"}
   }
   ↓
2. SchedulerController (workflow-api)
   ↓
   WorkflowFacade.scheduleWorkflow()
   ↓
3. WorkflowSchedulerServiceImpl (this module)
   ├─ Serialize context to JSON
   ├─ Create JobDetail (WorkflowTriggerJob.class)
   ├─ Create CronTrigger (9 AM daily)
   └─ Scheduler.scheduleJob()
   ↓
4. Quartz Scheduler
   ├─ Persist JobDetail to QRTZ_JOB_DETAILS
   ├─ Persist Trigger to QRTZ_TRIGGERS
   └─ Persist Cron to QRTZ_CRON_TRIGGERS
   ↓
5. Daily at 9 AM (trigger fires)
   ├─ Quartz locks trigger (QRTZ_LOCKS)
   ├─ Records in QRTZ_FIRED_TRIGGERS
   └─ Executes WorkflowTriggerJob.execute()
   ↓
6. WorkflowTriggerJob
   ├─ Extract workflowId = "daily-report"
   ├─ Deserialize context = {"reportType": "summary"}
   └─ workflowFacade.startWorkflowByDefinitionName()
   ↓
7. WorkflowFacade (workflow-core)
   ├─ Lookup workflow definition by name
   ├─ Create WorkflowRun instance
   └─ Submit to WorkflowEngine for execution
   ↓
8. Standard Workflow Execution
   (See workflow-core documentation)
```

---

## Dependencies

### Maven Dependencies

```xml
<!-- Quartz Scheduler -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
    <version>4.0.0-M3</version>
</dependency>

<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.7</version>
</dependency>

<!-- Workflow Core -->
<dependency>
    <groupId>com.example.workflow</groupId>
    <artifactId>workflow-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## Clustering Support

### How Clustering Works

Quartz supports **distributed scheduling** across multiple application instances:

1. **Shared Database**: All scheduler instances share the same PostgreSQL database
2. **Instance ID**: Each instance has unique `instanceId` (AUTO-generated)
3. **Distributed Locking**: QRTZ_LOCKS table prevents duplicate execution
4. **Failover**: If one instance fails, another picks up its jobs
5. **Load Balancing**: Jobs distributed across available instances

### Configuration for Clustering

```properties
# Enable clustering
org.quartz.jobStore.isClustered=true
org.quartz.jobStore.clusterCheckinInterval=20000  # 20 seconds

# Auto-generate unique instance ID
org.quartz.scheduler.instanceId=AUTO
```

### Clustering Benefits
- **High Availability**: Jobs execute even if one instance fails
- **Scalability**: Add more scheduler instances to handle load
- **Fault Tolerance**: Automatic job recovery on instance failure

---

## Placeholders (Future Enhancements)

### SchedulerJobEntity

**Location:** `repo/SchedulerJobEntity.java`
**Status:** Empty stub
**Purpose:** Potential custom entity for storing scheduler metadata

**Possible Use Cases:**
- Track scheduling history
- Store additional job metadata beyond Quartz
- Implement soft delete for schedules
- Audit trail for schedule changes

---

### QuartzJobBuilder

**Location:** `service/QuartzJobBuilder.java`
**Status:** Empty stub
**Purpose:** Potential builder for complex job configurations

**Possible Use Cases:**
- Fluent API for job creation
- Template-based job generation
- Validation of job configurations
- Pre-configured job templates

---

## Error Handling

### Exceptions

**WorkflowSchedulerException** (from workflow-core):
- Thrown when scheduling operations fail
- Wraps Quartz `SchedulerException` and Jackson `JsonProcessingException`

**WorkflowEngineException** (from workflow-core):
- Thrown by WorkflowTriggerJob on execution failure
- Propagated to Quartz (triggers misfire handling)

### Misfire Handling

Quartz supports misfire policies when a trigger couldn't fire at scheduled time:
- **Fire Now**: Execute immediately
- **Fire Next**: Wait for next scheduled time
- **Do Nothing**: Skip this occurrence

Default policy can be configured in trigger:
```java
.withSchedule(CronScheduleBuilder.cronSchedule(expression)
    .withMisfireHandlingInstructionFireAndProceed())
```

---

## Testing Recommendations

### Unit Tests
- Test WorkflowSchedulerServiceImpl methods
- Mock Quartz Scheduler
- Test cron expression parsing
- Test error scenarios

### Integration Tests
- Use in-memory H2 database for Quartz
- Test actual job scheduling and firing
- Test clustering with multiple scheduler instances
- Test job persistence and recovery

### Example Test
```java
@SpringBootTest
class WorkflowSchedulerServiceTest {
    @Autowired
    private WorkflowSchedulerService schedulerService;

    @Test
    void testScheduleWorkflow() {
        String workflowId = "test-workflow";
        String cron = "0 0 * * * ?";  // Every hour
        Map<String, Object> context = Map.of("key", "value");

        schedulerService.scheduleWorkflow(workflowId, cron, context);

        List<String> jobs = schedulerService.listScheduledJobs();
        assertTrue(jobs.contains("workflow-" + workflowId));
    }
}
```

---

## Monitoring and Operations

### Job Monitoring

**Query Current Jobs:**
```sql
SELECT job_name, job_group, job_class_name
FROM qrtz_job_details
WHERE sched_name = 'WorkflowQuartzScheduler';
```

**Query Active Triggers:**
```sql
SELECT trigger_name, trigger_group, trigger_state, next_fire_time
FROM qrtz_triggers
WHERE sched_name = 'WorkflowQuartzScheduler';
```

**Query Fired Triggers:**
```sql
SELECT trigger_name, fired_time, state
FROM qrtz_fired_triggers
WHERE sched_name = 'WorkflowQuartzScheduler';
```

### Operational Tasks

**Pause All Triggers:**
```java
scheduler.pauseAll();
```

**Resume All Triggers:**
```java
scheduler.resumeAll();
```

**Pause Specific Job:**
```java
scheduler.pauseJob(JobKey.jobKey("workflow-123", "workflow-jobs"));
```

**Trigger Job Manually:**
```java
scheduler.triggerJob(JobKey.jobKey("workflow-123", "workflow-jobs"));
```

---

## Best Practices

1. **Idempotent Workflows**: Ensure workflows can safely re-run (in case of retries)
2. **Unique Workflow IDs**: Use unique identifiers for each scheduled workflow
3. **Validate Cron Expressions**: Validate before scheduling
4. **Monitor Misfires**: Track and alert on frequent misfires
5. **Set Job Timeouts**: Prevent long-running jobs from blocking thread pool
6. **Regular Cleanup**: Purge old completed jobs from database
7. **Connection Pooling**: Configure appropriate database connection pool size
8. **Logging**: Log all scheduling operations for audit trail

---

## Future Enhancements

- **Calendar Support**: Exclude holidays from schedule
- **Job Chaining**: Trigger one workflow after another
- **Conditional Scheduling**: Schedule based on runtime conditions
- **Priority Scheduling**: Support job priorities
- **Job History**: Track execution history beyond Quartz
- **Dynamic Cron**: Allow cron modification without reschedule
- **Timezone Support**: Schedule in different timezones
- **Notification**: Alert on job failures

---

**Last Updated:** January 2026
**Module Version:** 1.0-SNAPSHOT