package com.omnichannel.reservation.booking.infrastructure.persistence.repository;

import com.omnichannel.reservation.booking.infrastructure.persistence.entity.ReservationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataReservationJpaRepository extends JpaRepository<ReservationJpaEntity, Long> {

    @Query("SELECT r FROM ReservationJpaEntity r LEFT JOIN FETCH r.items WHERE r.reservationCode = :code")
    Optional<ReservationJpaEntity> findByReservationCodeWithItems(@Param("code") String code);
}
