package com.omnichannel.reservation.application.port.out;

import com.omnichannel.reservation.application.model.OutboxEvent;

/**
 * Driven/Outbound Port: Hợp đồng lưu trữ sự kiện Outbox vào bộ nhớ bền vững (Database).
 * Được gọi trực tiếp bên trong cùng Transaction của Use Case.
 */
public interface OutboxEventPort {
    void save(OutboxEvent outboxEvent);
}
