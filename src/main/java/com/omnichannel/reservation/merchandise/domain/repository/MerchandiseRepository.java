package com.omnichannel.reservation.merchandise.domain.repository;

import com.omnichannel.reservation.merchandise.domain.model.MerchandiseItem;

import java.util.List;
import java.util.Optional;

/**
 * Domain Repository Port: Quản lý lưu trữ và khóa dòng merchandise trong Database.
 */
public interface MerchandiseRepository {

    Optional<MerchandiseItem> findById(Long id);

    /**
     * Tìm danh sách Merchandise theo IDs kèm Pessimistic Lock (SELECT ... FOR UPDATE).
     * Áp dụng Canonical Ordering để chống Deadlock.
     */
    List<MerchandiseItem> findAllByIdWithLock(List<Long> ids);

    void save(MerchandiseItem merchandiseItem);

    void saveAll(List<MerchandiseItem> merchandiseItems);
}
