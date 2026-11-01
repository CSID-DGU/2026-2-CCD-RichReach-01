package com.richreach.card.service;

import com.richreach.card.dto.CardBenefitsResponse;
import com.richreach.card.entity.CardProduct;
import com.richreach.card.repository.BenefitRuleRepository;
import com.richreach.card.repository.CardProductRepository;
import com.richreach.card.repository.PerformanceExclusionRepository;
import com.richreach.card.repository.PerformanceTierRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드 상품의 혜택 조회. 카드 혜택은 사용자와 무관한 공개 정보라서 소유 여부는 확인하지 않는다.
 * 발급 중단 카드도 조회할 수 있다. (신규 추천에서만 빠지고, 이미 가진 카드의 분석에는 계속 쓰기 때문이다.)
 */
@Service
public class CardBenefitService {

    private final CardProductRepository cards;
    private final BenefitRuleRepository benefitRules;
    private final PerformanceTierRepository performanceTiers;
    private final PerformanceExclusionRepository exclusions;

    public CardBenefitService(CardProductRepository cards, BenefitRuleRepository benefitRules,
                              PerformanceTierRepository performanceTiers, PerformanceExclusionRepository exclusions) {
        this.cards = cards;
        this.benefitRules = benefitRules;
        this.performanceTiers = performanceTiers;
        this.exclusions = exclusions;
    }

    /** 승격된 카드 상품만 조회된다. 없는 카드는 {@link ErrorCode#CARD_NOT_FOUND}다. */
    @Transactional(readOnly = true)
    public CardBenefitsResponse getBenefits(long cardProductId) {
        CardProduct card = cards.findById(cardProductId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
        return CardBenefitsResponse.of(card,
            benefitRules.findByCardProductId(cardProductId),
            performanceTiers.findByCardProductId(cardProductId),
            exclusions.findByCardProductId(cardProductId));
    }

}
