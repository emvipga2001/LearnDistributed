package com.omnichannel.reservation.inventory.application.port.out;

import java.util.Map;

public interface InventoryStockCachePort {

    /**
     * Cố gắng trừ tồn kho trên Redis theo cơ chế nguyên tử (All-or-Nothing).
     *
     * @param itemsToDeduct Map chứa itemId và số lượng muốn trừ
     * @return true nếu tất cả các mặt hàng đều đủ tồn kho và đã trừ thành công, false nếu thiếu hàng
     */
    boolean tryDeductStock(Map<Long, Integer> itemsToDeduct);

    /**
     * Tiện ích trừ tồn kho cho 1 mặt hàng đơn lẻ.
     */
    default boolean tryDeductStock(Long itemId, int quantity) {
        return tryDeductStock(Map.of(itemId, quantity));
    }

    /**
     * Hoàn trả lại tồn kho trên Redis (Compensating Action) khi có sự cố ở tầng DB hoặc hủy đơn.
     *
     * @param itemsToRevert Map chứa itemId và số lượng muốn hoàn trả
     */
    void revertStock(Map<Long, Integer> itemsToRevert);

    /**
     * Tiện ích hoàn trả tồn kho cho 1 mặt hàng đơn lẻ.
     */
    default void revertStock(Long itemId, int quantity) {
        revertStock(Map.of(itemId, quantity));
    }

    /**
     * Nạp tồn kho ban đầu (Pre-warming) lên Redis trước giờ mở bán.
     */
    void setStock(Long itemId, int quantity);

    /**
     * Lấy số lượng tồn kho khả dụng hiện tại trên Redis (trả về null nếu key chưa tồn tại).
     */
    Integer getStock(Long itemId);
}
