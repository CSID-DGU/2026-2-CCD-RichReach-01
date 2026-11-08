package com.richreach.transaction.repository;

import com.richreach.transaction.entity.MerchantCategoryRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantCategoryRuleRepository extends JpaRepository<MerchantCategoryRule, Long> {

    /** 쓰는 규칙(active)만. 적용 순서는 분류기가 정한다. */
    List<MerchantCategoryRule> findByActiveTrue();

}
