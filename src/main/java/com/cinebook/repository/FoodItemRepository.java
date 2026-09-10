package com.cinebook.repository;

import com.cinebook.entity.FoodItem;
import com.cinebook.enums.FoodItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodItemRepository extends JpaRepository<FoodItem, String> {

    List<FoodItem> findByStatusAndDeletedAtIsNull(FoodItemStatus status);

    List<FoodItem> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    Optional<FoodItem> findByIdAndDeletedAtIsNull(String id);

    boolean existsByNameAndDeletedAtIsNull(String name);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM FoodItem f " +
           "WHERE LOWER(f.name) = LOWER(:name) AND f.id != :id AND f.deletedAt IS NULL")
    boolean existsByNameAndIdNot(@Param("name") String name, @Param("id") String id);

    @Query("SELECT f FROM FoodItem f WHERE f.deletedAt IS NULL " +
           "AND (:keyword IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR f.status = :status) " +
           "ORDER BY f.createdAt DESC")
    List<FoodItem> searchAdminFoodItems(@Param("keyword") String keyword, @Param("status") FoodItemStatus status);
}

