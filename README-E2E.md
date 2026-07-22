# LearnIQ End-to-End (E2E) Testing Guide

This guide walks you through testing the entire Cloud-Native architecture: 
**Gateway Routing & JWT Auth -> PostgreSQL Multi-Tenant DB -> Kafka Event Publishing -> Notification Logging -> LocalStack S3 Storage**.

## Prerequisites
Ensure all Docker containers are running and microservices are started locally:
```bash
cd /Users/sanem/LearnIQ/learniq-backend
docker-compose up -d
```
*(Make sure to run your Spring Boot applications via your IDE or Maven: `gateway-service` on port 8080, `auth-service` on 8081, `exam-service` on 8082, and `notification-service` on 8083).*

## Step 1: Generate a Mock JWT for Tenant

First, we need to authenticate and get a JWT token that has `tenantId = "akademi_1"` injected into its claims. The `auth-service` is exposed via the API Gateway.

**cURL Request:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "password",
    "tenantId": "akademi_1"
  }'
```
**Expected Output:**
You will receive a JSON response containing the `token`. Copy this token string for Step 2.

## Step 2: Submit an Exam Result via Gateway

Now, use the token to submit a student's exam result. The Gateway's `JwtAuthenticationFilter` will intercept this, strip the token, validate the cryptographic signature, and silently inject `X-Tenant-ID: akademi_1` into the headers before routing the request to `exam-service`.

*(Note: Because of our relational integrity, the `examId`, `studentId`, and `subjectId` must be valid UUIDs that exist in the database. If you haven't seeded the DB yet, you may need to insert mock rows into the `public.tenants`, `akademi_1.exams`, and `akademi_1.subjects` tables first).*

**cURL Request:**
```bash
curl -X POST http://localhost:8080/api/v1/exams/123e4567-e89b-12d3-a456-426614174000/results \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": "987e6543-e21b-12d3-a456-426614174000",
    "totalScore": 450.50,
    "classRank": 1,
    "schoolRank": 5,
    "subjectResults": [
      {
        "subjectId": "555e4567-e89b-12d3-a456-426614174000",
        "correctCount": 35,
        "wrongCount": 5,
        "netScore": 33.75
      }
    ]
  }'
```
**Expected Output:**
`HTTP 201 Created`

## Step 3: Verify the Kafka Notification

When the `exam-service` successfully saves the result to PostgreSQL, it fires an `ExamResultProcessedEvent` to the Kafka broker.

**Verification Step:**
Check the console output/logs of the running `notification-service` to ensure the consumer received the event from the `exam-results-topic`.

**Expected Log Output:**
`INFO  c.l.n.listener.NotificationListener - Notification [Tenant: akademi_1]: Exam 123e4567-e89b-12d3-a456-426614174000 results published for Student 987e6543-e21b-12d3-a456-426614174000. Score: 450.50`

## Step 4: Verify LocalStack S3 Storage

The `exam-service` will generate and upload the report to LocalStack S3 (this logic will be fully integrated during the LLM step, but you can test the `ReportStorageService` bean directly).

**Verification Step:**
Use the `aws` CLI (configured for local use) or `awslocal` to list the bucket contents and verify our strict multi-tenant path isolation.

```bash
# 1. List buckets to ensure 'learniq-reports' was created dynamically by the service
aws --endpoint-url=http://localhost:4566 s3 ls

# 2. List objects inside the specific tenant's directory to verify upload
aws --endpoint-url=http://localhost:4566 s3 ls s3://learniq-reports/tenants/akademi_1/students/987e6543-e21b-12d3-a456-426614174000/reports/
```

**Expected Output:**
You should see `latest_report.txt` securely isolated under the exact tenant and student structure!
