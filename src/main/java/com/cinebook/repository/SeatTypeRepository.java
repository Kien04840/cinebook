package com.cinebook.repository;

import com.cinebook.entity.SeatType;
import com.cinebook.enums.SeatTypeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeatTypeRepository
        extends JpaRepository<SeatType, String> {

    Optional<SeatType> findByName(String name);

    boolean existsByName(String name);

    Page<SeatType> findByStatus(
            SeatTypeStatus status,
            Pageable pageable
    );
}