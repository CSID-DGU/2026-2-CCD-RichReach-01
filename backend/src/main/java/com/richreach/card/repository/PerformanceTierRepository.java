package com.richreach.card.repository;

import com.richreach.card.entity.PerformanceTier;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceTierRepository extends JpaRepository<PerformanceTier, Long> {

    /**
     * 카드 한 장의 혜택에 붙은 실적 조건을 한 번에 가져온다. 혜택마다 따로 조회하지 않도록 혜택(benefitRule)도 같이 읽는다.
     * 실적 조건이 있는 혜택만 행이 있다.
     */
    @Query("select t from PerformanceTier t join fetch t.benefitRule r where r.cardProduct.id = :cardProductId")
    List<PerformanceTier> findByCardProductId(@Param("cardProductId") Long cardProductId);

}
