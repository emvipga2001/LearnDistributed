package com.omnichannel.reservation.booking.domain.model;

import com.omnichannel.reservation.booking.domain.event.ReservationCancelledEvent;
import com.omnichannel.reservation.booking.domain.event.ReservationConfirmedEvent;
import com.omnichannel.reservation.booking.domain.event.ReservationExpiredEvent;
import com.omnichannel.reservation.booking.domain.exception.InvalidReservationStateException;
import com.omnichannel.reservation.shared.domain.model.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private Reservation createPendingReservation(Instant expiresAt) {
        ReservationItem item = new ReservationItem(1L, 101L, 1, BigDecimal.valueOf(500000), Instant.now());
        return new Reservation(
                1L,
                "RES-12345",
                Channel.ONLINE,
                ReservationStatus.PENDING,
                "CUST-001",
                expiresAt,
                0L,
                List.of(item),
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("confirm: Thành công khi đơn đang ở PENDING và chưa hết hạn")
    void shouldConfirmSuccessfully_WhenPendingAndNotExpired() {
        Reservation reservation = createPendingReservation(Instant.now().plusSeconds(600));

        reservation.confirm();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        ReservationConfirmedEvent event = ReservationConfirmedEvent.from(reservation);
        assertThat(event.reservationId()).isEqualTo(1L);
        assertThat(event.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(event.items()).hasSize(1);
    }

    @Test
    @DisplayName("confirm: Ném InvalidReservationStateException khi đơn đã quá hạn expiresAt")
    void shouldThrowException_WhenConfirmingExpiredReservation() {
        Reservation reservation = createPendingReservation(Instant.now().minusSeconds(10));

        assertThatThrownBy(reservation::confirm)
                .isInstanceOf(InvalidReservationStateException.class)
                .hasMessageContaining("Cannot confirm an expired reservation");
    }

    @Test
    @DisplayName("cancel: Thành công chuyển sang CANCELLED khi đang PENDING")
    void shouldCancelSuccessfully_WhenPending() {
        Reservation reservation = createPendingReservation(Instant.now().plusSeconds(600));

        reservation.cancel();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);

        ReservationCancelledEvent event = ReservationCancelledEvent.from(reservation, "Payment timeout");
        assertThat(event.reason()).isEqualTo("Payment timeout");
        assertThat(event.inventoryItemIds()).containsExactly(101L);
    }

    @Test
    @DisplayName("cancel: Ném InvalidReservationStateException khi cố tình hủy đơn đã CONFIRMED")
    void shouldThrowException_WhenCancellingConfirmedReservation() {
        Reservation reservation = createPendingReservation(Instant.now().plusSeconds(600));
        reservation.confirm();

        assertThatThrownBy(reservation::cancel)
                .isInstanceOf(InvalidReservationStateException.class)
                .hasMessageContaining("Cannot cancel an already confirmed reservation");
    }

    @Test
    @DisplayName("expire: Thành công chuyển sang EXPIRED khi đang PENDING")
    void shouldExpireSuccessfully_WhenPending() {
        Reservation reservation = createPendingReservation(Instant.now().minusSeconds(10));

        reservation.expire();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        ReservationExpiredEvent event = ReservationExpiredEvent.from(reservation);
        assertThat(event.inventoryItemIds()).containsExactly(101L);
    }

    @Test
    @DisplayName("expire: Ném InvalidReservationStateException khi cố tình expire đơn đã CONFIRMED")
    void shouldThrowException_WhenExpiringConfirmedReservation() {
        Reservation reservation = createPendingReservation(Instant.now().plusSeconds(600));
        reservation.confirm();

        assertThatThrownBy(reservation::expire)
                .isInstanceOf(InvalidReservationStateException.class)
                .hasMessageContaining("Cannot expire an already confirmed reservation");
    }
}
