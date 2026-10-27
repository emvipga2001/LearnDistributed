package com.omnichannel.reservation.booking.presentation.dto;

import com.omnichannel.reservation.booking.domain.model.ReservationStatus;
import com.omnichannel.reservation.shared.domain.model.Channel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record ReservationResponse(
        Long reservationId,
        String reservationCode,
        Channel channel,
        ReservationStatus status,
        String customerId,
        Instant expiresAt,
        List<ItemResponse> items,
        List<MerchandiseResponse> merchandises
) {
    public ReservationResponse(Long reservationId, String reservationCode, Channel channel,
                               ReservationStatus status, String customerId, Instant expiresAt,
                               List<ItemResponse> items) {
        this(reservationId, reservationCode, channel, status, customerId, expiresAt, items, Collections.emptyList());
    }

    public record ItemResponse(
            Long itemId,
            Long inventoryItemId,
            int quantity
    ) {}

    public record MerchandiseResponse(
            Long id,
            Long merchandiseId,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
