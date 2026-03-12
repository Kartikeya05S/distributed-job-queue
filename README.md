# Distributed Job Queue System

A production-grade distributed asynchronous job processing system using **Apache Kafka**, **Redis**, and **Spring Boot**. Processes thousands of background tasks with retry logic, dead letter queues, exponential backoff, Prometheus metrics, and horizontal scalability.

## Architecture

```
                              ┌────────────────────┐
                              │      Client         │
                              └────────┬───────────┘
                                       │ POST /api/jobs
                                       ▼
                              ┌────────────────────┐        ┌──────────────────┐
                              │    API Service      │───────►│      Redis       │
                              │  (Spring Boot)      │        │  (Job State)     │
                              └────────┬───────────┘        └────────▲─────────┘
                                       │ Kafka Produce               │ Read/Write
                                       ▼                             │
                    ┌──────────────────────────────────┐             │
                    │    Kafka Topic: job_queue         │             │
                    │    (3 partitions)                 │             │
                    └───┬──────────┬──────────┬────────┘             │
                        │          │          │                      │
                  ┌─────▼──┐ ┌────▼───┐ ┌────▼───┐                  │
                  │Worker 1│ │Worker 2│ │Worker 3│──────────────────┘
                  └────┬───┘ └────┬───┘ └────┬───┘
                       │          │          │    On failure (retries exhausted)
                       ▼          ▼          ▼
                    ┌──────────────────────────────────┐
                    │   Kafka Topic: job_queue_dlq      │
                    │   (Dead Letter Queue)              │
                    └──────────────────────────────────┘

                    ┌──────────────────────────────────┐
                    │         Prometheus + Grafana       │
                    │    (Metrics & Monitoring)          │
                    └──────────────────────────────────┘
```

### Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| API Service | Spring Boot 3.2 | REST API for job submission and status queries |
| Message Queue | Apache Kafka | Decouples producers from consumers, ensures message durability |
| State Store | Redis 7 | Shared job state with TTL-based auto-cleanup |
| Workers | Spring Boot + Kafka Consumer | Process jobs asynchronously in consumer groups |
| Dead Letter Queue | Kafka Topic | Stores permanently failed jobs for manual inspection |
| Monitoring | Prometheus + Grafana | Real-time metrics, dashboards, and alerting |

## Key Features

- **Horizontal Scaling** — Kafka consumer groups distribute partitions across workers automatically
- **Retry with Exponential Backoff** — Failed jobs retry 3 times with delays: 1s → 2s → 4s
- **Dead Letter Queue** — Jobs exceeding max retries go to DLQ for manual inspection
- **Manual Acknowledgment** — Kafka offsets committed only after successful processing
- **Idempotent Producer** — Prevents duplicate messages on Kafka producer retries
- **Prometheus Metrics** — `jobs_processed_total`, `jobs_failed_total`, `job_processing_duration_seconds` (p50/p95/p99)
- **Health Checks** — Spring Actuator endpoints for liveness/readiness probes
- **Structured Error Handling** — Global exception handler, custom exceptions, clean API error responses
- **Redis TTL** — Job records auto-expire after 24 hours

## How It Works

### Job Lifecycle

```
QUEUED → PROCESSING → COMPLETED
                    → FAILED → (retry ≤ 3) → QUEUED
                             → (retry > 3) → DEAD_LETTER
```

1. **Client** submits a job via `POST /api/jobs`
2. **API Service** generates UUID, stores state in Redis (`QUEUED`), publishes to Kafka
3. **Kafka** distributes messages across 3 partitions
4. **Worker** consumes from assigned partition, updates Redis to `PROCESSING`
5. **Worker** executes the job handler based on `jobType`
6. **On success**: Redis updated to `COMPLETED` with result
7. **On failure**: Exponential backoff retry, then Dead Letter Queue

### Kafka Concepts Explained

**Topics** — Named channels for messages. `job_queue` for active jobs, `job_queue_dlq` for dead letters.

**Partitions** — Each topic is split into partitions (3 in this system). Partitions enable parallelism — each partition is consumed by exactly one worker in a consumer group. The `jobId` message key ensures all messages for the same job land on the same partition, maintaining ordering guarantees.

**Consumer Groups** — All workers share the group `job-workers`. Kafka automatically assigns partitions to group members. If a worker dies, Kafka triggers a **rebalance** — surviving workers take over the dead worker's partitions. Unacknowledged messages are redelivered.

**Message Durability** — Kafka persists messages to disk with `acks=all`. Combined with manual offset commits, this ensures no job is lost even if workers crash mid-processing.

### Retry Strategy

```
Retry 1: wait 1s  → re-publish to job_queue
Retry 2: wait 2s  → re-publish to job_queue
Retry 3: wait 4s  → re-publish to job_queue
Retry 4: FAILED   → send to job_queue_dlq
```

Exponential backoff prevents retry storms that could overwhelm downstream services. This is the same pattern used by AWS SQS, Google Cloud Tasks, and Stripe webhooks.

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 17+
- Maven 3.9+

### Run Everything

```bash
docker compose up --build
```

This starts:
- **Zookeeper** — Kafka coordination (port 2181)
- **Kafka** — Message broker with 3 partitions (port 9092)
- **Redis** — Job state store (port 6379)
- **API Service** — REST API (port 8080)
- **3 Worker instances** — Kafka consumer group
- **Prometheus** — Metrics collection (port 9090)
- **Grafana** — Dashboards (port 3000, login: admin/admin)

### Submit Jobs

