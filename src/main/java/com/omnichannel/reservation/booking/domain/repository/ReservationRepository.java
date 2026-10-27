package com.omnichannel.reservation.booking.domain.repository;

import com.omnichannel.reservation.booking.domain.model.Reservation;

import java.util.Optional;

public interface ReservationRepository {
    Reservation save(Reservation reservation);
    Optional<Reservation> findById(Long id);
    Optional<Reservation> findByReservationCode(String reservationCode);
}
