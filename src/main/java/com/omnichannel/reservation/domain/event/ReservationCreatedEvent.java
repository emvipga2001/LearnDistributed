package com.omnichannel.reservation.domain.event;

import com.omnichannel.reservation.domain.model.Channel;
import com.omnichannel.reservation.domain.model.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event: Phát sinh khi một đơn giữ chỗ (Reservation) được tạo thành công trong hệ thống.
 * Chứa đầy đủ ngữ cảnh (Event-Carried State Transfer) để các service vệ tinh (Payment, Notification) tiêu thụ.
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
        Instant occurredAt
) {
    public record ReservationItemPayload(
            Long itemId,
            Long inventoryItemId,
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
        return new ReservationCreatedEvent(
                UUID.randomUUID(),
                reservationId,
                reservationCode,
                customerId,
                channel,
                status,
                expiresAt,
                items,
                Instant.now()
        );
    }
}
