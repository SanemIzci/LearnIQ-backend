package com.learniq.exam.it;

import com.learniq.common.tenant.TenantContext;
import com.learniq.common.tenant.flyway.TenantSchemaMigrator;
import com.learniq.exam.controller.StudentProfileController;
import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.dto.ClassroomAnalyticsResponse;
import com.learniq.exam.dto.StudentDashboardResponse;
import com.learniq.exam.repository.ExamRepository;
import com.learniq.exam.repository.StudentExamResultRepository;
import com.learniq.exam.security.AccessControlService;
import com.learniq.exam.service.ClassSubjectTrendCalculator;
import com.learniq.exam.service.ClassroomService;
import com.learniq.exam.service.ExamFinalizationService;
import com.learniq.exam.service.ShareLinkService;
import com.learniq.exam.service.StudentDashboardService;
import com.learniq.exam.service.TeacherAnalyticsService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-3: exam-service'in gerçek PostgreSQL üzerindeki raw-SQL yollarını
 * (şema-nitelikli, TenantContext tabanlı) uçtan uca doğrular.
 *
 * JPA repository'leri mock'tur; DB, migration'lar ve JDBC servisleri gerçektir.
 * Kafka mock'tur (Kafka doğrulaması kapsamda değildir).
 */
@Testcontainers
@DisplayName("Exam flow real-PostgreSQL IT")
class ExamFlowIT {

    private static final String TENANT_A = "it_tenant_a";
    private static final String TENANT_B = "it_tenant_b";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static NamedParameterJdbcTemplate namedJdbc;

    @BeforeAll
    static void setUp() {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        TenantSchemaMigrator.migrateTenant(dataSource, TENANT_A);
        TenantSchemaMigrator.migrateTenant(dataSource, TENANT_B);
    }

