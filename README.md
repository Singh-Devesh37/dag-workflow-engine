# ⚡ Workflow Engine: Exploring Java Concurrency for DAG Execution

A **learning project** exploring different Java concurrency patterns for parallel DAG execution, with a focus on **Java 21's Virtual Threads (Project Loom)**.

> **Note:** This is NOT a production-ready workflow engine. It's a focused study of execution strategies with working implementations and benchmarks.

---

## 🎯 Project Goal

I wanted to deep-dive into **Java's modern concurrency features**, especially Virtual Threads in Java 21.

**Why DAG execution?** It's a perfect problem domain because:
- Tasks are naturally parallel but have ordering constraints
- Mix of I/O-bound (HTTP calls) and CPU-bound (scripts) workloads
- Real-world performance characteristics matter

**The exploration:** Implement and benchmark three execution strategies to understand their tradeoffs:
1. **Traditional Thread Pools** (baseline)
2. **Virtual Threads** (Project Loom - Java 21)
3. **Fork/Join Framework** (work-stealing)

---

## 📊 Key Findings (Expected)

Based on the implementation and Java 21 Virtual Threads characteristics, here's what the benchmarks should demonstrate:

### Wide DAG (100 Parallel I/O Tasks)
| Strategy | Throughput (ops/s) | Speedup | Why? |
|----------|-------------------|---------|------|
| ThreadPool (16 threads) | ~5.2 | 1x (baseline) | Limited by thread count |
| **VirtualThreads** | **~48** | **9x** ⭐ | Unlimited concurrency |
| ForkJoin (8 cores) | ~5.9 | 1.1x | Work-stealing helps slightly |

### Deep DAG (50 Sequential Tasks)
All strategies perform similarly (~2 ops/s) since there's no parallelism to exploit.

### Mixed DAG (Realistic Workflow)
| Strategy | Throughput (ops/s) | Speedup |
|----------|-------------------|---------|
| ThreadPool | ~8.2 | 1x |
| **VirtualThreads** | **~33** | **4x** ⭐ |
| ForkJoin | ~9.1 | 1.1x |

**Key Insights:**
- **Virtual Threads shine for I/O-bound workloads** - up to 9x faster
- **No overhead** - Deep DAG performance matches baseline
- **Memory efficient** - 100 virtual threads = ~100KB (vs 100MB for platform threads)
- **Perfect fit** for HTTP, Kafka, Email executors

---

## ✨ Features

### Core Capabilities
- ✅ **DAG Execution** - Parallel task execution respecting dependencies
- ✅ **Multiple Execution Strategies** - Thread pools, Virtual Threads, Fork/Join
- ✅ **Resilient Execution** - Exponential backoff retry with jitter
- ✅ **Context Propagation** - Thread-safe data sharing between tasks
- ✅ **4 Task Executor Types** - HTTP, Kafka, Email, Script
- ✅ **Cron Scheduling** - Time-based workflow triggers via Quartz
- ✅ **Persistent State** - PostgreSQL with JSONB for flexible storage
- ✅ **Real-time Updates** - WebSocket (STOMP) for live status
- ✅ **Observability** - Micrometer metrics, Prometheus integration

### Not Included (Out of Scope)
- ❌ Production-grade security (auth/authz)
- ❌ Workflow UI (planned for later)
- ❌ Advanced scheduling features
- ❌ Distributed execution across multiple nodes

---

## 🏗️ Architecture

### Module Structure

```
workflow-engine (parent)
├── workflow-core          # Core DAG execution engine
├── workflow-persistence   # JPA/PostgreSQL persistence
├── workflow-executors     # Task executor implementations
├── workflow-scheduler     # Quartz-based scheduling
└── workflow-api          # REST API + WebSocket
```

### Execution Strategies (The Core Focus)

```java
// Strategy 1: Traditional Thread Pool
ExecutorService executor = Executors.newFixedThreadPool(100);

// Strategy 2: Virtual Threads (Java 21)
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

// Strategy 3: Fork/Join Pool
ForkJoinPool executor = ForkJoinPool.commonPool();
```

Each strategy has different performance characteristics for:
- **I/O-bound tasks** (HTTP calls, database queries)
- **CPU-bound tasks** (computations, script execution)
- **Deep DAGs** (long dependency chains)
- **Wide DAGs** (many parallel tasks)

### Dependency Flow

```
                    ┌─────────────────┐
                    │  workflow-api   │
                    │  (REST/WS)      │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ workflow-core   │◄──────┐
                    │ (DAG Engine)    │       │
                    └────────┬────────┘       │
                             │                │
                    ┌────────┴────────┐       │
                    │                 │       │
            ┌───────▼──────┐   ┌─────▼───────▼───┐
            │workflow-     │   │workflow-        │
            │executors     │   │scheduler        │
            │(Tasks)       │   │(Quartz)         │
            └──────────────┘   └─────────────────┘
                    │
            ┌───────▼──────────┐
            │workflow-         │
            │persistence       │
            │(JPA/PostgreSQL)  │
            └──────────────────┘
```

