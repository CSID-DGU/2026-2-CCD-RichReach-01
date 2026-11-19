package com.richreach.plannedexpense.repository;

import com.richreach.plannedexpense.entity.PlannedExpense;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlannedExpenseRepository extends JpaRepository<PlannedExpense, Long> {

    /** 그 달의 예정지출. 가장 최근에 만든 것이 먼저다. */
    List<PlannedExpense> findByUserIdAndPlanMonthOrderByIdDesc(Long userId, String planMonth);

    Optional<PlannedExpense> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndPlanMonth(Long userId, String planMonth);

}