```bash
# Email job
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"jobType":"email","payload":{"to":"user@example.com","subject":"Welcome!","body":"Hello"}}'

# Image processing job
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"jobType":"image","payload":{"url":"https://example.com/photo.jpg","operation":"resize"}}'

# Report generation job
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"jobType":"report","payload":{"type":"monthly-sales","format":"pdf"}}'

# Push notification job
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"jobType":"notification","payload":{"userId":"user-123","title":"New message","channel":"push"}}'
```

### Check Job Status

```bash
curl http://localhost:8080/api/jobs/{jobId}
```

Response:
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "result": "Email sent to user@example.com",
  "retryCount": 0,
  "createdAt": "2026-03-10T10:00:00Z",
  "updatedAt": "2026-03-10T10:00:01Z"
}
```

## API Endpoints

| Method | Endpoint | Description | Response |
|--------|----------|-------------|----------|
| `POST` | `/api/jobs` | Submit a new job | `202 Accepted` with job ID |
| `GET` | `/api/jobs/{jobId}` | Query job status | `200 OK` or `404 Not Found` |
| `GET` | `/actuator/health` | Health check | Service health status |
| `GET` | `/actuator/prometheus` | Prometheus metrics | Metrics in Prometheus format |

## Scaling Workers

Workers scale horizontally via Kafka consumer groups:

```bash
# Scale to 5 workers (add more service entries in docker-compose.yml)
docker compose up --build
```

**Scaling rule**: Maximum parallelism = number of Kafka partitions. With 3 partitions:
- 3 workers → each gets 1 partition (optimal)
- 2 workers → one gets 2 partitions
- 4 workers → one is idle (increase partitions first)

## Monitoring

### Prometheus Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `jobs_submitted_total` | Counter | Jobs submitted via API |
| `jobs_processed_total` | Counter | Successfully processed jobs |
| `jobs_failed_total` | Counter | Failed job processing attempts |
| `jobs_retried_total` | Counter | Job retry attempts |
| `jobs_dead_letter_total` | Counter | Jobs sent to DLQ |
| `job_processing_duration_seconds` | Timer | Processing latency (p50/p95/p99) |

### Dashboards

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)

## Inspecting the System

```bash
# View Redis job state
docker exec redis redis-cli GET "job:YOUR_JOB_ID"

# List Kafka topics
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# View DLQ messages
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic job_queue_dlq \
  --from-beginning

# View worker logs
docker logs worker-1 -f

# Check consumer group lag
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group job-workers
```

## Supported Job Types

| Type | Description | Simulated Duration |
|------|-------------|-------------------|
| `email` | Send email notification | ~1 second |
| `image` | Process/resize image | ~2 seconds |
| `report` | Generate PDF report | ~3 seconds |
| `notification` | Push notification dispatch | ~0.5 seconds |

Adding a new job type requires only implementing the `JobHandler` interface — the consumer auto-discovers handlers via Spring component scanning.

## Fault Tolerance

| Failure Scenario | How It's Handled |
|-----------------|-----------------|
| Worker crash | Kafka rebalances partitions to surviving workers; unacked messages are redelivered |
| Job processing error | Retry up to 3 times with exponential backoff, then send to DLQ |
| API service restart | Job state persists in Redis; Kafka retains unprocessed messages |
| Kafka broker restart | Messages are persisted to disk; producers/consumers reconnect automatically |
| Redis unavailable | Jobs still flow through Kafka; status queries return errors gracefully |
| Duplicate messages | Idempotent Kafka producer prevents duplicates on producer retries |

## Project Structure

```
distributed-job-queue/
├── api-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/jobqueue/api/
│       ├── ApiServiceApplication.java
│       ├── config/
│       │   ├── JacksonConfig.java
│       │   ├── MetricsConfig.java
│       │   └── RedisConfig.java
│       ├── controller/
│       │   ├── GlobalExceptionHandler.java
│       │   ├── JobController.java
│       │   └── JobSubmissionException.java
│       ├── dto/
│       │   ├── JobRequest.java
│       │   └── JobResponse.java
│       ├── kafka/
│       │   ├── JobProducer.java
│       │   └── KafkaProducerConfig.java
│       ├── model/
│       │   ├── Job.java
│       │   └── JobStatus.java
│       └── service/
│           └── JobService.java
├── worker-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/jobqueue/worker/
│       ├── WorkerServiceApplication.java
│       ├── config/
│       │   ├── JacksonConfig.java
│       │   ├── KafkaConsumerConfig.java
│       │   ├── KafkaProducerConfig.java
│       │   ├── MetricsConfig.java
│       │   └── RedisConfig.java
│       ├── consumer/
│       │   └── JobConsumer.java
│       ├── handler/
│       │   ├── EmailJobHandler.java
│       │   ├── ImageJobHandler.java
│       │   ├── JobHandler.java
│       │   ├── NotificationJobHandler.java
│       │   └── ReportJobHandler.java
│       ├── model/
│       │   ├── Job.java
│       │   └── JobStatus.java
│       └── service/
│           └── JobStateService.java
├── docker/
│   └── prometheus.yml
├── docker-compose.yml
└── README.md
```

## Tech Stack

- **Java 17** + **Spring Boot 3.2**
- **Apache Kafka** (Confluent 7.5)
- **Redis 7** (Alpine)
- **Docker** + **Docker Compose**
- **Prometheus** + **Grafana**
- **Micrometer** (metrics instrumentation)
- **JUnit 5** + **MockMvc** (testing)
