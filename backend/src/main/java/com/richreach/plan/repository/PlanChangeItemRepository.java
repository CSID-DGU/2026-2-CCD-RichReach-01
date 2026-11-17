package com.richreach.plan.repository;

import com.richreach.plan.entity.PlanChangeItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanChangeItemRepository extends JpaRepository<PlanChangeItem, Long> {

    List<PlanChangeItem> findByChangeIdInOrderById(Collection<Long> changeIds);
}
