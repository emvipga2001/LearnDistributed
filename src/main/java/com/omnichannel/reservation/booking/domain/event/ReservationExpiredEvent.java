package com.omnichannel.reservation.booking.domain.event;

import com.omnichannel.reservation.booking.domain.model.Reservation;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event: Phát sinh khi một đơn đặt chỗ bị quá hạn thanh toán (EXPIRED - sau 10 phút giữ chỗ).
 * Kích hoạt dọn dẹp khóa trên Redis và đưa ghế trở lại kho vé cho cộng đồng.
 */
public record ReservationExpiredEvent(
        UUID eventId,
        Long reservationId,
        String reservationCode,
        String customerId,
        List<Long> inventoryItemIds,
        Instant occurredAt
) {
    public static ReservationExpiredEvent from(Reservation reservation) {
        List<Long> itemIds = reservation.getItems() != null
                ? reservation.getItems().stream()
                .map(it -> it.getInventoryItemId())
                .toList()
                : Collections.emptyList();

        return new ReservationExpiredEvent(
                UUID.randomUUID(),
                reservation.getId(),
                reservation.getReservationCode(),
                reservation.getCustomerId(),
                itemIds,
                Instant.now()
        );
    }
}
