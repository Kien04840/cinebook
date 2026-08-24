package com.cinebook.repository;

import com.cinebook.entity.DayPricingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DayPricingRuleRepository
        extends JpaRepository<DayPricingRule, String> {

    Optional<DayPricingRule> findByDayOfWeek(String dayOfWeek);

    boolean existsByDayOfWeek(String dayOfWeek);
}