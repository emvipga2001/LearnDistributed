package com.omnichannel.reservation.shared.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * Driven / Outbound Port: Hợp đồng giao tiếp cho hệ thống Idempotency 2 tầng (Redis L1 + Database L2).
 */
public interface IdempotencyPort {
    // TODO: Định nghĩa các phương thức giao tiếp tại đây
    public boolean tryLock(String key, Duration timeout);
    public Optional<IdempotencyRecord> findRecord(String key);
    public void createProcessingRecord(String key, String handlerName);
    public void markAsCompleted(String key, String responsePayload);
    public void unlock(String key);

}
