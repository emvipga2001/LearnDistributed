# BẢN ĐỒ KIẾN THỨC KIẾN TRÚC HỆ THỐNG PHÂN TÁN (DISTRIBUTED SYSTEMS CORE KNOWLEDGE BASE)

Tài liệu này tổng hợp toàn bộ các nguyên lý lý thuyết, mẫu thiết kế (Design Patterns), cơ chế giải quyết xung đột và tư duy kiến trúc phân tán cốt lõi đã học, được trừu tượng hóa hoàn toàn khỏi phạm vi một dự án cụ thể.

---

## MỤC LỤC
1. [Xử lý tranh chấp đồng thời & Triệt tiêu Deadlock (Concurrency & Deadlocks)](#1-xử-lý-tranh-chấp-đồng-thời--triệt-tiêu-deadlock)
2. [Tầng bảo vệ L1 In-Memory Guard & Scripting nguyên tử (Redis Atomic Lua)](#2-tầng-bảo-vệ-l1-in-memory-guard--scripting-nguyên-tử)
3. [Bài toán Dual-Write & Transactional Outbox Pattern](#3-bài-toán-dual-write--transactional-outbox-pattern)
4. [Tính bất biến & Idempotency phân tán hai tầng (Two-Tier Idempotency)](#4-tính-bất-biến--idempotency-phân-tán-hai-tầng)
5. [Saga Orchestration & Máy trạng thái hữu hạn (State Machine)](#5-saga-orchestration--máy-trạng-thái-hữu-hạn)
6. [Tích hợp dịch vụ bên thứ ba & Kiến trúc Webhook (Asynchronous Callbacks)](#6-tích-hợp-dịch-vụ-bên-thứ-ba--kiến-trúc-webhook)

---

## 1. XỬ LÝ TRANH CHẤP ĐỒNG THỜI & TRIỆT TIÊU DEADLOCK

### 1.1. Bản chất của Deadlock đa tài nguyên (Multi-Resource Deadlock)
* **Nguyên nhân gốc rễ (Coffman Conditions)**: Xảy ra khi 2 hay nhiều giao dịch (Transactions) đồng thời yêu cầu khóa độc quyền trên nhiều tài nguyên theo **thứ tự chéo nhau** (Circular Wait):
  * Transaction A: Khóa tài nguyên `X`, chờ khóa tài nguyên `Y`.
  * Transaction B: Khóa tài nguyên `Y`, chờ khóa tài nguyên `X`.
  * $\rightarrow$ Hệ quản trị cơ sở dữ liệu (DBMS) rơi vào bế tắc vô tận và buộc phải kích hoạt cơ chế Deadlock Detector để hy sinh (kill/rollback) một transaction với mã lỗi `40P01`.

### 1.2. Giải pháp triệt tiêu: Canonical Lock Ordering
* **Nguyên lý toán học**: Nếu tất cả các tiến trình trong hệ thống đều bị bắt buộc phải xin khóa tài nguyên theo một **thứ tự đơn điệu tăng dần duy nhất** (ví dụ sắp xếp theo Primary Key `ID` tăng dần):
  $$\text{Resource}_1 < \text{Resource}_2 < \dots < \text{Resource}_n$$
* **Kết quả**: Đồ thị phụ thuộc tài nguyên (Resource Allocation Graph) được chuyển hóa từ **Đồ thị có chu trình (Cyclic Graph)** thành **Đồ thị có hướng không chu trình (Directed Acyclic Graph - DAG)** $\rightarrow$ **Về mặt toán học, Deadlock bị triệt tiêu 100%**.

### 1.3. Khóa bi quan (Pessimistic) vs Khóa lạc quan (Optimistic)
* **Pessimistic Locking (`SELECT ... FOR UPDATE`)**: Dành cho bài toán tranh chấp cao (High Contention), nơi xung đột xảy ra thường xuyên. Transaction giữ khóa vật lý ở cấp độ dòng (row-level lock) để ngăn các transaction khác đọc/ghi đè.
* **Optimistic Locking (`@Version`)**: Dành cho bài toán tranh chấp thấp (Low Contention), kiểm tra phiên bản dữ liệu lúc `UPDATE`. Nếu có va chạm thì retry. Nếu áp dụng vào luồng tranh chấp nghìn request/giây, Optimistic Lock sẽ gây bão Exception và lãng phí CPU do phải rollback liên tục.

---

## 2. TẦNG BẢO VỆ L1 IN-MEMORY GUARD & SCRIPTING NGUYÊN TỬ

### 2.1. Vấn đề nghẽn Connection Pool Database
* Khi hàng nghìn request ập đến cùng 1 giây, nếu tất cả chọc thẳng xuống Database để `SELECT FOR UPDATE`, toàn bộ Connection Pool (ví dụ HikariCP max 10-20 connections) sẽ cạn kiệt trong vài mili-giây $\rightarrow$ Hệ thống nghẽn mạng dây chuyền (Cascading Failure).

### 2.2. Atomic Lua Scripting trên In-Memory Store
* **Cơ chế**: Redis chạy đơn luồng (Single-Threaded Execution Engine) cho việc thực thi lệnh. Khi một đoạn mã Lua được gửi đến, Redis thực thi toàn bộ script từ đầu đến cuối như một khối lệnh đơn nhất.
* **Tính chất All-or-Nothing**: Kiểm tra điều kiện (Check) $\rightarrow$ Ghi dữ liệu (Set) $\rightarrow$ Đặt thời gian sống (Expire TTL) diễn ra trong cùng 1 chu kỳ vi xử lý, không một request nào khác có thể chen ngang.
* **Safe Release Guard (Kiểm tra quyền sở hữu trước khi nhả khóa)**:
  * Tránh lỗi kinh điển: Transaction A chạy chậm vượt quá TTL của lock $\rightarrow$ Lock tự hết hạn $\rightarrow$ Transaction B vào chiếm lock $\rightarrow$ Transaction A hoàn tất và chạy lệnh `DEL lock` $\rightarrow$ Transaction A xóa nhầm lock của Transaction B!
  * **Giải pháp**: Phải dùng Lua Script kiểm tra giá trị chủ sở hữu trước khi xóa: `if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end`.

---

## 3. BÀI TOÁN DUAL-WRITE & TRANSACTIONAL OUTBOX PATTERN

### 3.1. Nghịch lý ghi phân tán (The Dual-Write Problem)
Trong kiến trúc phân tán, việc thực hiện 2 thao tác ghi vào 2 hệ thống độc lập không bao giờ có thể đảm bảo ACID bằng các kỹ thuật thông thường:
```
Ghi Database (Postgres)  <-- Hệ thống 1
Bắn Message Queue (MQ)   <-- Hệ thống 2
```
* Nếu **Lưu DB trước $\rightarrow$ Bắn MQ sau**: DB commit xong, mạng chập chờn khiến bắn MQ lỗi $\rightarrow$ Mất sự kiện vĩnh viễn, các service vệ tinh không bao giờ biết dữ liệu đã thay đổi.
* Nếu **Bắn MQ trước $\rightarrow$ Lưu DB sau**: MQ nhận tin và Consumer xử lý xong, nhưng DB chính lại gặp lỗi ràng buộc và Rollback $\rightarrow$ Xuất hiện dữ liệu ma (Phantom Data / Ghost Events).

### 3.2. Transactional Outbox Pattern
* **Bản chất**: Thay vì bắn MQ trực tiếp, ta tạo thêm một bảng `outbox_events` nằm **ngay trong cùng một Database** với các bảng nghiệp vụ.
* **Nguyên tắc ACID cục bộ**:
  $$\text{Transaction bắt đầu} \rightarrow \text{Cập nhật dữ liệu nghiệp vụ} \rightarrow \text{INSERT vào bảng outbox\_events} \rightarrow \text{Commit}$$
  Cả 2 hành động cùng thành công hoặc cùng rollback 100%.
* **Dispatcher (Anh bưu tá)**: Một tiến trình chạy nền quét bảng Outbox để bắn sang Message Broker. Khi bắn thành công thì cập nhật trạng thái `PUBLISHED`.
* **Kỹ thuật Non-Blocking Concurrency (`FOR UPDATE SKIP LOCKED`)**:
  * Khi mở rộng nhiều máy chủ Dispatcher chạy song song, `SKIP LOCKED` cho phép Node 1 khóa 10 dòng đầu, Node 2 tự động nhảy cóc qua 10 dòng đó để lấy 10 dòng tiếp theo mà không bị xung đột, nghẽn hay khóa lẫn nhau.

### 3.3. Định danh và Tiến hóa Outbox
* `aggregate_type` & `event_type`: Căn cứ để định tuyến tin nhắn động (`<aggregate_type>.<event_type>`) và phục vụ việc giải mã đa hình (Polymorphic Deserialization) ở phía Consumer.
* **Lộ trình tiến hóa**: Polling Scheduler $\rightarrow$ Phân tách bảng Outbox theo Bounded Context $\rightarrow$ Change Data Capture (CDC / Debezium) đọc trực tiếp từ Database Write-Ahead Log (WAL) để đạt độ trễ sub-millisecond mà không tải DB.

---

## 4. TÍNH BẤT BIẾN & IDEMPOTENCY PHÂN TÁN HAI TẦNG

### 4.1. Bản chất mạng phân tán: At-Least-Once Delivery
* Trên đường truyền mạng, không tồn tại cơ chế gửi chính xác 1 lần duy nhất (Exactly-Once) ở tầng truyền dẫn vật lý. Client bấm đúp chuột (double-click), mạng timeout khiến gateway tự động gửi lại (retry), hoặc broker re-deliver message.
* Do đó, **mọi endpoint thay đổi trạng thái (State-changing APIs) đều phải có tính Idempotent** (Xử lý nhiều lần cho ra kết quả giống hệt xử lý một lần).

### 4.2. Kiến trúc Idempotency 2 tầng (Two-Tier Architecture)
* **Tầng 1 - In-Memory Fast Mutex (Redis `SETNX` kèm TTL ngắn ~120s)**:
  * Chặn đứng bão request đồng thời (Concurrent Request Storm) trong khoảng thời gian mili-giây. Chỉ duy nhất request đầu tiên lấy được khóa để vào xử lý.
* **Tầng 2 - Persistent Store (PostgreSQL Permanent Table)**:
  * Lưu trữ vĩnh viễn kết quả xử lý (Response Payload Body, HTTP Status, Created At).
  * Khi các request retry đến muộn (vài phút, vài ngày sau), hệ thống tra cứu DB và trả ngay kết quả đã lưu trong bộ nhớ đệm mà không tái thực thi nghiệp vụ lõi.

### 4.3. Băm dấu vân tay Payload (Request Fingerprinting với SHA-256)
* **Rủi ro**: Client tái sử dụng cùng một `Idempotency-Key` nhưng lại tráo đổi nội dung payload body (ví dụ đổi giá trị từ 100.000đ thành 10.000.000đ).
* **Giải pháp**: Băm toàn bộ request body thành chuỗi mã hóa SHA-256. Nếu cùng key nhưng hash khác nhau $\rightarrow$ Từ chối xử lý ngay lập tức với mã lỗi `422 Unprocessable Entity` (Payload Mismatch).

---

## 5. SAGA ORCHESTRATION & MÁY TRẠNG THÁI HỮU HẠN

### 5.1. Tại sao không thể dùng 2-Phase Commit (2PC / XA Transactions)?
* 2PC đòi hỏi giữ kết nối và khóa tài nguyên trên tất cả các dịch vụ tham gia suốt toàn bộ quy trình.
* Trong môi trường vi dịch vụ và tích hợp hệ thống bên ngoài (Cổng thanh toán, Ngân hàng), 2PC gây suy giảm hiệu năng khủng khiếp, dễ dẫn đến nghẽn tắc toàn hệ thống khi có 1 node mạng chậm.

### 5.2. Saga Pattern & Hai trường phái
Saga chia một giao dịch lớn kéo dài (Long-Running Transaction) thành chuỗi các giao dịch cục bộ (Local Transactions).
* **Choreography (Phi tập trung / Hướng sự kiện thuần)**:
  * Các dịch vụ tự lắng nghe sự kiện của nhau và tự phản ứng.
  * *Nhược điểm*: Khó kiểm soát luồng đi tổng thể, dễ tạo chu trình vòng lặp sự kiện (Cyclic Dependencies), khó debug khi lỗi.
* **Orchestration (Tập trung / Có nhạc trưởng)**:
  * Một bộ điều phối trung tâm (`SagaCoordinator`) nắm giữ toàn bộ trạng thái của quy trình, ra lệnh cho từng bên thực thi và quyết định khi nào cần bồi hoàn.
  * *Ưu điểm*: Luồng dữ liệu tường minh, dễ theo dõi, giám sát (Observability) và kiểm soát rủi ro.

### 5.3. Giao dịch bồi hoàn (Compensating Transactions)
* Không có khái niệm "Rollback tự động" qua các hệ thống phân tán.
* Khi một bước trong chuỗi Saga thất bại:
  $$\text{Thất bại tại Bước } N \rightarrow \text{Kích hoạt các thao tác bồi hoàn ngữ nghĩa: } C_{N-1}, C_{N-2}, \dots, C_1$$
* **Yêu cầu sống còn của hành động bồi hoàn**:
  1. Phải có tính **Idempotent** (có thể gọi lại nhiều lần mà không sai lệch).
  2. Không được phép thất bại (hoặc phải retry cho tới khi thành công).

### 5.4. Máy trạng thái hữu hạn (Finite State Machine - Domain Invariants)
* Mọi thực thể nghiệp vụ cốt lõi phải được bảo vệ bởi máy trạng thái:
  * Trạng thái khởi tạo: `PENDING`
  * Trạng thái chuyển đổi hợp lệ: `PENDING` $\rightarrow$ `CONFIRMED` | `CANCELLED` | `EXPIRED`
  * Trạng thái kết thúc (Terminal States): `CONFIRMED`, `CANCELLED`, `EXPIRED`. Không bao giờ cho phép chuyển đổi qua lại giữa các trạng thái kết thúc.
* Tuyệt đối không để lộ các hàm setter tùy tiện (`setStatus`), mọi thay đổi trạng thái phải đi qua hành vi nghiệp vụ rõ ràng (`confirm()`, `cancel()`, `expire()`).

---

## 6. TÍCH HỢP DỊCH VỤ BÊN THỨ BA & KIẾN TRÚC WEBHOOK

### 6.1. Return URL vs Webhook IPN (Instant Payment Notification)
* **Return URL (Client-Side Redirect)**:
  * Chỉ phục vụ hiển thị giao diện cho người dùng (UI/UX).
  * **Tuyệt đối không dùng để chốt trạng thái nghiệp vụ** vì người dùng có thể can thiệp tham số, rớt mạng, hoặc tắt app trước khi redirect hoàn tất.
* **Webhook IPN (Server-to-Server Callback)**:
  * Máy chủ đối tác gọi trực tiếp vào máy chủ của ta qua giao thức an toàn.
  * Là **Nguồn sự thật duy nhất (Single Source of Truth)** để kích hoạt bước tiếp theo trong chuỗi Saga.

### 6.2. Semantics phản hồi HTTP cho Webhook
* Khi nhận được kết quả thất bại từ đối tác (ví dụ: thẻ không đủ tiền), nếu hệ thống của ta đã xử lý bồi hoàn và hủy đơn trơn tru:
  * **Bắt buộc phải trả về HTTP 200 OK** cho máy chủ đối tác.
  * Vì từ góc độ hạ tầng phân tán, giao dịch thông báo đã được tiếp nhận và xử lý thành công. Nếu trả về lỗi 4xx/5xx, đối tác sẽ hiểu lầm là server bị sự cố và kích hoạt bão Retry (Retry Storm) liên tục.

---
*Tài liệu này đóng vai trò là kim chỉ nam kiến trúc phân tán nền tảng trước khi bước vào các bài toán nâng cao về phân vùng mạng và giải quyết xung đột dữ liệu.*
