package com.omnichannel.reservation.inventory.domain.exception;

import com.omnichannel.reservation.shared.domain.exception.BusinessException;

public class ReservationIsHeld extends BusinessException {
    public ReservationIsHeld() {
        super("Table is Held");
    }
}
