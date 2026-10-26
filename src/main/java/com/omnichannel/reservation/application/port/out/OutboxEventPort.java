package com.omnichannel.reservation.application.port.out;

import com.omnichannel.reservation.application.model.OutboxEvent;

import java.util.List;
import java.util.UUID;

/**
 * Driven/Outbound Port: Hợp đồng lưu trữ và truy vấn sự kiện Outbox.
 * - save: Được gọi trực tiếp bên trong cùng Transaction của Use Case.
 * - findPendingEventsWithLock, markAsPublished, markAsFailed: Phục vụ cho Background Dispatcher Worker.
 */
public interface OutboxEventPort {
    void save(OutboxEvent outboxEvent);

    List<OutboxEvent> findPendingEventsWithLock(int limit);

    void markAsPublished(UUID eventId);

    void markAsFailed(UUID eventId, int retryCount);
}
