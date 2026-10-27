package com.omnichannel.reservation.booking.domain.exception;

import com.omnichannel.reservation.booking.domain.model.Reservation;
import com.omnichannel.reservation.shared.domain.exception.BusinessException;

public class ReservationNotFoundException extends BusinessException {
    public ReservationNotFoundException(String reservationCode) {
        super("Reservation not found with code: " + reservationCode);
    }
}
