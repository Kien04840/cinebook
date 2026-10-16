package com.cinebook.repository;

import com.cinebook.entity.Seat;
import com.cinebook.enums.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, String> {

    List<Seat> findByAuditoriumId(String auditoriumId);

    List<Seat> findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc(String auditoriumId);

    List<Seat> findByAuditoriumIdAndStatus(String auditoriumId, SeatStatus status);

    List<Seat> findByIdIn(Collection<String> ids);

    List<Seat> findByIdInAndAuditoriumId(Collection<String> ids, String auditoriumId);

    Optional<Seat> findByAuditoriumIdAndRowLabelAndSeatNumber(
            String auditoriumId,
            String rowLabel,
            Short seatNumber
    );

    boolean existsByAuditoriumIdAndRowLabelAndSeatNumber(
            String auditoriumId,
            String rowLabel,
            Short seatNumber
    );

    long countByAuditoriumId(String auditoriumId);

    long countByAuditoriumIdAndStatus(String auditoriumId, SeatStatus status);

    boolean existsBySeatTypeId(String seatTypeId);

    long countBySeatTypeId(String seatTypeId);

    List<Seat> findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc(String auditoriumId, String rowLabel);

    @org.springframework.data.jpa.repository.Query("""
        SELECT COALESCE(SUM(st.capacity), 0)
        FROM Seat s
        JOIN s.seatType st
        WHERE s.auditorium.id = :auditoriumId
          AND s.status = :status
    """)
    long sumCapacityByAuditoriumIdAndStatus(
            @org.springframework.data.repository.query.Param("auditoriumId") String auditoriumId,
            @org.springframework.data.repository.query.Param("status") SeatStatus status
    );

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Seat s WHERE s.auditorium.id = :auditoriumId")
    void deleteByAuditoriumId(@org.springframework.data.repository.query.Param("auditoriumId") String auditoriumId);

    @org.springframework.data.jpa.repository.Query(value = """
        SELECT s.auditorium_id AS auditorium_id,
               COALESCE(SUM(st.capacity), 0) AS total_capacity
        FROM seats s
        JOIN seat_types st ON s.seat_type_id = st.id
        WHERE s.status = 'ACTIVE'
          AND s.auditorium_id IN (:auditoriumIds)
        GROUP BY s.auditorium_id
    """, nativeQuery = true)
    List<Object[]> sumCapacityByAuditoriumIds(
            @org.springframework.data.repository.query.Param("auditoriumIds") Collection<String> auditoriumIds
    );
}