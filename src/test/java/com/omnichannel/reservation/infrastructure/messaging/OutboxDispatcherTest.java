package com.omnichannel.reservation.infrastructure.messaging;

import com.omnichannel.reservation.application.model.OutboxEvent;
import com.omnichannel.reservation.application.port.out.OutboxEventPort;
import com.omnichannel.reservation.infrastructure.config.RabbitMqConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

    @Mock
    private OutboxEventPort outboxEventPort;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OutboxDispatcher outboxDispatcher;

    @Test
    @DisplayName("HAPPY PATH: Gửi tin thành công sang RabbitMQ -> Đánh dấu PUBLISHED")
    void shouldDispatchPendingEventsSuccessfully() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                eventId, "RESERVATION", "RES-001", "RESERVATION_CREATED",
                "{\"code\":\"RES-001\"}", "PENDING", 0, Instant.now(), null
        );

        when(outboxEventPort.findPendingEventsWithLock(10)).thenReturn(List.of(event));

        // WHEN: Dispatcher quét sự kiện
        outboxDispatcher.dispatchPendingEvents();

        // THEN: Phải bắn sang RabbitMQ đúng Exchange và Routing Key
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMqConfig.RESERVATION_EXCHANGE),
                eq(RabbitMqConfig.RESERVATION_ROUTING_KEY),
                eq("{\"code\":\"RES-001\"}")
        );

        // Phải cập nhật trạng thái đã gửi thành công
        verify(outboxEventPort, times(1)).markAsPublished(eq(eventId));
        verify(outboxEventPort, never()).markAsFailed(any(), anyInt());
    }

    @Test
    @DisplayName("CHAOS SIMULATION: RabbitMQ sập/lỗi mạng -> Đánh dấu FAILED và tăng retry_count")
    void shouldHandleFailureAndIncrementRetryCount_WhenRabbitMqFails() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                eventId, "RESERVATION", "RES-002", "RESERVATION_CREATED",
                "{\"code\":\"RES-002\"}", "PENDING", 1, Instant.now(), null
        );

        when(outboxEventPort.findPendingEventsWithLock(10)).thenReturn(List.of(event));

        // GIẢ LẬP SỰ CỐ: RabbitMQ ném ngoại lệ AmqpException (đứt mạng)
        doThrow(new AmqpException("Connection refused to RabbitMQ"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // WHEN: Dispatcher chạy
        outboxDispatcher.dispatchPendingEvents();

        // THEN: Không được đánh dấu PUBLISHED, mà phải gọi markAsFailed với retry_count = 2 (1 + 1)
        verify(outboxEventPort, never()).markAsPublished(any());
        verify(outboxEventPort, times(1)).markAsFailed(eq(eventId), eq(2));
    }

    @Test
    @DisplayName("HÒM THƯ RỖNG: Không có sự kiện PENDING -> Không gọi RabbitMQ")
    void shouldDoNothingWhenNoPendingEvents() {
        when(outboxEventPort.findPendingEventsWithLock(10)).thenReturn(Collections.emptyList());

        outboxDispatcher.dispatchPendingEvents();

        verifyNoInteractions(rabbitTemplate);
        verify(outboxEventPort, never()).markAsPublished(any());
        verify(outboxEventPort, never()).markAsFailed(any(), anyInt());
    }
}
