package com.omnichannel.reservation.booking.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.reservation.booking.application.dto.ReservationResult;
import com.omnichannel.reservation.booking.application.port.in.ReserveInventoryCommand;
import com.omnichannel.reservation.booking.application.port.in.ReserveInventoryUseCase;
import com.omnichannel.reservation.booking.domain.event.ReservationCreatedEvent;
import com.omnichannel.reservation.booking.domain.model.Reservation;
import com.omnichannel.reservation.booking.domain.model.ReservationItem;
import com.omnichannel.reservation.booking.domain.model.ReservationStatus;
import com.omnichannel.reservation.booking.domain.repository.ReservationRepository;
import com.omnichannel.reservation.inventory.application.port.out.InventoryHoldPort;
import com.omnichannel.reservation.inventory.application.port.out.InventoryStockCachePort;
import com.omnichannel.reservation.inventory.domain.exception.HaveMerchandise;
import com.omnichannel.reservation.inventory.domain.exception.InventoryInsufficientException;
import com.omnichannel.reservation.inventory.domain.exception.ReservationIsHeld;
import com.omnichannel.reservation.inventory.domain.model.InventoryItem;
import com.omnichannel.reservation.inventory.domain.repository.InventoryRepository;
import com.omnichannel.reservation.merchandise.domain.model.MerchandiseItem;
import com.omnichannel.reservation.merchandise.domain.model.ReservationMerchandise;
import com.omnichannel.reservation.merchandise.domain.repository.MerchandiseRepository;
import com.omnichannel.reservation.outbox.application.model.OutboxEvent;
import com.omnichannel.reservation.outbox.application.port.out.OutboxEventPort;
import com.omnichannel.reservation.shared.domain.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService implements ReserveInventoryUseCase {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final MerchandiseRepository merchandiseRepository;
    private final InventoryHoldPort inventoryHoldPort;
    private final InventoryStockCachePort inventoryStockCachePort;
    private final OutboxEventPort outboxEventPort;
    private final ObjectMapper objectMapper;

    // ID các vật phẩm trong VIP Merchandise Kit mặc định (Lightstick ID 1, Banner ID 2)
    private static final Long VIP_KIT_LIGHTSTICK_ID = 1L;
    private static final Long VIP_KIT_BANNER_ID = 2L;

    @Override
    @Transactional
    public ReservationResult reserve(ReserveInventoryCommand command) {
        log.info("Processing hybrid reservation for customer: {}, channel: {}, items count: {}",
                command.customerId(), command.channel(), command.items().size());

        // 1. DEADLOCK PREVENTION: Sắp xếp ID toàn bộ tài nguyên vé tăng dần trước khi khóa
        List<Long> requestedItemIds = command.items().stream()
                .map(ReserveInventoryCommand.ItemReservationRequest::inventoryItemId)
                .distinct()
                .sorted()
                .toList();

        // 2. PHÂN LOẠI NGHIỆP VỤ: Ghế định danh độc nhất (VIP) vs Vé số lượng (General Stock)
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

        // 2.1. RÀNG BUỘC NGHIỆP VỤ MERCHANDISE: Vé VIP vs Vé Thường Add-on
        boolean hasVipTicket = !assignedSeatIds.isEmpty();
        if (hasVipTicket && command.merchandises() != null && !command.merchandises().isEmpty()) {
            throw new HaveMerchandise();
        }

        // 2.2. XÁC ĐỊNH DANH SÁCH MERCHANDISE CẦN GIỮ KHO
        Map<Long, Integer> requiredMerchMap = new LinkedHashMap<>();
        if (hasVipTicket) {
            int vipSeatCount = command.items().stream()
                    .filter(ReserveInventoryCommand.ItemReservationRequest::isAssignedSeat)
                    .mapToInt(ReserveInventoryCommand.ItemReservationRequest::quantity)
                    .sum();
            requiredMerchMap.put(VIP_KIT_LIGHTSTICK_ID, vipSeatCount);
            requiredMerchMap.put(VIP_KIT_BANNER_ID, vipSeatCount);
        } else if (command.merchandises() != null && !command.merchandises().isEmpty()) {
            for (var reqMerch : command.merchandises()) {
                requiredMerchMap.merge(reqMerch.merchandiseId(), reqMerch.quantity(), Integer::sum);
            }
        }

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

        List<ReservationMerchandise> finalMerchandises = new ArrayList<>();

        try {
            // 4. TẦNG BỀN VỮNG DATABASE L2 (ACID DURABILITY):
            // 4.1. Khóa và trừ kho vé (Inventory Items) theo thứ tự ID tăng dần
            List<InventoryItem> lockedInventories = inventoryRepository.findAllByIdsInOrderWithLock(requestedItemIds);
            Map<Long, InventoryItem> inventoryMap = lockedInventories.stream()
                    .collect(Collectors.toMap(InventoryItem::getId, item -> item));

            if (inventoryMap.size() < requestedItemIds.size()) {
                throw new IllegalArgumentException("One or more inventory items do not exist");
            }

            for (var reqItem : command.items()) {
                InventoryItem inventory = inventoryMap.get(reqItem.inventoryItemId());
                inventory.reserveForOnline(reqItem.quantity());
                inventoryRepository.save(inventory);
            }

            // 4.2. Khóa và trừ kho Merchandise (Áp dụng Canonical Lock Ordering chống Deadlock)
            if (!requiredMerchMap.isEmpty()) {
                List<Long> sortedMerchIds = requiredMerchMap.keySet().stream().sorted().toList();
                List<MerchandiseItem> lockedMerchandises = merchandiseRepository.findAllByIdWithLock(sortedMerchIds);
                Map<Long, MerchandiseItem> merchMap = lockedMerchandises.stream()
                        .collect(Collectors.toMap(MerchandiseItem::getId, m -> m));

                if (merchMap.size() < sortedMerchIds.size()) {
                    throw new IllegalArgumentException("One or more merchandise items do not exist");
                }

                for (var entry : requiredMerchMap.entrySet()) {
                    Long merchId = entry.getKey();
                    int qty = entry.getValue();
                    MerchandiseItem merch = merchMap.get(merchId);
                    merch.reserve(qty);

                    // Nếu là vé VIP thì giá vật phẩm tặng kèm là 0đ, nếu là vé thường thì lấy giá niêm yết
                    BigDecimal unitPrice = hasVipTicket ? BigDecimal.ZERO : merch.getUnitPrice();
                    finalMerchandises.add(new ReservationMerchandise(null, merchId, qty, unitPrice, Instant.now()));
                }

                merchandiseRepository.saveAll(lockedMerchandises);
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

        // 5. Lưu đơn hàng vào Database (Aggregate Persistence)
        Reservation savedReservation = saveReservationAggregate(command, finalMerchandises);

        // 6. Ghi sự kiện vào Outbox trong cùng Transaction (Transactional Outbox)
        recordOutboxEvent(savedReservation);

        log.info("Successfully created reservationCode: {}, expiresAt: {}",
                savedReservation.getReservationCode(), savedReservation.getExpiresAt());

        List<ReservationResult.ReservationItemResult> itemResults = savedReservation.getItems().stream()
                .map(item -> new ReservationResult.ReservationItemResult(item.getId(), item.getInventoryItemId(), item.getQuantity()))
                .toList();

        List<ReservationResult.ReservationMerchandiseResult> merchandiseResults = savedReservation.getMerchandises().stream()
                .map(m -> new ReservationResult.ReservationMerchandiseResult(m.getId(), m.getMerchandiseId(), m.getQuantity(), m.getUnitPrice()))
                .toList();

        return new ReservationResult(
                savedReservation.getId(),
                savedReservation.getReservationCode(),
                savedReservation.getChannel(),
                savedReservation.getStatus(),
                savedReservation.getCustomerId(),
                savedReservation.getExpiresAt(),
                itemResults,
                merchandiseResults
        );
    }

    /**
     * Tách riêng hàm lưu trữ đơn đặt chỗ (Reservation Aggregate).
     */
    private Reservation saveReservationAggregate(ReserveInventoryCommand command, List<ReservationMerchandise> merchandises) {
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
                merchandises,
                Instant.now(),
                Instant.now()
        );

        return reservationRepository.save(reservation);
    }

    /**
     * Tách riêng hàm đóng gói và lưu sự kiện vào Outbox (Transactional Outbox Pattern).
     * Hàm này chạy trong cùng 1 Transaction với hàm lưu đơn hàng ở trên để đảm bảo tính nguyên tử tuyệt đối (ACID).
     */
    private void recordOutboxEvent(Reservation savedReservation) {
        List<ReservationCreatedEvent.ReservationItemPayload> reservationItemPayloads = savedReservation.getItems().stream()
                .map(item -> new ReservationCreatedEvent.ReservationItemPayload(
                        item.getId(),
                        item.getInventoryItemId(),
                        item.getQuantity(),
                        item.getUnitPrice()))
                .toList();

        List<ReservationCreatedEvent.ReservationMerchandisePayload> merchandisePayloads = savedReservation.getMerchandises().stream()
                .map(m -> new ReservationCreatedEvent.ReservationMerchandisePayload(
                        m.getMerchandiseId(),
                        m.getQuantity(),
                        m.getUnitPrice()))
                .toList();

        ReservationCreatedEvent event = ReservationCreatedEvent.of(
                savedReservation.getId(),
                savedReservation.getReservationCode(),
                savedReservation.getCustomerId(),
                savedReservation.getChannel(),
                savedReservation.getStatus(),
                savedReservation.getExpiresAt(),
                reservationItemPayloads,
                merchandisePayloads
        );

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize ReservationCreatedEvent to JSON", ex);
            throw new IllegalStateException("Failed to serialize Outbox event payload", ex);
        }

        String aggregateId = savedReservation.getId() != null
                ? savedReservation.getId().toString()
                : savedReservation.getReservationCode();

        OutboxEvent outboxEvent = OutboxEvent.of(
                "RESERVATION",
                aggregateId,
                "ReservationCreatedEvent",
                payloadJson
        );

        outboxEventPort.save(outboxEvent);
    }
}
