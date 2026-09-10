package com.cinebook.repository;

import com.cinebook.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, String> {

    Optional<Role> findByName(String name);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT r FROM Role r WHERE r.name = :name")
    Optional<Role> findByNameWithLock(@org.springframework.data.repository.query.Param("name") String name);

    boolean existsByName(String name);
}