package com.omnichannel.reservation.presentation.dto;

import com.omnichannel.reservation.domain.model.Channel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
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
        List<ReservationItemRequest> items
) {
    public record ReservationItemRequest(
            @NotNull(message = "Inventory item ID is required")
            Long inventoryItemId,

            @Positive(message = "Quantity must be strictly positive")
            int quantity,

            BigDecimal unitPrice
    ) {}
}
