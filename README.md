# Omnichannel Booking & Inventory Reservation Engine (LearnDistributed)

Dự án thực chiến xây dựng nền tảng đặt phòng, vé và quản lý tồn kho đa kênh tích hợp quầy POS Offline và Online theo chuẩn Production, áp dụng **Clean Architecture / Domain-Driven Design (DDD)** theo mô hình **Bounded Contexts (Package-by-Feature)** và phương pháp **Problem-First & Chaos Injection**.

---

## 📌 Công Nghệ Cốt Lõi (Core Tech Stack)
* **Runtime**: Java 21 LTS (Virtual Threads, Records, Pattern Matching, Sealed Classes).
  > ⚠️ **Lưu ý quan trọng**: Phải dùng JDK 21 LTS (ví dụ: `ms-21` hoặc Eclipse Temurin 21). Không dùng JDK 25 vì Lombok chưa tương thích (`TypeTag :: UNKNOWN`).
* **Framework**: Spring Boot 3.3.4 (Spring Data JPA, Spring AMQP / RabbitMQ, Spring Data Redis, Actuator).
* **Datastores & Message Queue**:
  - PostgreSQL 16 (Port `5433` - tránh xung đột với Postgres 5432 native của máy).
  - Redis 7 (Port `6379`) & Redisson 3.36.0 (Distributed Lock, Atomic Lua Script Guard).
  - RabbitMQ 3.13-management (Ports `5672`, `15672`).
* **Database Migration**: Flyway (`V1__init_schema.sql`, `V2__seed_sample_data.sql`, `V3__add_merchandise_schema.sql`).
* **Kiểm thử**: JUnit 5, Mockito, Testcontainers, k6 Load Testing.

---

## 🏛️ Cấu Trúc Mã Nguồn (Bounded Contexts / Package-by-Feature)

Dự án được phân chia theo các phân hệ nghiệp vụ độc lập, trong mỗi phân hệ tuân thủ nghiêm ngặt **Clean Architecture (Hexagonal)**:

```text
src/main/java/com/omnichannel/reservation/
├── shared/                         # 🌐 HẠ TẦNG & THÀNH PHẦN DÙNG CHUNG
│   ├── domain/                     # BaseEntity, BaseUpdatableEntity, Channel, BusinessException
│   ├── infrastructure/config/      # OpenApiConfig, RabbitMqConfig, RedisConfig
│   └── presentation/exception/     # GlobalExceptionHandler (RFC 7807 ProblemDetail Advice)
│
├── booking/                        # 🎯 PHÂN HỆ ĐẶT CHỖ (Reservation Bounded Context)
│   ├── domain/                     # Reservation, ReservationItem, ReservationStatus, Events
│   ├── application/                # ReservationService, ReserveInventoryUseCase, Command
│   ├── infrastructure/             # ReservationJpaEntity, SpringDataReservationJpaRepository
│   └── presentation/               # ReservationController, CreateReservationRequest
│
├── inventory/                      # 🎫 PHÂN HỆ KHO VÉ & GHẾ (Inventory Bounded Context)
│   ├── domain/                     # InventoryItem, InventoryRepository, Domain Exceptions
│   ├── application/port/out/       # InventoryHoldPort, InventoryStockCachePort
│   └── infrastructure/             # Redis Hold & Stock Adapters, SpringDataInventoryJpaRepository
│
├── merchandise/                    # 🎁 PHÂN HỆ VẬT PHẨM CỔ VŨ (Merchandise Bounded Context)
│   ├── domain/                     # MerchandiseItem, ReservationMerchandise, MerchandiseRepository
│   └── infrastructure/             # MerchandiseJpaEntity, SpringDataMerchandiseJpaRepository
│
└── outbox/                         # 📨 HỆ THỐNG SỰ KIỆN GIAO DỊCH (Transactional Outbox)
    ├── application/                # OutboxEvent, OutboxEventPort
    └── infrastructure/             # OutboxEventJpaEntity, SpringDataOutboxJpaRepository (SKIP LOCKED), OutboxDispatcher
```

---

## 📖 Tài Liệu Nghiệp Vụ & Kịch Bản Phân Tán
* 📄 [Tài liệu Kịch bản Đặt chỗ Đa mô hình (Hybrid Concert & Flash Sale)](docs/HYBRID_CONCERT_RESERVATION_SCENARIO.md)

---

## 🚀 Hướng Dẫn Chạy Trên Máy Mới (Quickstart For Any Machine)

### 1. Yêu cầu tiên quyết
- Cài đặt **Docker & Docker Desktop**.
- Cài đặt **Java 21 LTS** (nhớ trỏ biến môi trường `JAVA_HOME` về thư mục JDK 21).

### 2. Khởi động hạ tầng Docker
```bash
docker compose up -d
```
Kiểm tra các container đã chạy:
- PostgreSQL 16: `localhost:5433` (Database: `reservation_db`, User: `postgres`, Pass: `postgrespassword`)
- Redis 7: `localhost:6379`
- RabbitMQ UI: `http://localhost:15672` (User/Pass: `guest`/`guest`)

