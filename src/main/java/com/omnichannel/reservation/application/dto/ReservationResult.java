package com.omnichannel.reservation.application.dto;

import com.omnichannel.reservation.domain.model.Channel;
import com.omnichannel.reservation.domain.model.ReservationStatus;

import java.time.Instant;
import java.util.List;

public record ReservationResult(
        Long reservationId,
        String reservationCode,
        Channel channel,
        ReservationStatus status,
        String customerId,
        Instant expiresAt,
        List<ReservationItemResult> items
) {
    public record ReservationItemResult(
            Long itemId,
            Long inventoryItemId,
            int quantity
    ) {}
}
