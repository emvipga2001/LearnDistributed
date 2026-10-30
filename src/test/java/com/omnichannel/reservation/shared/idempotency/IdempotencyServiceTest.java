package com.omnichannel.reservation.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.reservation.shared.domain.exception.IdempotencyConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyPort idempotencyPort;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private IdempotencyService idempotencyService;

    private static final String KEY = "uuid-key-1234";
    private static final String HANDLER = "TestHandler";
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    record SampleResponse(String message, int code) {}

    @Test
    @DisplayName("Chạy thẳng nghiệp vụ khi key rỗng hoặc null, không gọi idempotencyPort")
    void shouldExecuteDirectly_WhenKeyIsNullOrBlank() {
        AtomicInteger counter = new AtomicInteger(0);

        SampleResponse res1 = idempotencyService.execute(null, HANDLER, TIMEOUT, SampleResponse.class, () -> {
            counter.incrementAndGet();
            return new SampleResponse("OK", 200);
        });

        SampleResponse res2 = idempotencyService.execute("  ", HANDLER, TIMEOUT, SampleResponse.class, () -> {
            counter.incrementAndGet();
            return new SampleResponse("OK", 200);
        });

        assertThat(res1.message()).isEqualTo("OK");
        assertThat(res2.message()).isEqualTo("OK");
        assertThat(counter.get()).isEqualTo(2);
        verifyNoInteractions(idempotencyPort);
    }

    @Test
    @DisplayName("Request mới: Chiếm lock thành công, lưu PROCESSING, chạy nghiệp vụ, lưu COMPLETED và trả về kết quả")
    void shouldExecuteAndComplete_WhenNewRequest() {
        when(idempotencyPort.tryLock(KEY, TIMEOUT)).thenReturn(true);
        when(idempotencyPort.findRecord(KEY)).thenReturn(Optional.empty());

        SampleResponse result = idempotencyService.execute(KEY, HANDLER, TIMEOUT, SampleResponse.class,
                () -> new SampleResponse("Created", 201));

        assertThat(result.message()).isEqualTo("Created");
        assertThat(result.code()).isEqualTo(201);

        verify(idempotencyPort).tryLock(KEY, TIMEOUT);
        verify(idempotencyPort).createProcessingRecord(KEY, HANDLER);
        verify(idempotencyPort).markAsCompleted(eq(KEY), contains("Created"));
        verify(idempotencyPort, never()).unlock(anyString());
    }

    @Test
    @DisplayName("Không chiếm được lock: Nếu DB đã có kết quả COMPLETED thì trả về kết quả cũ ngay")
    void shouldReturnCachedResponse_WhenLockFailed_AndRecordCompleted() {
        when(idempotencyPort.tryLock(KEY, TIMEOUT)).thenReturn(false);

        IdempotencyRecord cachedRecord = new IdempotencyRecord(
                KEY, HANDLER, IdempotencyStatus.COMPLETED,
                "{\"message\":\"FromCache\",\"code\":200}",
                Instant.now(), Instant.now()
        );
        when(idempotencyPort.findRecord(KEY)).thenReturn(Optional.of(cachedRecord));

        AtomicInteger executed = new AtomicInteger(0);
        SampleResponse result = idempotencyService.execute(KEY, HANDLER, TIMEOUT, SampleResponse.class, () -> {
            executed.incrementAndGet();
            return new SampleResponse("ShouldNotRun", 500);
        });

        assertThat(result.message()).isEqualTo("FromCache");
        assertThat(result.code()).isEqualTo(200);
        assertThat(executed.get()).isZero(); // Nghiệp vụ hoàn toàn không bị chạy lại!
        verify(idempotencyPort, never()).createProcessingRecord(anyString(), anyString());
    }

    @Test
    @DisplayName("Không chiếm được lock: Nếu DB đang PROCESSING thì ném IdempotencyConflictException")
    void shouldThrowConflict_WhenLockFailed_AndRecordProcessing() {
        when(idempotencyPort.tryLock(KEY, TIMEOUT)).thenReturn(false);

        IdempotencyRecord processingRecord = new IdempotencyRecord(
                KEY, HANDLER, IdempotencyStatus.PROCESSING,
                null, Instant.now(), Instant.now()
        );
        when(idempotencyPort.findRecord(KEY)).thenReturn(Optional.of(processingRecord));

        assertThatThrownBy(() -> idempotencyService.execute(KEY, HANDLER, TIMEOUT, SampleResponse.class, () -> null))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("Yêu cầu đang được xử lý");
    }

    @Test
    @DisplayName("Replay sau khi Redis lock hết hạn: Chiếm được lock nhưng thấy DB đã COMPLETED -> nhả lock và trả về kết quả cũ")
    void shouldReturnCachedResponseAndUnlock_WhenLockSucceeded_AndRecordAlreadyCompleted() {
        when(idempotencyPort.tryLock(KEY, TIMEOUT)).thenReturn(true);

        IdempotencyRecord completedRecord = new IdempotencyRecord(
                KEY, HANDLER, IdempotencyStatus.COMPLETED,
                "{\"message\":\"ReplayAfterExpired\",\"code\":200}",
                Instant.now(), Instant.now()
        );
        when(idempotencyPort.findRecord(KEY)).thenReturn(Optional.of(completedRecord));

        AtomicInteger executed = new AtomicInteger(0);
        SampleResponse result = idempotencyService.execute(KEY, HANDLER, TIMEOUT, SampleResponse.class, () -> {
            executed.incrementAndGet();
            return new SampleResponse("ShouldNotRun", 500);
        });

        assertThat(result.message()).isEqualTo("ReplayAfterExpired");
        assertThat(executed.get()).isZero();
        verify(idempotencyPort).unlock(KEY); // Phải nhả lock tạm vừa chiếm
    }

    @Test
    @DisplayName("Bồi hoàn: Khi nghiệp vụ quăng lỗi, lập tức unlock trên Redis và ném lại exception")
    void shouldUnlockAndRethrow_WhenBusinessLogicFails() {
        when(idempotencyPort.tryLock(KEY, TIMEOUT)).thenReturn(true);
        when(idempotencyPort.findRecord(KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> idempotencyService.execute(KEY, HANDLER, TIMEOUT, SampleResponse.class, () -> {
            throw new IllegalStateException("Database connection broken!");
        })).isInstanceOf(IllegalStateException.class)
           .hasMessageContaining("Database connection broken!");

        verify(idempotencyPort).unlock(KEY); // Giải phóng khóa L1 ngay lập tức
        verify(idempotencyPort, never()).markAsCompleted(anyString(), anyString());
    }
}
