package com.omnichannel.reservation.shared.domain.exception;

/**
 * Ngoại lệ ném ra khi Client sử dụng lại một Idempotency-Key đã tồn tại
 * nhưng gửi kèm Payload / Request Body có nội dung khác với yêu cầu ban đầu.
 */
public class IdempotencyPayloadMismatchException extends RuntimeException {
    public IdempotencyPayloadMismatchException(String message) {
        super(message);
    }
}
