package com.omnichannel.reservation.infrastructure.persistence.adapter;

import com.omnichannel.reservation.domain.model.Reservation;
import com.omnichannel.reservation.domain.model.ReservationItem;
import com.omnichannel.reservation.domain.repository.ReservationRepository;
import com.omnichannel.reservation.infrastructure.persistence.entity.ReservationItemJpaEntity;
import com.omnichannel.reservation.infrastructure.persistence.entity.ReservationJpaEntity;
import com.omnichannel.reservation.infrastructure.persistence.repository.SpringDataReservationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReservationRepositoryAdapter implements ReservationRepository {

    private final SpringDataReservationJpaRepository jpaRepository;

    @Override
    public Reservation save(Reservation reservation) {
        ReservationJpaEntity entity = new ReservationJpaEntity();
        if (reservation.getId() != null) {
            entity.setId(reservation.getId());
        }
        entity.setReservationCode(reservation.getReservationCode());
        entity.setChannel(reservation.getChannel());
        entity.setStatus(reservation.getStatus());
        entity.setCustomerId(reservation.getCustomerId());
        entity.setExpiresAt(reservation.getExpiresAt());

        if (reservation.getItems() != null) {
            for (ReservationItem domainItem : reservation.getItems()) {
                ReservationItemJpaEntity itemEntity = new ReservationItemJpaEntity();
                itemEntity.setId(domainItem.getId());
                itemEntity.setInventoryItemId(domainItem.getInventoryItemId());
                itemEntity.setQuantity(domainItem.getQuantity());
                itemEntity.setUnitPrice(domainItem.getUnitPrice());
                entity.addItem(itemEntity);
            }
        }

        ReservationJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Reservation> findByReservationCode(String reservationCode) {
        return jpaRepository.findByReservationCodeWithItems(reservationCode).map(this::toDomain);
    }

    private Reservation toDomain(ReservationJpaEntity entity) {
        List<ReservationItem> items = entity.getItems().stream()
                .map(item -> new ReservationItem(
                        item.getId(),
                        item.getInventoryItemId(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getCreatedAt()))
                .toList();

        return new Reservation(
                entity.getId(),
                entity.getReservationCode(),
                entity.getChannel(),
                entity.getStatus(),
                entity.getCustomerId(),
                entity.getExpiresAt(),
                entity.getVersion(),
                items,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
