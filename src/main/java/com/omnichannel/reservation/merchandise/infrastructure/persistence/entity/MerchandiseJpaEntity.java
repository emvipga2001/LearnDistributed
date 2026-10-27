package com.omnichannel.reservation.merchandise.infrastructure.persistence.entity;

import com.omnichannel.reservation.shared.infrastructure.persistence.entity.BaseUpdatableEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "merchandise_items")
@Getter
@Setter
@NoArgsConstructor
public class MerchandiseJpaEntity extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_code", nullable = false, unique = true, length = 64)
    private String itemCode;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    @Column(name = "reserved_stock", nullable = false)
    private int reservedStock;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
