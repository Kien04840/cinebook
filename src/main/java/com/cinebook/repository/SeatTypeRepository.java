package com.cinebook.repository;

import com.cinebook.entity.SeatType;
import com.cinebook.enums.SeatTypeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatTypeRepository extends JpaRepository<SeatType, String> {

    Optional<SeatType> findByName(String name);

    Optional<SeatType> findByNameIgnoreCase(String name);

    boolean existsByName(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, String id);

    List<SeatType> findByStatus(SeatTypeStatus status);

    Page<SeatType> findByStatus(SeatTypeStatus status, Pageable pageable);
}