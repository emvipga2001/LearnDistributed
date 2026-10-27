package com.omnichannel.reservation.inventory.infrastructure.redis;

import com.omnichannel.reservation.inventory.application.port.out.InventoryStockCachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisInventoryStockAdapter implements InventoryStockCachePort {

    private final RedisScript<Long> decrScript = RedisScript.of(
            new ClassPathResource("scripts/decr_stock.lua"), Long.class);

    private final RedisScript<Long> incrScript = RedisScript.of(
            new ClassPathResource("scripts/incr_stock.lua"), Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    private static final String STOCK_KEY_PREFIX = "inventory:stock:";

    @Override
    public boolean tryDeductStock(Map<Long, Integer> itemsToDeduct) {
        if (itemsToDeduct == null || itemsToDeduct.isEmpty()) {
            return true;
        }

        // Sắp xếp ID tăng dần để đảm bảo tính tất định (Canonical Ordering)
        List<Map.Entry<Long, Integer>> sortedEntries = itemsToDeduct.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        List<String> keys = sortedEntries.stream()
                .map(e -> STOCK_KEY_PREFIX + e.getKey())
                .toList();

        Object[] args = sortedEntries.stream()
                .map(e -> String.valueOf(e.getValue()))
                .toArray(Object[]::new);

        Long result = stringRedisTemplate.execute(decrScript, keys, args);
        boolean success = result != null && result == 1L;

        log.debug("Redis tryDeductStock for keys: {}, result: {}", keys, success);
        return success;
    }

    @Override
    public void revertStock(Map<Long, Integer> itemsToRevert) {
        if (itemsToRevert == null || itemsToRevert.isEmpty()) {
            return;
        }

        List<Map.Entry<Long, Integer>> sortedEntries = itemsToRevert.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        List<String> keys = sortedEntries.stream()
                .map(e -> STOCK_KEY_PREFIX + e.getKey())
                .toList();

        Object[] args = sortedEntries.stream()
                .map(e -> String.valueOf(e.getValue()))
                .toArray(Object[]::new);

        stringRedisTemplate.execute(incrScript, keys, args);
        log.debug("Redis revertStock executed for keys: {}", keys);
    }

    @Override
    public void setStock(Long itemId, int quantity) {
        stringRedisTemplate.opsForValue().set(STOCK_KEY_PREFIX + itemId, String.valueOf(quantity));
        log.info("Redis setStock: itemId={}, quantity={}", itemId, quantity);
    }

    @Override
    public Integer getStock(Long itemId) {
        String val = stringRedisTemplate.opsForValue().get(STOCK_KEY_PREFIX + itemId);
        return val != null ? Integer.parseInt(val) : null;
    }
}
