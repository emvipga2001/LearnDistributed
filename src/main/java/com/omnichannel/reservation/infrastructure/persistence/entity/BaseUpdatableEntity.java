package com.omnichannel.reservation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Base Entity mở rộng cho các bảng có trường cập nhật (updated_at).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseUpdatableEntity extends BaseEntity {

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
