package com.richreach.card.service;

import com.richreach.card.dto.CalculationRule;
import com.richreach.benefit.dto.TransactionBenefitRule;
import com.richreach.card.entity.BenefitRule;
import com.richreach.card.entity.PerformanceTier;
import com.richreach.card.repository.BenefitRuleRepository;
import com.richreach.card.repository.PerformanceTierRepository;
import com.richreach.card.service.BenefitCategoryMapper.MappedCategories;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 혜택 계산에 쓰는 카드 혜택 규칙 조회. 다른 도메인이 카드 엔티티나 리포지토리를 직접 읽지 않도록 계산에 필요한 값만
 * {@link CalculationRule}로 만들어 내려준다.
 *
 * <p>카드 상품이나 규칙이 없으면 예외 대신 빈 목록이다. (혜택 상세 조회와 달리 계산은 "혜택 없음"으로 이어져야 한다.)
 * 확인 전 규칙도 함께 내려주고, 쓸지는 {@link CalculationRule#ruleEngineReady()}를 보고 계산하는 쪽이 정한다.
 */
@Service
@RequiredArgsConstructor
public class BenefitRuleQueryService {

    private final BenefitRuleRepository rules;
    private final PerformanceTierRepository tiers;
    private final BenefitCategoryMapper categoryMapper;

    /** 카드 상품 한 개의 혜택 규칙(전월실적 구간 포함)을 id 순서로 돌려준다. */
    @Transactional(readOnly = true)
    public List<CalculationRule> findForCalculation(long cardProductId) {
        List<BenefitRule> found = rules.findByCardProductId(cardProductId);
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, PerformanceTier> tierByRule = new HashMap<>();
        for (PerformanceTier tier : tiers.findByCardProductId(cardProductId)) {
            tierByRule.put(tier.getBenefitRule().getId(), tier);
        }
        return found.stream().map(rule -> toCalculationRule(rule, tierByRule.get(rule.getId()))).toList();
    }

    /** Full transaction predicates. No supported-family filtering or silent dropping. */
    @Transactional(readOnly = true)
    public List<TransactionBenefitRule> findForTransactions(long cardProductId) {
        Map<Long, PerformanceTier> byRule = new HashMap<>();
        for (PerformanceTier tier : tiers.findByCardProductId(cardProductId))
            byRule.put(tier.getBenefitRule().getId(), tier);
        return rules.findByCardProductId(cardProductId).stream().map(rule -> {
            PerformanceTier tier = byRule.get(rule.getId());
            var spec = TransactionBenefitRule.parse(rule.getConditions());
            return new TransactionBenefitRule(rule.getSourceBenefitId(), spec.path("family").asText(),
                rule.getRatePercent(), rule.getAmount(), rule.getMonthlyLimit(), rule.getDailyLimit(),
                rule.getDailyCountLimit(), rule.getMonthlyCountLimit(), rule.getAnnualCountLimit(),
                tier == null ? null : tier.getPreviousMonthMinKrw(), rule.getCapGroupId(),
                rule.getSharedMonthlyLimit(), rule.getRewardUnit(), rule.isRuleEngineReady(), spec);
        }).toList();
    }

    private CalculationRule toCalculationRule(BenefitRule rule, PerformanceTier tier) {
        if(rule.getConditions()!=null && rule.getConditions().contains("\"card-benefit-v1\""))
            throw new IllegalStateException("Structured transaction rules require findForTransactions and transaction-level input");
        MappedCategories mapped = categoryMapper.map(rule.getCategory());
        boolean merchantSpecific = notBlank(rule.getMerchants()) || notBlank(rule.getTargetsNormalized());
        return new CalculationRule(rule.getId(), mapped.categories(), mapped.allMerchants(), merchantSpecific,
            rule.getRatePercent(), rule.getRewardUnit(), rule.getMonthlyLimit(), rule.getCapGroupId(),
            rule.getSharedMonthlyLimit(), rule.getDailyLimit(), rule.getDailyCountLimit(),
            rule.getMonthlyCountLimit(), rule.getAnnualCountLimit(), rule.getEligibleSpendMonthlyLimitKrw(),
            rule.getRuleMode(), tier == null ? null : tier.getPreviousMonthMinKrw(),
            tier == null ? null : tier.getPreviousMonthMaxExclusiveKrw(), rule.isRuleEngineReady());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

}
