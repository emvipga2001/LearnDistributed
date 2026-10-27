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
     * @param quantity số lượng muốn mua/giữ
     */
    public void reserve(int quantity) {
        int available = getAvailableStock();
        if (available < quantity) {
            throw new InventoryInsufficientException(itemCode, quantity, available);
        }
        this.reservedStock += quantity;
        this.updatedAt = Instant.now();
    }

    /**
     * Hoàn trả lại số lượng đã giữ khi đơn hàng bị hủy hoặc hết hạn.
     */
    public void release(int quantity) {
        this.reservedStock = Math.max(0, this.reservedStock - quantity);
        this.updatedAt = Instant.now();
    }
}
