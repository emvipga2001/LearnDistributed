package com.omnichannel.reservation.booking.domain.model;

import com.omnichannel.reservation.merchandise.domain.model.ReservationMerchandise;
import com.omnichannel.reservation.shared.domain.model.Channel;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Getter
public class Reservation {
    private final Long id;
    private final String reservationCode;
    private final Channel channel;
    private ReservationStatus status;
    private final String customerId;
    private final Instant expiresAt;
    private final Long version;
    private final List<ReservationItem> items;
    private final List<ReservationMerchandise> merchandises;
    private final Instant createdAt;
    private Instant updatedAt;

    public Reservation(Long id, String reservationCode, Channel channel, ReservationStatus status,
                       String customerId, Instant expiresAt, Long version,
                       List<ReservationItem> items, List<ReservationMerchandise> merchandises,
                       Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.reservationCode = reservationCode;
        this.channel = channel;
        this.status = status;
        this.customerId = customerId;
        this.expiresAt = expiresAt;
        this.version = version;
        this.items = items != null ? items : Collections.emptyList();
        this.merchandises = merchandises != null ? merchandises : Collections.emptyList();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public Reservation(Long id, String reservationCode, Channel channel, ReservationStatus status,
                       String customerId, Instant expiresAt, Long version,
                       List<ReservationItem> items, Instant createdAt, Instant updatedAt) {
        this(id, reservationCode, channel, status, customerId, expiresAt, version, items, Collections.emptyList(), createdAt, updatedAt);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void confirm() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("Cannot confirm reservation in status: " + this.status);
        }
        if (isExpired()) {
            throw new IllegalStateException("Cannot confirm an expired reservation");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (this.status == ReservationStatus.CANCELLED) {
            return;
        }
        this.status = ReservationStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
}
