package com.omnichannel.reservation.booking.domain.event;

import com.omnichannel.reservation.booking.domain.model.Reservation;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event: Phát sinh khi một đơn đặt chỗ bị hủy (CANCELLED) do khách hàng hủy hoặc thanh toán thất bại.
 * Kích hoạt Compensating Actions để giải phóng ghế trên Redis và bồi hoàn tồn kho.
 */
public record ReservationCancelledEvent(
        UUID eventId,
        Long reservationId,
        String reservationCode,
        String customerId,
        String reason,
        List<Long> inventoryItemIds,
        Instant occurredAt
) {
    public static ReservationCancelledEvent from(Reservation reservation, String reason) {
        List<Long> itemIds = reservation.getItems() != null
                ? reservation.getItems().stream()
                .map(it -> it.getInventoryItemId())
                .toList()
                : Collections.emptyList();

        return new ReservationCancelledEvent(
                UUID.randomUUID(),
                reservation.getId(),
                reservation.getReservationCode(),
                reservation.getCustomerId(),
                reason,
                itemIds,
                Instant.now()
        );
    }
}
