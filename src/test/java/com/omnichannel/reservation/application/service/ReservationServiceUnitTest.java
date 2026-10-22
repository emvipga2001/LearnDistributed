package com.omnichannel.reservation.application.service;

import com.omnichannel.reservation.application.dto.ReservationResult;
import com.omnichannel.reservation.application.port.in.ReserveInventoryCommand;
import com.omnichannel.reservation.application.port.out.InventoryHoldPort;
import com.omnichannel.reservation.domain.exception.InventoryInsufficientException;
import com.omnichannel.reservation.domain.exception.ReservationIsHeld;
import com.omnichannel.reservation.domain.model.Channel;
import com.omnichannel.reservation.domain.model.InventoryItem;
import com.omnichannel.reservation.domain.model.Reservation;
import com.omnichannel.reservation.domain.model.ReservationStatus;
import com.omnichannel.reservation.domain.repository.InventoryRepository;
import com.omnichannel.reservation.domain.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceUnitTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private InventoryHoldPort inventoryHoldPort;

    @InjectMocks
    private ReservationService reservationService;

    private ReserveInventoryCommand sampleCommand;

    @BeforeEach
    void setUp() {
        sampleCommand = new ReserveInventoryCommand(
                "CUST-001",
                Channel.ONLINE,
                Duration.ofMinutes(10),
                List.of(
                        new ReserveInventoryCommand.ItemReservationRequest(101L, 1, BigDecimal.valueOf(100.0)),
                        new ReserveInventoryCommand.ItemReservationRequest(102L, 1, BigDecimal.valueOf(100.0))
                )
        );
    }

    @Test
    @DisplayName("TẦNG 1 THẤT BẠI: Redis báo ghế đã bị giữ -> Chặn đứng ngay, TUYỆT ĐỐI không gọi DB")
    void shouldFastFailAtRedis_WhenSeatAlreadyHeld_WithoutTouchingDatabase() {
        // GIVEN: Ghế đã có người giữ trên Redis -> tryHold trả về false
        when(inventoryHoldPort.tryHold(anyList(), eq("CUST-001"), any(Duration.class)))
                .thenReturn(false);

        // WHEN & THEN: Phải ném ngoại lệ ReservationIsHeld
        assertThatThrownBy(() -> reservationService.reserve(sampleCommand))
                .isInstanceOf(ReservationIsHeld.class);

        // XÁC NHẬN BẢO VỆ DB: Hoàn toàn KHÔNG có bất kỳ lệnh nào chọc xuống Database
        verifyNoInteractions(inventoryRepository);
        verifyNoInteractions(reservationRepository);
        verify(inventoryHoldPort, never()).releaseHold(anyList(), anyString());
    }

    @Test
    @DisplayName("CƠ CHẾ ĐỀN BÙ: Khi DB bị lỗi -> Phải tự động gọi releaseHold để nhả ghế trên Redis")
    void shouldCompensateAndReleaseRedisHold_WhenDatabaseFails() {
        // GIVEN: Tầng 1 Redis giữ thành công
        when(inventoryHoldPort.tryHold(anyList(), eq("CUST-001"), any(Duration.class)))
                .thenReturn(true);

        // Nhưng tầng 2 Database ném lỗi (ví dụ kho hết vé)
        InventoryItem item101 = new InventoryItem(101L, "SEAT-101", "Seat 101", 1, 1, 0, 0L, Instant.now(), Instant.now()); // Hết vé vì capacity=1, reserved=1
        InventoryItem item102 = new InventoryItem(102L, "SEAT-102", "Seat 102", 1, 0, 0, 0L, Instant.now(), Instant.now());
        when(inventoryRepository.findAllByIdsInOrderWithLock(anyList()))
                .thenReturn(List.of(item101, item102));

        // WHEN & THEN: Sẽ ném InventoryInsufficientException
        assertThatThrownBy(() -> reservationService.reserve(sampleCommand))
                .isInstanceOf(InventoryInsufficientException.class);

        // XÁC NHẬN ĐỀN BÙ: Phải gọi releaseHold trên Redis ngay lập tức để không giữ oan ghế!
        verify(inventoryHoldPort, times(1)).releaseHold(List.of(101L, 102L), "CUST-001");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("HAPPY PATH: Redis giữ thành công VÀ Database lưu thành công")
    void shouldReserveSuccessfully_WhenBothRedisAndDbSucceed() {
        // GIVEN: Redis giữ thành công
        when(inventoryHoldPort.tryHold(anyList(), eq("CUST-001"), any(Duration.class)))
                .thenReturn(true);

        // Database còn đủ vé
        InventoryItem item101 = new InventoryItem(101L, "SEAT-101", "Seat 101", 10, 0, 0, 0L, Instant.now(), Instant.now());
        InventoryItem item102 = new InventoryItem(102L, "SEAT-102", "Seat 102", 10, 0, 0, 0L, Instant.now(), Instant.now());
        when(inventoryRepository.findAllByIdsInOrderWithLock(anyList()))
                .thenReturn(List.of(item101, item102));

        Reservation mockSavedReservation = new Reservation(
                1L, "RES-TEST-001", Channel.ONLINE, ReservationStatus.PENDING, "CUST-001",
                Instant.now().plus(Duration.ofMinutes(10)), 0L, List.of(), Instant.now(), Instant.now()
        );
        when(reservationRepository.save(any(Reservation.class))).thenReturn(mockSavedReservation);

        // WHEN
        ReservationResult result = reservationService.reserve(sampleCommand);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.reservationCode()).isEqualTo("RES-TEST-001");
        assertThat(result.status()).isEqualTo(ReservationStatus.PENDING);

        // Kiểm tra đúng thứ tự: tryHold được gọi trước khi lưu DB
        verify(inventoryHoldPort, times(1)).tryHold(List.of(101L, 102L), "CUST-001", Duration.ofMinutes(10));
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(inventoryHoldPort, never()).releaseHold(anyList(), anyString());
    }
}
