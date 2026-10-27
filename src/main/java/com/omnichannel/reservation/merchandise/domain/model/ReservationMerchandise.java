package com.omnichannel.reservation.merchandise.domain.model;

import com.omnichannel.reservation.booking.domain.model.Reservation;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain Entity: Chi tiết một dòng sản phẩm phụ kiện (Banner/Lightstick) gắn kèm Reservation.
 */
@Getter
public class ReservationMerchandise {
    private final Long id;
    private final Long merchandiseId;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final Instant createdAt;

    public ReservationMerchandise(Long id, Long merchandiseId, int quantity, BigDecimal unitPrice, Instant createdAt) {
        this.id = id;
        this.merchandiseId = merchandiseId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
