package com.omnichannel.reservation.outbox.infrastructure.messaging;

import com.omnichannel.reservation.outbox.application.model.OutboxEvent;
import com.omnichannel.reservation.outbox.application.port.out.OutboxEventPort;
import com.omnichannel.reservation.shared.infrastructure.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatcher {
    private final OutboxEventPort outboxEventPort;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 1000)
    public void dispatchPendingEvents(){
        List<OutboxEvent> events = outboxEventPort.findPendingEventsWithLock(10);
        if(events.isEmpty()) return;
        events.forEach(outboxEvent -> {
            try {
                rabbitTemplate.convertAndSend(RabbitMqConfig.RESERVATION_EXCHANGE, RabbitMqConfig.RESERVATION_ROUTING_KEY, outboxEvent.getPayload());
                outboxEventPort.markAsPublished(outboxEvent.getId());
                log.info("Outbox Event Published Successfully:  {}", outboxEvent.getId());
            }catch (Exception e){
                outboxEventPort.markAsFailed(outboxEvent.getId(), outboxEvent.getRetryCount() + 1);
                log.error("Outbox Event Published Failed: id={}, error={}", outboxEvent.getId(), e.getMessage(), e);
            }
        });

    }
}
