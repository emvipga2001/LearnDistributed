package com.omnichannel.reservation.inventory.domain.model;

import com.omnichannel.reservation.inventory.domain.exception.InventoryInsufficientException;
import lombok.Getter;

import java.time.Instant;

@Getter
public class InventoryItem {
    private final Long id;
    private final String itemCode;
    private final String itemName;
    private final int totalCapacity;
    private int reservedCapacity;
    private int allocatedPosBuffer;
    private final Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public InventoryItem(Long id, String itemCode, String itemName, int totalCapacity,
                         int reservedCapacity, int allocatedPosBuffer, Long version,
                         Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.totalCapacity = totalCapacity;
        this.reservedCapacity = reservedCapacity;
        this.allocatedPosBuffer = allocatedPosBuffer;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getAvailableForOnline() {
        return Math.max(0, totalCapacity - reservedCapacity - allocatedPosBuffer);
    }

    public void reserveForOnline(int quantity) {
        int available = getAvailableForOnline();
        if (available < quantity) {
            throw new InventoryInsufficientException(itemCode, quantity, available);
        }
        this.reservedCapacity += quantity;
        this.updatedAt = Instant.now();
    }

    public void releaseReservation(int quantity) {
        this.reservedCapacity = Math.max(0, this.reservedCapacity - quantity);
        this.updatedAt = Instant.now();
    }

    public void allocatePosBuffer(int buffer) {
        if (this.reservedCapacity + buffer > this.totalCapacity) {
            throw new IllegalArgumentException("Cannot allocate POS buffer exceeding unreserved capacity");
        }
        this.allocatedPosBuffer = buffer;
        this.updatedAt = Instant.now();
    }
}
