package com.omnichannel.reservation.domain.exception;

public class InventoryInsufficientException extends BusinessException {
    public InventoryInsufficientException(String itemCode, int requested, int available) {
        super("Insufficient inventory for item: %s. Requested: %d, Available: %d"
                .formatted(itemCode, requested, available));
    }
}
