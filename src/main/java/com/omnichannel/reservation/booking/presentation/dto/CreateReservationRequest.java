package com.omnichannel.reservation.booking.presentation.dto;

import com.omnichannel.reservation.shared.domain.model.Channel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public record CreateReservationRequest(
        @NotNull(message = "Customer ID is required")
        String customerId,

        @NotNull(message = "Channel is required")
        Channel channel,

        @Positive(message = "Hold duration seconds must be positive")
        Long holdDurationSeconds,

        @NotEmpty(message = "At least one item must be reserved")
        @Valid
        List<ReservationItemRequest> items,

        @Valid
        List<MerchandiseOrderRequest> merchandises
) {
    public List<MerchandiseOrderRequest> getSafeMerchandises() {
        return merchandises != null ? merchandises : Collections.emptyList();
    }

    public record ReservationItemRequest(
            @NotNull(message = "Inventory item ID is required")
            Long inventoryItemId,

            @Positive(message = "Quantity must be strictly positive")
            int quantity,

            BigDecimal unitPrice,

            Boolean isAssignedSeat
    ) {
        public boolean assignedSeat() {
            return isAssignedSeat != null ? isAssignedSeat : (quantity == 1);
        }
    }

    public record MerchandiseOrderRequest(
            @NotNull(message = "Merchandise ID is required")
            Long merchandiseId,

            @Positive(message = "Quantity must be strictly positive")
            int quantity
    ) {}
}
