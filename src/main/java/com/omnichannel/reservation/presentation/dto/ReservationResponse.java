package com.omnichannel.reservation.presentation.dto;

import com.omnichannel.reservation.domain.model.Channel;
import com.omnichannel.reservation.domain.model.ReservationStatus;

import java.time.Instant;
import java.util.List;

public record ReservationResponse(
        Long reservationId,
        String reservationCode,
        Channel channel,
        ReservationStatus status,
        String customerId,
        Instant expiresAt,
        List<ItemResponse> items
) {
    public record ItemResponse(
            Long itemId,
            Long inventoryItemId,
            int quantity
    ) {}
}
