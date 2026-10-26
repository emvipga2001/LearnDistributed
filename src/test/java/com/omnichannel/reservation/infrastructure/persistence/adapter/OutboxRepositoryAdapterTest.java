package com.omnichannel.reservation.infrastructure.persistence.adapter;

import com.omnichannel.reservation.application.model.OutboxEvent;
import com.omnichannel.reservation.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.omnichannel.reservation.infrastructure.persistence.repository.SpringDataOutboxJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRepositoryAdapterTest {

    @Mock
    private SpringDataOutboxJpaRepository outboxJpaRepository;

    @InjectMocks
    private OutboxRepositoryAdapter outboxRepositoryAdapter;

    private UUID eventId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Lưu OutboxEvent vào Database chuyển đổi đúng từ Domain sang JPA Entity")
    void shouldSaveOutboxEventProperly() {
        OutboxEvent domain = OutboxEvent.of("RESERVATION", "101", "RESERVATION_CREATED", "{\"code\":\"RES-123\"}");

        outboxRepositoryAdapter.save(domain);

        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxJpaRepository).save(captor.capture());

        OutboxEventJpaEntity captured = captor.getValue();
        assertThat(captured.getId()).isEqualTo(domain.getId());
        assertThat(captured.getAggregateType()).isEqualTo("RESERVATION");
        assertThat(captured.getAggregateId()).isEqualTo("101");
        assertThat(captured.getEventType()).isEqualTo("RESERVATION_CREATED");
        assertThat(captured.getStatus()).isEqualTo("PENDING");
        assertThat(captured.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("Quét các sự kiện PENDING có cơ chế khóa SKIP LOCKED")
    void shouldFindPendingEventsWithLock() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                eventId, "RESERVATION", "101", "RESERVATION_CREATED",
                "{}", "PENDING", 0, null
        );
        entity.setCreatedAt(Instant.now());

        when(outboxJpaRepository.findPendingEventsWithLock(eq(10))).thenReturn(List.of(entity));

        List<OutboxEvent> result = outboxRepositoryAdapter.findPendingEventsWithLock(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(eventId);
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Đánh dấu sự kiện PUBLISHED và cập nhật processed_at")
    void shouldMarkAsPublished() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                eventId, "RESERVATION", "101", "RESERVATION_CREATED",
                "{}", "PENDING", 0, null
        );

        when(outboxJpaRepository.findById(eq(eventId))).thenReturn(Optional.of(entity));

        outboxRepositoryAdapter.markAsPublished(eventId);

        assertThat(entity.getStatus()).isEqualTo("PUBLISHED");
        assertThat(entity.getProcessedAt()).isNotNull();
        verify(outboxJpaRepository).save(entity);
    }

    @Test
    @DisplayName("Tăng retry_count và đánh dấu FAILED khi đạt ngưỡng tối đa 3 lần")
    void shouldMarkAsFailedWhenMaxRetriesExceeded() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                eventId, "RESERVATION", "101", "RESERVATION_CREATED",
                "{}", "PENDING", 2, null
        );

        when(outboxJpaRepository.findById(eq(eventId))).thenReturn(Optional.of(entity));

        // Retry lần thứ 3 (đạt ngưỡng max retries)
        outboxRepositoryAdapter.markAsFailed(eventId, 3);

        assertThat(entity.getRetryCount()).isEqualTo(3);
        assertThat(entity.getStatus()).isEqualTo("FAILED");
        assertThat(entity.getProcessedAt()).isNotNull();
        verify(outboxJpaRepository).save(entity);
    }
}
