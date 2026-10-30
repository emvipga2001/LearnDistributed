package com.omnichannel.reservation.shared.idempotency;

import java.time.Instant;

/**
 * Record lưu trữ thông tin Idempotency nhằm tránh xử lý trùng lặp giao dịch.
 */
public record IdempotencyRecord(
        String key,
        String handlerName,
        IdempotencyStatus status,
        String responsePayload,
        Instant createdAt,
        Instant updatedAt
) {
    // Compact constructor để kiểm tra tính hợp lệ của dữ liệu (Validation) nếu cần
    public IdempotencyRecord {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency key không được để trống");
        }
        if (handlerName == null || handlerName.isBlank()) {
            throw new IllegalArgumentException("Handler name không được để trống");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status không được null");
        }
    }
}