### 3. Chạy ứng dụng Spring Boot
```bash
# Trên Linux/macOS:
./mvnw spring-boot:run

# Trên Windows PowerShell:
.\mvnw.cmd spring-boot:run
```
- Swagger UI tài liệu API: `http://localhost:8080/swagger-ui.html`
- Health check Actuator: `http://localhost:8080/actuator/health`

### 4. Chạy toàn bộ bài kiểm thử (Tests)
```bash
# Chạy toàn bộ unit test (Booking Service, Outbox Dispatcher, Outbox Persistence Adapter):
.\mvnw.cmd test "-Dtest=ReservationServiceUnitTest,OutboxDispatcherTest,OutboxRepositoryAdapterTest"

# Chạy Integration Test kiểm tra Redis Lua Script giữ ghế:
.\mvnw.cmd test "-Dtest=RedisInventoryHoldAdapterIntegrationTest"

# Chạy Integration Test kiểm tra Redis Lua Script trừ tồn kho Flash Sale:
.\mvnw.cmd test "-Dtest=RedisInventoryStockAdapterIntegrationTest"

# Chạy Test 100 Threads đồng thời chứng minh Zero Deadlock:
.\mvnw.cmd test "-Dtest=DeadlockPreventionIntegrationTest"
```

---

## 🧪 Hướng Dẫn Kiểm Thử Thủ Công (Manual Testing Flow)

Dành cho việc kiểm tra toàn bộ luồng hoạt động trực quan từ API xuống Database, Redis và RabbitMQ:

1. **Khởi động ứng dụng**: Chạy hàm `main` trong `ReservationEngineApplication.java`.
2. **Mở Swagger UI**: Truy cập `http://localhost:8080/swagger-ui.html`.
3. **Thực hiện đặt chỗ**: Gọi `POST /api/v1/reservations` với body:
   ```json
   {
     "customerId": "CUST-9999",
     "channel": "ONLINE",
     "holdDurationSeconds": 300,
     "items": [
       {
         "inventoryItemId": 1,
         "quantity": 1,
         "unitPrice": 500000.00,
         "isAssignedSeat": true
       }
     ]
   }
   ```
4. **Kiểm tra trạng thái hệ thống**:
   - **PostgreSQL**: Xem bảng `outbox_events` (`SELECT status, processed_at FROM outbox_events;`), trạng thái sẽ chuyển thành `PUBLISHED`.
   - **Redis**: Xem key hold và TTL đếm ngược: `redis-cli get inventory:hold:1` và `redis-cli ttl inventory:hold:1`.
   - **RabbitMQ UI**: Mở `http://localhost:15672` $\rightarrow$ Queues $\rightarrow$ `reservation.created.queue` $\rightarrow$ Get Messages để thấy payload JSON đã được bắn tới.
5. **Kiểm tra Conflict**: Gọi lại request đặt ghế `1` ngay sau đó $\rightarrow$ Hệ thống lập tức từ chối và bảo vệ tài nguyên.

---

## 📊 NHẬT KÝ TIẾN ĐỘ THỰC HIỆN (PROGRESS TRACKER)

### ✅ ĐÃ HOÀN THÀNH (Completed)

#### 1. Khởi tạo Khung Dự án & Hạ tầng chuẩn Clean Architecture
- [x] Tái cấu trúc theo **Bounded Contexts (Package-by-Feature)**: `booking`, `inventory`, `merchandise`, `outbox`, `shared`.
- [x] Docker Compose quản lý 3 service (PostgreSQL 16, Redis 7, RabbitMQ 3.13) với healthcheck.
- [x] Kích hoạt Virtual Threads Java 21 (`spring.threads.virtual.enabled=true`).
- [x] Khởi tạo Flyway migration `V1__init_schema.sql`, `V2__seed_sample_data.sql`, `V3__add_merchandise_schema.sql`.

#### 2. Case Study 1: Multi-Item Deadlock trong đặt giữ chỗ theo nhóm
- [x] **Vấn đề**: 2 giao dịch đồng thời đặt chéo ghế (`[101 -> 102]` vs `[102 -> 101]`) gây ra chu trình chờ khóa (Circular Wait / Lỗi `40P01` của PostgreSQL).
- [x] **Giải pháp**: Áp dụng thuật toán **Canonical Lock Ordering** (sắp xếp ID tài nguyên tăng dần trước khi khóa) kết hợp **Pessimistic Locking** (`SELECT ... FOR UPDATE`).
- [x] **Kiểm chứng**: Bài test `DeadlockPreventionIntegrationTest` kích nổ **100 threads đồng thời** $\rightarrow$ **0 Deadlock phát hiện**.

