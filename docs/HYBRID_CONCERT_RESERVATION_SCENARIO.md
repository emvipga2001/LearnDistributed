# 🎫 KỊCH BẢN NGHIỆP VỤ: ĐẶT CHỖ ĐA MÔ HÌNH (HYBRID CONCERT & FLASH SALE RESERVATION)

Tài liệu này mô tả chi tiết thiết kế kiến trúc và luồng xử lý của hệ thống cho kịch bản kết hợp cả **Ghế định danh độc nhất (Assigned Seating)** và **Vé/Hàng hóa số lượng cạnh tranh cao (Flash Sale / Fungible Stock)** trong cùng một nền tảng.

---

## 1. 📌 Bối Cảnh Nghiệp Vụ Thực Tế

Trong các sự kiện âm nhạc quy mô lớn (Mega Concert), đại nhạc hội hoặc khách sạn nghỉ dưỡng cao cấp, một đơn hàng đặt chỗ của khách hàng thường bao gồm 2 nhóm sản phẩm với đặc thù kỹ thuật hoàn toàn khác nhau:

| Phân Loại | Ví Dụ Thực Tế | Đặc Tính Kỹ Thuật | Cơ Chế Bảo Vệ |
| :--- | :--- | :--- | :--- |
| **Ghế định danh độc nhất (Assigned Seating)** | Ghế VIP `SEAT-101`, `SEAT-102` trên khán đài, Bàn VIP số 5, Phòng Suite 301. | Tài nguyên độc nhất (Unique entity). Khách hàng chọn đích danh vị trí. Chỉ cần 1 người chọn thì không ai khác được bấm vào. | **`InventoryHoldPort`**<br>(Gắn cờ giữ khóa độc quyền `inventory:hold:{id}` trên Redis với TTL 10 phút). |
| **Vé số lượng cạnh tranh cao (General Stock / Flash Sale)** | Vé đứng tự do (General Admission Standing Zone B - 5.000 vé), Voucher thức uống giới hạn. | Tài nguyên số lượng (Fungible Pool). Không có số ghế cụ thể. Tải cạnh tranh cực cao (10.000 req/s tranh 5 vé còn lại). | **`InventoryStockCachePort`**<br>(Trừ kho số lượng nguyên tử tại RAM qua Lua Script `decr_stock.lua` All-or-Nothing). |

Một khách hàng có thể đặt một **Đơn hàng kết hợp (Hybrid Reservation)**: 
> *Ví dụ: Mua [1 Ghế VIP A-101] + [2 Vé đứng Zone B] trong cùng 1 lần nhấn nút Thanh toán.*

---

## 2. 🏛️ Luồng Xử Lý Kiến Trúc 3 Pha (Three-Phase Architecture Flow)

