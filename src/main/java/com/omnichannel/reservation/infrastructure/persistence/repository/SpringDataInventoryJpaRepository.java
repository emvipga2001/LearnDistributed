package com.omnichannel.reservation.infrastructure.persistence.repository;

import com.omnichannel.reservation.infrastructure.persistence.entity.InventoryItemJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataInventoryJpaRepository extends JpaRepository<InventoryItemJpaEntity, Long> {

    Optional<InventoryItemJpaEntity> findByItemCode(String itemCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItemJpaEntity i WHERE i.id IN :ids ORDER BY i.id ASC")
    List<InventoryItemJpaEntity> findAllByIdInOrderWithPessimisticLock(@Param("ids") List<Long> ids);
}
