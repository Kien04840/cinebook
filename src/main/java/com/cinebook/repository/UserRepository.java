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

    @Query("""
        SELECT u FROM User u
        WHERE (:status IS NULL OR u.status = :status)
          AND (:keyword IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    Page<User> findAdminUsers(
            @Param("keyword") String keyword,
            @Param("status") UserStatus status,
            Pageable pageable
    );

    long countByCreatedAtBetween(
            java.time.LocalDateTime from,
            java.time.LocalDateTime to
    );

    long countByStatus(UserStatus status);

    @Query("SELECT COUNT(DISTINCT u.id) FROM User u JOIN u.userRoles ur JOIN ur.role r " +
           "WHERE (r.name = 'ADMIN' OR r.name = 'ROLE_ADMIN') " +
           "AND u.status = com.cinebook.enums.UserStatus.ACTIVE " +
           "AND u.deletedAt IS NULL")
    long countActiveAdmins();

    @Query(value = """
        SELECT COUNT(*),
               COALESCE(SUM(CASE WHEN created_at >= :from AND created_at <= :to THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN status = 'BLOCKED' THEN 1 ELSE 0 END), 0)
        FROM users
    """, nativeQuery = true)
    Object[] countUserKpiBetween(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );
}
