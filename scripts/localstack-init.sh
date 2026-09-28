#!/bin/bash
# ============================================================
# LocalStack Init Script
# LocalStack "ready" olduğunda otomatik çalışır.
# Bucket zaten varsa hata vermez (|| true).
# ============================================================

echo "🚀 LearnIQ LocalStack init başlıyor..."

# S3 bucket oluştur (yoksa)
awslocal s3 mb s3://learniq-reports 2>/dev/null || echo "✅ learniq-reports bucket zaten mevcut"

# Bucket listesini doğrula
echo "📦 Mevcut bucket'lar:"
awslocal s3 ls

echo "✅ LocalStack init tamamlandı."
