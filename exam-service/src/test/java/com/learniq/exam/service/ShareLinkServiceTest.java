package com.learniq.exam.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.dto.StudentDashboardResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareLinkServiceTest {

    @Mock
    private StudentDashboardService studentDashboardService;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ShareLinkService shareLinkService;

    @SuppressWarnings("unchecked")
    private void stubSnapshotQuery(ResultSet rs) {
        doAnswer(invocation -> {
            ResultSetExtractor<StudentDashboardResponse> extractor = invocation.getArgument(2);
            return extractor.extractData(rs);
        }).when(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), any(ResultSetExtractor.class));
    }

    @Test
    @DisplayName("generateShareLink: 43 karakterlik token üretir ve DB'ye yazar")
    void generateShareLink_success() throws Exception {
        UUID examId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        StudentDashboardResponse dashboard = StudentDashboardResponse.builder()
                .netScore(new BigDecimal("90.5"))
                .build();

        when(studentDashboardService.getDashboard(examId, studentId)).thenReturn(dashboard);
        when(objectMapper.writeValueAsString(dashboard)).thenReturn("{\"netScore\":90.5}");
        when(jdbcTemplate.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        String token = shareLinkService.generateShareLink(examId, studentId);

        assertNotNull(token);
        assertEquals(43, token.length());
        verify(jdbcTemplate).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    @DisplayName("generateShareLink: 30 gün geçerlilik ve doğru parametrelerle DB'ye yazar")
    void generateShareLink_persistsThirtyDayExpiry() throws Exception {
        UUID examId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        StudentDashboardResponse dashboard = StudentDashboardResponse.builder()
                .netScore(new BigDecimal("10.0"))
                .build();

        when(studentDashboardService.getDashboard(examId, studentId)).thenReturn(dashboard);
        when(objectMapper.writeValueAsString(dashboard)).thenReturn("{}");
        when(jdbcTemplate.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        shareLinkService.generateShareLink(examId, studentId);

        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        SqlParameterSource params = captor.getValue();

        assertEquals(studentId, params.getValue("studentId"));
        assertNotNull(params.getValue("token"));

        LocalDateTime createdAt = (LocalDateTime) params.getValue("createdAt");
        LocalDateTime expiresAt = (LocalDateTime) params.getValue("expiresAt");
        assertEquals(30, ChronoUnit.DAYS.between(createdAt, expiresAt));
    }

    @Test
    @DisplayName("generateShareLink: JSON serialize edilemezse RuntimeException, DB'ye yazılmaz")
    void generateShareLink_serializationFails_throws() throws Exception {
        UUID examId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        StudentDashboardResponse dashboard = StudentDashboardResponse.builder().build();

        when(studentDashboardService.getDashboard(examId, studentId)).thenReturn(dashboard);
        when(objectMapper.writeValueAsString(dashboard))
                .thenThrow(new JsonProcessingException("boom") {
                });

        assertThrows(RuntimeException.class, () -> shareLinkService.generateShareLink(examId, studentId));
        verify(jdbcTemplate, never()).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    @DisplayName("getSharedSnapshot: geçerli token snapshot'ı döner")
    void getSharedSnapshot_valid_returnsSnapshot() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getTimestamp("expires_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now().plusDays(5)));
        when(rs.getTimestamp("revoked_at")).thenReturn(null);
        when(rs.getString("snapshot")).thenReturn("{\"netScore\":88.0}");

        StudentDashboardResponse parsed = StudentDashboardResponse.builder()
                .netScore(new BigDecimal("88.0"))
                .build();
        when(objectMapper.readValue(anyString(), eq(StudentDashboardResponse.class))).thenReturn(parsed);
        stubSnapshotQuery(rs);

        StudentDashboardResponse result = shareLinkService.getSharedSnapshot("token-abc");

        assertEquals(new BigDecimal("88.0"), result.getNetScore());
    }

    @Test
    @DisplayName("getSharedSnapshot: token bulunamazsa IllegalArgumentException")
    void getSharedSnapshot_notFound_throws() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        stubSnapshotQuery(rs);

        assertThrows(IllegalArgumentException.class, () -> shareLinkService.getSharedSnapshot("missing"));
    }

    @Test
    @DisplayName("getSharedSnapshot: süresi dolmuş token reddedilir")
    void getSharedSnapshot_expired_throws() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getTimestamp("expires_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now().minusDays(1)));
        stubSnapshotQuery(rs);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> shareLinkService.getSharedSnapshot("expired"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("süresi dolmuş"));
    }

    @Test
    @DisplayName("getSharedSnapshot: iptal edilmiş (revoked) token reddedilir")
    void getSharedSnapshot_revoked_throws() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getTimestamp("expires_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now().plusDays(5)));
        when(rs.getTimestamp("revoked_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now().minusDays(1)));
        stubSnapshotQuery(rs);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> shareLinkService.getSharedSnapshot("revoked"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("iptal"));
    }

    @Test
    @DisplayName("getSharedSnapshot: hem süresi dolmuş hem iptal edilmişse önce 'süresi dolmuş' döner (kontrol sırası)")
    void getSharedSnapshot_expiredAndRevoked_prefersExpired() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getTimestamp("expires_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now().minusDays(2)));
        // Not: kod süre kontrolünü iptal kontrolünden ÖNCE yaptığı için revoked_at hiç okunmaz.
        stubSnapshotQuery(rs);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> shareLinkService.getSharedSnapshot("both"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("süresi dolmuş"));
    }

    @Test
    @DisplayName("getSharedSnapshot: snapshot JSON parse edilemezse RuntimeException")
    void getSharedSnapshot_invalidJson_throws() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getTimestamp("expires_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now().plusDays(1)));
        when(rs.getTimestamp("revoked_at")).thenReturn(null);
        when(rs.getString("snapshot")).thenReturn("{not-json}");
        when(objectMapper.readValue(anyString(), eq(StudentDashboardResponse.class)))
                .thenThrow(new JsonProcessingException("bad json") {
                });
        stubSnapshotQuery(rs);

        assertThrows(RuntimeException.class, () -> shareLinkService.getSharedSnapshot("bad"));
    }
}
