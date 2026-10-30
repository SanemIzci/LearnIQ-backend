package com.learniq.exam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.domain.TeacherObservation;
import com.learniq.exam.dto.CreateObservationRequest;
import com.learniq.exam.dto.ObservationResponse;
import com.learniq.exam.dto.UpdateObservationRequest;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.TeacherObservationRepository;
import com.learniq.exam.security.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherObservationService Unit Tests")
class TeacherObservationServiceTest {

    @Mock private TeacherObservationRepository observationRepository;
    @Mock private AccessControlService accessControlService;
    @Mock private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private TeacherObservationService observationService;

    private ObjectMapper objectMapper;
    private UUID teacherId;
    private UUID classroomId;
    private UUID observationId;
    private final String tenantId = "tenant_test";
    private final String role = "TEACHER";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Manuel inject: @InjectMocks ObjectMapper inject etmez çünkü field adı farklı olabilir
        // Reflection ile inject ediyoruz
        try {
            var field = TeacherObservationService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(observationService, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        teacherId     = UUID.randomUUID();
        classroomId   = UUID.randomUUID();
        observationId = UUID.randomUUID();
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create - başarıyla gözlem notu oluşturur")
    void create_success() {
        CreateObservationRequest request = new CreateObservationRequest();
        request.setContent("Sınıf bu denemede matematik konusunda iyileşme gösterdi.");
        request.setTags(List.of("matematik", "iyileşme"));

        TeacherObservation saved = TeacherObservation.builder()
                .id(observationId)
                .teacherId(teacherId)
                .classroomId(classroomId)
                .content(request.getContent())
                .tags("[\"matematik\",\"iyileşme\"]")
                .createdAt(LocalDateTime.now())
                .build();

        doNothing().when(accessControlService).validateClassroomAccess(any(), any(), any(), any());
        when(observationRepository.save(any())).thenReturn(saved);

        ObservationResponse response = observationService.create(tenantId, teacherId, role, classroomId, request);

        assertThat(response.getId()).isEqualTo(observationId);
        assertThat(response.getContent()).isEqualTo(request.getContent());
        assertThat(response.getTags()).containsExactly("matematik", "iyileşme");
        verify(accessControlService).validateClassroomAccess(tenantId, teacherId, role, classroomId);
        verify(observationRepository).save(any(TeacherObservation.class));
        verify(kafkaTemplate).send(eq(TeacherObservationService.OBSERVATION_TOPIC), any());
    }

    @Test
    @DisplayName("create - içerik trim edilir")
    void create_contentIsTrimmed() {
        CreateObservationRequest request = new CreateObservationRequest();
        request.setContent("   Sınıf iyi performans gösterdi.   ");

        TeacherObservation saved = TeacherObservation.builder()
                .id(observationId)
                .teacherId(teacherId)
                .classroomId(classroomId)
                .content("Sınıf iyi performans gösterdi.")
                .build();

        doNothing().when(accessControlService).validateClassroomAccess(any(), any(), any(), any());
        when(observationRepository.save(any())).thenReturn(saved);

        ObservationResponse response = observationService.create(tenantId, teacherId, role, classroomId, request);

        assertThat(response.getContent()).isEqualTo("Sınıf iyi performans gösterdi.");
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listByClassroom - sınıfa ait notları döndürür")
    void listByClassroom_returnsObservations() {
        TeacherObservation obs = TeacherObservation.builder()
                .id(observationId)
                .teacherId(teacherId)
                .classroomId(classroomId)
                .content("Genel not")
                .build();

        doNothing().when(accessControlService).validateClassroomAccess(any(), any(), any(), any());
        when(observationRepository.findByTeacherIdAndClassroomIdOrderByCreatedAtDesc(teacherId, classroomId))
                .thenReturn(List.of(obs));

        List<ObservationResponse> result = observationService.listByClassroom(tenantId, teacherId, role, classroomId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClassroomId()).isEqualTo(classroomId);
    }

    @Test
    @DisplayName("listAll - öğretmenin tüm notlarını döndürür")
    void listAll_returnsAllObservations() {
        TeacherObservation obs1 = TeacherObservation.builder().id(UUID.randomUUID()).teacherId(teacherId).classroomId(classroomId).content("Not 1").build();
        TeacherObservation obs2 = TeacherObservation.builder().id(UUID.randomUUID()).teacherId(teacherId).classroomId(classroomId).content("Not 2").build();

        when(observationRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId)).thenReturn(List.of(obs1, obs2));

        List<ObservationResponse> result = observationService.listAll(teacherId);

        assertThat(result).hasSize(2);
    }

    // ── Get ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById - mevcut not döndürülür")
    void getById_found() {
        TeacherObservation obs = TeacherObservation.builder()
                .id(observationId).teacherId(teacherId).classroomId(classroomId).content("Not").build();

        when(observationRepository.findByIdAndTeacherId(observationId, teacherId)).thenReturn(Optional.of(obs));

        ObservationResponse response = observationService.getById(teacherId, observationId);

        assertThat(response.getId()).isEqualTo(observationId);
    }

    @Test
    @DisplayName("getById - bulunamayan not 404 fırlatır")
    void getById_notFound() {
        when(observationRepository.findByIdAndTeacherId(observationId, teacherId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> observationService.getById(teacherId, observationId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update - içerik ve etiketler güncellenir")
    void update_success() {
        TeacherObservation existing = TeacherObservation.builder()
                .id(observationId).teacherId(teacherId).classroomId(classroomId)
                .content("Eski not").tags(null).build();

        UpdateObservationRequest request = new UpdateObservationRequest();
        request.setContent("Güncellenmiş not içeriği");
        request.setTags(List.of("yeni-etiket"));

        when(observationRepository.findByIdAndTeacherId(observationId, teacherId)).thenReturn(Optional.of(existing));
        when(observationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObservationResponse response = observationService.update(teacherId, observationId, request);

        assertThat(response.getContent()).isEqualTo("Güncellenmiş not içeriği");
        assertThat(response.getTags()).containsExactly("yeni-etiket");
    }

    @Test
    @DisplayName("update - sahip olmayan notta 404 fırlatır")
    void update_notOwned_throws() {
        when(observationRepository.findByIdAndTeacherId(observationId, teacherId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> observationService.update(teacherId, observationId, new UpdateObservationRequest()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete - başarıyla siler")
    void delete_success() {
        TeacherObservation existing = TeacherObservation.builder()
                .id(observationId).teacherId(teacherId).classroomId(classroomId).content("Not").build();

        when(observationRepository.findByIdAndTeacherId(observationId, teacherId)).thenReturn(Optional.of(existing));
        doNothing().when(observationRepository).delete(existing);

        assertThatCode(() -> observationService.delete(teacherId, observationId)).doesNotThrowAnyException();
        verify(observationRepository).delete(existing);
    }

    @Test
    @DisplayName("delete - bulunamayan not 404 fırlatır")
    void delete_notFound() {
        when(observationRepository.findByIdAndTeacherId(observationId, teacherId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> observationService.delete(teacherId, observationId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("create - yayınlanan event payload'ı doğru içerir")
    void create_publishesIndexedEventPayload() {
        CreateObservationRequest request = new CreateObservationRequest();
        request.setContent("Sınıf bu denemede başarı gösterdi, dikkat artıyor.");

        doNothing().when(accessControlService).validateClassroomAccess(any(), any(), any(), any());
        when(observationRepository.save(any())).thenReturn(TeacherObservation.builder()
                .id(observationId).teacherId(teacherId).classroomId(classroomId).build());

        observationService.create(tenantId, teacherId, role, classroomId, request);

        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq(TeacherObservationService.OBSERVATION_TOPIC), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue())
                .containsEntry("eventType", "TEACHER_OBSERVATION_CREATED")
                .containsEntry("tenantId", tenantId)
                .containsEntry("observationId", observationId.toString())
                .containsEntry("classroomId", classroomId.toString());
    }

    @Test
    @DisplayName("delete - yayınlanan event payload'ı DELETED eventType içerir")
    void delete_publishesDeletedEventPayload() {
        when(observationRepository.findByIdAndTeacherId(observationId, teacherId))
                .thenReturn(Optional.of(TeacherObservation.builder()
                        .id(observationId).teacherId(teacherId).classroomId(classroomId).build()));

        observationService.delete(teacherId, observationId);

        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq(TeacherObservationService.OBSERVATION_TOPIC), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue())
                .containsEntry("eventType", "TEACHER_OBSERVATION_DELETED")
                .containsEntry("observationId", observationId.toString());
    }
}
