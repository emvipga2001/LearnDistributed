package com.omnichannel.reservation.application.port.in;

import com.omnichannel.reservation.domain.model.Channel;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

public record ReserveInventoryCommand(
        String customerId,
        Channel channel,
        Duration holdDuration,
        List<ItemReservationRequest> items
) {
    public record ItemReservationRequest(
            Long inventoryItemId,
            int quantity,
            BigDecimal unitPrice,
            boolean isAssignedSeat
    ) {
        public ItemReservationRequest(Long inventoryItemId, int quantity, BigDecimal unitPrice) {
            this(inventoryItemId, quantity, unitPrice, quantity == 1);
        }
    }
}