#### 3. Tầng Bảo Vệ Redis L1 Guard (Atomic Multi-Item Hold)
- [x] **Vấn đề**: Tránh 5.000 requests cùng chọc thẳng vào Database làm cạn kiệt Connection Pool.
- [x] **Giải pháp**: Lua Script `multi_hold.lua` (All-or-Nothing), `InventoryHoldPort`, `RedisInventoryHoldAdapter`, TTL 600s kèm Compensating Action.

#### 4. Case Study 2: Hotspot Inventory Contention & Mô Hình Đặt Chỗ Đa Mô Hình (Hybrid Dual-Guard)
- [x] **Vấn đề**: 10.000 req/s tranh mua số lượng Flash Sale và xử lý đơn hàng hỗn hợp (Ghế VIP định danh + Vé đứng tự do GA).
- [x] **Giải pháp**: Lua Script `decr_stock.lua` & `incr_stock.lua`, `InventoryStockCachePort`, `RedisInventoryStockAdapter`, cơ chế **Cascading Compensation** (hoàn trả ghế nếu trừ vé trượt, hoàn trả cả 2 nếu DB gặp lỗi).
- [x] **Tài liệu**: Biên soạn [`docs/HYBRID_CONCERT_RESERVATION_SCENARIO.md`](docs/HYBRID_CONCERT_RESERVATION_SCENARIO.md).

#### 5. Case Study 3: Transactional Outbox Pattern (Đảm bảo tính nhất quán phân tán - Dual-Write Problem)
*Giải quyết triệt để vấn đề: Lưu Database thành công nhưng bắn Message Queue thất bại (mất event), hoặc bắn Message Queue thành công nhưng DB rollback (gặp phantom event).*
- [x] **Khối 1: Domain & Application Layer**:
  - `ReservationCreatedEvent`: Domain Event mang đầy đủ thông tin (Event-Carried State Transfer).
  - `OutboxEvent`: Application Model biểu diễn bản ghi outbox.
  - `OutboxEventPort`: Driven/Outbound Port interface.
  - `ReservationService`: Ghi nhận sự kiện trong cùng một database transaction với Reservation Aggregate (`@Transactional`).
  - Unit test: `ReservationServiceUnitTest` (4/4 tests pass).
- [x] **Khối 2: Infrastructure Persistence Layer**:
  - `BaseEntity` & `BaseUpdatableEntity`: Tách lớp dùng chung cho audit timestamp (`created_at`, `updated_at`, `@PrePersist`, `@PreUpdate`).
  - `OutboxEventJpaEntity`: JPA Entity ánh xạ bảng `outbox_events` (`status`: `PENDING`, `PUBLISHED`, `FAILED`, `retry_count`, `payload` JSON, `processed_at`).
  - `SpringDataOutboxJpaRepository`: Native query sử dụng `SELECT * FROM outbox_events WHERE status = 'PENDING' AND retry_count < 3 ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED` (kỹ thuật non-blocking concurrency giúp nhiều worker node chạy song song không tranh chấp).
  - `OutboxRepositoryAdapter`: Adapter triển khai `OutboxEventPort` (`save`, `findPendingEventsWithLock`, `markAsPublished`, `markAsFailed`).
  - Unit test: `OutboxRepositoryAdapterTest` (4/4 tests pass).
- [x] **Khối 3: Background Worker & Message Broker Integration**:
  - `OutboxDispatcher`: `@Scheduled(fixedDelay = 1000)` worker liên tục poll các sự kiện `PENDING`.
  - RabbitMQ Integration: Bắn message sang `reservation.topic.exchange` với routing key `reservation.event.created`.
  - Poison Pill & Retry Resilience: Bắt lỗi gửi tin, tăng `retry_count`, tự động đánh dấu `FAILED` khi chạm ngưỡng (`max_retries = 3`).
  - Unit test: `OutboxDispatcherTest` (3/3 tests pass).

---

### ⏳ KẾ HOẠCH BÀI TIẾP THEO (Up Next)
* **Giai đoạn 2 - Case Study 3 (Mở rộng Merchandise Bundle & Add-on)**:
  - Hoàn thiện nghiệp vụ VIP tự động có sẵn Merchandise Kit (Lightstick + Banner).
  - Chặn vé VIP mua thêm Merch lẻ; chỉ cho phép vé thường mua thêm add-on.
  - Outbox Event bắn thông tin Merchandise sang hệ thống Kho Vận (Fulfillment).
* **Giai đoạn 2 - Case Study 4**: Distributed Idempotency 2 tầng (Redis TTL L1 + DB Unique Key L2) chống duplicate request từ client hoặc network retry.
* **Giai đoạn 3**: Distributed Saga Orchestration State Machine (Giữ chỗ -> Thanh toán -> Trừ kho -> Xuất vé) kèm Compensating Transactions.
* **Giai đoạn 4**: Đồng bộ kho Đa kênh (Offline POS Counter vs Online App) giải quyết bài toán rớt mạng Split-Brain.
* **Giai đoạn 5**: Redisson Distributed Lock (Watchdog), Resilience4j Circuit Breaker và kiểm thử phá hủy hệ thống bằng k6.
