package com.omnichannel.reservation.integration;

import com.omnichannel.reservation.inventory.domain.exception.ReservationIsHeld;

import com.omnichannel.reservation.booking.application.dto.ReservationResult;
import com.omnichannel.reservation.booking.application.port.in.ReserveInventoryCommand;
import com.omnichannel.reservation.booking.application.port.in.ReserveInventoryUseCase;
import com.omnichannel.reservation.inventory.domain.exception.InventoryInsufficientException;
import com.omnichannel.reservation.shared.domain.model.Channel;
import com.omnichannel.reservation.inventory.infrastructure.persistence.entity.InventoryItemJpaEntity;
import com.omnichannel.reservation.inventory.infrastructure.persistence.repository.SpringDataInventoryJpaRepository;
import com.omnichannel.reservation.booking.infrastructure.persistence.repository.SpringDataReservationJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Slf4j
public class DeadlockPreventionIntegrationTest {

    @Autowired
    private ReserveInventoryUseCase reserveInventoryUseCase;

    @Autowired
    private SpringDataInventoryJpaRepository inventoryRepository;

    @Autowired
    private SpringDataReservationJpaRepository reservationRepository;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private Long seat101Id;
    private Long seat102Id;

    @BeforeEach
    void setupTestData() {
        reservationRepository.deleteAllInBatch();
        inventoryRepository.deleteAllInBatch();
        if (seat101Id != null) redisTemplate.delete("inventory:hold:" + seat101Id);
        if (seat102Id != null) redisTemplate.delete("inventory:hold:" + seat102Id);

        InventoryItemJpaEntity seat101 = new InventoryItemJpaEntity();
        seat101.setItemCode("SEAT-VIP-101");
        seat101.setItemName("VIP Suite Seat 101");
        seat101.setTotalCapacity(25);
        seat101.setReservedCapacity(0);
        seat101.setAllocatedPosBuffer(0);
        seat101 = inventoryRepository.saveAndFlush(seat101);
        seat101Id = seat101.getId();

        InventoryItemJpaEntity seat102 = new InventoryItemJpaEntity();
        seat102.setItemCode("SEAT-VIP-102");
        seat102.setItemName("VIP Suite Seat 102");
        seat102.setTotalCapacity(25);
        seat102.setReservedCapacity(0);
        seat102.setAllocatedPosBuffer(0);
        seat102 = inventoryRepository.saveAndFlush(seat102);
        seat102Id = seat102.getId();

        log.info("Initialized test seats: SEAT-101 (ID: {}), SEAT-102 (ID: {}) with capacity 25 each",
                seat101Id, seat102Id);
    }

