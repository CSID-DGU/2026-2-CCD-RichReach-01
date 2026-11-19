package com.richreach.card.service;

import com.richreach.card.dto.CalculationRule;
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

    private CalculationRule toCalculationRule(BenefitRule rule, PerformanceTier tier) {
        MappedCategories mapped = categoryMapper.map(rule.getCategory());
        boolean merchantSpecific = notBlank(rule.getMerchants()) || notBlank(rule.getTargetsNormalized());
        // The category aggregate cannot verify payment rails, variable rates, or an unspecified cap.
        // Exclude those rows before both the immediate selector and CP-SAT use them.
        boolean unsupportedCondition = notBlank(rule.getPaymentMethods())
            || rule.getRateMinPercent() != null || rule.getRateMaxPercent() != null
            || (notBlank(rule.getAmountBasis())
                && !"PERCENT_OF_ELIGIBLE_SPEND".equals(rule.getAmountBasis()))
            || !knownMonthlyCap(rule)
            || unsupportedRewardUnit(rule)
            || !("BASE".equals(rule.getRuleMode()) || "ADDITIONAL".equals(rule.getRuleMode()));
        return new CalculationRule(rule.getId(), mapped.categories(), mapped.allMerchants(), merchantSpecific,
            rule.getRatePercent(), rule.getRewardUnit(), rule.getMonthlyLimit(), rule.getCapGroupId(),
            rule.getSharedMonthlyLimit(), rule.getDailyLimit(), rule.getDailyCountLimit(),
            rule.getMonthlyCountLimit(), rule.getAnnualCountLimit(), rule.getEligibleSpendMonthlyLimitKrw(),
            rule.getRuleMode(), tier == null ? null : tier.getPreviousMonthMinKrw(),
            tier == null ? null : tier.getPreviousMonthMaxExclusiveKrw(), rule.isRuleEngineReady(),
            unsupportedCondition);
    }

    private static boolean knownMonthlyCap(BenefitRule rule) {
        if (notBlank(rule.getCapGroupId()) && rule.getSharedMonthlyLimit() == null) return false;
        return switch (rule.getMonthlyLimitStatus() == null ? "" : rule.getMonthlyLimitStatus()) {
            case "UNLIMITED" -> true;
            case "LIMITED" -> rule.getMonthlyLimit() != null;
            case "SHARED_LIMIT" -> notBlank(rule.getCapGroupId()) && rule.getSharedMonthlyLimit() != null;
            default -> false;
        };
    }

    private static boolean unsupportedRewardUnit(BenefitRule rule) {
        String unit = rule.getRewardUnit();
        return unit == null
            ? !("DISCOUNT".equals(rule.getBenefitType()) || "CASHBACK".equals(rule.getBenefitType()))
            : !("KRW".equals(unit) || "POINT".equals(unit));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

}
