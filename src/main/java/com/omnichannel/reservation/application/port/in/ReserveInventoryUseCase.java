package com.omnichannel.reservation.application.port.in;

import com.omnichannel.reservation.application.dto.ReservationResult;

public interface ReserveInventoryUseCase {
    ReservationResult reserve(ReserveInventoryCommand command);
}
