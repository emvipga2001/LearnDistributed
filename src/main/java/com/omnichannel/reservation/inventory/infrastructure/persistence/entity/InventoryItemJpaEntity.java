package com.omnichannel.reservation.inventory.infrastructure.persistence.entity;

import com.omnichannel.reservation.shared.infrastructure.persistence.entity.BaseUpdatableEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
public class InventoryItemJpaEntity extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_code", nullable = false, unique = true, length = 64)
    private String itemCode;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Column(name = "reserved_capacity", nullable = false)
    private int reservedCapacity;

    @Column(name = "allocated_pos_buffer", nullable = false)
    private int allocatedPosBuffer;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;


}
