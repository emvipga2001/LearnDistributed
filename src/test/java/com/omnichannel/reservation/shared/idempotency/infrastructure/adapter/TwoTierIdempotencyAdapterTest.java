package com.omnichannel.reservation.shared.idempotency.infrastructure.adapter;

import com.omnichannel.reservation.shared.idempotency.IdempotencyRecord;
import com.omnichannel.reservation.shared.idempotency.IdempotencyStatus;
import com.omnichannel.reservation.shared.idempotency.infrastructure.persistence.entity.IdempotencyJpaEntity;
import com.omnichannel.reservation.shared.idempotency.infrastructure.persistence.repository.SpringDataIdempotencyJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwoTierIdempotencyAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SpringDataIdempotencyJpaRepository jpaRepository;

    @InjectMocks
    private TwoTierIdempotencyAdapter adapter;

    private static final String KEY = "test-key-123";
    private static final String LOCK_KEY = "idempotency:lock:test-key-123";
    private static final String HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("tryLock thành công: Redis SET NX trả về true")
    void shouldReturnTrue_WhenSetIfAbsentSucceeds() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        boolean locked = adapter.tryLock(KEY, Duration.ofSeconds(120));

        assertThat(locked).isTrue();
        verify(valueOperations).setIfAbsent(LOCK_KEY, "PROCESSING", Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("tryLock thất bại: Redis SET NX trả về false do key đã tồn tại")
    void shouldReturnFalse_WhenSetIfAbsentFails() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

        boolean locked = adapter.tryLock(KEY, Duration.ofSeconds(120));

        assertThat(locked).isFalse();
    }

    @Test
    @DisplayName("findRecord: Tìm thấy bản ghi trong Database và ánh xạ sang IdempotencyRecord")
    void shouldReturnMappedRecord_WhenRecordExistsInDatabase() {
        IdempotencyJpaEntity entity = new IdempotencyJpaEntity();
        entity.setIdempotencyKey(KEY);
        entity.setHandlerName("ReservationController.reserve");
        entity.setRequestHash(HASH);
        entity.setStatus(IdempotencyStatus.COMPLETED);
        entity.setResponsePayload("{\"reservationCode\":\"RES-12345\"}");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        when(jpaRepository.findById(KEY)).thenReturn(Optional.of(entity));

        Optional<IdempotencyRecord> result = adapter.findRecord(KEY);

        assertThat(result).isPresent();
        assertThat(result.get().key()).isEqualTo(KEY);
        assertThat(result.get().requestHash()).isEqualTo(HASH);
        assertThat(result.get().status()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(result.get().responsePayload()).isEqualTo("{\"reservationCode\":\"RES-12345\"}");
    }

    @Test
    @DisplayName("createProcessingRecord: Lưu bản ghi mới với trạng thái PROCESSING và requestHash")
    void shouldSaveEntityWithProcessingStatus() {
        adapter.createProcessingRecord(KEY, "ReservationController.reserve", HASH);

        ArgumentCaptor<IdempotencyJpaEntity> captor = ArgumentCaptor.forClass(IdempotencyJpaEntity.class);
        verify(jpaRepository).save(captor.capture());

        IdempotencyJpaEntity saved = captor.getValue();
        assertThat(saved.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(saved.getHandlerName()).isEqualTo("ReservationController.reserve");
        assertThat(saved.getRequestHash()).isEqualTo(HASH);
        assertThat(saved.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
    }

    @Test
    @DisplayName("markAsCompleted: Cập nhật status thành COMPLETED và lưu kèm response payload")
    void shouldUpdateEntityToCompletedStatusWithPayload() {
        IdempotencyJpaEntity entity = new IdempotencyJpaEntity();
        entity.setIdempotencyKey(KEY);
        entity.setStatus(IdempotencyStatus.PROCESSING);

        when(jpaRepository.getReferenceById(KEY)).thenReturn(entity);

        adapter.markAsCompleted(KEY, "{\"status\":\"SUCCESS\"}");

        assertThat(entity.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(entity.getResponsePayload()).isEqualTo("{\"status\":\"SUCCESS\"}");
        verify(jpaRepository).save(entity);
    }

    @Test
    @DisplayName("unlock: Gọi xóa key khóa L1 trên Redis và xóa bản ghi trong DB")
    void shouldDeleteLockKeyFromRedisAndDatabase() {
        adapter.unlock(KEY);

        verify(redisTemplate).delete(LOCK_KEY);
        verify(jpaRepository).deleteById(KEY);
    }

    @Test
    @DisplayName("deleteRecordsOlderThan: Gọi repository xóa các bản ghi trước cutoff time và trả về số dòng bị xóa")
    void shouldDeleteRecordsOlderThanRetentionPeriod() {
        when(jpaRepository.deleteExpiredBefore(any(Instant.class))).thenReturn(5);

        int deletedCount = adapter.deleteRecordsOlderThan(Duration.ofDays(7));

        assertThat(deletedCount).isEqualTo(5);
        verify(jpaRepository).deleteExpiredBefore(any(Instant.class));
    }
}
