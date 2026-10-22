package com.omnichannel.reservation.domain.exception;

public class ReservationNotFoundException extends BusinessException {
    public ReservationNotFoundException(String reservationCode) {
        super("Reservation not found with code: " + reservationCode);
    }
}
