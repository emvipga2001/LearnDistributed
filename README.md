# Omnichannel Booking & Inventory Reservation Engine (LearnDistributed)

Dự án thực chiến xây dựng nền tảng đặt phòng, vé và quản lý tồn kho đa kênh tích hợp quầy POS Offline và Online theo chuẩn Production, áp dụng **Clean Architecture / Domain-Driven Design (DDD)** và phương pháp **Problem-First & Chaos Injection**.

---

## 📌 Công Nghệ Cốt Lõi (Core Tech Stack)
* **Runtime**: Java 21 LTS (Virtual Threads, Records, Pattern Matching, Sealed Classes).
  > ⚠️ **Lưu ý quan trọng**: Phải dùng JDK 21 LTS (ví dụ: `ms-21` hoặc Eclipse Temurin 21). Không dùng JDK 25 vì Lombok chưa tương thích (`TypeTag :: UNKNOWN`).
* **Framework**: Spring Boot 3.3.4 (Spring Data JPA, Spring AMQP / Kafka, Spring Data Redis, Actuator).
* **Datastores & Message Queue**:
  - PostgreSQL 16 (Port `5433` - tránh xung đột với Postgres 5432 native của máy).
  - Redis 7 (Port `6379`) & Redisson 3.36.0 (Distributed Lock, Atomic Lua Script Guard).
  - RabbitMQ 3.13-management (Ports `5672`, `15672`).
* **Database Migration**: Flyway (`V1__init_schema.sql`).
* **Kiểm thử**: JUnit 5, Mockito, Testcontainers, k6 Load Testing.

---

## 🏛️ Cấu Trúc Mã Nguồn (Clean Architecture / Hexagonal)

```text
src/main/java/com/omnichannel/reservation/
├── domain/                      # 100% PURE BUSINESS LOGIC (Zero framework dependencies)
│   ├── model/                   # Aggregates & Entities: InventoryItem, Reservation, ReservationItem, Channel
│   ├── exception/               # Domain Exceptions: InventoryInsufficientException, ReservationIsHeld, ReservationNotFoundException
│   └── repository/              # Domain Port Interfaces: InventoryRepository, ReservationRepository
│
├── application/                 # USE CASES & ORCHESTRATION
│   ├── port/
│   │   ├── in/                  # Inbound Driving Ports: ReserveInventoryUseCase, ReserveInventoryCommand
│   │   └── out/                 # Outbound Driven Ports: InventoryHoldPort (Hợp đồng gọi ra hạ tầng)
│   ├── dto/                     # Application DTOs: ReservationResult
│   └── service/                 # Use Case Implementations: ReservationService (Chống Deadlock + Tầng bảo vệ Redis)
│
├── infrastructure/              # ADAPTERS & EXTERNAL TOOLS
│   ├── config/                  # OpenApiConfig, RedisConfig, RabbitMqConfig
│   ├── persistence/             # Spring Data JPA Entities, Repositories, Repository Adapters
│   └── redis/                   # RedisInventoryHoldAdapter (Thực thi Lua Script giữ chỗ nguyên tử)
│
└── presentation/                # DRIVERS (REST API / WEB)
    ├── rest/                    # ReservationController
    ├── dto/                     # CreateReservationRequest, ReservationResponse
    └── exception/               # GlobalExceptionHandler (RFC 7807 ProblemDetail Advice)
```

---

## 🚀 Hướng Dẫn Chạy Trên Máy Mới (Quickstart For Any Machine)

### 1. Yêu cầu tiên quyết
- Cài đặt **Docker & Docker Compose**.
- Cài đặt **Java 21 LTS** (nhớ trỏ `JAVA_HOME` về JDK 21).

### 2. Khởi động hạ tầng Docker
```bash
docker compose up -d
```
Kiểm tra các container đã chạy:
- PostgreSQL 16: `localhost:5433`
- Redis 7: `localhost:6379`
- RabbitMQ UI: `http://localhost:15672` (user/pass: `guest`/`guest`)

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
# Chạy Unit Test kiểm tra logic giữ chỗ & đền bù:
.\mvnw.cmd test "-Dtest=ReservationServiceUnitTest"

# Chạy Integration Test kiểm tra Redis Lua Script thật:
.\mvnw.cmd test "-Dtest=RedisInventoryHoldAdapterIntegrationTest"

