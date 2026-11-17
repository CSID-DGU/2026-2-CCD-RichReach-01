package com.richreach.plan.repository;

import com.richreach.plan.entity.PlanChange;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanChangeRepository extends JpaRepository<PlanChange, Long> {

    /** 사용자의 그 달 플랜 변경 기록. 가장 최근 것이 먼저다. */
    List<PlanChange> findByUserIdAndPlanMonthOrderByIdDesc(Long userId, String planMonth);

    /** 사용자의 변경 기록. 없거나 다른 사용자의 것이면 비어 있다. */
    Optional<PlanChange> findByIdAndUserId(Long id, Long userId);
}
