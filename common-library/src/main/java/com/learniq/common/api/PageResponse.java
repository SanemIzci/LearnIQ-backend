package com.learniq.common.api;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Shared pagination envelope for list endpoints.
 *
 * Contract: {content, totalElements, page, size, totalPages}.
 */
public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int page,
        int size,
        int totalPages
) {

    public static <T> PageResponse<T> of(List<T> content, long totalElements, int page, int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / (double) size) : 0;
        return new PageResponse<>(content, totalElements, page, size, totalPages);
    }

    public static <R> PageResponse<R> from(Page<R> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize(), page.getTotalPages());
    }
}
