package com.omnichannel.reservation.application.service;

import com.omnichannel.reservation.application.dto.ReservationResult;
import com.omnichannel.reservation.application.port.in.ReserveInventoryCommand;
import com.omnichannel.reservation.application.port.in.ReserveInventoryUseCase;
import com.omnichannel.reservation.application.port.out.InventoryHoldPort;
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

    @Override
    @Transactional
    public ReservationResult reserve(ReserveInventoryCommand command) {
        log.info("Processing reservation command for customer: {}, channel: {}, items count: {}",
                command.customerId(), command.channel(), command.items().size());

        // 1. DEADLOCK PREVENTION: Sort inventory IDs ascending before acquiring locks
        List<Long> requestedItemIds = command.items().stream()
                .map(ReserveInventoryCommand.ItemReservationRequest::inventoryItemId)
                .distinct()
                .sorted()
                .toList();

        boolean isHeld = inventoryHoldPort.tryHold(requestedItemIds, command.customerId(), command.holdDuration());
        if (!isHeld) {
            throw new ReservationIsHeld();
        }

        try {
            // 2. Fetch inventory items with pessimistic write lock in strict ID order
            List<InventoryItem> lockedInventories = inventoryRepository.findAllByIdsInOrderWithLock(requestedItemIds);
            Map<Long, InventoryItem> inventoryMap = lockedInventories.stream()
                    .collect(Collectors.toMap(InventoryItem::getId, item -> item));

            if (inventoryMap.size() < requestedItemIds.size()) {
                throw new IllegalArgumentException("One or more inventory items do not exist");
            }

            // 3. Check capacity and reserve
            for (var reqItem : command.items()) {
                InventoryItem inventory = inventoryMap.get(reqItem.inventoryItemId());
                inventory.reserveForOnline(reqItem.quantity());
                inventoryRepository.save(inventory);
            }
        } catch (Exception ex){
            log.error("Lỗi khi xử lý DB, đang giải phóng khóa Redis...", ex);
            inventoryHoldPort.releaseHold(requestedItemIds, command.customerId());
            throw ex;
        }

        // 4. Construct reservation aggregate
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

        log.info("Successfully reserved reservationCode: {}, expiresAt: {}", reservationCode, expiresAt);

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
