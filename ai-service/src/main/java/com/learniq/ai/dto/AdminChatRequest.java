package com.learniq.ai.dto;

import java.util.UUID;

/**
 * POST /api/v1/admin/ai/chat istek gövdesi.
 *
 * Sınıf/ders bağımlı sorular süzülerek semantik retrieval'a önceden filtreleme
 * olarak verilir; alanlar opsiyoneldir (tüm kiracıda genel sorular desteklenir).
 */
public record AdminChatRequest(String message, UUID classroomId, UUID subjectId, UUID examId) {
}
