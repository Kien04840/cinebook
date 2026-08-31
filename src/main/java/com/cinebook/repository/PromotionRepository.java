package com.cinebook.repository;

import com.cinebook.entity.Promotion;
import com.cinebook.enums.PromotionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, String> {

    Optional<Promotion> findByCode(String code);

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Promotion p WHERE p.code = :code")
    Optional<Promotion> findByCodeWithLock(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Promotion p WHERE p.id = :id")
    Optional<Promotion> findByIdWithLock(@Param("id") String id);

    Page<Promotion> findByStatus(
            PromotionStatus status,
            Pageable pageable
    );

    @Query("SELECT p FROM Promotion p WHERE " +
            "(:status IS NULL OR p.status = :status) AND " +
            "(:keyword IS NULL OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Promotion> findAdminPromotions(
            @Param("status") PromotionStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}