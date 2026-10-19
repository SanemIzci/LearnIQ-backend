package com.learniq.exam.repository;

import com.learniq.exam.domain.AiReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiReportRepository extends JpaRepository<AiReport, UUID> {

    Optional<AiReport> findByStudentIdAndDataHash(UUID studentId, String dataHash);

    // Aynı hash'e sahip en son READY rapor (herhangi bir öğrenciye ait olabilir).
    // Derived query: JPQL'de standart olmayan LIMIT kullanımından kaçınır.
    Optional<AiReport> findFirstByDataHashAndStatusOrderByCompletedAtDesc(String dataHash, AiReport.ReportStatus status);

    default Optional<AiReport> findFirstReadyByDataHash(String dataHash) {
        return findFirstByDataHashAndStatusOrderByCompletedAtDesc(dataHash, AiReport.ReportStatus.READY);
    }

    boolean existsByStudentIdAndS3Key(UUID studentId, String s3Key);
}
