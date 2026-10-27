package com.omnichannel.reservation.merchandise.domain.model;

import com.omnichannel.reservation.inventory.domain.exception.InventoryInsufficientException;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain Entity: Quản lý hàng hóa vật lý (Banner, Lightstick...)
 * Nghiệp vụ cốt lõi: Kiểm tra tồn kho và tăng/giảm số lượng giữ chỗ (reserved_stock).
 */
@Getter
public class MerchandiseItem {
    private final Long id;
    private final String itemCode;
    private final String itemName;
    private final int totalStock;
    private int reservedStock;
    private final BigDecimal unitPrice;
    private final Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public MerchandiseItem(Long id, String itemCode, String itemName, int totalStock,
                           int reservedStock, BigDecimal unitPrice, Long version,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.totalStock = totalStock;
        this.reservedStock = reservedStock;
        this.unitPrice = unitPrice;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Lấy số lượng khả dụng còn lại trong kho.
     */
    public int getAvailableStock() {
        return Math.max(0, totalStock - reservedStock);
    }

    /**
     * Giữ kho cho sản phẩm merchandise.
     * TODO: User sẽ thực hiện kiểm tra điều kiện tồn kho và cập nhật reservedStock.
     * @param quantity số lượng muốn mua/giữ
     */
    public void reserve(int quantity) {
        // TODO: Kiểm tra số lượng tồn khả dụng (availableStock).
        // Nếu không đủ, bắn Exception tương ứng (ví dụ: InventoryInsufficientException).
        // Nếu đủ, cộng thêm quantity vào reservedStock và cập nhật updatedAt.
    }

    /**
     * Hoàn trả lại số lượng đã giữ khi đơn hàng bị hủy hoặc hết hạn.
     */
    public void release(int quantity) {
        // TODO: Giảm reservedStock một cách an toàn (không âm) và cập nhật updatedAt.
    }
}
