package com.omnichannel.reservation.outbox.application.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Đại diện cho một sự kiện được lưu trong bảng outbox_events.
 * Phục vụ cơ chế Transactional Outbox Pattern nhằm đảm bảo At-Least-Once Delivery.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    private UUID id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;
    private String status;
    private int retryCount;
    private Instant createdAt;
    private Instant processedAt;

    public static OutboxEvent of(String aggregateType, String aggregateId, String eventType, String payload) {
        return new OutboxEvent(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                payload,
                "PENDING",
                0,
                Instant.now(),
                null
        );
    }
}
