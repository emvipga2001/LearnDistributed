package com.omnichannel.reservation.shared.idempotency.infrastructure.adapter;

import com.omnichannel.reservation.shared.idempotency.IdempotencyPort;
import com.omnichannel.reservation.shared.idempotency.IdempotencyRecord;
import com.omnichannel.reservation.shared.idempotency.IdempotencyStatus;
import com.omnichannel.reservation.shared.idempotency.infrastructure.persistence.entity.IdempotencyJpaEntity;
import com.omnichannel.reservation.shared.idempotency.infrastructure.persistence.repository.SpringDataIdempotencyJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Two-Tier Idempotency Adapter:
 * - Tầng L1 (Redis): Thao tác qua StringRedisTemplate (Fast-path / In-flight deduplication)
 * - Tầng L2 (Database): Thao tác qua SpringDataIdempotencyJpaRepository (Persistent-path)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TwoTierIdempotencyAdapter implements IdempotencyPort {

    private static final String LOCK_KEY_PREFIX = "idempotency:lock:";

    private final StringRedisTemplate redisTemplate;
    private final SpringDataIdempotencyJpaRepository jpaRepository;

    @Override
    public boolean tryLock(String key, Duration timeout) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY_PREFIX + key, "PROCESSING", timeout));
    }

    @Override
    public Optional<IdempotencyRecord> findRecord(String key) {
        return jpaRepository.findById(key).map(this::toDomain);
    }

    @Override
    public void createProcessingRecord(String key, String handlerName, String requestHash) {
        IdempotencyJpaEntity entity = new IdempotencyJpaEntity();
        entity.setIdempotencyKey(key);
        entity.setHandlerName(handlerName);
        entity.setRequestHash(requestHash);
        entity.setStatus(IdempotencyStatus.PROCESSING);
        jpaRepository.save(entity);
    }

    @Override
    public void markAsCompleted(String key, String responsePayload) {
        IdempotencyJpaEntity entity = jpaRepository.getReferenceById(key);
        entity.setStatus(IdempotencyStatus.COMPLETED);
        entity.setResponsePayload(responsePayload);
        jpaRepository.save(entity);
    }

    @Override
    public void unlock(String key) {
        redisTemplate.delete(LOCK_KEY_PREFIX + key);
        jpaRepository.deleteById(key);
    }

    @Override
    @Transactional
    public int deleteRecordsOlderThan(Duration retentionPeriod) {
        Instant now = Instant.now();
        Instant cutoffTime = now.minus(retentionPeriod);
        return jpaRepository.deleteExpiredBefore(cutoffTime);
    }

    private IdempotencyRecord toDomain(IdempotencyJpaEntity entity) {
        return new IdempotencyRecord(
                entity.getIdempotencyKey(),
                entity.getHandlerName(),
                entity.getRequestHash(),
                entity.getStatus(),
                entity.getResponsePayload(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
