package com.cinebook.repository;

import com.cinebook.entity.Auditorium;
import com.cinebook.enums.AuditoriumStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditoriumRepository
        extends JpaRepository<Auditorium, String> {

    List<Auditorium> findByCinemaId(String cinemaId);

    Page<Auditorium> findByCinemaId(
            String cinemaId,
            Pageable pageable
    );

    Optional<Auditorium> findByCinemaIdAndName(
            String cinemaId,
            String name
    );

    boolean existsByCinemaIdAndName(
            String cinemaId,
            String name
    );

    Page<Auditorium> findByStatus(
            AuditoriumStatus status,
            Pageable pageable
    );

    Page<Auditorium> findByCinemaIdAndStatus(
            String cinemaId,
            AuditoriumStatus status,
            Pageable pageable
    );
}