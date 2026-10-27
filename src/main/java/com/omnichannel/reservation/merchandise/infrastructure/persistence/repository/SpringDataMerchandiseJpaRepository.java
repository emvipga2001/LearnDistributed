package com.omnichannel.reservation.merchandise.infrastructure.persistence.repository;

import com.omnichannel.reservation.merchandise.infrastructure.persistence.entity.MerchandiseJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataMerchandiseJpaRepository extends JpaRepository<MerchandiseJpaEntity, Long> {

    Optional<MerchandiseJpaEntity> findByItemCode(String itemCode);

    /**
     * Khóa bi quan (SELECT ... FOR UPDATE) và áp dụng Canonical Ordering (ORDER BY m.id ASC)
     * để chống Deadlock giữa các giao dịch tranh mua banner/lightstick cùng lúc.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MerchandiseJpaEntity m WHERE m.id IN :ids ORDER BY m.id ASC")
    List<MerchandiseJpaEntity> findAllByIdInOrderWithPessimisticLock(@Param("ids") List<Long> ids);
}
