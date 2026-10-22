package com.omnichannel.reservation.infrastructure.redis;

import com.omnichannel.reservation.application.port.out.InventoryHoldPort;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisInventoryHoldAdapter implements InventoryHoldPort {

    private final RedisScript<Long> holdScript = RedisScript.of(
            new ClassPathResource("scripts/multi_hold.lua"), Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean tryHold(List<Long> itemIds, String customerId, Duration ttl) {
        List<String> redisKeys = itemIds.stream()
                .map(id -> "inventory:hold:" + id)
                .toList();

        Long res = stringRedisTemplate.execute(holdScript, redisKeys, customerId, String.valueOf(ttl.toSeconds()));
        return res != null && res == 1L;
    }

    @Override
    public void releaseHold(List<Long> itemIds, String customerId) {
        List<String> redisKeys = itemIds.stream()
                .map(id -> "inventory:hold:" + id)
                .toList();

        stringRedisTemplate.delete(redisKeys);
    }
}
