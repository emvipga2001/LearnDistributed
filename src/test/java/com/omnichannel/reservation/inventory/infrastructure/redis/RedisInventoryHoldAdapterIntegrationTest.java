package com.omnichannel.reservation.inventory.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class RedisInventoryHoldAdapterIntegrationTest {

    @Autowired
    private RedisInventoryHoldAdapter redisAdapter;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final List<Long> seats = List.of(9901L, 9902L);

    @BeforeEach
    void cleanUp() {
        seats.forEach(id -> stringRedisTemplate.delete("inventory:hold:" + id));
    }

    @Test
    @DisplayName("Khách 1 giữ 2 ghế thành công trên Redis -> Kiểm tra Key và TTL")
    void shouldHoldSeatsSuccessfully_AndSetProperTtl() {
        // GIVEN: 2 ghế chưa ai đặt
        // WHEN: Khách CUST-01 gọi tryHold 10 phút
        boolean success = redisAdapter.tryHold(seats, "CUST-01", Duration.ofMinutes(10));

        // THEN: Phải thành công
        assertThat(success).isTrue();

        // Kiểm tra trực tiếp trên Redis
        assertThat(stringRedisTemplate.opsForValue().get("inventory:hold:9901")).isEqualTo("CUST-01");
        assertThat(stringRedisTemplate.opsForValue().get("inventory:hold:9902")).isEqualTo("CUST-01");

        // TTL phải còn khoảng 550 - 600 giây
        Long ttl = stringRedisTemplate.getExpire("inventory:hold:9901");
        assertThat(ttl).isGreaterThan(500L).isLessThanOrEqualTo(600L);
    }

    @Test
    @DisplayName("Khách 2 tranh ghế với Khách 1 -> Bị từ chối ngay lập tức, không khóa dở dang")
    void shouldRejectSecondCustomer_WhenSeatIsAlreadyHeld() {
        // GIVEN: Khách CUST-01 đã giữ ghế 9901 và 9902
        redisAdapter.tryHold(seats, "CUST-01", Duration.ofMinutes(10));

        // WHEN: Khách CUST-02 cố tình nhảy vào đặt ghế 9902 và 9903
        List<Long> overlapSeats = List.of(9902L, 9903L);
        boolean successCust2 = redisAdapter.tryHold(overlapSeats, "CUST-02", Duration.ofMinutes(10));

        // THEN: Khách 2 phải bị từ chối
        assertThat(successCust2).isFalse();

        // Ghế 9903 TUYỆT ĐỐI không bị khóa oan (Tính nguyên tử của Lua Script)
        assertThat(stringRedisTemplate.hasKey("inventory:hold:9903")).isFalse();

        // Ghế 9902 vẫn thuộc về Khách 1
        assertThat(stringRedisTemplate.opsForValue().get("inventory:hold:9902")).isEqualTo("CUST-01");
    }

    @Test
    @DisplayName("Giải phóng khóa -> Người khác có thể đặt lại bình thường")
    void shouldReleaseHoldProperly() {
        // Khách 1 giữ ghế
        redisAdapter.tryHold(seats, "CUST-01", Duration.ofMinutes(10));

        // Khách 1 hủy hoặc DB lỗi -> Gọi releaseHold
        redisAdapter.releaseHold(seats, "CUST-01");

        // Key trên Redis phải biến mất
        assertThat(stringRedisTemplate.hasKey("inventory:hold:9901")).isFalse();
        assertThat(stringRedisTemplate.hasKey("inventory:hold:9902")).isFalse();

        // Giờ Khách 2 có thể giữ bình thường
        boolean successCust2 = redisAdapter.tryHold(seats, "CUST-02", Duration.ofMinutes(10));
        assertThat(successCust2).isTrue();
    }
}