```text
                     [ Client gửi CreateReservationRequest ]
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ PHA 1: PHÂN LOẠI YÊU CẦU (Classification Phase)                              │
│ - Tách danh sách mặt hàng thành 2 tập hợp:                                   │
│   + `assignedSeatIds`: Các ghế định danh cụ thể (isAssignedSeat = true)      │
│   + `stockItemsToDeduct`: Các mặt hàng trừ số lượng kho (isAssignedSeat = false)│
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ PHA 2: LÁ CHẮN TỐC ĐỘ CAO TRÊN REDIS (L1 Dual-Guard Phase)                  │
│                                                                             │
│ [Bước 2.1 - Giữ ghế VIP]                                                    │
│ └── Gọi `InventoryHoldPort.tryHold(...)`                                    │
│     ├── Thất bại: Ghế đã bị người khác giữ -> Ném lỗi `ReservationIsHeld` (409)│
│     └── Thành công: Đi tiếp sang Bước 2.2                                  │
│                                                                             │
│ [Bước 2.2 - Trừ kho vé đứng / Flash Sale]                                    │
│ └── Gọi `InventoryStockCachePort.tryDeductStock(...)`                        │
│     ├── Thất bại: Hết số lượng khả dụng ->                                  │
│     │   🔥 BỒI HOÀN LIÊN ĐỚI (Cascading Compensation): Nhả lại ghế VIP đã   │
│     │   giữ ở Bước 2.1, sau đó ném lỗi `InventoryInsufficientException`     │
│     └── Thành công: Cả 2 điều kiện đều thỏa mãn -> Chuyển sang Pha 3        │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ (Vượt qua cửa ngõ Redis Shield)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ PHA 3: TẦNG BỀN VỮNG DATABASE (L2 PostgreSQL Durability Phase)               │
│                                                                             │
│ 1. Sắp xếp toàn bộ ID tăng dần (Canonical Order) để phòng chống Deadlock.   │
│ 2. Khóa ghi bi quan (`SELECT ... FOR UPDATE`) các bản ghi trong DB.         │
│ 3. Tăng `reservedCapacity` trong DB và lưu bản ghi `Reservation` (PENDING). │
│                                                                             │
│ ⚠️ BẢO HIỂM SỰ CỐ (Compensating Safety Net):                                │
│ Nếu Database gặp ngoại lệ (Timeout, đứt kết nối mạng, Disk full):           │
│ └── Khối `catch (Exception ex)` tự động:                                    │
│     + Gọi `inventoryHoldPort.releaseHold` để nhả ghế VIP                     │
│     + Gọi `inventoryStockCachePort.revertStock` để hoàn trả tồn kho vé đứng │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 🛡️ Tính Nguyên Tử & Bồi Hoàn (Atomicity & Compensating Actions)

1. **Nguyên tắc "All-or-Nothing"**:
   * Khách hàng không bao giờ rơi vào trạng thái "giữ được ghế VIP nhưng vé đứng lại hết". Hệ thống đảm bảo tính nguyên tử: nếu một trong hai loại mặt hàng không thể đáp ứng, toàn bộ giao dịch lập tức bị hủy bỏ và trạng thái ban đầu được khôi phục.
2. **Cơ chế Bồi hoàn Liên đới (Cascading Compensation)**:
   * Nếu Phase 2.1 giữ ghế thành công nhưng Phase 2.2 trừ kho thất bại, hệ thống tự động nhả ghế Phase 2.1 ngay lập tức.
3. **Phòng vệ rủi ro Dual-Write (L1 Cache vs L2 Database)**:
   * Do Redis và PostgreSQL là hai hệ thống độc lập, việc dùng `try-catch` bồi hoàn tại tầng Application Service giải quyết triệt để bài toán mất mát tồn kho khi ghi Database thất bại.

---

## 4. 🚀 Hướng Dẫn Sử Dụng API

### Endpoint
* **Method**: `POST`
* **URL**: `/api/v1/reservations`
* **Header**: `Content-Type: application/json`

### Request Body Mẫu (Đặt đơn kết hợp Hybrid)
```json
{
  "customerId": "CUST-9999",
  "channel": "ONLINE",
  "holdDurationSeconds": 600,
  "items": [
    {
      "inventoryItemId": 101,
      "quantity": 1,
      "unitPrice": 2500000,
      "isAssignedSeat": true
    },
    {
      "inventoryItemId": 201,
      "quantity": 2,
      "unitPrice": 850000,
      "isAssignedSeat": false
    }
  ]
}
```

### Response Thành Công (`201 CREATED`)
```json
{
  "reservationId": 1,
  "reservationCode": "RES-E3A5F89B",
  "channel": "ONLINE",
  "status": "PENDING",
  "customerId": "CUST-9999",
  "expiresAt": "2026-10-22T16:15:00Z",
  "items": [
    {
      "itemId": 1,
      "inventoryItemId": 101,
      "quantity": 1
    },
    {
      "itemId": 2,
      "inventoryItemId": 201,
      "quantity": 2
    }
  ]
}
```

---

## 5. 🧪 Kiểm Thử Tự Động Đã Xác Minh (Verification)

Bộ kiểm thử sau đã được chạy và vượt qua 100%:
* `ReservationServiceUnitTest`:
  * Xác nhận từ chối tức thì khi ghế VIP bị giữ trước.
  * Xác nhận kích hoạt **Cascading Compensation** nhả ghế VIP khi vé số lượng hết hàng.
  * Xác nhận kích hoạt bồi hoàn cả 2 tầng khi Database gặp lỗi timeout.
  * Xác nhận đơn Hybrid thành công trọn vẹn.
* `RedisInventoryStockAdapterIntegrationTest`: Kiểm tra 4 kịch bản chạy trực tiếp trên Redis thật.
* `DeadlockPreventionIntegrationTest`: Kích nổ 100 threads đồng thời chứng minh Zero Deadlock.
