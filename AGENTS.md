# Omnichannel Booking & Inventory Reservation Engine

## Project Context
- **Stack**: Java 21, Spring Boot 3.3.4, PostgreSQL 16, Redis 7, RabbitMQ 3.13, Docker Compose.
- **Architecture**: Domain-Driven Design (DDD) & Clean Architecture / Hexagonal Architecture.
- **Workflow**: Problem-First & Chaos Injection.

## Package Structure
- `com.omnichannel.reservation.domain`: Aggregates, Entities, Value Objects, Domain Events, Domain Exceptions, Domain Service Interfaces. Zero framework dependencies.
- `com.omnichannel.reservation.application`: Use Case interactors, Commands, Queries, Application Services, Saga Orchestration, Outbox/Event Interfaces.
- `com.omnichannel.reservation.infrastructure`: Spring Data JPA Entities & Repositories, Database Migrations (Flyway), Redis/Redisson Lock Adapters, RabbitMQ Message Publishers/Listeners, Outbox Dispatcher.
- `com.omnichannel.reservation.presentation`: REST Controllers, Request/Response Records, OpenAPI specifications, Controller Advice.

## Coding Standards
- Java 21: Records for DTOs/Value Objects, Virtual Threads enabled (`spring.threads.virtual.enabled=true`), switch expressions, pattern matching.
- Defensive programming: Deterministic resource sorting to prevent deadlocks, two-tier idempotency, outbox pattern for distributed consistency.
- Observability: Trace correlation with `X-Correlation-Id`, Micrometer Tracing.

## Working Agreement & Pacing
- **User-Led Progression**: Tuyệt đối KHÔNG tự ý nhảy sang bài mới hoặc case study tiếp theo.
- **Pacing**: Dừng lại sau mỗi phần để user hiểu sâu, đặt câu hỏi hoặc yêu cầu giải thích thêm.
- **Explicit Permission**: Chỉ khi user nói rõ đã hiểu và cho phép chuyển bài ("qua bài mới", "tiếp tục") thì mới được chuyển sang phần tiếp theo.
- **Hands-on Ownership**: User là người trực tiếp suy nghĩ, thiết kế và viết code; Tech Lead chỉ định hướng, gợi mở, review phản biện và chỉ ra edge cases.
- **Theory-First & No Spoon-Feeding**: Chỉ tập trung giải thích bản chất lý thuyết, cơ chế hoạt động, luồng đi của dữ liệu và phân tích các góc cạnh/edge cases. Tuyệt đối KHÔNG đưa code mẫu quá chi tiết hay viết sẵn cách giải để user tự tay tư duy và code.
