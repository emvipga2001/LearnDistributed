package com.omnichannel.reservation.shared.idempotency.infrastructure.persistence.repository;

import com.omnichannel.reservation.shared.idempotency.infrastructure.persistence.entity.IdempotencyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface SpringDataIdempotencyJpaRepository extends JpaRepository<IdempotencyJpaEntity, String> {

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM IdempotencyJpaEntity e WHERE e.createdAt < :cutoffTime")
    int deleteExpiredBefore(@Param("cutoffTime") Instant cutoffTime);
}
