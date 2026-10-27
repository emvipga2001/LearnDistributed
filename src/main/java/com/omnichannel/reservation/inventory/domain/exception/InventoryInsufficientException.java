package com.omnichannel.reservation.inventory.domain.exception;

import com.omnichannel.reservation.shared.domain.exception.BusinessException;

public class InventoryInsufficientException extends BusinessException {
    public InventoryInsufficientException(String itemCode, int requested, int available) {
        super("Insufficient inventory for item: %s. Requested: %d, Available: %d"
                .formatted(itemCode, requested, available));
    }
}
