package com.richreach.plan.repository;

import com.richreach.plan.entity.PlanCardAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanCardAllocationRepository extends JpaRepository<PlanCardAllocation, Long> {

    List<PlanCardAllocation> findByPlanId(Long planId);
}
