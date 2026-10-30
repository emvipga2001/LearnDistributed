package com.omnichannel.reservation.shared.idempotency.infrastructure.persistence.repository;

import com.omnichannel.reservation.shared.idempotency.infrastructure.persistence.entity.IdempotencyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataIdempotencyJpaRepository extends JpaRepository<IdempotencyJpaEntity, String> {
}
