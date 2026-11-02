package com.omnichannel.reservation.booking.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omnichannel.reservation.booking.domain.exception.InvalidReservationStateException;
import com.omnichannel.reservation.booking.domain.exception.ReservationNotFoundException;
import com.omnichannel.reservation.booking.domain.model.Reservation;
import com.omnichannel.reservation.booking.domain.model.ReservationItem;
import com.omnichannel.reservation.booking.domain.model.ReservationStatus;
import com.omnichannel.reservation.booking.domain.repository.ReservationRepository;
import com.omnichannel.reservation.inventory.application.port.out.InventoryHoldPort;
import com.omnichannel.reservation.outbox.application.model.OutboxEvent;
import com.omnichannel.reservation.outbox.application.port.out.OutboxEventPort;
import com.omnichannel.reservation.shared.domain.model.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingSagaCoordinatorTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private InventoryHoldPort inventoryHoldPort;

    @Mock
    private OutboxEventPort outboxEventPort;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private BookingSagaCoordinator sagaCoordinator;

    private static final String CODE = "RES-SUCCESS-123";
    private static final String TXN_ID = "TXN-998877";

    private Reservation createPendingReservation(Instant expiresAt) {
        ReservationItem item = new ReservationItem(1L, 101L, 1, BigDecimal.valueOf(500000), Instant.now());
        return new Reservation(
                1L,
                CODE,
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
    @DisplayName("handlePaymentSuccess: Chuyển trạng thái sang CONFIRMED và ghi Outbox event thành công")
    void shouldConfirmReservationAndSaveOutbox_WhenPaymentSucceeds() {
        Reservation reservation = createPendingReservation(Instant.now().plusSeconds(600));
        when(reservationRepository.findByReservationCode(CODE)).thenReturn(Optional.of(reservation));

        sagaCoordinator.handlePaymentSuccess(CODE, TXN_ID);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository).save(reservation);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEventPort.class != null ? OutboxEvent.class : null);
        verify(outboxEventPort).save(outboxCaptor.capture());

        OutboxEvent savedEvent = outboxCaptor.getValue();
        assertThat(savedEvent.getEventType()).isEqualTo("ReservationConfirmedEvent");
        assertThat(savedEvent.getAggregateId()).isEqualTo("1");
        assertThat(savedEvent.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("handlePaymentSuccess: Bỏ qua khi đơn đã CONFIRMED từ trước (Idempotent Webhook Retry)")
    void shouldSkipProcessing_WhenReservationAlreadyConfirmed() {
        Reservation reservation = createPendingReservation(Instant.now().plusSeconds(600));
        reservation.confirm();
        when(reservationRepository.findByReservationCode(CODE)).thenReturn(Optional.of(reservation));

        sagaCoordinator.handlePaymentSuccess(CODE, TXN_ID);

        verify(reservationRepository, never()).save(any());
        verify(outboxEventPort, never()).save(any());
    }

    @Test
    @DisplayName("handlePaymentSuccess: Ném InvalidReservationStateException khi đơn đã quá hạn 10 phút")
    void shouldThrowException_WhenPaymentSuccessArrivesForExpiredReservation() {
        Reservation reservation = createPendingReservation(Instant.now().minusSeconds(10));
        when(reservationRepository.findByReservationCode(CODE)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> sagaCoordinator.handlePaymentSuccess(CODE, TXN_ID))
                .isInstanceOf(InvalidReservationStateException.class)
                .hasMessageContaining("Cannot confirm an expired reservation");

        verify(reservationRepository, never()).save(any());
        verify(outboxEventPort, never()).save(any());
    }

    @Test
    @DisplayName("handlePaymentFailed: Chuyển CANCELLED, kích hoạt Compensating Action nhả ghế Redis và ghi Outbox")
    void shouldCancelReservation_AndReleaseRedisHold_AndSaveOutbox_WhenPaymentFails() {
        Reservation reservation = createPendingReservation(Instant.now().plusSeconds(600));
        when(reservationRepository.findByReservationCode(CODE)).thenReturn(Optional.of(reservation));

        sagaCoordinator.handlePaymentFailed(CODE, "Card declined: Insufficient funds");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(reservationRepository).save(reservation);

        // Kiểm chứng Bồi hoàn trên Redis (Compensating Action)
        verify(inventoryHoldPort).releaseHold(eq(List.of(101L)), eq("CUST-001"));

        // Kiểm chứng lưu Outbox Event
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventPort).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("ReservationCancelledEvent");
        assertThat(outboxCaptor.getValue().getPayload()).contains("Card declined");
    }

    @Test
    @DisplayName("handlePaymentFailed: Bỏ qua khi đơn đã CANCELLED từ trước (Idempotent Webhook Retry)")
    void shouldSkipProcessing_WhenReservationAlreadyCancelled() {
        Reservation reservation = createPendingReservation(Instant.now().plusSeconds(600));
        reservation.cancel();
        when(reservationRepository.findByReservationCode(CODE)).thenReturn(Optional.of(reservation));

        sagaCoordinator.handlePaymentFailed(CODE, "Timeout");

        verify(reservationRepository, never()).save(any());
        verifyNoInteractions(inventoryHoldPort);
        verifyNoInteractions(outboxEventPort);
    }

    @Test
    @DisplayName("handlePaymentFailed: Ném ReservationNotFoundException khi mã code không tồn tại")
    void shouldThrowNotFound_WhenReservationCodeNotFound() {
        when(reservationRepository.findByReservationCode("INVALID-CODE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sagaCoordinator.handlePaymentFailed("INVALID-CODE", "Timeout"))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessageContaining("Không tìm thấy mã đặt chỗ");

        verifyNoInteractions(inventoryHoldPort);
        verifyNoInteractions(outboxEventPort);
    }
}
