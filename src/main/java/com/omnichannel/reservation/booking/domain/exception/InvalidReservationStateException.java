package com.omnichannel.reservation.booking.domain.exception;

/**
 * Ngoại lệ miền được ném ra khi vi phạm quy tắc chuyển đổi trạng thái của Reservation Aggregate.
 */
public class InvalidReservationStateException extends RuntimeException {
    public InvalidReservationStateException(String message) {
        super(message);
    }
}
