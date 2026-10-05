# CloudOps Triage Platform

Autonomous incident triage system for microservice health monitoring. When a service degrades, the platform automatically detects it, runs an AI advisor to diagnose the root cause, and surfaces a remediation suggestion for an on-call engineer to accept or reject — all without waking anyone up unnecessarily.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Module Structure](#module-structure)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Incident Lifecycle](#incident-lifecycle)
- [Advisor Strategies](#advisor-strategies)
- [Database Schema](#database-schema)
- [Project Structure](#project-structure)
- [Running Tests](#running-tests)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         CloudOps UI                             │
│                  React 18 · TypeScript · Vite                   │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP / REST
┌────────────────────────────▼────────────────────────────────────┐
│                       cloudops-api                              │
│   REST controllers · DTOs · Swagger UI · Global error handler   │
└────────┬────────────────────────────────────────────┬───────────┘
         │                                            │
┌────────▼─────────┐                    ┌─────────────▼──────────┐
│  cloudops-service│                    │  cloudops-repository   │
│  Business logic  │                    │  Spring Data JPA       │
│  Agentic loop    │                    │  Flyway migrations     │
│  AI advisors     │                    │  PostgreSQL            │
└────────┬─────────┘                    └────────────────────────┘
         │
┌────────▼─────────┐
│  cloudops-domain │
│  JPA entities    │
│  Enums           │
└──────────────────┘
```

The **agentic incident loop** (`AgenticIncidentLoop`) listens for `ServiceStatusChangedEvent` events asynchronously. When a service transitions to `DEGRADED` it fetches the latest health signals, builds an analysis context, runs the configured advisor, and creates a draft incident with a `PENDING` remediation suggestion. The loop is idempotent: if an open incident already exists for the service, no duplicate is created.

---

## Module Structure

| Module | Responsibility |
|---|---|
| `cloudops-domain` | JPA entity classes and all enums — shared by all modules, no Spring dependencies |
| `cloudops-repository` | Spring Data JPA repositories and Flyway migration scripts |
| `cloudops-service` | Business logic: incident service, remediation service, agentic loop, advisors, signal correlation |
| `cloudops-api` | Spring Boot entry point, REST controllers, DTOs, MapStruct mappers, Swagger config, global exception handler |
| `cloudops-ui` | React/TypeScript front-end (Vite, Tailwind CSS) |

---

## Tech Stack

**Backend**
- Java 21
- Spring Boot 3.2.5 (Web, Validation, JPA, Async)
- PostgreSQL 15+ with Flyway schema migrations
- MapStruct 1.5.5 for DTO mapping
- springdoc-openapi 2.5.0 (OpenAPI 3 / Swagger UI)

**Frontend**
- React 18 with TypeScript 5
- Vite 5 build tooling
- Tailwind CSS 3
- React Router 6 · Axios

---

## Prerequisites

| Tool | Minimum version |
|---|---|
| JDK | 21 |
| Maven | 3.9 (or use the included `mvnw` wrapper) |
| PostgreSQL | 15 |
| Node.js | 18 |
| npm | 9 |

---

## Getting Started

### 1. Database

```bash
psql -U postgres -c "CREATE DATABASE cloudops;"
psql -U postgres -c "CREATE USER cloudops WITH PASSWORD 'cloudops';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE cloudops TO cloudops;"
```

Flyway runs automatically on startup and applies all migrations under `cloudops-repository/src/main/resources/db/migration/`.

### 2. Backend

```bash
cd cloudops-triage
./mvnw clean package -DskipTests
java -jar cloudops-api/target/cloudops-api-1.0.0-SNAPSHOT.jar
```

The API starts on **http://localhost:8080**.

### 3. Frontend

```bash
cd cloudops-triage/cloudops-ui
npm install
npm run dev
```

The UI starts on **http://localhost:5173**.

### 4. Swagger UI

Once the backend is running, open:

```
http://localhost:8080/swagger-ui.html
```

The raw OpenAPI 3 spec is available at:

```
http://localhost:8080/v3/api-docs
```

---

## Configuration

All configuration lives in [`cloudops-api/src/main/resources/application.yml`](cloudops-api/src/main/resources/application.yml).

### Database

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cloudops
    username: cloudops
    password: cloudops
```

Override with environment variables in production:
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://prod-host:5432/cloudops
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
```

### Advisor Strategy

The incident advisor can be switched between two implementations without a code change:

```yaml
cloudops:
  advisor:
    type: rule-based   # default — deterministic, no external calls
    # type: llm        # switch to LLM-based analysis
    llm:
      endpoint: https://api.openai.com/v1/chat/completions
      api-key: ${OPENAI_API_KEY}   # never commit a real key
      model: gpt-4o
      timeout: 30s
      max-tokens: 512
      temperature: 0.2
```

The LLM advisor is always wrapped by a `FallbackIncidentAdvisor` that automatically falls back to the rule-based implementation if the LLM call fails, ensuring no incident is silently dropped.

### CORS

```yaml
cloudops:
  cors:
    allowed-origins: http://localhost:5173
```

---

## API Reference

Full interactive documentation is available in Swagger UI. The endpoints are grouped into three tags:

### Services

| Method | Path | Description |
|---|---|---|
| `GET` | `/services` | List all monitored services |
| `POST` | `/services` | Register a new service |
| `GET` | `/services/{id}/signals` | Get recent health signals for a service |
| `POST` | `/services/{id}/signals` | Ingest a metric reading |
| `PATCH` | `/services/{id}/status` | Manually update a service's health status |

### Incidents

| Method | Path | Description |
|---|---|---|
| `GET` | `/incidents` | List all incidents, filterable by `?status=` |
| `GET` | `/incidents/{id}` | Get a single incident with all suggestions |
| `POST` | `/incidents/{id}/analyze` | Trigger on-demand AI re-analysis |

### Suggestions

| Method | Path | Description |
|---|---|---|
| `PATCH` | `/suggestions/{id}` | Accept or reject a remediation suggestion |

### Error Responses

All errors follow [RFC 7807 Problem Details](https://datatracker.ietf.org/doc/html/rfc7807) with `Content-Type: application/problem+json`.

| HTTP Status | URN Type | Trigger |
|---|---|---|
| `400` | `urn:cloudops:error:bad-request` | Illegal argument |
| `400` | `urn:cloudops:error:validation` | Bean validation failure |
| `404` | `urn:cloudops:error:not-found` | Resource does not exist |
| `409` | `urn:cloudops:error:invalid-state-transition` | Illegal state transition (e.g. re-resolving an already-resolved incident) |

**Example error response:**
```json
{
  "type": "urn:cloudops:error:not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Incident 3fa85f64-... not found"
}
```

---

## Incident Lifecycle

```
Service DEGRADED
      │
      ▼
AgenticIncidentLoop (async)
      │
      ├─ Pull last 20 health signals
      ├─ Build AnalysisContext (trends, breach run-lengths)
      ├─ Run IncidentAdvisor → AdvisorRecommendation
      │
      ▼
Incident created  ──── status: DRAFT ──── auto_detected: true
      │
      ▼
RemediationSuggestion ──── status: PENDING
      │
      ├─ On-call ACCEPTS  →  suggestion: ACCEPTED
      │                      (manual remediation proceeds)
      │
      └─ On-call REJECTS  →  suggestion: REJECTED
                             (incident stays DRAFT for further review)

Service RECOVERED
      │
      └─ All DRAFT incidents auto-resolved  →  status: RESOLVED
         (ACKNOWLEDGED incidents left for on-call to close manually)
```

**Incident status values:**

| Status | Meaning |
|---|---|
| `DRAFT` | Newly created by the agentic loop, awaiting on-call acknowledgement |
| `ACKNOWLEDGED` | On-call has seen it; active work in progress |
| `RESOLVED` | Incident closed (manually or by auto-recovery) |

**Suggestion status values:**

| Status | Meaning |
|---|---|
| `PENDING` | Awaiting on-call decision |
| `ACCEPTED` | On-call approved the recommendation |
| `REJECTED` | On-call dismissed the recommendation |

---

## Advisor Strategies

### Rule-Based (default)

Deterministic analysis using fixed thresholds. No external calls, always available, used as the fallback when LLM is unavailable.

| Metric | MEDIUM | HIGH | CRITICAL |
|---|---|---|---|
| `ERROR_RATE` (%) | ≥ 2 | ≥ 5 | ≥ 10 |
| `LATENCY_P99` (ms) | ≥ 500 | ≥ 1 000 | ≥ 2 000 |
| `CPU_USAGE` (%) | ≥ 60 | ≥ 75 | ≥ 90 |
| `MEMORY_USAGE` (%) | ≥ 60 | ≥ 75 | ≥ 90 |

The advisor selects the highest severity across all breached metrics, enriches the root cause with trend direction (RISING / STABLE / FALLING), and produces a structured `AdvisorRecommendation` with a confidence of `0.72`.

### LLM-Based

Sends a structured prompt with the full `AnalysisContext` (metric timeline, breach summaries, trends, trigger reason) to a chat-completions endpoint and parses the JSON response. Compatible with any OpenAI-compatible API, including IBM watsonx.ai.

Set `cloudops.advisor.type: llm` and provide `OPENAI_API_KEY` (or the equivalent bearer token for your provider).

### Supported Metric Types

| Value | Description |
|---|---|
| `ERROR_RATE` | Percentage of requests returning 5xx errors |
| `LATENCY_P99` | 99th-percentile response time in milliseconds |
| `CPU_USAGE` | CPU utilisation percentage |
| `MEMORY_USAGE` | Heap / memory utilisation percentage |
| `REQUEST_RATE` | Requests per second (informational, no threshold rule) |

---

## Database Schema

Four tables managed by Flyway:

```
monitored_services
  id · name · team_owner · description · health_status · last_status_changed_at · registered_at

health_signals
  id · service_id(FK) · metric_type · value · threshold_breached · recorded_at

incidents
  id · service_id(FK) · title · summary · status · severity · auto_detected
     · created_at · acknowledged_at · resolved_at

remediation_suggestions
  id · incident_id(FK) · recommended_action · root_cause_hypothesis · confidence
     · reasoning · status · trigger_reason · on_call_note · created_at · decided_at
```

Migrations:

| Version | Description |
|---|---|
| `V1__init_schema.sql` | Creates all four tables with indexes |
| `V2__expand_metric_types.sql` | Widens `metric_type` check constraint to include `CPU_USAGE`, `MEMORY_USAGE`, `REQUEST_RATE` |

---

## Project Structure

```
cloudops-triage/
├── cloudops-domain/                 # Entities & enums (no Spring)
│   └── src/main/java/com/cloudops/domain/
│       ├── enums/                   # IncidentStatus, IncidentSeverity, MetricType, …
│       └── model/                   # MonitoredService, Incident, HealthSignal, RemediationSuggestion
│
├── cloudops-repository/             # Data access
│   └── src/main/
│       ├── java/com/cloudops/repository/   # Spring Data JPA repositories
│       └── resources/db/migration/         # Flyway SQL scripts
│
├── cloudops-service/                # Business & agentic logic
│   └── src/main/java/com/cloudops/service/
│       ├── AgenticIncidentLoop.java         # Async event-driven triage loop
│       ├── IncidentService.java
│       ├── MonitoredServiceService.java
│       ├── RemediationService.java
│       ├── advisor/                         # IncidentAdvisor, RuleBased, LLM, Fallback
│       └── correlation/                     # SignalAnalyzer, ThresholdRule
│
├── cloudops-api/                    # Spring Boot entry point
│   └── src/main/
│       ├── java/com/cloudops/api/
│       │   ├── CloudOpsApplication.java
│       │   ├── SwaggerConfig.java
│       │   ├── GlobalExceptionHandler.java
│       │   ├── controller/          # IncidentController, ServiceController, SuggestionController
│       │   ├── dto/                 # IncidentDtos, ServiceDtos
│       │   └── mapper/              # DtoMapper (MapStruct)
│       └── resources/
│           └── application.yml
│
├── cloudops-ui/                     # React front-end
│   ├── src/
│   ├── package.json
│   └── vite.config.ts
│
└── pom.xml                          # Parent POM
```

---

## Running Tests

```bash
# All modules
cd cloudops-triage
./mvnw test

# Service module only
./mvnw test -pl cloudops-service
```

Test coverage includes:

- `AgenticIncidentLoopTest` — verifies DEGRADED/RECOVERED paths and idempotency guard
- `RuleBasedSignalAnalyzerTest` — threshold boundary conditions for all metric types
- `LlmIncidentAdvisorTest` — JSON parsing and fallback behaviour on HTTP errors
