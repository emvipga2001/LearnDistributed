package com.omnichannel.reservation.booking.application.port.in;

import com.omnichannel.reservation.shared.domain.model.Channel;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public record ReserveInventoryCommand(
        String customerId,
        Channel channel,
        Duration holdDuration,
        List<ItemReservationRequest> items,
        List<MerchandiseReservationRequest> merchandises
) {
    public ReserveInventoryCommand(String customerId, Channel channel, Duration holdDuration, List<ItemReservationRequest> items) {
        this(customerId, channel, holdDuration, items, Collections.emptyList());
    }

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

    public record MerchandiseReservationRequest(
            Long merchandiseId,
            int quantity
    ) {}
}
