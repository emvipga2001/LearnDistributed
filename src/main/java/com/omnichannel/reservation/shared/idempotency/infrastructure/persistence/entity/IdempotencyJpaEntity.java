package com.omnichannel.reservation.shared.idempotency.infrastructure.persistence.entity;

import com.omnichannel.reservation.shared.idempotency.IdempotencyStatus;
import com.omnichannel.reservation.shared.infrastructure.persistence.entity.BaseUpdatableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyJpaEntity extends BaseUpdatableEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "handler_name", nullable = false, length = 64)
    private String handlerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private IdempotencyStatus status;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;
}
