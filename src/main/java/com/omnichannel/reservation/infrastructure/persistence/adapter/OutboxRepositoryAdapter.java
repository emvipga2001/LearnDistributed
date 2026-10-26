package com.omnichannel.reservation.infrastructure.persistence.adapter;

import com.omnichannel.reservation.application.model.OutboxEvent;
import com.omnichannel.reservation.application.port.out.OutboxEventPort;
import com.omnichannel.reservation.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.omnichannel.reservation.infrastructure.persistence.repository.SpringDataOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRepositoryAdapter implements OutboxEventPort {

    private final SpringDataOutboxJpaRepository outboxJpaRepository;

    @Override
    @Transactional
    public void save(OutboxEvent outboxEvent) {
        OutboxEventJpaEntity entity = toEntity(outboxEvent);
        outboxJpaRepository.save(entity);
        log.debug("Saved outbox event: id={}, eventType={}", outboxEvent.getId(), outboxEvent.getEventType());
    }

    @Override
    @Transactional
    public List<OutboxEvent> findPendingEventsWithLock(int limit) {
        return outboxJpaRepository.findPendingEventsWithLock(limit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void markAsPublished(UUID eventId) {
        outboxJpaRepository.findById(eventId).ifPresent(entity -> {
            entity.setStatus("PUBLISHED");
            entity.setProcessedAt(Instant.now());
            outboxJpaRepository.save(entity);
            log.debug("Marked outbox event as PUBLISHED: id={}", eventId);
        });
    }

    @Override
    @Transactional
    public void markAsFailed(UUID eventId, int retryCount) {
        outboxJpaRepository.findById(eventId).ifPresent(entity -> {
            entity.setRetryCount(retryCount);
            if (retryCount >= 3) {
                entity.setStatus("FAILED");
                entity.setProcessedAt(Instant.now());
                log.warn("Outbox event reached max retries, marked as FAILED: id={}, retryCount={}", eventId, retryCount);
            } else {
                log.info("Incremented retry count for outbox event: id={}, retryCount={}", eventId, retryCount);
            }
            outboxJpaRepository.save(entity);
        });
    }

    private OutboxEventJpaEntity toEntity(OutboxEvent domain) {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(domain.getId());
        entity.setAggregateType(domain.getAggregateType());
        entity.setAggregateId(domain.getAggregateId());
        entity.setEventType(domain.getEventType());
        entity.setPayload(domain.getPayload());
        entity.setStatus(domain.getStatus());
        entity.setRetryCount(domain.getRetryCount());
        entity.setProcessedAt(domain.getProcessedAt());
        if (domain.getCreatedAt() != null) {
            entity.setCreatedAt(domain.getCreatedAt());
        }
        return entity;
    }

    private OutboxEvent toDomain(OutboxEventJpaEntity entity) {
        return new OutboxEvent(
                entity.getId(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getStatus(),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getProcessedAt()
        );
    }
}
