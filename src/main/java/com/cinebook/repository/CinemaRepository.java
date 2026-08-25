package com.cinebook.repository;

import com.cinebook.entity.Cinema;
import com.cinebook.enums.CinemaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CinemaRepository extends JpaRepository<Cinema, String>, JpaSpecificationExecutor<Cinema> {

    Optional<Cinema> findByIdAndDeletedAtIsNull(String id);

    boolean existsByNameAndCityAndDeletedAtIsNull(String name, String city);

    Page<Cinema> findByStatus(CinemaStatus status, Pageable pageable);

    Page<Cinema> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Cinema> findByCityContainingIgnoreCase(String city, Pageable pageable);
}