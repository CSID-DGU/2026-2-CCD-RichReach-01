package com.richreach.card.repository;

import com.richreach.card.entity.BenefitRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BenefitRuleRepository extends JpaRepository<BenefitRule, Long> {

    /** 카드 한 장의 혜택 규칙. 항상 같은 순서로 내려주려고 id 순으로 정렬한다. */
    @Query("select r from BenefitRule r where r.cardProduct.id = :cardProductId order by r.id")
    List<BenefitRule> findByCardProductId(@Param("cardProductId") Long cardProductId);

}
