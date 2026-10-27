package com.omnichannel.reservation.merchandise.infrastructure.persistence.entity;

import com.omnichannel.reservation.booking.infrastructure.persistence.entity.ReservationJpaEntity;
import com.omnichannel.reservation.shared.infrastructure.persistence.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "reservation_merchandises")
@Getter
@Setter
@NoArgsConstructor
public class ReservationMerchandiseJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private ReservationJpaEntity reservation;

    @Column(name = "merchandise_id", nullable = false)
    private Long merchandiseId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;
}
