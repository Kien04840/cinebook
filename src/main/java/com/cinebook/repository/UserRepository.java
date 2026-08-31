package com.cinebook.repository;

import com.cinebook.entity.User;
import com.cinebook.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Page<User> findByStatus(
            UserStatus status,
            Pageable pageable
    );

    @Query("""
        SELECT u
        FROM User u
        WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
    """)
    Page<User> searchByNameOrEmail(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    long countByCreatedAtBetween(
            java.time.LocalDateTime from,
            java.time.LocalDateTime to
    );

    long countByStatus(UserStatus status);
}