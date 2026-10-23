package com.omnichannel.reservation.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omnichannel.reservation.application.dto.ReservationResult;
import com.omnichannel.reservation.application.model.OutboxEvent;
import com.omnichannel.reservation.application.port.in.ReserveInventoryCommand;
import com.omnichannel.reservation.application.port.out.InventoryHoldPort;
import com.omnichannel.reservation.application.port.out.InventoryStockCachePort;
import com.omnichannel.reservation.application.port.out.OutboxEventPort;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    @Mock
    private InventoryStockCachePort inventoryStockCachePort;

    @Mock
    private OutboxEventPort outboxEventPort;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private ReservationService reservationService;

    private ReserveInventoryCommand seatOnlyCommand;
    private ReserveInventoryCommand hybridCommand;

    @BeforeEach
    void setUp() {
        seatOnlyCommand = new ReserveInventoryCommand(
                "CUST-001",
                Channel.ONLINE,
                Duration.ofMinutes(10),
                List.of(
                        new ReserveInventoryCommand.ItemReservationRequest(101L, 1, BigDecimal.valueOf(100.0), true),
                        new ReserveInventoryCommand.ItemReservationRequest(102L, 1, BigDecimal.valueOf(100.0), true)
                )
        );

        hybridCommand = new ReserveInventoryCommand(
                "CUST-HYBRID",
                Channel.ONLINE,
                Duration.ofMinutes(10),
                List.of(
                        new ReserveInventoryCommand.ItemReservationRequest(101L, 1, BigDecimal.valueOf(100.0), true),  // VIP Seat
                        new ReserveInventoryCommand.ItemReservationRequest(201L, 2, BigDecimal.valueOf(50.0), false)   // General Standing
                )
        );
    }

    @Test
    @DisplayName("Đặt ghế: Redis Hold từ chối -> Ném lỗi ReservationIsHeld, DB không bị đụng đến")
    void shouldRejectFast_WhenRedisHoldFails() {
        when(inventoryHoldPort.tryHold(anyList(), anyString(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> reservationService.reserve(seatOnlyCommand))
                .isInstanceOf(ReservationIsHeld.class);

        verify(inventoryRepository, never()).findAllByIdsInOrderWithLock(any());
        verify(reservationRepository, never()).save(any());
        verify(outboxEventPort, never()).save(any());
    }

    @Test
    @DisplayName("Đơn kết hợp Hybrid: Hold ghế VIP thành công, nhưng hết vé đứng trên Redis -> Bồi hoàn nhả ghế VIP")
    void shouldReleaseHeldSeats_WhenStockDeductionFails() {
        when(inventoryHoldPort.tryHold(anyList(), anyString(), any(Duration.class))).thenReturn(true);
        when(inventoryStockCachePort.tryDeductStock(anyMap())).thenReturn(false);

        assertThatThrownBy(() -> reservationService.reserve(hybridCommand))
                .isInstanceOf(InventoryInsufficientException.class);

        // Xác nhận đã bồi hoàn nhả ghế VIP vừa giữ
        verify(inventoryHoldPort).releaseHold(eq(List.of(101L)), eq("CUST-HYBRID"));
        // Xác nhận DB chưa hề bị chạm vào
        verify(inventoryRepository, never()).findAllByIdsInOrderWithLock(any());
        verify(outboxEventPort, never()).save(any());
    }

    @Test
    @DisplayName("Đơn kết hợp: Redis qua hết nhưng DB ném lỗi -> Tự động bồi hoàn cả nhả ghế lẫn hoàn tồn kho")
    void shouldCompensateBothHoldAndStock_WhenDatabaseFails() {
        when(inventoryHoldPort.tryHold(anyList(), anyString(), any(Duration.class))).thenReturn(true);
        when(inventoryStockCachePort.tryDeductStock(anyMap())).thenReturn(true);
        when(inventoryRepository.findAllByIdsInOrderWithLock(anyList()))
                .thenThrow(new RuntimeException("Database connection timeout!"));

        assertThatThrownBy(() -> reservationService.reserve(hybridCommand))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection timeout!");

        // Xác nhận đã nhả ghế
        verify(inventoryHoldPort).releaseHold(eq(List.of(101L)), eq("CUST-HYBRID"));
        // Xác nhận đã hoàn trả tồn kho trên Redis
        verify(inventoryStockCachePort).revertStock(eq(Map.of(201L, 2)));
        // Outbox không được lưu
        verify(outboxEventPort, never()).save(any());
    }

    @Test
    @DisplayName("Đơn kết hợp thành công trọn vẹn: Tạo Reservation PENDING và lưu OutboxEvent vào Database")
    void shouldCompleteReservationSuccessfully_WhenAllPass() {
        when(inventoryHoldPort.tryHold(anyList(), anyString(), any(Duration.class))).thenReturn(true);
        when(inventoryStockCachePort.tryDeductStock(anyMap())).thenReturn(true);

        InventoryItem seat101 = new InventoryItem(101L, "SEAT-101", "VIP 101", 1, 0, 0, 0L, Instant.now(), Instant.now());
        InventoryItem stock201 = new InventoryItem(201L, "GA-201", "Standing Zone", 100, 0, 0, 0L, Instant.now(), Instant.now());
        when(inventoryRepository.findAllByIdsInOrderWithLock(anyList())).thenReturn(List.of(seat101, stock201));

        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            return new Reservation(
                    1L,
                    r.getReservationCode(),
                    r.getChannel(),
                    r.getStatus(),
                    r.getCustomerId(),
                    r.getExpiresAt(),
                    r.getVersion(),
                    r.getItems(),
                    r.getCreatedAt(),
                    r.getUpdatedAt()
            );
        });

        ReservationResult result = reservationService.reserve(hybridCommand);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(result.items()).hasSize(2);
        assertThat(seat101.getReservedCapacity()).isEqualTo(1);
        assertThat(stock201.getReservedCapacity()).isEqualTo(2);

        // 🔥 XÁC NHẬN SỰ KIỆN OUTBOX ĐÃ ĐƯỢC LƯU
        verify(outboxEventPort, times(1)).save(any(OutboxEvent.class));
    }
}
