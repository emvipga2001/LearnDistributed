package com.omnichannel.reservation.shared.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * Driven / Outbound Port: Hợp đồng giao tiếp cho hệ thống Idempotency 2 tầng (Redis L1 + Database L2).
 */
public interface IdempotencyPort {
    boolean tryLock(String key, Duration timeout);
    Optional<IdempotencyRecord> findRecord(String key);
    void createProcessingRecord(String key, String handlerName, String requestHash);
    void markAsCompleted(String key, String responsePayload);
    void unlock(String key);
    int deleteRecordsOlderThan(Duration retentionPeriod);
}
