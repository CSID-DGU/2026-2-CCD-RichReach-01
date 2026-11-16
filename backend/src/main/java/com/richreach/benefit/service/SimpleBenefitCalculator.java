package com.richreach.benefit.service;

import com.richreach.benefit.config.BenefitProperties;
import com.richreach.benefit.config.BenefitProperties.RuleSelection;
import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitMath.RuleInput;
import com.richreach.card.dto.CalculationRule;
import com.richreach.card.service.BenefitRuleQueryService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 임시 혜택 계산기. 다른 계산기를 꽂기 전까지 월간 플랜이 돌아가게 하는 단순 구현이다. ({@link BenefitMath} 참고)
 *
 * <p>쓸 규칙은 설정({@code richreach.benefit.rule-selection})으로 정한다. 기본은 사람이 원문과 대조해 확인한 규칙만 쓰고,
 * ALL이면 확인 전 규칙도 써서 결과의 {@code confirmedOnly}가 false가 된다.
 * 카드 규칙은 카드 도메인의 {@link BenefitRuleQueryService}를 거쳐서만 읽는다.
 * 일별·횟수·할인대상금액 한도는 월간 업종 합계만으로 적용할 수 없으므로 해당 규칙의
 * 혜택 전체를 예상 금액에서 제외한다.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "richreach.benefit.engine", havingValue = "simple", matchIfMissing = true)
public class SimpleBenefitCalculator implements BenefitCalculator {

    private final BenefitRuleQueryService ruleQuery;
    private final BenefitProperties properties;

    @Override
    public BenefitResult calculate(BenefitQuery query) {
        boolean readyOnly = properties.ruleSelection() == RuleSelection.READY_ONLY;
        List<CalculationRule> candidates = ruleQuery.findForCalculation(query.cardProductId()).stream()
            .filter(rule -> !readyOnly || rule.ruleEngineReady()).toList();
        if (candidates.isEmpty()) {
            List<String> notes = readyOnly
                ? List.of("확인된 혜택 규칙이 없어 혜택을 계산하지 못했습니다.")
                : List.of("혜택 규칙이 없어 혜택을 계산하지 못했습니다.");
            return BenefitResult.none(readyOnly, notes);
        }
        // The monthly category aggregate cannot enforce per-day, per-payment, or count limits.
        // Excluding those rules is safer than presenting an inflated discount as a forecast.
        List<CalculationRule> calculable = candidates.stream()
            .filter(rule -> !BenefitRuleSupport.hasUnmodelledLimits(rule)).toList();
        List<RuleInput> inputs = calculable.stream().map(SimpleBenefitCalculator::toInput).toList();
        BenefitResult result = BenefitMath.compute(inputs, query.monthlySpend(), query.previousMonthSpend(), readyOnly);
        if (calculable.size() == candidates.size()) return result;
        List<String> notes = new ArrayList<>(result.notes());
        notes.add("일별·횟수·할인대상금액 한도를 확인할 수 없는 혜택은 예상 금액에서 제외했습니다.");
        return new BenefitResult(result.totalBenefit(), result.benefitByCategory(), result.confirmedOnly(), notes);
    }

    private static RuleInput toInput(CalculationRule rule) {
        return new RuleInput(String.valueOf(rule.id()), rule.categories(), rule.allMerchants(),
            rule.merchantSpecific(), rule.ratePercent(), rule.rewardUnit(), rule.monthlyLimit(), rule.capGroupId(),
            rule.sharedMonthlyLimit(), rule.ruleMode(), rule.previousMonthMinKrw(),
            rule.previousMonthMaxExclusiveKrw(), List.of());
    }

}
