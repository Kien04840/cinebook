package com.cinebook.repository;

import com.cinebook.entity.UserRole;
import com.cinebook.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository
        extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserId(String userId);

    List<UserRole> findByRoleId(String roleId);

    boolean existsByUserIdAndRoleId(
            String userId,
            String roleId
    );
}