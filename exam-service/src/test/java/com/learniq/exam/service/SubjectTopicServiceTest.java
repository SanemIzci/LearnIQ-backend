package com.learniq.exam.service;

import com.learniq.exam.domain.Subject;
import com.learniq.exam.domain.SubjectTopic;
import com.learniq.exam.dto.CreateSubjectTopicRequest;
import com.learniq.exam.dto.SubjectTopicResponse;
import com.learniq.exam.dto.UpdateSubjectTopicRequest;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.SubjectRepository;
import com.learniq.exam.repository.SubjectTopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubjectTopicService Unit Tests")
class SubjectTopicServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private SubjectTopicRepository subjectTopicRepository;

    @InjectMocks
    private SubjectTopicService subjectTopicService;

    private UUID subjectId;
    private UUID topicId;
    private Subject subject;
    private SubjectTopic topic;

    @BeforeEach
    void setUp() {
        subjectId = UUID.randomUUID();
        topicId = UUID.randomUUID();

        subject = Subject.builder()
                .id(subjectId)
                .name("Matematik")
                .createdAt(LocalDateTime.now())
                .build();

        topic = SubjectTopic.builder()
                .id(topicId)
                .subject(subject)
                .name("Türev")
                .code("MAT-TUR")
                .orderIndex(1)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("listTopicsBySubject should throw EntityNotFoundException when subject does not exist")
    void listTopicsBySubject_WhenSubjectNotFound_ThrowsException() {
        when(subjectRepository.existsById(subjectId)).thenReturn(false);

        assertThatThrownBy(() -> subjectTopicService.listTopicsBySubject(subjectId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Subject not found");

        verify(subjectTopicRepository, never()).findBySubjectIdOrderByOrderIndexAscNameAsc(any());
    }

    @Test
    @DisplayName("listTopicsBySubject should return mapped topics when subject exists")
    void listTopicsBySubject_WhenSubjectExists_ReturnsTopics() {
        when(subjectRepository.existsById(subjectId)).thenReturn(true);
        when(subjectTopicRepository.findBySubjectIdOrderByOrderIndexAscNameAsc(subjectId))
                .thenReturn(List.of(topic));

        List<SubjectTopicResponse> result = subjectTopicService.listTopicsBySubject(subjectId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Türev");
        assertThat(result.get(0).getCode()).isEqualTo("MAT-TUR");
        assertThat(result.get(0).getSubjectId()).isEqualTo(subjectId);
    }

    @Test
    @DisplayName("getTopic should return topic when found")
    void getTopic_WhenFound_ReturnsTopic() {
        when(subjectTopicRepository.findByIdAndSubjectId(topicId, subjectId)).thenReturn(Optional.of(topic));

        SubjectTopicResponse response = subjectTopicService.getTopic(subjectId, topicId);

        assertThat(response.getId()).isEqualTo(topicId);
        assertThat(response.getName()).isEqualTo("Türev");
    }

    @Test
    @DisplayName("getTopic should throw EntityNotFoundException when not found")
    void getTopic_WhenNotFound_ThrowsException() {
        when(subjectTopicRepository.findByIdAndSubjectId(topicId, subjectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectTopicService.getTopic(subjectId, topicId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("createTopic should throw DataIntegrityViolationException when topic name already exists")
    void createTopic_WhenDuplicateName_ThrowsException() {
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
        when(subjectTopicRepository.existsBySubjectIdAndNameIgnoreCase(subjectId, "Türev")).thenReturn(true);

        CreateSubjectTopicRequest request = CreateSubjectTopicRequest.builder()
                .name("Türev")
                .code("MAT-TUR")
                .build();

        assertThatThrownBy(() -> subjectTopicService.createTopic(subjectId, request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("already exists for this subject");

        verify(subjectTopicRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTopic should save and return topic when valid")
    void createTopic_WhenValid_SavesAndReturns() {
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
        when(subjectTopicRepository.existsBySubjectIdAndNameIgnoreCase(subjectId, "İntegral")).thenReturn(false);
        when(subjectTopicRepository.save(any(SubjectTopic.class))).thenAnswer(invocation -> {
            SubjectTopic saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        CreateSubjectTopicRequest request = CreateSubjectTopicRequest.builder()
                .name("İntegral")
                .code("MAT-INT")
                .orderIndex(2)
                .build();

        SubjectTopicResponse response = subjectTopicService.createTopic(subjectId, request);

        assertThat(response.getName()).isEqualTo("İntegral");
        assertThat(response.getCode()).isEqualTo("MAT-INT");
        assertThat(response.getOrderIndex()).isEqualTo(2);
        assertThat(response.getSubjectId()).isEqualTo(subjectId);
    }

    @Test
    @DisplayName("updateTopic should throw DataIntegrityViolationException when updated name already taken by another topic")
    void updateTopic_WhenDuplicateName_ThrowsException() {
        when(subjectTopicRepository.findByIdAndSubjectId(topicId, subjectId)).thenReturn(Optional.of(topic));
        when(subjectTopicRepository.existsBySubjectIdAndNameIgnoreCaseAndIdNot(subjectId, "İntegral", topicId))
                .thenReturn(true);

        UpdateSubjectTopicRequest request = UpdateSubjectTopicRequest.builder()
                .name("İntegral")
                .build();

        assertThatThrownBy(() -> subjectTopicService.updateTopic(subjectId, topicId, request))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(subjectTopicRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTopic should update and return topic when valid")
    void updateTopic_WhenValid_UpdatesAndReturns() {
        when(subjectTopicRepository.findByIdAndSubjectId(topicId, subjectId)).thenReturn(Optional.of(topic));
        when(subjectTopicRepository.existsBySubjectIdAndNameIgnoreCaseAndIdNot(subjectId, "Türev 2", topicId))
                .thenReturn(false);
        when(subjectTopicRepository.save(any(SubjectTopic.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSubjectTopicRequest request = UpdateSubjectTopicRequest.builder()
                .name("Türev 2")
                .code("MAT-TUR2")
                .orderIndex(3)
                .build();

        SubjectTopicResponse response = subjectTopicService.updateTopic(subjectId, topicId, request);

        assertThat(response.getName()).isEqualTo("Türev 2");
        assertThat(response.getCode()).isEqualTo("MAT-TUR2");
        assertThat(response.getOrderIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("deleteTopic should delete topic when found")
    void deleteTopic_WhenFound_DeletesTopic() {
        when(subjectTopicRepository.findByIdAndSubjectId(topicId, subjectId)).thenReturn(Optional.of(topic));

        subjectTopicService.deleteTopic(subjectId, topicId);

        verify(subjectTopicRepository, times(1)).delete(topic);
    }

    @Test
    @DisplayName("deleteTopic should throw EntityNotFoundException when not found")
    void deleteTopic_WhenNotFound_ThrowsException() {
        when(subjectTopicRepository.findByIdAndSubjectId(topicId, subjectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectTopicService.deleteTopic(subjectId, topicId))
                .isInstanceOf(EntityNotFoundException.class);

        verify(subjectTopicRepository, never()).delete(any());
    }
}
