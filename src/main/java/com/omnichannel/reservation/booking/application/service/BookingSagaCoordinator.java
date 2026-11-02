package com.omnichannel.reservation.booking.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.reservation.booking.application.port.in.ProcessPaymentResultUseCase;
import com.omnichannel.reservation.booking.domain.event.ReservationCancelledEvent;
import com.omnichannel.reservation.booking.domain.event.ReservationConfirmedEvent;
import com.omnichannel.reservation.booking.domain.exception.ReservationNotFoundException;
import com.omnichannel.reservation.booking.domain.model.Reservation;
import com.omnichannel.reservation.booking.domain.model.ReservationItem;
import com.omnichannel.reservation.booking.domain.model.ReservationStatus;
import com.omnichannel.reservation.booking.domain.repository.ReservationRepository;
import com.omnichannel.reservation.inventory.application.port.out.InventoryHoldPort;
import com.omnichannel.reservation.outbox.application.model.OutboxEvent;
import com.omnichannel.reservation.outbox.application.port.out.OutboxEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingSagaCoordinator implements ProcessPaymentResultUseCase {
    private final ReservationRepository reservationRepository;
    private final InventoryHoldPort inventoryHoldPort;
    private final OutboxEventPort outboxEventPort;
    private final ObjectMapper objectMapper;


    @Transactional // 1. Mở transaction với PostgreSQL
    public void handlePaymentSuccess(String reservationCode, String paymentTransactionId) {
        // Bước 1: Nạp đơn đặt chỗ từ Database lên
        Reservation reservation = reservationRepository.findByReservationCode(reservationCode)
                .orElseThrow(() -> new ReservationNotFoundException("Không tìm thấy mã đặt chỗ"));

        // Bước 2: Kiểm tra Idempotency - Nếu đơn đã CONFIRMED từ trước (do webhook gửi trùng) -> Thoát ngay an toàn!
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            log.info("Đơn hàng {} đã được xác nhận trước đó, bỏ qua xử lý lặp.", reservationCode);
            return;
        }

        // Bước 3: Đổi trạng thái Entity sang CONFIRMED (bảo vệ bởi State Machine)
        reservation.confirm();
        reservationRepository.save(reservation);

        // Bước 4: Ghi Domain Event vào Outbox để OutboxDispatcher bắn sang Ticketing Service xuất vé
        ReservationConfirmedEvent event = ReservationConfirmedEvent.from(reservation);

        String eventJson;
        try {
            eventJson = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize ReservationCreatedEvent to JSON", ex);
            throw new IllegalStateException("Failed to serialize Outbox event payload", ex);
        }

        outboxEventPort.save(new OutboxEvent(
                event.eventId(),
                "Reservation",
                reservation.getId().toString(),
                "ReservationConfirmedEvent",
                eventJson,
                "PENDING",
                0,
                Instant.now(),
                null
        ));

    }
// Kết thúc hàm: PostgreSQL commit cả 2 việc: Đơn đổi sang CONFIRMED + Outbox có sự kiện xuất vé!

    @Transactional // 1. Mở transaction với PostgreSQL
    public void handlePaymentFailed(String reservationCode, String reason) {
        // Bước 1: Tìm đơn
        Reservation reservation = reservationRepository.findByReservationCode(reservationCode)
                .orElseThrow(() -> new ReservationNotFoundException("Không tìm thấy mã đặt chỗ"));

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            log.info("Đơn hàng {} đã được hủy trước đó, bỏ qua xử lý lặp.", reservationCode);
            return;
        }

        // Bước 2: Đổi trạng thái sang CANCELLED
        reservation.cancel();
        reservationRepository.save(reservation);

        // Bước 3: KÍCH HOẠT HÀNH ĐỘNG BỒI HOÀN (COMPENSATING ACTION TRÊN REDIS)
        // Lấy danh sách ID của các ghế và nhả khóa an toàn bằng Safe Release Lua Script!
        List<Long> itemIds = reservation.getItems().stream()
                .map(ReservationItem::getInventoryItemId)
                .toList();
        inventoryHoldPort.releaseHold(itemIds, reservation.getCustomerId());

        // Bước 4: Ghi Outbox sự kiện ReservationCancelledEvent
        ReservationCancelledEvent event = ReservationCancelledEvent.from(reservation, reason);
        String eventJson;
        try {
            eventJson = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize ReservationCreatedEvent to JSON", ex);
            throw new IllegalStateException("Failed to serialize Outbox event payload", ex);
        }
        outboxEventPort.save(new OutboxEvent(
                event.eventId(),
                "Reservation",
                reservation.getId().toString(),
                "ReservationCancelledEvent",
                eventJson,
                "PENDING",
                0,
                Instant.now(),
                null
        ));
    }
}
