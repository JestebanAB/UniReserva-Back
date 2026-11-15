package com.unireserva.back.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.unireserva.back.model.Reservation;
import com.unireserva.back.model.ReservationStatus;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Unidades ya comprometidas del recurso en el intervalo [start, end). */
    @Query("""
            SELECT COALESCE(SUM(r.quantity), 0) FROM Reservation r
            WHERE r.resource.id = :resourceId
              AND r.status IN :statuses
              AND r.startTime < :end
              AND r.endTime > :start
          """)
    long sumReservedQuantity(
            @Param("resourceId") Long resourceId,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** Reservas que ocupan el recurso dentro de una ventana de tiempo (para calcular disponibilidad). */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.resource.id = :resourceId
              AND r.status IN :statuses
              AND r.startTime < :end
              AND r.endTime > :start
            ORDER BY r.startTime
          """)
    List<Reservation> findOccupying(
            @Param("resourceId") Long resourceId,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT r FROM Reservation r JOIN FETCH r.resource res JOIN FETCH res.category JOIN FETCH r.user
            WHERE r.user.id = :userId
            ORDER BY r.startTime DESC
          """)
    List<Reservation> findByUserWithResource(@Param("userId") Long userId);

    @Query("""
            SELECT r FROM Reservation r JOIN FETCH r.resource res JOIN FETCH res.category JOIN FETCH r.user
            WHERE (:status IS NULL OR r.status = :status)
            ORDER BY r.startTime DESC
          """)
    List<Reservation> findAllForAdmin(@Param("status") ReservationStatus status);

    @Query("""
            SELECT r FROM Reservation r JOIN FETCH r.resource res JOIN FETCH res.category JOIN FETCH r.user
            WHERE r.id = :id
          """)
    Optional<Reservation> findDetailedById(@Param("id") Long id);
}
