package com.learniq.exam.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportStorageService {

    private final S3Client s3Client;
    private static final String BUCKET_NAME = "learniq-reports";

    public String uploadReport(String tenantId, String studentId, String reportContent) {
        ensureBucketExists();

        String objectKey = String.format("tenants/%s/students/%s/reports/latest_report.txt", tenantId, studentId);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(objectKey)
                .contentType("text/plain")
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromString(reportContent));
        
        String s3Uri = String.format("s3://%s/%s", BUCKET_NAME, objectKey);
        log.info("Successfully uploaded report to {}", s3Uri);
        return s3Uri;
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(BUCKET_NAME).build());
        } catch (S3Exception e) {
            // If it returns a 404 (Not Found), the bucket doesn't exist
            if (e.statusCode() == 404 || e.statusCode() == 403) {
                log.info("Bucket '{}' does not exist. Creating it now via LocalStack...", BUCKET_NAME);
                s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
            } else {
                throw e;
            }
        }
    }
}
