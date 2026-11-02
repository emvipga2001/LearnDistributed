package com.omnichannel.reservation.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.reservation.shared.domain.exception.IdempotencyConflictException;
import com.omnichannel.reservation.shared.domain.exception.IdempotencyPayloadMismatchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
     * Bao bọc và thực thi logic nghiệp vụ dưới sự bảo vệ của Idempotency Key kèm kiểm tra dấu vân tay Payload (SHA-256).
     *
     * @param key            Idempotency key từ client (header X-Idempotency-Key)
     * @param handlerName    Tên hàm/ngữ cảnh xử lý (ví dụ: "ReservationController.reserve")
     * @param requestPayload Request object hoặc chuỗi JSON cần băm để chống giả mạo
     * @param timeout        Thời hạn khóa trên L1 Redis
     * @param responseType   Kiểu dữ liệu của kết quả trả về
     * @param businessLogic  Khối code nghiệp vụ cần chạy
     * @param <T>            Kiểu dữ liệu trả về
     * @return Kết quả trả về (từ cache cũ hoặc từ nghiệp vụ vừa thực thi)
     */
    public <T> T execute(
            String key,
            String handlerName,
            Object requestPayload,
            Duration timeout,
            Class<T> responseType,
            Supplier<T> businessLogic
    ) {
        // 1. Nếu client không truyền Idempotency Key -> chạy nghiệp vụ bình thường
        if (key == null || key.isBlank()) {
            return businessLogic.get();
        }

        String currentHash = computeHash(requestPayload);

        // 2. Thử chiếm khóa L1 (Redis Atomic SET NX EX)
        boolean locked = idempotencyPort.tryLock(key, timeout);

        if (!locked) {
            // Không chiếm được lock: Kiểm tra xem request trước đó đã chạy xong chưa
            Optional<IdempotencyRecord> recordOpt = idempotencyPort.findRecord(key);
            if (recordOpt.isPresent()) {
                validatePayloadHash(recordOpt.get(), currentHash);
                if (recordOpt.get().status() == IdempotencyStatus.COMPLETED) {
                    log.info("Idempotency cache hit (in-flight window): key={}", key);
                    return parseResponse(recordOpt.get().responsePayload(), responseType);
                }
            }
            // Nếu chưa có trong DB hoặc trạng thái vẫn là PROCESSING -> Đang có request khác chạy song song
            log.warn("Idempotency conflict detected: key={}", key);
            throw new IdempotencyConflictException("Yêu cầu đang được xử lý, vui lòng không gửi lại.");
        }

        // 3. Chiếm được lock: Kiểm tra xem có phải là request cũ gửi lại sau nhiều giờ (khi Redis lock đã hết hạn)
        Optional<IdempotencyRecord> existingRecord = idempotencyPort.findRecord(key);
        if (existingRecord.isPresent()) {
            validatePayloadHash(existingRecord.get(), currentHash);
            if (existingRecord.get().status() == IdempotencyStatus.COMPLETED) {
                idempotencyPort.unlock(key); // Nhả lock tạm vừa chiếm
                log.info("Idempotency cache hit (expired redis lock window): key={}", key);
                return parseResponse(existingRecord.get().responsePayload(), responseType);
            }
        }

        // 4. Nếu là request mới toanh: Ghi nhận trạng thái PROCESSING kèm Request Hash vào Database L2
        idempotencyPort.createProcessingRecord(key, handlerName, currentHash);

        // 5. Thực thi logic nghiệp vụ được bọc trong try-catch để đảm bảo tính an toàn
        try {
            T result = businessLogic.get();
            String jsonPayload = objectMapper.writeValueAsString(result);
            idempotencyPort.markAsCompleted(key, jsonPayload);
            return result;
        } catch (Exception ex) {
            // Bồi hoàn: nhả khóa L1 trên Redis và xóa bản ghi kẹt trong DB ngay lập tức
            idempotencyPort.unlock(key);
            log.error("Lỗi khi thực thi nghiệp vụ có idempotency key={}, đã giải phóng lock.", key, ex);
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(ex);
        }
    }

    /**
     * Phương thức overload giữ tương thích ngược khi không truyền requestPayload.
     */
    public <T> T execute(
            String key,
            String handlerName,
            Duration timeout,
            Class<T> responseType,
            Supplier<T> businessLogic
    ) {
        return execute(key, handlerName, null, timeout, responseType, businessLogic);
    }

    private void validatePayloadHash(IdempotencyRecord record, String currentHash) {
        if (record.requestHash() != null && !record.requestHash().isBlank()
                && currentHash != null && !currentHash.isBlank()
                && !record.requestHash().equals(currentHash)) {
            log.warn("Idempotency payload mismatch for key {}: stored={}, current={}",
                    record.key(), record.requestHash(), currentHash);
            throw new IdempotencyPayloadMismatchException(
                    "Idempotency-Key đã được sử dụng cho một yêu cầu khác với nội dung payload không khớp.");
        }
    }

    private String computeHash(Object requestPayload) {
        if (requestPayload == null) {
            return "";
        }
        try {
            String json;
            if (requestPayload instanceof String str) {
                json = str;
            } else {
                json = objectMapper.writeValueAsString(requestPayload);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Lỗi khi tính SHA-256 hash cho request payload: {}", e.getMessage());
            throw new RuntimeException("Không thể tính hash của request payload", e);
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
