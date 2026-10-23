package com.omnichannel.reservation.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.reservation.application.dto.ReservationResult;
import com.omnichannel.reservation.application.model.OutboxEvent;
import com.omnichannel.reservation.application.port.in.ReserveInventoryCommand;
import com.omnichannel.reservation.application.port.in.ReserveInventoryUseCase;
import com.omnichannel.reservation.application.port.out.InventoryHoldPort;
import com.omnichannel.reservation.application.port.out.InventoryStockCachePort;
import com.omnichannel.reservation.application.port.out.OutboxEventPort;
import com.omnichannel.reservation.domain.event.ReservationCreatedEvent;
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
    private final OutboxEventPort outboxEventPort;
    private final ObjectMapper objectMapper;

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

        // 5. Lưu đơn hàng vào Database (Aggregate Persistence)
        Reservation savedReservation = saveReservationAggregate(command);

        // 6. Ghi sự kiện vào Outbox trong cùng Transaction (Transactional Outbox)
        recordOutboxEvent(savedReservation);

        log.info("Successfully created reservationCode: {}, expiresAt: {}",
                savedReservation.getReservationCode(), savedReservation.getExpiresAt());

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

    /**
     * Tách riêng hàm lưu trữ đơn đặt chỗ (Reservation Aggregate).
     */
    private Reservation saveReservationAggregate(ReserveInventoryCommand command) {
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

        return reservationRepository.save(reservation);
    }

    /**
     * Tách riêng hàm đóng gói và lưu sự kiện vào Outbox (Transactional Outbox Pattern).
     * Hàm này chạy trong cùng 1 Transaction với hàm lưu đơn hàng ở trên để đảm bảo tính nguyên tử tuyệt đối (ACID).
     */
    private void recordOutboxEvent(Reservation savedReservation) {
        // Dòng 1: Chuyển đổi các Entity con (ReservationItem) sang DTO Payload công khai
        // Mục đích: Không để lộ Entity nội bộ ra ngoài, chỉ gửi đúng dữ liệu công khai mà các service khác cần
        List<ReservationCreatedEvent.ReservationItemPayload> reservationItemPayloads = savedReservation.getItems().stream()
                .map(item -> new ReservationCreatedEvent.ReservationItemPayload(
                        item.getId(),
                        item.getInventoryItemId(),
                        item.getQuantity(),
                        item.getUnitPrice()))
                .toList();

        // Dòng 2: Khởi tạo đối tượng Domain Event bất biến (Record)
        // Mục đích: Đóng gói toàn bộ ngữ cảnh sự việc đã xảy ra (Event-Carried State Transfer)
        ReservationCreatedEvent event = ReservationCreatedEvent.of(
                savedReservation.getId(),
                savedReservation.getReservationCode(),
                savedReservation.getCustomerId(),
                savedReservation.getChannel(),
                savedReservation.getStatus(),
                savedReservation.getExpiresAt(),
                reservationItemPayloads
        );

        // Dòng 3: Serialize (Chuyển đổi) Domain Event thành chuỗi văn bản JSON
        // Mục đích: Để cột JSONB trong bảng outbox_events có thể lưu bất kỳ loại sự kiện nào mà không cần tạo nhiều bảng
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize ReservationCreatedEvent to JSON", ex);
            // Nếu lỗi parse JSON -> Ném ngoại lệ để Spring @Transactional rollback toàn bộ giao dịch, không lưu đơn rác
            throw new IllegalStateException("Failed to serialize Outbox event payload", ex);
        }

        // Dòng 4: Xác định khóa định danh của Aggregate (Ưu tiên Database ID, fallback về mã code)
        // Mục đích: Phục vụ việc đánh chỉ mục, truy vết và phân luồng thứ tự (Partitioning) sau này
        String aggregateId = savedReservation.getId() != null
                ? savedReservation.getId().toString()
                : savedReservation.getReservationCode();

        // Dòng 5: Đóng gói thành bản ghi OutboxEvent (Chiếc phong bì thư có nhãn)
        // Mục đích: Gắn nhãn aggregateType và eventType để Worker đọc được mà không cần bóc nội dung JSON
        OutboxEvent outboxEvent = OutboxEvent.of(
                "RESERVATION",
                aggregateId,
                "ReservationCreatedEvent",
                payloadJson
        );

        // Dòng 6: Gọi Outbound Port để lưu bản ghi Outbox vào bảng outbox_events trong Database
        // Mục đích: Nhét thư vào hộp thư trên cùng một kết nối DB, cam kết At-Least-Once Delivery
        outboxEventPort.save(outboxEvent);
    }
}
