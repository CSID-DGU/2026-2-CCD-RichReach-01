package com.richreach.plan.repository;

import com.richreach.plan.entity.PlanJob;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanJobRepository extends JpaRepository<PlanJob, Long> {

    /** 사용자의 작업. 없거나 다른 사용자의 작업이면 비어 있다. */
    Optional<PlanJob> findByJobIdAndUserId(String jobId, Long userId);

    /** 사용자의 가장 최근 작업 중 주어진 상태인 것. */
    Optional<PlanJob> findFirstByUserIdAndStatusOrderByIdDesc(Long userId, String status);

}
