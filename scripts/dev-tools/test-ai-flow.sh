#!/bin/bash

# ==============================================================================
# E2E Test Script for Phase 7 (AI Report Flow)
# Ensure docker-compose up -d is running and exam-service + ai-service are started.
# ==============================================================================

set -e

# Configuration
EXAM_SERVICE_URL="http://localhost:8082"
TENANT_ID="akademi_1"
STUDENT_ID="11111111-1111-1111-1111-111111111111"
AWS_CMD="docker exec learniq-localstack awslocal"

echo "========================================================"
echo " 🚀 Testing AI Report Generation Flow End-to-End"
echo "========================================================"

echo ""
echo "0️⃣  Ensuring LocalStack S3 Bucket Exists..."
# Create the bucket, ignore error if it already exists
$AWS_CMD s3 mb s3://learniq-reports 2>/dev/null || true
echo "✅ Bucket 'learniq-reports' is ready."

echo ""
echo "1️⃣  Triggering Endpoint: POST /api/v1/analytics/students/{studentId}/reports/generate"
echo "Sending request to exam-service (Port 8082) with X-Tenant-Id: $TENANT_ID..."

HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -H "X-Tenant-Id: $TENANT_ID" \
  "$EXAM_SERVICE_URL/api/v1/analytics/students/$STUDENT_ID/reports/generate")

if [ "$HTTP_STATUS" -ne 202 ]; then
  echo "❌ FAILED: Expected HTTP 202 Accepted, but got HTTP $HTTP_STATUS."
  echo "Ensure exam-service is running on port 8082."
  exit 1
else
  echo "✅ SUCCESS: Received HTTP 202 Accepted. Event successfully published to Kafka!"
fi

echo ""
echo "2️⃣  Waiting for ai-service to consume the event and call the LLM..."
echo "(This usually takes 5-15 seconds depending on OpenRouter/Gemma speed)"

# We wait 20 seconds to be safe
for i in {1..20}; do
    echo -n "."
    sleep 1
done
echo " Done waiting!"

echo ""
echo "3️⃣  Verifying S3 Upload via LocalStack..."
echo "Checking bucket: s3://learniq-reports/reports/$TENANT_ID/"

# Check if the bucket exists, if not, it will fail the aws command
FILES_FOUND=$($AWS_CMD s3 ls "s3://learniq-reports/reports/$TENANT_ID/" || true)

if [[ -z "$FILES_FOUND" ]]; then
    echo "❌ FAILED: No files found in S3 bucket for tenant $TENANT_ID."
    echo "Check the logs of ai-service for any LLM API errors or Kafka connection issues."
    exit 1
else
    echo "✅ SUCCESS: File found in LocalStack S3!"
    echo "$FILES_FOUND"
    echo ""
    echo "========================================================"
    echo " 🎉 E2E AI Flow Test Passed Successfully!"
    echo "========================================================"
fi
