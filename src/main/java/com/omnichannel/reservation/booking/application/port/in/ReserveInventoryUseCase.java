package com.omnichannel.reservation.booking.application.port.in;

import com.omnichannel.reservation.booking.application.dto.ReservationResult;

public interface ReserveInventoryUseCase {
    ReservationResult reserve(ReserveInventoryCommand command);
}
