package com.omnichannel.reservation.booking.domain.event;

import com.omnichannel.reservation.booking.domain.model.Reservation;

import com.omnichannel.reservation.shared.domain.model.Channel;
import com.omnichannel.reservation.booking.domain.model.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event: Phát sinh khi một đơn giữ chỗ (Reservation) được tạo thành công trong hệ thống.
 * Chứa đầy đủ ngữ cảnh (Event-Carried State Transfer) để các service vệ tinh (Payment, Notification, Warehouse/Fulfillment) tiêu thụ.
 */
public record ReservationCreatedEvent(
        UUID eventId,
        Long reservationId,
        String reservationCode,
        String customerId,
        Channel channel,
        ReservationStatus status,
        Instant expiresAt,
        List<ReservationItemPayload> items,
        List<ReservationMerchandisePayload> merchandises,
        Instant occurredAt
) {
    public record ReservationItemPayload(
            Long itemId,
            Long inventoryItemId,
            int quantity,
            BigDecimal unitPrice
    ) {}

    public record ReservationMerchandisePayload(
            Long merchandiseId,
            int quantity,
            BigDecimal unitPrice
    ) {}

    public static ReservationCreatedEvent of(
            Long reservationId,
            String reservationCode,
            String customerId,
            Channel channel,
            ReservationStatus status,
            Instant expiresAt,
            List<ReservationItemPayload> items
    ) {
        return of(reservationId, reservationCode, customerId, channel, status, expiresAt, items, Collections.emptyList());
    }

    public static ReservationCreatedEvent of(
            Long reservationId,
            String reservationCode,
            String customerId,
            Channel channel,
            ReservationStatus status,
            Instant expiresAt,
            List<ReservationItemPayload> items,
            List<ReservationMerchandisePayload> merchandises
    ) {
        return new ReservationCreatedEvent(
                UUID.randomUUID(),
                reservationId,
                reservationCode,
                customerId,
                channel,
                status,
                expiresAt,
                items,
                merchandises != null ? merchandises : Collections.emptyList(),
                Instant.now()
        );
    }
}
