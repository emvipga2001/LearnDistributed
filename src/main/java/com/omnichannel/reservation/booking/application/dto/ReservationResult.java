package com.omnichannel.reservation.booking.application.dto;

import com.omnichannel.reservation.booking.domain.model.ReservationStatus;
import com.omnichannel.reservation.shared.domain.model.Channel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record ReservationResult(
        Long reservationId,
        String reservationCode,
        Channel channel,
        ReservationStatus status,
        String customerId,
        Instant expiresAt,
        List<ReservationItemResult> items,
        List<ReservationMerchandiseResult> merchandises
) {
    public ReservationResult(Long reservationId, String reservationCode, Channel channel,
                             ReservationStatus status, String customerId, Instant expiresAt,
                             List<ReservationItemResult> items) {
        this(reservationId, reservationCode, channel, status, customerId, expiresAt, items, Collections.emptyList());
    }

    public record ReservationItemResult(
            Long itemId,
            Long inventoryItemId,
            int quantity
    ) {}

    public record ReservationMerchandiseResult(
            Long id,
            Long merchandiseId,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
