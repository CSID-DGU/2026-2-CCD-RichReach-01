package com.richreach.plan.repository;

import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanStrategy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyPlanRepository extends JpaRepository<MonthlyPlan, Long> {

    /** 사용자·월·전략의 가장 최신 version 플랜 */
    Optional<MonthlyPlan> findFirstByUserIdAndPlanMonthAndStrategyOrderByVersionDesc(Long userId, String planMonth,
                                                                                   PlanStrategy strategy);

    List<MonthlyPlan> findByUserIdAndPlanMonth(Long userId, String planMonth);
}
