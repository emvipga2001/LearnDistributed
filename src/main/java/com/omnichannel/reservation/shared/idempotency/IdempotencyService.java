package com.omnichannel.reservation.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.reservation.shared.domain.exception.IdempotencyConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Service điều phối chu trình sống của Idempotency 2 tầng (Redis L1 + DB L2).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyPort idempotencyPort;
    private final ObjectMapper objectMapper;

    /**
     * Bao bọc và thực thi logic nghiệp vụ dưới sự bảo vệ của Idempotency Key.
     *
     * @param key           Idempotency key từ client (header X-Idempotency-Key)
     * @param handlerName   Tên hàm/ngữ cảnh xử lý (ví dụ: "ReservationController.reserve")
     * @param timeout       Thời hạn khóa trên L1 Redis
     * @param responseType  Kiểu dữ liệu của kết quả trả về
     * @param businessLogic Khối code nghiệp vụ cần chạy
     * @param <T>           Kiểu dữ liệu trả về
     * @return Kết quả trả về (từ cache cũ hoặc từ nghiệp vụ vừa thực thi)
     */
    public <T> T execute(
            String key,
            String handlerName,
            Duration timeout,
            Class<T> responseType,
            Supplier<T> businessLogic
    ) {
        // 1. Nếu client không truyền Idempotency Key -> chạy nghiệp vụ bình thường
        if (key == null || key.isBlank()) {
            return businessLogic.get();
        }

        // 2. Thử chiếm khóa L1 (Redis Atomic SET NX EX)
        boolean locked = idempotencyPort.tryLock(key, timeout);

        if (!locked) {
            // Không chiếm được lock: Kiểm tra xem request trước đó đã chạy xong chưa
            Optional<IdempotencyRecord> recordOpt = idempotencyPort.findRecord(key);
            if (recordOpt.isPresent() && recordOpt.get().status() == IdempotencyStatus.COMPLETED) {
                log.info("Idempotency cache hit (in-flight window): key={}", key);
                return parseResponse(recordOpt.get().responsePayload(), responseType);
            }
            // Nếu chưa có trong DB hoặc trạng thái vẫn là PROCESSING -> Đang có request khác chạy song song
            log.warn("Idempotency conflict detected: key={}", key);
            throw new IdempotencyConflictException("Yêu cầu đang được xử lý, vui lòng không gửi lại.");
        }

        // 3. Chiếm được lock: Kiểm tra xem có phải là request cũ gửi lại sau nhiều giờ (khi Redis lock đã hết hạn)
        Optional<IdempotencyRecord> existingRecord = idempotencyPort.findRecord(key);
        if (existingRecord.isPresent() && existingRecord.get().status() == IdempotencyStatus.COMPLETED) {
            idempotencyPort.unlock(key); // Nhả lock tạm vừa chiếm
            log.info("Idempotency cache hit (expired redis lock window): key={}", key);
            return parseResponse(existingRecord.get().responsePayload(), responseType);
        }

        // 4. Nếu là request mới toanh: Ghi nhận trạng thái PROCESSING vào Database L2
        idempotencyPort.createProcessingRecord(key, handlerName);

        // 5. Thực thi logic nghiệp vụ được bọc trong try-catch để đảm bảo tính an toàn
        try {
            T result = businessLogic.get();
            String jsonPayload = objectMapper.writeValueAsString(result);
            idempotencyPort.markAsCompleted(key, jsonPayload);
            return result;
        } catch (Exception ex) {
            // Bồi hoàn: nhả khóa L1 trên Redis ngay lập tức để không giữ lock oan
            idempotencyPort.unlock(key);
            log.error("Lỗi khi thực thi nghiệp vụ có idempotency key={}, đã giải phóng lock.", key, ex);
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(ex);
        }
    }

    private <T> T parseResponse(String payload, Class<T> responseType) {
        try {
            return objectMapper.readValue(payload, responseType);
        } catch (Exception e) {
            log.error("Lỗi khi parse response payload cũ từ JSON sang {}: {}", responseType.getSimpleName(), e.getMessage());
            throw new RuntimeException("Không thể đọc kết quả phản hồi cũ", e);
        }
    }
}
