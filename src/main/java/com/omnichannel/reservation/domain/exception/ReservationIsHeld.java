package com.omnichannel.reservation.domain.exception;

public class ReservationIsHeld extends BusinessException {
    public ReservationIsHeld() {
        super("Table is Held");
    }
}
