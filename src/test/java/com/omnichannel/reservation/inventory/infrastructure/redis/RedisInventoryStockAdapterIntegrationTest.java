package com.omnichannel.reservation.inventory.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class RedisInventoryStockAdapterIntegrationTest {

    @Autowired
    private RedisInventoryStockAdapter stockAdapter;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final Long ITEM_VIP = 8801L;
    private static final Long ITEM_REGULAR = 8802L;

    @BeforeEach
    void cleanUp() {
        stringRedisTemplate.delete("inventory:stock:" + ITEM_VIP);
        stringRedisTemplate.delete("inventory:stock:" + ITEM_REGULAR);
    }

    @Test
    @DisplayName("Nạp tồn kho (Pre-warming) và trừ tồn kho đơn lẻ thành công")
    void shouldDeductStockSuccessfully_WhenStockAvailable() {
        // GIVEN: Nạp sẵn 10 vé VIP lên Redis
        stockAdapter.setStock(ITEM_VIP, 10);
        assertThat(stockAdapter.getStock(ITEM_VIP)).isEqualTo(10);

        // WHEN: Trừ 3 vé
        boolean deducted = stockAdapter.tryDeductStock(ITEM_VIP, 3);

        // THEN: Trừ thành công và còn lại đúng 7 vé
        assertThat(deducted).isTrue();
        assertThat(stockAdapter.getStock(ITEM_VIP)).isEqualTo(7);
    }

    @Test
    @DisplayName("Trừ tồn kho vượt quá số lượng hiện có -> Bị từ chối ngay lập tức, không bị âm kho")
    void shouldRejectDeduction_WhenInsufficientStock() {
        // GIVEN: Chỉ còn 2 vé
        stockAdapter.setStock(ITEM_VIP, 2);

        // WHEN: Cố tình mua 3 vé
        boolean deducted = stockAdapter.tryDeductStock(ITEM_VIP, 3);

        // THEN: Bị từ chối và tồn kho vẫn giữ nguyên 2 (Zero Overselling)
        assertThat(deducted).isFalse();
        assertThat(stockAdapter.getStock(ITEM_VIP)).isEqualTo(2);
    }

    @Test
    @DisplayName("Trừ nhiều mặt hàng cùng lúc: Nếu 1 mặt hàng không đủ kho -> Không trừ mặt hàng nào (All-or-Nothing)")
    void shouldRollbackAll_WhenOneItemFailsInMultiItemDeduction() {
        // GIVEN: VIP còn 5 vé, REGULAR chỉ còn 1 vé
        stockAdapter.setStock(ITEM_VIP, 5);
        stockAdapter.setStock(ITEM_REGULAR, 1);

        // WHEN: Mua 2 VIP và 2 REGULAR (REGULAR không đủ)
        boolean deducted = stockAdapter.tryDeductStock(Map.of(
                ITEM_VIP, 2,
                ITEM_REGULAR, 2
        ));

        // THEN: Toàn bộ lệnh bị từ chối, VIP vẫn nguyên 5 vé, REGULAR vẫn nguyên 1 vé
        assertThat(deducted).isFalse();
        assertThat(stockAdapter.getStock(ITEM_VIP)).isEqualTo(5);
        assertThat(stockAdapter.getStock(ITEM_REGULAR)).isEqualTo(1);
    }

    @Test
    @DisplayName("Hoàn trả tồn kho (Compensating Action) -> Số lượng được cộng lại chính xác")
    void shouldRevertStockSuccessfully() {
        // GIVEN: Còn 5 vé, trừ đi 2 vé còn 3 vé
        stockAdapter.setStock(ITEM_VIP, 5);
        stockAdapter.tryDeductStock(ITEM_VIP, 2);
        assertThat(stockAdapter.getStock(ITEM_VIP)).isEqualTo(3);

        // WHEN: Hoàn trả lại 2 vé (do DB lỗi hoặc timeout)
        stockAdapter.revertStock(ITEM_VIP, 2);

        // THEN: Tồn kho trên Redis quay lại 5
        assertThat(stockAdapter.getStock(ITEM_VIP)).isEqualTo(5);
    }
}
