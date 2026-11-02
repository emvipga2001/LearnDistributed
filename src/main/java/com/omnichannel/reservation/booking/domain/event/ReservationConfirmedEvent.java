package com.omnichannel.reservation.booking.domain.event;

import com.omnichannel.reservation.booking.domain.model.Reservation;
import com.omnichannel.reservation.booking.domain.model.ReservationStatus;
import com.omnichannel.reservation.shared.domain.model.Channel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event: Phát sinh khi một đơn đặt chỗ được xác nhận thanh toán thành công (CONFIRMED).
 * Mang thông tin để Ticketing Service xuất vé và Notification Service gửi email xác nhận.
 */
public record ReservationConfirmedEvent(
        UUID eventId,
        Long reservationId,
        String reservationCode,
        String customerId,
        Channel channel,
        ReservationStatus status,
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

    public static ReservationConfirmedEvent from(Reservation reservation) {
        List<ReservationItemPayload> itemPayloads = reservation.getItems() != null
                ? reservation.getItems().stream()
                .map(it -> new ReservationItemPayload(it.getId(), it.getInventoryItemId(), it.getQuantity(), it.getUnitPrice()))
                .toList()
                : Collections.emptyList();

        List<ReservationMerchandisePayload> merchPayloads = reservation.getMerchandises() != null
                ? reservation.getMerchandises().stream()
                .map(m -> new ReservationMerchandisePayload(m.getMerchandiseId(), m.getQuantity(), m.getUnitPrice()))
                .toList()
                : Collections.emptyList();

        return new ReservationConfirmedEvent(
                UUID.randomUUID(),
                reservation.getId(),
                reservation.getReservationCode(),
                reservation.getCustomerId(),
                reservation.getChannel(),
                reservation.getStatus(),
                itemPayloads,
                merchPayloads,
                Instant.now()
        );
    }
}