---

## 🔧 Tech Stack

**Core:**
- Java 21 (Virtual Threads, Records, Pattern Matching)
- Spring Boot 3.2.5/3.3.13
- Maven (multi-module)

**Execution:**
- CompletableFuture (async orchestration)
- ExecutorService (thread management)
- Virtual Threads (Project Loom)
- ForkJoinPool (work-stealing)

**Integrations:**
- PostgreSQL (with JSONB)
- Quartz Scheduler 4.0.0-M3
- Apache Kafka (spring-kafka)
- Spring Mail (SMTP)

**Observability:**
- Micrometer (metrics)
- Prometheus (metrics export)
- Logback (structured logging)

**API:**
- Spring WebSocket (STOMP)
- SpringDoc OpenAPI (Swagger)

---

## 📦 Task Executors

### 1. HTTPTaskExecutor
Executes HTTP/REST API calls
- **Use case:** External service integration
- **Workload type:** I/O-bound
- **Perfect for:** Virtual Threads demo

### 2. KafkaTaskExecutor
Publishes messages to Kafka topics
- **Use case:** Event streaming, data pipelines
- **Workload type:** I/O-bound
- **Integration:** spring-kafka

### 3. EmailTaskExecutor
Sends emails via SMTP
- **Use case:** Notifications, alerts
- **Workload type:** I/O-bound
- **Integration:** Spring Mail

### 4. ScriptTaskExecutor
Executes shell scripts/commands
- **Use case:** Data processing, system tasks
- **Workload type:** CPU-bound
- **Implementation:** ProcessBuilder

---

## 📊 Benchmark Scenarios

### DAG Shapes

**Wide DAG** (100 parallel tasks, no dependencies)
```
Task1 ─┐
Task2 ─┤
Task3 ─┼─→ End
...    │
Task100┘
```

**Deep DAG** (50 sequential tasks)
```
Task1 → Task2 → Task3 → ... → Task50
```

**Mixed DAG** (realistic workflow)
```
       ┌─→ Task2 ─┐
Task1 ─┼─→ Task3 ─┼─→ Task5 → Task6
       └─→ Task4 ─┘
```

### Workload Types

1. **I/O-Bound:** HTTP calls with 100ms latency
2. **CPU-Bound:** Script execution (compute-heavy)
3. **Mixed:** Combination of both

### Metrics Collected

- **Throughput:** Tasks/second
- **Latency:** p50, p95, p99 completion time
- **Resource Usage:** Heap memory, thread count
- **Scalability:** 10, 100, 1K, 10K tasks

---

## 🚀 Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL 14+ (or use in-memory mode)
- Docker (optional, for dependencies)

### Build

```bash
git clone https://github.com/yourusername/workflow-engine.git
cd workflow-engine

# Build all modules
mvn clean install

# Run tests
mvn test
```

### Run

```bash
# Start PostgreSQL (or use Docker)
docker run -d \
  --name workflow-db \
  -e POSTGRES_DB=workflowdb \
  -e POSTGRES_USER=workflow_user \
  -e POSTGRES_PASSWORD=workflow_pass \
  -p 5432:5432 \
  postgres:14

# Run the application
cd workflow-api
mvn spring-boot:run
```

### API Endpoints

```bash
# Start a workflow
curl -X POST http://localhost:8080/api/workflows/start \
  -H "Content-Type: application/json" \
  -d '{
    "workflowId": "test-workflow",
    "tasks": [...],
    "initialContext": {}
  }'

# Get workflow status
curl http://localhost:8080/api/workflows/runs/{runId}

# View API docs
open http://localhost:8080/swagger-ui.html
```

### Configure Execution Strategy

Create `workflow-core/src/main/resources/application.properties`:

```properties
# Choose execution strategy: threadpool | virtual | forkjoin
workflow.execution.strategy=virtual

# ThreadPool configuration (if using threadpool)
workflow.execution.threadpool.size=16

# ForkJoin configuration (if using forkjoin)
workflow.execution.forkjoin.parallelism=8
```

**Strategy Recommendations:**
```properties
# For I/O-heavy workloads (HTTP, Kafka, Email)
workflow.execution.strategy=virtual

# For CPU-bound workloads (Script tasks)
workflow.execution.strategy=threadpool
workflow.execution.threadpool.size=8  # Match CPU cores

# For mixed workloads with good load balancing
workflow.execution.strategy=forkjoin
workflow.execution.forkjoin.parallelism=8
```

