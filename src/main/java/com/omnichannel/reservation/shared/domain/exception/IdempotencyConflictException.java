package com.omnichannel.reservation.shared.domain.exception;

public class IdempotencyConflictException extends BusinessException {
    public IdempotencyConflictException() {
        super("Yêu cầu đang được xử lý hoặc đã hoàn tất, vui lòng không thử lại.");
    }
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
