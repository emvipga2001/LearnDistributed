package com.omnichannel.reservation.infrastructure.persistence.adapter;

import com.omnichannel.reservation.domain.model.InventoryItem;
import com.omnichannel.reservation.domain.repository.InventoryRepository;
import com.omnichannel.reservation.infrastructure.persistence.entity.InventoryItemJpaEntity;
import com.omnichannel.reservation.infrastructure.persistence.repository.SpringDataInventoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InventoryRepositoryAdapter implements InventoryRepository {

    private final SpringDataInventoryJpaRepository jpaRepository;

    @Override
    public Optional<InventoryItem> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<InventoryItem> findByItemCode(String itemCode) {
        return jpaRepository.findByItemCode(itemCode).map(this::toDomain);
    }

    @Override
    public List<InventoryItem> findAllByIdsInOrderWithLock(List<Long> ids) {
        return jpaRepository.findAllByIdInOrderWithPessimisticLock(ids).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public InventoryItem save(InventoryItem item) {
        InventoryItemJpaEntity entity;
        if (item.getId() != null) {
            entity = jpaRepository.findById(item.getId())
                    .orElseGet(InventoryItemJpaEntity::new);
        } else {
            entity = new InventoryItemJpaEntity();
        }

        entity.setItemCode(item.getItemCode());
        entity.setItemName(item.getItemName());
        entity.setTotalCapacity(item.getTotalCapacity());
        entity.setReservedCapacity(item.getReservedCapacity());
        entity.setAllocatedPosBuffer(item.getAllocatedPosBuffer());

        InventoryItemJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    private InventoryItem toDomain(InventoryItemJpaEntity entity) {
        return new InventoryItem(
                entity.getId(),
                entity.getItemCode(),
                entity.getItemName(),
                entity.getTotalCapacity(),
                entity.getReservedCapacity(),
                entity.getAllocatedPosBuffer(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
