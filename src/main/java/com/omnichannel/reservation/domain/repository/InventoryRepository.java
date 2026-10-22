package com.omnichannel.reservation.domain.repository;

import com.omnichannel.reservation.domain.model.InventoryItem;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository {
    Optional<InventoryItem> findById(Long id);
    Optional<InventoryItem> findByItemCode(String itemCode);
    List<InventoryItem> findAllByIdsInOrderWithLock(List<Long> ids);
    InventoryItem save(InventoryItem item);
}