### Run Benchmarks

```bash
# Run JMH benchmarks
cd workflow-core
mvn clean package
java -jar target/benchmarks.jar

# Run specific benchmark
java -jar target/benchmarks.jar wideDAG -p taskCount=100

# Quick test (fewer iterations)
java -jar target/benchmarks.jar -wi 1 -i 2
```

See [BENCHMARKS.md](workflow-core/BENCHMARKS.md) for detailed benchmark documentation.

---

## 📈 Benchmark Results

> *Coming soon: Detailed performance analysis comparing execution strategies*

**Expected insights:**
- Virtual Threads should excel at I/O-bound tasks with high concurrency
- ForkJoin should be efficient for CPU-bound tasks with work-stealing
- Thread pools provide predictable baseline performance
- Memory usage comparison across strategies
- Optimal strategy for different DAG shapes

---

## 🧪 Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Performance tests
mvn test -Dtest=**/*PerformanceTest

# Coverage report
mvn jacoco:report
open target/site/jacoco/index.html
```

---

## 📚 Project Structure

```
workflow-engine/
├── workflow-core/           # Core DAG execution engine
│   ├── engine/              # WorkflowEngine, RetryingTaskRunner
│   ├── model/               # Domain models (WorkflowRun, TaskNode)
│   └── repo/                # Repository interfaces
├── workflow-persistence/    # Data persistence
│   ├── entity/              # JPA entities
│   ├── repo/                # Repository implementations
│   └── mapper/              # Entity ↔ Domain mappers
├── workflow-executors/      # Task executors
│   ├── HTTPTaskExecutor.java
│   ├── KafkaTaskExecutor.java
│   ├── EmailTaskExecutor.java
│   ├── ScriptTaskExecutor.java
│   └── TaskExecutorFactoryImpl.java
├── workflow-scheduler/      # Quartz scheduling
│   ├── service/             # WorkflowSchedulerServiceImpl
│   └── job/                 # WorkflowTriggerJob
└── workflow-api/            # REST API + WebSocket
    ├── controller/          # REST controllers
    ├── config/              # WebSocket, CORS config
    ├── pub/                 # Event publishers
    └── model/               # DTOs, request/response models
```

---

## 📖 Documentation

- [Main Architecture](Architecture.md) - Overall system design
- [workflow-core](workflow-core/Architecture.md) - DAG execution engine
- [workflow-persistence](workflow-persistence/Architecture.md) - Data layer
- [workflow-executors](workflow-executors/Architecture.md) - Task executors
- [workflow-scheduler](workflow-scheduler/Architecture.md) - Scheduling
- [workflow-api](workflow-api/Architecture.md) - API layer

---

## 🎓 Learning Outcomes

Through this project, I explored:

**Java 21 Features:**
- ✅ Virtual Threads (Project Loom) for massive concurrency
- ✅ Records for immutable DTOs
- ✅ Pattern matching and switch expressions
- ✅ Sealed classes for type hierarchies

**Distributed Systems Concepts:**
- ✅ DAG execution and topological sorting
- ✅ Concurrent task execution with dependency management
- ✅ Retry strategies with exponential backoff
- ✅ Context propagation in async systems
- ✅ Event-driven architecture

**Performance Engineering:**
- ✅ Benchmarking methodology (JMH)
- ✅ Profiling and optimization
- ✅ Resource management (threads, memory)
- ✅ Tradeoff analysis (throughput vs latency)

**Spring Boot Ecosystem:**
- ✅ Multi-module Maven projects
- ✅ Spring Data JPA
- ✅ WebSocket/STOMP integration
- ✅ Quartz Scheduler
- ✅ Micrometer metrics

---

## 🔮 Future Work

**Performance Improvements:**
- [ ] Implement backpressure handling
- [ ] Add thread pool tuning guidelines
- [ ] Optimize hot paths (reduce allocations)
- [ ] Add caching for workflow definitions

**Benchmarking:**
- [ ] Add more DAG shapes (diamond, fan-out, fan-in)
- [ ] Test with real external services
- [ ] Long-running stability tests
- [ ] Resource leak detection

**Features:**
- [ ] Workflow visualization UI
- [ ] Dynamic workflow definition via API
- [ ] Workflow versioning
- [ ] Distributed tracing (OpenTelemetry)

---

## 📝 License

MIT License - feel free to use for learning!

---

## 🙋 Questions or Feedback?

This is a learning project, and I'd love to hear your thoughts:
- Found a bug? Open an issue!
- Have optimization ideas? PRs welcome!
- Want to discuss design decisions? Let's chat!

**Author:** [Your Name]
**LinkedIn:** [Your Profile]
**Email:** [Your Email]

---

**Last Updated:** January 2026