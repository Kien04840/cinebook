package com.cinebook.repository;

import com.cinebook.entity.Promotion;
import com.cinebook.enums.PromotionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, String> {

    Optional<Promotion> findByCode(String code);

    boolean existsByCode(String code);

    Page<Promotion> findByStatus(
            PromotionStatus status,
            Pageable pageable
    );
}