    @AfterAll
    static void tearDown() {
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ── Senaryo A: tam akış (onaylı sonuç → finalize → trend → panel → analitik) ──

    @Test
    @DisplayName("IT-A: sonuç onayı → finalize (sınıf sıralaması & yüzdelik) → trend → öğrenci paneli → öğretmen analitiği")
    void examFlow_endToEnd() {
        TenantContext.setCurrentTenant(TENANT_A);
        UUID teacherId = UUID.randomUUID();
        UUID classroomId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID s3 = UUID.randomUUID();

        // Kullanıcılar önce (ClassroomService guard'ları users tablosunu sorgular)
        String insUser = ("""
                INSERT INTO %1$s.users (id, username, password_hash, email, full_name, role)
                VALUES (?, ?, 'x', ?, ?, '%2$s') ON CONFLICT DO NOTHING
                """).formatted(TENANT_A, "STUDENT");
        jdbc.update(insUser, s1, "it_" + s1, s1 + "@it.test", "Ayşe Yılmaz");
        jdbc.update(insUser, s2, "it_" + s2, s2 + "@it.test", "Mehmet Demir");
        jdbc.update(insUser, s3, "it_" + s3, s3 + "@it.test", "Zeynep Kaya");
        jdbc.update(insUser.formatted(TENANT_A, "STUDENT").replace("'STUDENT'", "'TEACHER'"),
                teacherId, "it_" + teacherId, teacherId + "@it.test", "IT Öğretmen");
        jdbc.update("INSERT INTO %1$s.subjects (id, name) VALUES (?, ?) ON CONFLICT DO NOTHING".formatted(TENANT_A),
                subjectId, "IT Ders " + subjectId);

        ClassroomService classroomService = new ClassroomService(jdbc);
        classroomId = UUID.fromString((String) classroomService
                .createClassroom(TENANT_A, "IT-A").get("id"));
        classroomService.addTeacher(TENANT_A, classroomId, teacherId);
        classroomService.addStudent(TENANT_A, classroomId, s1);
        classroomService.addStudent(TENANT_A, classroomId, s2);
        classroomService.addStudent(TENANT_A, classroomId, s3);
        jdbc.update("""
                INSERT INTO %s.exams (id, title, exam_date, exam_type, status)
                VALUES (?, 'Flow Sınavı', CURRENT_DATE, 'TYT', 'OPEN')
                """.formatted(TENANT_A), examId);
        insertApprovedResult(TENANT_A, s1, examId, "70.00");
        insertApprovedResult(TENANT_A, s2, examId, "80.00");
        insertApprovedResult(TENANT_A, s3, examId, "90.00");
        insertSubjectResult(TENANT_A, s1, examId, subjectId, "5.00");
        insertSubjectResult(TENANT_A, s2, examId, subjectId, "8.00");
        insertSubjectResult(TENANT_A, s3, examId, subjectId, "12.00");

        Exam examEntity = Exam.builder()
                .id(examId)
                .title("Flow Sınavı")
                .examDate(LocalDate.now())
                .examType("TYT")
                .status(Exam.ExamStatus.OPEN)
                .classroomIds(new java.util.HashSet<>(List.of(classroomId)))
                .build();

        Map<UUID, StudentExamResult> resultsByStudent = new HashMap<>();

        StudentExamResult r1 = resultEntity(examEntity, s1, "70.00");
        StudentExamResult r2 = resultEntity(examEntity, s2, "80.00");
        StudentExamResult r3 = resultEntity(examEntity, s3, "90.00");
        resultsByStudent.put(s1, r1);
        resultsByStudent.put(s2, r2);
        resultsByStudent.put(s3, r3);

        ExamRepository examRepository = mock(ExamRepository.class);
        when(examRepository.findById(examId)).thenReturn(Optional.of(examEntity));
        StudentExamResultRepository resultsRepository = mock(StudentExamResultRepository.class);
        when(resultsRepository.findByExamIdAndStatusOrderByTotalScoreDesc(
                eq(examId), eq(StudentExamResult.ResultStatus.APPROVED)))
                .thenReturn(List.of(r3, r2, r1)); // puan sırasına göre
        when(resultsRepository.findByExamIdAndStudentId(eq(examId), any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID studentId = inv.getArgument(1);
                    return Optional.ofNullable(resultsByStudent.get(studentId));
                });

        org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate = mock();

        ExamFinalizationService finalizationService = new ExamFinalizationService(
                examRepository, resultsRepository, namedJdbc, kafkaTemplate);
        finalizationService.finalizeExam(examId);

        // JPA saveAll mock'ta kalır; sıralama değerlerini DB'ye gerçek kodun ürettiği
        // entity'lerden senkronize eder (gerçek ağaçta flush/devam ile aynı etki).
        assertThat(r1.getClassRank()).isEqualTo(3);
        assertThat(r3.getClassRank()).isEqualTo(1);
        assertThat(r1.getPercentile()).isEqualByComparingTo("100.00");
        assertThat(r3.getPercentile()).isEqualByComparingTo("33.33");
        for (StudentExamResult r : List.of(r1, r2, r3)) {
            jdbc.update("""
                    UPDATE %s.student_exam_results SET class_rank = ?, school_rank = ?, percentile = ?
                    WHERE exam_id = ? AND student_id = ?
                    """.formatted(TENANT_A), r.getClassRank(), r.getSchoolRank(),
                    r.getPercentile(), examId, r.getStudentId());
        }
        jdbc.update("UPDATE %s.exams SET status = 'FINALIZED', finalized_at = NOW() WHERE id = ?"
                .formatted(TENANT_A), examId);

        // EXAM_FINALIZED event üretildi (Kafka doğrulaması kapsam dışı; çağrı bekleniyor)
        verify(kafkaTemplate, times(1)).send(eq("exam-results-topic"), any());

        ClassSubjectTrendCalculator trendCalculator = new ClassSubjectTrendCalculator(jdbc, kafkaTemplate);
        trendCalculator.calculate(TENANT_A, examId);

        BigDecimal avgNet = jdbc.queryForObject(
                "SELECT avg_net FROM %s.class_subject_trends WHERE exam_id = ?".formatted(TENANT_A),
                BigDecimal.class, examId);
        assertThat(avgNet).isEqualByComparingTo("8.33"); // (5 + 8 + 12) / 3
        verify(kafkaTemplate, times(1)).send(eq("class-analysis-topic"), anyString(), any());

        // Öğrenci paneli — gerçek SQL, gerçek DB
        StudentDashboardService dashboardService = new StudentDashboardService(resultsRepository, namedJdbc);
        StudentDashboardResponse dashboard = dashboardService.getDashboard(examId, s3);
        assertThat(dashboard.getClassRank()).isEqualTo(1);
        assertThat(dashboard.getNetScore()).isEqualByComparingTo("90.00");
        assertThat(dashboard.getClassAverage()).isEqualByComparingTo("80.00");
        assertThat(dashboard.getTop3Students()).hasSize(3);
        // leaderboard_consent varsayılan FALSE → anonim
        assertThat(dashboard.getTop3Students().get(0).getDisplayName()).isEqualTo("Anonim Öğrenci");

        // Öğretmen analitiği — gerçek SQL + gerçek AccessControl
        TeacherAnalyticsService analyticsService = new TeacherAnalyticsService(
                jdbc, new AccessControlService(jdbc));
        ClassroomAnalyticsResponse analytics = analyticsService.getClassroomAnalytics(
                TENANT_A, teacherId, "TEACHER", classroomId, examId);
        assertThat(analytics.getTotalStudents()).isEqualTo(3);
        assertThat(analytics.getParticipationCount()).isEqualTo(3);
        assertThat(analytics.getParticipationRate()).isEqualByComparingTo("100.00");
        assertThat(analytics.getClassAverageScore()).isEqualByComparingTo("80.00");
        assertThat(analytics.getSubjectAverages()).hasSize(1);
        assertThat(analytics.getSubjectAverages().get(0).getAvgNet()).isEqualByComparingTo("8.33");
    }

    // ── Senaryo B: tenant izolasyonu + paylaşım linki ─────────────────────────

    @Test
    @DisplayName("IT-B: aynı UUID'ye sahip veriler tenant'lar arasında sızamaz; paylaşım linki tenant'ına hapsolur")
    void tenantIsolation_andShareLink() {
        // Aynı UUID'ler iki tenant şemasında da kullanılır.
        UUID sharedStudent = UUID.randomUUID();
        UUID sharedExam = UUID.randomUUID();
        UUID sharedSubject = UUID.randomUUID();
        UUID sharedTeacher = UUID.randomUUID();
        UUID sharedClassroom = UUID.randomUUID();

        Exam examEntity = Exam.builder()
                .id(sharedExam)
                .title("Sınav")
                .examDate(LocalDate.now())
                .examType("TYT")
                .status(Exam.ExamStatus.FINALIZED)
                .build();

        // Tenant A: 90.000 puan
        TenantContext.setCurrentTenant(TENANT_A);
        seedSharedIsolationData(TENANT_A, sharedExam, sharedClassroom, sharedTeacher,
                sharedStudent, sharedSubject, "90.00", "12.00");
        // Tenant B: aynı UUID'ler, farklı veri (40.000)
        TenantContext.setCurrentTenant(TENANT_B);
        seedSharedIsolationData(TENANT_B, sharedExam, sharedClassroom, sharedTeacher,
                sharedStudent, sharedSubject, "40.00", "3.00");

        // 1) Dashboard: aynı (examId, studentId) sorgusu tenant'a göre farklı sonucu döner
        StudentExamResultRepository resultsRepository = mock(StudentExamResultRepository.class);
        when(resultsRepository.findByExamIdAndStudentId(eq(sharedExam), eq(sharedStudent)))
                .thenAnswer(inv -> Optional.of(resultEntity(examEntity, sharedStudent,
                        TenantContext.getCurrentTenant().equals(TENANT_A) ? "90.00" : "40.00")));
        StudentDashboardService dashboardService = new StudentDashboardService(resultsRepository, namedJdbc);

        TenantContext.setCurrentTenant(TENANT_A);
        StudentDashboardResponse dashboardA = dashboardService.getDashboard(sharedExam, sharedStudent);
        assertThat(dashboardA.getNetScore()).isEqualByComparingTo("90.00");

        TenantContext.setCurrentTenant(TENANT_B);
        StudentDashboardResponse dashboardB = dashboardService.getDashboard(sharedExam, sharedStudent);
        assertThat(dashboardB.getNetScore()).isEqualByComparingTo("40.00");

        // 2) Profil: leaderboard onayı yalnız hedef tenant şemasında yazılır
        StudentProfileController profileController = new StudentProfileController(namedJdbc);
        ResponseEntity<Void> consentResponse = profileController.updateLeaderboardConsent(
                sharedStudent.toString(), "STUDENT", true);
        assertThat(consentResponse.getStatusCode().value()).isEqualTo(200);
        Boolean consentB = jdbc.queryForObject(
                "SELECT leaderboard_consent FROM %s.users WHERE id = ?".formatted(TENANT_B),
                Boolean.class, sharedStudent);
        assertThat(consentB).isTrue();
        Boolean consentA = jdbc.queryForObject(
                "SELECT leaderboard_consent FROM %s.users WHERE id = ?".formatted(TENANT_A),
                Boolean.class, sharedStudent);
        assertThat(consentA).isFalse();

        // 3) Paylaşım linki: token'a tenant öneklendiği için public okuma doğru şemada bulunur
        StudentDashboardService mockedDashboard = mock(StudentDashboardService.class);
        when(mockedDashboard.getDashboard(any(UUID.class), any(UUID.class))).thenAnswer(inv ->
                StudentDashboardResponse.builder()
                        .netScore(TenantContext.getCurrentTenant().equals(TENANT_A)
                                ? new BigDecimal("90.00") : new BigDecimal("40.00"))
                        .build());
        ShareLinkService shareLinkService = new ShareLinkService(mockedDashboard, namedJdbc,
                new com.fasterxml.jackson.databind.ObjectMapper());

        TenantContext.setCurrentTenant(TENANT_A);
        String tokenA = shareLinkService.generateShareLink(sharedExam, sharedStudent);
        assertThat(tokenA).startsWith(TENANT_A + ".");

        TenantContext.setCurrentTenant(TENANT_B);
        String tokenB = shareLinkService.generateShareLink(sharedExam, sharedStudent);
        assertThat(tokenB).startsWith(TENANT_B + ".");
        assertThat(tokenB).isNotEqualTo(tokenA);

        // Tenant context'siz (public route simülasyonu) okuma — token önekinden çözülür
        TenantContext.clear();
        StudentDashboardResponse readA = shareLinkService.getSharedSnapshot(tokenA);
        assertThat(readA.getNetScore()).isEqualByComparingTo("90.00");
        StudentDashboardResponse readB = shareLinkService.getSharedSnapshot(tokenB);
        assertThat(readB.getNetScore()).isEqualByComparingTo("40.00");

        // Tenant öneki olmayan token reddedilir
        assertThatThrownBy(() -> shareLinkService.getSharedSnapshot("noprefix-token"))
                .isInstanceOf(IllegalArgumentException.class);
        // Var olmayan tenant önekli token reddedilir (tenant'ına hapsolma kanıtı)
        assertThatThrownBy(() -> shareLinkService.getSharedSnapshot(
                "nope_tenant." + tokenA.substring(tokenA.indexOf('.') + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private void insertApprovedResult(String tenant, UUID studentId, UUID examId, String totalScore) {
        jdbc.update("""
                INSERT INTO %s.student_exam_results (student_id, exam_id, total_score, status)
                VALUES (?, ?, ?, 'APPROVED')
                """.formatted(tenant), studentId, examId, new BigDecimal(totalScore));
    }

    private void insertSubjectResult(String tenant, UUID studentId, UUID examId,
                                     UUID subjectId, String netScore) {
        jdbc.update("""
                INSERT INTO %s.student_subject_results (student_id, exam_id, subject_id, net_score)
                VALUES (?, ?, ?, ?)
                """.formatted(tenant), studentId, examId, subjectId, new BigDecimal(netScore));
    }

    private StudentExamResult resultEntity(Exam exam, UUID studentId, String totalScore) {
        return StudentExamResult.builder()
                .id(UUID.randomUUID())
                .studentId(studentId)
                .exam(exam)
                .totalScore(new BigDecimal(totalScore))
                .status(StudentExamResult.ResultStatus.APPROVED)
                .build();
    }

    private void seedSharedIsolationData(String tenant, UUID examId, UUID classroomId, UUID teacherId,
                                         UUID studentId, UUID subjectId, String totalScore, String netScore) {
        jdbc.update("""
                INSERT INTO %1$s.users (id, username, password_hash, email, full_name, role)
                VALUES (?, ?, 'x', ?, ?, 'STUDENT')
                """.formatted(tenant), studentId, "it_" + tenant + "_" + studentId,
                studentId + "@it.test", "Zeynep Kaya");
        jdbc.update("""
                INSERT INTO %1$s.users (id, username, password_hash, email, role)
                VALUES (?, ?, 'x', ?, 'TEACHER')
                """.formatted(tenant), teacherId, "it_" + tenant + "_" + teacherId, teacherId + "@it.test");
        jdbc.update("INSERT INTO %1$s.classrooms (id, name) VALUES (?, ?)".formatted(tenant),
                classroomId, "Sınıf " + tenant);
        jdbc.update("""
                INSERT INTO %s.classroom_students (classroom_id, student_id) VALUES (?, ?)
                """.formatted(tenant), classroomId, studentId);
        jdbc.update("""
                INSERT INTO %s.classroom_teachers (classroom_id, teacher_id) VALUES (?, ?)
                """.formatted(tenant), classroomId, teacherId);
        jdbc.update("""
                INSERT INTO %1$s.subjects (id, name) VALUES (?, ?)
                """.formatted(tenant), subjectId, "Isolasyon Ders " + tenant);
        jdbc.update("""
                INSERT INTO %s.exams (id, title, exam_date, exam_type, status)
                VALUES (?, 'Izolasyon Sınavı', CURRENT_DATE, 'TYT', 'FINALIZED')
                """.formatted(tenant), examId);
        insertApprovedResult(tenant, studentId, examId, totalScore);
        insertSubjectResult(tenant, studentId, examId, subjectId, netScore);
    }
}
