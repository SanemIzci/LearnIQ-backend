package com.learniq.exam.service;

import com.learniq.exam.domain.Exam;
import com.learniq.exam.dto.ExamResponse;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.ExamRepository;
import com.learniq.exam.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExamService Unit Tests")
class ExamServiceTest {

    @Mock private ExamRepository examRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks private ExamService examService;

    private static final String TENANT = "tenant_test";
    private static final UUID EXAM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLASSROOM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private Exam testExam;

    @BeforeEach
    void setUp() {
        testExam = Exam.builder()
                .id(EXAM_ID)
                .title("TYT Deneme 1")
                .examDate(LocalDate.of(2025, 6, 1))
                .examType("TYT")
                .subjects(new HashSet<>())
                .classroomIds(new HashSet<>())
                .build();
    }

    // ── assignClassroom Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("assignClassroom - Başarılı atama sınıfı exam'e ekler ve ExamResponse döner")
    void assignClassroom_success() {
        when(examRepository.findByIdWithSubjects(EXAM_ID)).thenReturn(Optional.of(testExam));
        when(jdbcTemplate.queryForList(contains("FROM " + TENANT + ".classrooms"), eq(Integer.class), eq(CLASSROOM_ID)))
                .thenReturn(List.of(1));
        when(jdbcTemplate.queryForList(contains("FROM " + TENANT + ".exam_classrooms"), eq(Integer.class), eq(EXAM_ID), eq(CLASSROOM_ID)))
                .thenReturn(List.of());
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

        ExamResponse response = examService.assignClassroom(TENANT, EXAM_ID, CLASSROOM_ID);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(EXAM_ID);
        assertThat(response.getClassroomIds()).contains(CLASSROOM_ID);
        verify(examRepository).save(testExam);
    }

    @Test
    @DisplayName("assignClassroom - Sınav kiracıda bulunamazsa 404 EntityNotFoundException fırlatır")
    void assignClassroom_examNotFound_throws404() {
        when(examRepository.findByIdWithSubjects(EXAM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> examService.assignClassroom(TENANT, EXAM_ID, CLASSROOM_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Exam");
        verify(examRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignClassroom - Sınıf kiracıda bulunamazsa 404 EntityNotFoundException fırlatır (cross-tenant izolasyonu)")
    void assignClassroom_classroomNotFound_throws404() {
        when(examRepository.findByIdWithSubjects(EXAM_ID)).thenReturn(Optional.of(testExam));
        when(jdbcTemplate.queryForList(contains("FROM " + TENANT + ".classrooms"), eq(Integer.class), eq(CLASSROOM_ID)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> examService.assignClassroom(TENANT, EXAM_ID, CLASSROOM_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Classroom");
        verify(examRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignClassroom - Sınav zaten bu sınıfa atanmışsa 409 Conflict fırlatır (duplicate engelleme)")
    void assignClassroom_alreadyAssigned_throws409Conflict() {
        testExam.getClassroomIds().add(CLASSROOM_ID);
        when(examRepository.findByIdWithSubjects(EXAM_ID)).thenReturn(Optional.of(testExam));
        when(jdbcTemplate.queryForList(contains("FROM " + TENANT + ".classrooms"), eq(Integer.class), eq(CLASSROOM_ID)))
                .thenReturn(List.of(1));

        assertThatThrownBy(() -> examService.assignClassroom(TENANT, EXAM_ID, CLASSROOM_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);
        verify(examRepository, never()).save(any());
    }

    // ── unassignClassroom Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("unassignClassroom - Başarılı kaldırma sınıfı exam'den çıkarır ve sonuçları korur")
    void unassignClassroom_success() {
        testExam.getClassroomIds().add(CLASSROOM_ID);
        when(examRepository.findByIdWithSubjects(EXAM_ID)).thenReturn(Optional.of(testExam));
        when(jdbcTemplate.queryForList(contains("FROM " + TENANT + ".classrooms"), eq(Integer.class), eq(CLASSROOM_ID)))
                .thenReturn(List.of(1));
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

        ExamResponse response = examService.unassignClassroom(TENANT, EXAM_ID, CLASSROOM_ID);

        assertThat(response).isNotNull();
        assertThat(response.getClassroomIds()).doesNotContain(CLASSROOM_ID);
        verify(examRepository).save(testExam);
    }

    @Test
    @DisplayName("unassignClassroom - Sınav bu sınıfa atanmamışsa 404 NOT_FOUND fırlatır")
    void unassignClassroom_notAssigned_throws404() {
        when(examRepository.findByIdWithSubjects(EXAM_ID)).thenReturn(Optional.of(testExam));
        when(jdbcTemplate.queryForList(contains("FROM " + TENANT + ".classrooms"), eq(Integer.class), eq(CLASSROOM_ID)))
                .thenReturn(List.of(1));
        when(jdbcTemplate.queryForList(contains("FROM " + TENANT + ".exam_classrooms"), eq(Integer.class), eq(EXAM_ID), eq(CLASSROOM_ID)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> examService.unassignClassroom(TENANT, EXAM_ID, CLASSROOM_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
        verify(examRepository, never()).save(any());
    }

    // ── listClassroomsForExam Tests ──────────────────────────────────────────

    @Test
    @DisplayName("listClassroomsForExam - Sınava atanmış sınıfları listeler")
    void listClassroomsForExam_success() {
        when(examRepository.existsById(EXAM_ID)).thenReturn(true);
        when(jdbcTemplate.queryForList(contains("FROM " + TENANT + ".classrooms"), eq(EXAM_ID)))
                .thenReturn(List.of(Map.of("id", CLASSROOM_ID.toString(), "name", "12-A")));

        List<Map<String, Object>> classrooms = examService.listClassroomsForExam(TENANT, EXAM_ID);

        assertThat(classrooms).hasSize(1);
        assertThat(classrooms.get(0).get("name")).isEqualTo("12-A");
    }

    @Test
    @DisplayName("listClassroomsForExam - Sınav bulunamazsa 404 fırlatır")
    void listClassroomsForExam_examNotFound_throws404() {
        when(examRepository.existsById(EXAM_ID)).thenReturn(false);

        assertThatThrownBy(() -> examService.listClassroomsForExam(TENANT, EXAM_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Exam");
    }
}

