package com.richreach.card.repository;

import com.richreach.card.entity.PerformanceExclusion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceExclusionRepository extends JpaRepository<PerformanceExclusion, Long> {

    @Query("select e from PerformanceExclusion e where e.cardProduct.id = :cardProductId order by e.id")
    List<PerformanceExclusion> findByCardProductId(@Param("cardProductId") Long cardProductId);

}
