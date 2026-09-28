package com.learniq.exam.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fetches AI-generated reports from S3.
 *
 * S3 key pattern (written by ai-service):
 *   reports/{tenantId}/student_{studentId}_{timestamp}.md
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    /**
     * List all report metadata for a given student in a tenant.
     * Returns: [ { key, filename, lastModified, size } ]
     */
    public List<Map<String, Object>> listReports(String tenantId, String studentId) {
        String prefix = String.format("reports/%s/student_%s_", tenantId, studentId);

        try {
            ListObjectsV2Response response = s3Client.listObjectsV2(
                    ListObjectsV2Request.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .build());

            return response.contents().stream()
                    .map(obj -> Map.<String, Object>of(
                            "key",          obj.key(),
                            "filename",     obj.key().substring(obj.key().lastIndexOf('/') + 1),
                            "lastModified", obj.lastModified().toString(),
                            "sizeBytes",    obj.size()
                    ))
                    .collect(Collectors.toList());

        } catch (S3Exception e) {
            log.error("S3 error listing reports for student {} in tenant {}: {}", studentId, tenantId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not fetch reports");
        }
    }

    /**
     * Get the content of the latest report for a student.
     */
    public String getLatestReport(String tenantId, String studentId) {
        List<Map<String, Object>> reports = listReports(tenantId, studentId);
        if (reports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No reports found for this student");
        }
        // Reports are sorted by S3 (lexicographically by key = by timestamp)
        String latestKey = (String) reports.get(reports.size() - 1).get("key");
        return fetchContent(latestKey);
    }

    /**
     * Get the content of a specific report by filename.
     */
    public String getReport(String tenantId, String studentId, String filename) {
        // Reconstruct full S3 key and validate it belongs to this student
        String key = String.format("reports/%s/%s", tenantId, filename);
        if (!filename.startsWith("student_" + studentId + "_")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Report does not belong to this student");
        }
        return fetchContent(key);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String fetchContent(String key) {
        try {
            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build());
            return new String(s3Object.readAllBytes(), StandardCharsets.UTF_8);

        } catch (NoSuchKeyException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        } catch (S3Exception | IOException e) {
            log.error("S3 error fetching report {}: {}", key, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not fetch report");
        }
    }
}
