package com.omnichannel.reservation.shared.idempotency.infrastructure.scheduler;

import com.omnichannel.reservation.shared.idempotency.IdempotencyPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Background Scheduler dọn dẹp các bản ghi Idempotency đã quá thời hạn lưu trữ (Retention Policy).
 * Chạy định kỳ lúc 02:00 sáng mỗi ngày.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupScheduler {

    private final IdempotencyPort idempotencyPort;

    // Thời gian lưu trữ bản ghi idempotency (7 ngày)
    private static final Duration RETENTION_PERIOD = Duration.ofDays(7);

    @Scheduled(cron = "${app.idempotency.cleanup-cron:0 0 2 * * ?}")
    public void cleanupExpiredRecords() {
        log.info("Bắt đầu tiến trình dọn dẹp bản ghi Idempotency cũ hơn {} ngày...", RETENTION_PERIOD.toDays());
        try {
            int deletedCount = idempotencyPort.deleteRecordsOlderThan(RETENTION_PERIOD);
            log.info("Hoàn tất dọn dẹp Idempotency: Đã xóa {} bản ghi hết hạn.", deletedCount);
        } catch (Exception e) {
            log.error("Lỗi xảy ra trong quá trình dọn dẹp bản ghi Idempotency: {}", e.getMessage(), e);
        }
    }
}
