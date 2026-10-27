package com.omnichannel.reservation.booking.domain.model;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
public class ReservationItem {
    private final Long id;
    private final Long inventoryItemId;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final Instant createdAt;

    public ReservationItem(Long id, Long inventoryItemId, int quantity, BigDecimal unitPrice, Instant createdAt) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be strictly positive");
        }
        this.id = id;
        this.inventoryItemId = inventoryItemId;
        this.quantity = quantity;
        this.unitPrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
