package com.learniq.exam.controller;

import com.learniq.exam.dto.StudentHomeResponseDto;
import com.learniq.exam.service.StudentHomeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = StudentHomeController.class,
    excludeAutoConfiguration = {
        KafkaAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
    }
)
class StudentHomeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private StudentHomeService studentHomeService;
    
    // Yetersiz WebMvcTest izolasyonu yüzünden gereken MockBean'ler
    @MockBean private com.learniq.exam.service.ExamService examService;
    @MockBean private com.learniq.exam.service.ExamResultService examResultService;
    @MockBean private com.learniq.exam.service.SubjectService subjectService;
    @MockBean private com.learniq.exam.service.AnalyticsService analyticsService;
    @MockBean private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;
    @MockBean private javax.sql.DataSource dataSource;
    @MockBean private com.learniq.exam.repository.StudentExamResultRepository studentExamResultRepository;
    @MockBean private org.springframework.jdbc.core.JdbcTemplate jdbcTmpl;
    @MockBean private org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbcTemplate;
    @MockBean private com.learniq.exam.security.AccessControlService accessControlService;
    @MockBean private com.learniq.exam.repository.ExamRepository examRepository;
    @MockBean private com.learniq.exam.repository.ExamTypeSubjectRuleRepository ruleRepository;
    @MockBean private com.learniq.exam.service.StudentDashboardService studentDashboardService;
    @MockBean private com.learniq.exam.service.ShareLinkService shareLinkService;
    @MockBean private com.learniq.exam.service.AiReportService aiReportService;
    @MockBean private com.learniq.exam.repository.AiReportRepository aiReportRepository;
    @MockBean private com.learniq.exam.repository.StudentSubjectResultRepository studentSubjectResultRepository;
    @MockBean private com.learniq.exam.service.DataFingerprintService fingerprintService;
    @MockBean private com.learniq.exam.service.StudentTrendService studentTrendService;
    @MockBean private com.learniq.exam.service.BadgeService badgeService;
    @MockBean private com.learniq.exam.repository.BadgeRepository badgeRepository;
    @MockBean private com.learniq.exam.repository.StudentBadgeRepository studentBadgeRepository;

    @Test
    @DisplayName("GET /api/v1/students/me/home - 200 OK")
    void getHomeData_success() throws Exception {
        UUID studentId = UUID.randomUUID();

        StudentHomeResponseDto mockResponse = StudentHomeResponseDto.builder()
                .studentName("Ahmet Yılmaz")
                .classroomName("12-A")
                .latestExam(StudentHomeResponseDto.LatestExamDto.builder()
                        .title("YKS Deneme 1")
                        .netScore(new BigDecimal("100.25"))
                        .build())
                .recentBadges(List.of(
                        StudentHomeResponseDto.BadgeDto.builder()
                                .title("Gelişim Ustası")
                                .build()
                ))
                .build();

        when(studentHomeService.getStudentHomeData(studentId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/students/me/home")
                .header("X-User-Id", studentId.toString())
                .header("X-User-Role", "STUDENT")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName").value("Ahmet Yılmaz"))
                .andExpect(jsonPath("$.classroomName").value("12-A"))
                .andExpect(jsonPath("$.latestExam.title").value("YKS Deneme 1"))
                .andExpect(jsonPath("$.recentBadges[0].title").value("Gelişim Ustası"));
    }

    @Test
    @DisplayName("GET /api/v1/students/me/home - 403 Forbidden")
    void getHomeData_wrongRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/students/me/home")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "TEACHER")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
