package com.omnichannel.reservation.infrastructure.persistence.entity;

import com.omnichannel.reservation.domain.model.Channel;
import com.omnichannel.reservation.domain.model.ReservationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
public class ReservationJpaEntity extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_code", nullable = false, unique = true, length = 64)
    private String reservationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReservationStatus status;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ReservationItemJpaEntity> items = new ArrayList<>();



    public void addItem(ReservationItemJpaEntity item) {
        items.add(item);
        item.setReservation(this);
    }
}