# Chạy Test 100 Threads đồng thời chứng minh Zero Deadlock:
.\mvnw.cmd test "-Dtest=DeadlockPreventionIntegrationTest"
```

---

## 📊 NHẬT KÝ TIẾN ĐỘ THỰC HIỆN (PROGRESS TRACKER)

### ✅ ĐÃ HOÀN THÀNH (Completed)

#### 1. Khởi tạo Khung Dự án & Hạ tầng chuẩn Clean Architecture
- [x] Cấu trúc đa tầng DDD (Domain - Application - Infrastructure - Presentation).
- [x] Docker Compose quản lý 3 service (PostgreSQL, Redis, RabbitMQ) với healthcheck.
- [x] Kích hoạt Virtual Threads Java 21 (`spring.threads.virtual.enabled=true`).
- [x] Khởi tạo Flyway migration `V1__init_schema.sql` với ràng buộc toàn vẹn dữ liệu.

#### 2. Case Study 1: Multi-Item Deadlock trong đặt giữ chỗ theo nhóm
- [x] **Vấn đề**: 2 giao dịch đồng thời đặt chéo ghế (`[101 -> 102]` vs `[102 -> 101]`) gây ra chu trình chờ khóa (Circular Wait / Lỗi `40P01` của PostgreSQL).
- [x] **Giải pháp**: Áp dụng thuật toán **Canonical Lock Ordering** (luôn sắp xếp ID tài nguyên tăng dần trước khi gửi câu lệnh SQL) kết hợp **Pessimistic Locking** (`SELECT ... FOR UPDATE`).
- [x] **Kiểm chứng**: Bài test `DeadlockPreventionIntegrationTest` kích nổ **100 threads đồng thời** bằng `CountDownLatch` $\rightarrow$ **0 Deadlock phát hiện**, bảo toàn tính nhất quán của dữ liệu.

#### 3. Tầng Bảo Vệ Redis L1 Guard (Atomic Multi-Item Hold)
- [x] **Vấn đề**: Tránh 5.000 requests cùng chọc thẳng vào Database làm cạn kiệt Connection Pool.
- [x] **Giải pháp**:
  - Viết Lua Script `multi_hold.lua` đảm bảo tính nguyên tử tuyệt đối (All-or-Nothing) khi giữ nhiều ghế.
  - Phân biệt kiến trúc `port.in` (UseCases) vs `port.out` (Driven Ports). Tạo `InventoryHoldPort`.
  - Viết `RedisInventoryHoldAdapter` nạp Lua script, lưu key `inventory:hold:{id}` kèm TTL (600s).
  - Tích hợp vào `ReservationService`: Chặn đứng tại Redis, và có khối `catch` gọi **Compensating Action (`releaseHold`)** khi DB xảy ra sự cố.
  - Xử lý ngoại lệ nghiệp vụ `ReservationIsHeld` trả về HTTP 409 Conflict.
- [x] **Kiểm chứng**:
  - `ReservationServiceUnitTest`: 3 test cases mockito xác nhận DB không bị đụng đến khi Redis từ chối, và tự động gọi `releaseHold` khi DB lỗi.
  - `RedisInventoryHoldAdapterIntegrationTest`: 3 test cases trên Redis thật xác nhận TTL, tính nguyên tử chặn khách thứ 2 và giải phóng khóa.

---

### ⏳ KẾ HOẠCH BÀI TIẾP THEO (Up Next)
* **Giai đoạn 1 - Case Study 2**: Hotspot Inventory Contention & Giải thuật Trừ tồn kho Flash Sale (Bucketized Inventory / Redis Atomic DECRBY với Lua Script).
* **Giai đoạn 2**: Idempotent Consumer 2 tầng & Transactional Outbox Pattern chống mất mát sự kiện.
* **Giai đoạn 3**: Distributed Saga Orchestration State Machine (Giữ chỗ -> Thanh toán -> Trừ kho -> Xuất vé) kèm Compensating Transactions.
* **Giai đoạn 4**: Đồng bộ kho Đa kênh (Offline POS Counter vs Online App) giải quyết bài toán rớt mạng Split-Brain.
* **Giai đoạn 5**: Redisson Distributed Lock (Watchdog), Resilience4j Circuit Breaker và kiểm thử phá hủy hệ thống bằng k6.
