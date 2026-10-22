package com.omnichannel.reservation.application.service;

import com.omnichannel.reservation.application.dto.ReservationResult;
import com.omnichannel.reservation.application.port.in.ReserveInventoryCommand;
import com.omnichannel.reservation.application.port.in.ReserveInventoryUseCase;
import com.omnichannel.reservation.application.port.out.InventoryHoldPort;
import com.omnichannel.reservation.application.port.out.InventoryStockCachePort;
import com.omnichannel.reservation.domain.exception.InventoryInsufficientException;
import com.omnichannel.reservation.domain.exception.ReservationIsHeld;
import com.omnichannel.reservation.domain.model.InventoryItem;
import com.omnichannel.reservation.domain.model.Reservation;
import com.omnichannel.reservation.domain.model.ReservationItem;
import com.omnichannel.reservation.domain.model.ReservationStatus;
import com.omnichannel.reservation.domain.repository.InventoryRepository;
import com.omnichannel.reservation.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService implements ReserveInventoryUseCase {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryHoldPort inventoryHoldPort;
    private final InventoryStockCachePort inventoryStockCachePort;

    @Override
    @Transactional
    public ReservationResult reserve(ReserveInventoryCommand command) {
        log.info("Processing hybrid reservation for customer: {}, channel: {}, items count: {}",
                command.customerId(), command.channel(), command.items().size());

        // 1. DEADLOCK PREVENTION: Sắp xếp ID toàn bộ tài nguyên tăng dần trước khi khóa
        List<Long> requestedItemIds = command.items().stream()
                .map(ReserveInventoryCommand.ItemReservationRequest::inventoryItemId)
                .distinct()
                .sorted()
                .toList();

        // 2. PHÂN LOẠI NGHIỆP VỤ: Ghế định danh độc nhất (Assigned Seat) vs Vé số lượng (General Stock)
        List<Long> assignedSeatIds = command.items().stream()
                .filter(ReserveInventoryCommand.ItemReservationRequest::isAssignedSeat)
                .map(ReserveInventoryCommand.ItemReservationRequest::inventoryItemId)
                .distinct()
                .sorted()
                .toList();

        Map<Long, Integer> stockItemsToDeduct = command.items().stream()
                .filter(it -> !it.isAssignedSeat())
                .collect(Collectors.toMap(
                        ReserveInventoryCommand.ItemReservationRequest::inventoryItemId,
                        ReserveInventoryCommand.ItemReservationRequest::quantity,
                        Integer::sum
                ));

        // 3. TẦNG BẢO VỆ REDIS L1 (DUAL-GUARD):
        // 3.1. Giữ chỗ độc quyền cho các ghế định danh
        if (!assignedSeatIds.isEmpty()) {
            boolean isHeld = inventoryHoldPort.tryHold(assignedSeatIds, command.customerId(), command.holdDuration());
            if (!isHeld) {
                log.warn("Assigned seats already held for items: {}", assignedSeatIds);
                throw new ReservationIsHeld();
            }
        }

        // 3.2. Trừ tồn kho nguyên tử (Fail-Fast Flash Sale) cho vé số lượng
        if (!stockItemsToDeduct.isEmpty()) {
            boolean stockDeducted = inventoryStockCachePort.tryDeductStock(stockItemsToDeduct);
            if (!stockDeducted) {
                log.warn("Insufficient stock on Redis for items: {}", stockItemsToDeduct);
                // BỒI HOÀN LIÊN ĐỚI (Cascading Compensation): Nhả lại ghế định danh nếu vé số lượng hết hàng
                if (!assignedSeatIds.isEmpty()) {
                    inventoryHoldPort.releaseHold(assignedSeatIds, command.customerId());
                }
                throw new InventoryInsufficientException("FLASH_SALE_STOCK", 0, 0);
            }
        }

        try {
            // 4. TẦNG BỀN VỮNG DATABASE L2 (ACID DURABILITY):
            // Lấy dữ liệu với khóa bi quan (Pessimistic Write Lock) theo thứ tự ID tăng dần
            List<InventoryItem> lockedInventories = inventoryRepository.findAllByIdsInOrderWithLock(requestedItemIds);
            Map<Long, InventoryItem> inventoryMap = lockedInventories.stream()
                    .collect(Collectors.toMap(InventoryItem::getId, item -> item));

            if (inventoryMap.size() < requestedItemIds.size()) {
                throw new IllegalArgumentException("One or more inventory items do not exist");
            }

            // Kiểm tra khả dụng và tăng số lượng đã giữ trong DB
            for (var reqItem : command.items()) {
                InventoryItem inventory = inventoryMap.get(reqItem.inventoryItemId());
                inventory.reserveForOnline(reqItem.quantity());
                inventoryRepository.save(inventory);
            }
        } catch (Exception ex) {
            log.error("Lỗi khi xử lý Database, kích hoạt bồi hoàn (Compensating Actions) trên Redis...", ex);
            if (!assignedSeatIds.isEmpty()) {
                inventoryHoldPort.releaseHold(assignedSeatIds, command.customerId());
            }
            if (!stockItemsToDeduct.isEmpty()) {
                inventoryStockCachePort.revertStock(stockItemsToDeduct);
            }
            throw ex;
        }

        // 5. Khởi tạo đối tượng Reservation Aggregate
        Instant expiresAt = Instant.now().plus(command.holdDuration());
        String reservationCode = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        List<ReservationItem> reservationItems = command.items().stream()
                .map(it -> new ReservationItem(null, it.inventoryItemId(), it.quantity(), it.unitPrice(), Instant.now()))
                .toList();

        Reservation reservation = new Reservation(
                null,
                reservationCode,
                command.channel(),
                ReservationStatus.PENDING,
                command.customerId(),
                expiresAt,
                0L,
                reservationItems,
                Instant.now(),
                Instant.now()
        );

        Reservation savedReservation = reservationRepository.save(reservation);

        log.info("Successfully created reservationCode: {}, expiresAt: {}", reservationCode, expiresAt);

        List<ReservationResult.ReservationItemResult> itemResults = savedReservation.getItems().stream()
                .map(item -> new ReservationResult.ReservationItemResult(item.getId(), item.getInventoryItemId(), item.getQuantity()))
                .toList();

        return new ReservationResult(
                savedReservation.getId(),
                savedReservation.getReservationCode(),
                savedReservation.getChannel(),
                savedReservation.getStatus(),
                savedReservation.getCustomerId(),
                savedReservation.getExpiresAt(),
                itemResults
        );
    }
}
