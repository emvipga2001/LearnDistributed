package com.omnichannel.reservation.shared.idempotency.infrastructure.scheduler;

import com.omnichannel.reservation.shared.idempotency.IdempotencyPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyCleanupSchedulerTest {

    @Mock
    private IdempotencyPort idempotencyPort;

    @InjectMocks
    private IdempotencyCleanupScheduler scheduler;

    @Test
    @DisplayName("cleanupExpiredRecords: Gọi idempotencyPort xóa các bản ghi cũ hơn 7 ngày")
    void shouldCallPortToDeleteRecordsOlderThanRetentionPeriod() {
        when(idempotencyPort.deleteRecordsOlderThan(eq(Duration.ofDays(7)))).thenReturn(10);

        scheduler.cleanupExpiredRecords();

        verify(idempotencyPort).deleteRecordsOlderThan(Duration.ofDays(7));
    }

    @Test
    @DisplayName("cleanupExpiredRecords: Nuốt exception và không làm sập scheduler khi port văng lỗi")
    void shouldHandleExceptionGracefully_WhenPortFails() {
        when(idempotencyPort.deleteRecordsOlderThan(any(Duration.class)))
                .thenThrow(new RuntimeException("Database timeout"));

        // Không được văng ngoại lệ
        scheduler.cleanupExpiredRecords();

        verify(idempotencyPort).deleteRecordsOlderThan(any(Duration.class));
    }
}