    @Test
    @DisplayName("100 concurrent threads booking cross-order seats [101, 102] vs [102, 101] -> ZERO Deadlock")
    void testConcurrentCrossBooking100Threads_ZeroDeadlock() throws InterruptedException {
        int totalThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientCount = new AtomicInteger(0);
        AtomicInteger deadlockCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        List<String> executionLogs = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < totalThreads; i++) {
            final int threadIdx = i;
            final boolean isEven = (i % 2 == 0);
            final String customerId = "CUST-" + String.format("%03d", i);

            // Cross-ordering payload:
            // Even threads request: [Seat 101, Seat 102]
            // Odd threads request:  [Seat 102, Seat 101]
            List<ReserveInventoryCommand.ItemReservationRequest> requestedItems;
            if (isEven) {
                requestedItems = List.of(
                        new ReserveInventoryCommand.ItemReservationRequest(seat101Id, 1, BigDecimal.valueOf(100.0)),
                        new ReserveInventoryCommand.ItemReservationRequest(seat102Id, 1, BigDecimal.valueOf(100.0))
                );
            } else {
                requestedItems = List.of(
                        new ReserveInventoryCommand.ItemReservationRequest(seat102Id, 1, BigDecimal.valueOf(100.0)),
                        new ReserveInventoryCommand.ItemReservationRequest(seat101Id, 1, BigDecimal.valueOf(100.0))
                );
            }

            executor.submit(() -> {
                try {
                    // All 100 threads wait here until startGun fires
                    startGun.await();

                    long startTime = System.currentTimeMillis();
                    String orderDisplay = isEven ? "[" + seat101Id + " -> " + seat102Id + "]" : "[" + seat102Id + " -> " + seat101Id + "]";

                    try {
                        ReserveInventoryCommand command = new ReserveInventoryCommand(
                                customerId,
                                Channel.ONLINE,
                                Duration.ofMinutes(10),
                                requestedItems
                        );

                        ReservationResult result = reserveInventoryUseCase.reserve(command);
                        long duration = System.currentTimeMillis() - startTime;
                        successCount.incrementAndGet();

                        executionLogs.add(String.format(
                                " Thread-%03d | %s | Input: %s -> [SUCCESS] Code: %s | Time: %3d ms",
                                threadIdx, customerId, orderDisplay, result.reservationCode(), duration
                        ));
                    } catch (InventoryInsufficientException ex) {
                        long duration = System.currentTimeMillis() - startTime;
                        insufficientCount.incrementAndGet();
                        executionLogs.add(String.format(
                                " Thread-%03d | %s | Input: %s -> [REJECTED] Kho hết vé | Time: %3d ms",
                                threadIdx, customerId, orderDisplay, duration
                        ));
                    } catch (com.omnichannel.reservation.inventory.domain.exception.ReservationIsHeld ex) {
                        long duration = System.currentTimeMillis() - startTime;
                        insufficientCount.incrementAndGet();
                        executionLogs.add(String.format(
                                " Thread-%03d | %s | Input: %s -> [REJECTED] Bàn đã bị giữ (Redis Fast Guard) | Time: %3d ms",
                                threadIdx, customerId, orderDisplay, duration
                        ));
                    } catch (DeadlockLoserDataAccessException | CannotAcquireLockException ex) {
                        long duration = System.currentTimeMillis() - startTime;
                        deadlockCount.incrementAndGet();
                        executionLogs.add(String.format(
                                " Thread-%03d | %s | Input: %s -> [CRITICAL DEADLOCK DETECTED!] Error: %s | Time: %3d ms",
                                threadIdx, customerId, orderDisplay, ex.getMessage(), duration
                        ));
                    } catch (Exception ex) {
                        long duration = System.currentTimeMillis() - startTime;
                        if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("deadlock")) {
                            deadlockCount.incrementAndGet();
                            executionLogs.add(String.format(
                                    " Thread-%03d | %s | Input: %s -> [CRITICAL DEADLOCK!] %s | Time: %3d ms",
                                    threadIdx, customerId, orderDisplay, ex.getMessage(), duration
                            ));
                        } else {
                            otherErrorCount.incrementAndGet();
                            executionLogs.add(String.format(
                                    " Thread-%03d | %s | Input: %s -> [ERROR] %s | Time: %3d ms",
                                    threadIdx, customerId, orderDisplay, ex.getMessage(), duration
                            ));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Fire all 100 threads at once!
        log.info(">>>> FIRE: 100 THREADS CONCURRENTLY HAMMERING RESERVATION ENGINE <<<<");
        startGun.countDown();

        boolean finished = finishLatch.await(30, TimeUnit.SECONDS);
        assertThat(finished).as("All threads must finish within 30 seconds").isTrue();
        executor.shutdown();

        // Print individual thread logs sorted
        System.out.println("\n=========================== CHI TIẾT 100 THREADS EXECUTION LOGS ===========================");
        executionLogs.stream().sorted().forEach(System.out::println);

        System.out.println("===========================================================================================");
        System.out.println("                               TỔNG KẾT BÁO CÁO KIỂM THỬ TẢI                              ");
        System.out.println("===========================================================================================");
        System.out.printf("  Tổng số Threads đồng thời     : %d\n", totalThreads);
        System.out.printf("  Số yêu cầu thành công         : %d\n", successCount.get());
        System.out.printf("  Số yêu cầu bị chặn/từ chối    : %d\n", insufficientCount.get());
        System.out.printf("  SỐ LƯỢNG DEADLOCK PHÁT HIỆN   : %d (KỲ VỌNG: 0)\n", deadlockCount.get());
        System.out.printf("  Số lỗi ngoại lệ khác          : %d\n", otherErrorCount.get());
        System.out.println("===========================================================================================\n");

        // Verify Database state
        InventoryItemJpaEntity finalSeat101 = inventoryRepository.findById(seat101Id).orElseThrow();
        InventoryItemJpaEntity finalSeat102 = inventoryRepository.findById(seat102Id).orElseThrow();

        System.out.printf("  [DB TRẠNG THÁI CUỐI] Seat 101 - Total: %d, Reserved: %d\n",
                finalSeat101.getTotalCapacity(), finalSeat101.getReservedCapacity());
        System.out.printf("  [DB TRẠNG THÁI CUỐI] Seat 102 - Total: %d, Reserved: %d\n",
                finalSeat102.getTotalCapacity(), finalSeat102.getReservedCapacity());

        // Strict Distributed Systems Assertions:
        assertThat(deadlockCount.get())
                .as("ZERO DEADLOCK: Canonical Lock Ordering must completely prevent deadlocks!")
                .isZero();

        assertThat(otherErrorCount.get())
                .as("Zero unexpected errors")
                .isZero();

        assertThat(successCount.get() + insufficientCount.get())
                .as("All 100 requests must be accounted for")
                .isEqualTo(100);

        assertThat(finalSeat101.getReservedCapacity())
                .as("Seat 101 reserved capacity must equal successful reservations")
                .isEqualTo(successCount.get());

        assertThat(finalSeat102.getReservedCapacity())
                .as("Seat 102 reserved capacity must equal successful reservations")
                .isEqualTo(successCount.get());
    }
}
