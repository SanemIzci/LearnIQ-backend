# LearnIQ Backend

A **production-grade, event-driven, multi-tenant SaaS backend** for test-prep academies. Built with a microservices architecture using Java 21 and Spring Boot 3.3.x.

---

## 🏗️ Architecture Overview

```
learniq-backend/
├── common-library/       → Shared DTOs, JWT utils, multi-tenancy interceptors, Flyway configs
├── gateway-service/      → Spring Cloud Gateway — centralized routing & JWT validation
├── auth-service/         → Authentication, RBAC, tenant registration (master schema owner)
├── exam-service/         → Exam & results ingestion, analytics REST API, Kafka event producer
├── notification-service/ → Kafka consumer for email/SMS notifications
└── ai-service/           → Kafka consumer — LLM orchestration (OpenRouter/Gemma), S3 uploads
```

## ✨ Key Features

- **Schema-per-Tenant Multi-Tenancy** — Each academy gets a fully isolated PostgreSQL schema, dynamically migrated via Flyway and resolved at runtime by Hibernate.
- **Centralized API Gateway** — All traffic is routed through Spring Cloud Gateway, which validates JWT tokens and injects `X-Tenant-Id` headers before forwarding requests.
- **Event-Driven AI Pipeline** — When a report is requested, `exam-service` publishes an `AiReportRequestedEvent` to Kafka and immediately returns `202 Accepted`. `ai-service` asynchronously consumes the event, calls the LLM, and uploads the generated Markdown report to AWS S3.
- **Personalized AI Reports** — Integrates with OpenRouter (Google Gemma model) via Spring AI to generate human-readable, data-driven student performance analyses.
- **Cloud-Native** — AWS S3 for report storage, emulated locally with LocalStack. Kafka for async event streaming.

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.x, Spring Cloud Gateway |
| Database | PostgreSQL 16, Flyway, Hibernate |
| Messaging | Apache Kafka |
| AI / LLM | Spring AI, OpenRouter (Google Gemma) |
| Storage | AWS S3 (LocalStack for local dev) |
| Security | JWT (JJWT), Spring Security |
| Build | Maven (multi-module monorepo) |
| Dev Infra | Docker Compose |

## 🚀 Running Locally

**Prerequisites:** Java 21, Maven, Docker

```bash
# 1. Start infrastructure (Postgres, Kafka, LocalStack)
docker-compose up -d

# 2. Start services (in separate terminals)
export OPENROUTER_API_KEY="your-key-here"

./mvnw-java21.sh spring-boot:run -pl auth-service
./mvnw-java21.sh spring-boot:run -pl exam-service
./mvnw-java21.sh spring-boot:run -pl ai-service

# 3. Run the End-to-End AI flow test
./test-ai-flow.sh
```

## 🔬 E2E Test Flow

The `test-ai-flow.sh` script verifies the full pipeline:
1. `POST /api/v1/analytics/students/{studentId}/reports/generate` → returns `202 Accepted`
2. `exam-service` publishes `AiReportRequestedEvent` to Kafka
3. `ai-service` consumes the event, calls the LLM, and generates a Markdown report
4. Report is uploaded to `s3://learniq-reports/reports/{tenantId}/`
