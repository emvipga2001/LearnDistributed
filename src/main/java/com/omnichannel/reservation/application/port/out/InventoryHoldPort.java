package com.omnichannel.reservation.application.port.out;

import java.time.Duration;
import java.util.List;

public interface InventoryHoldPort {
    boolean tryHold(List<Long> itemIds, String customerId, Duration ttl);
    void releaseHold(List<Long> itemIds, String customerId);
}