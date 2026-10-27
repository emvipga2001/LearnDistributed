package com.omnichannel.reservation.merchandise.infrastructure.persistence.adapter;

import com.omnichannel.reservation.merchandise.domain.model.MerchandiseItem;
import com.omnichannel.reservation.merchandise.domain.repository.MerchandiseRepository;
import com.omnichannel.reservation.merchandise.infrastructure.persistence.entity.MerchandiseJpaEntity;
import com.omnichannel.reservation.merchandise.infrastructure.persistence.repository.SpringDataMerchandiseJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MerchandiseRepositoryAdapter implements MerchandiseRepository {

    private final SpringDataMerchandiseJpaRepository repository;

    @Override
    public Optional<MerchandiseItem> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<MerchandiseItem> findAllByIdWithLock(List<Long> ids) {
        return repository.findAllByIdInOrderWithPessimisticLock(ids).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void save(MerchandiseItem merchandiseItem) {
        MerchandiseJpaEntity entity = repository.findById(merchandiseItem.getId())
                .orElseGet(MerchandiseJpaEntity::new);
        entity.setItemCode(merchandiseItem.getItemCode());
        entity.setItemName(merchandiseItem.getItemName());
        entity.setTotalStock(merchandiseItem.getTotalStock());
        entity.setReservedStock(merchandiseItem.getReservedStock());
        entity.setUnitPrice(merchandiseItem.getUnitPrice());
        repository.save(entity);
    }

    @Override
    public void saveAll(List<MerchandiseItem> merchandiseItems) {
        if (merchandiseItems == null || merchandiseItems.isEmpty()) {
            return;
        }
        List<Long> ids = merchandiseItems.stream().map(MerchandiseItem::getId).toList();
        Map<Long, MerchandiseJpaEntity> entityMap = repository.findAllById(ids).stream()
                .collect(Collectors.toMap(MerchandiseJpaEntity::getId, e -> e));

        List<MerchandiseJpaEntity> toSave = merchandiseItems.stream().map(item -> {
            MerchandiseJpaEntity entity = entityMap.getOrDefault(item.getId(), new MerchandiseJpaEntity());
            entity.setItemCode(item.getItemCode());
            entity.setItemName(item.getItemName());
            entity.setTotalStock(item.getTotalStock());
            entity.setReservedStock(item.getReservedStock());
            entity.setUnitPrice(item.getUnitPrice());
            return entity;
        }).toList();

        repository.saveAll(toSave);
    }

    private MerchandiseItem toDomain(MerchandiseJpaEntity entity) {
        return new MerchandiseItem(
                entity.getId(),
                entity.getItemCode(),
                entity.getItemName(),
                entity.getTotalStock(),
                entity.getReservedStock(),
                entity.getUnitPrice(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
