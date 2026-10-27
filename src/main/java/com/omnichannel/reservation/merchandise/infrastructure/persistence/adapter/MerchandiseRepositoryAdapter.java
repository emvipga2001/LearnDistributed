package com.omnichannel.reservation.merchandise.infrastructure.persistence.adapter;

import com.omnichannel.reservation.inventory.domain.model.InventoryItem;
import com.omnichannel.reservation.inventory.infrastructure.persistence.entity.InventoryItemJpaEntity;
import com.omnichannel.reservation.merchandise.domain.model.MerchandiseItem;
import com.omnichannel.reservation.merchandise.domain.repository.MerchandiseRepository;
import com.omnichannel.reservation.merchandise.infrastructure.persistence.entity.MerchandiseJpaEntity;
import com.omnichannel.reservation.merchandise.infrastructure.persistence.repository.SpringDataMerchandiseJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

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

    }

    @Override
    public void saveAll(List<MerchandiseItem> merchandiseItems) {

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
