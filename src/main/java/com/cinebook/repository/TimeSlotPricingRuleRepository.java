package com.cinebook.repository;

import com.cinebook.entity.TimeSlotPricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;

public interface TimeSlotPricingRuleRepository
        extends JpaRepository<TimeSlotPricingRule, String> {

    @Query("""
        SELECT r
        FROM TimeSlotPricingRule r
        WHERE :time >= r.startTime
          AND :time < r.endTime
        """)
    List<TimeSlotPricingRule> findApplicableRules(
            @Param("time") LocalTime time
    );
}