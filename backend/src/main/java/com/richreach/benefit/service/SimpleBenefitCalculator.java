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
        List<RuleInput> inputs = candidates.stream().map(SimpleBenefitCalculator::toInput).toList();
        return BenefitMath.compute(inputs, query.monthlySpend(), query.previousMonthSpend(), readyOnly);
    }

    private static RuleInput toInput(CalculationRule rule) {
        return new RuleInput(String.valueOf(rule.id()), rule.categories(), rule.allMerchants(),
            rule.merchantSpecific(), rule.ratePercent(), rule.rewardUnit(), rule.monthlyLimit(), rule.capGroupId(),
            rule.sharedMonthlyLimit(), rule.ruleMode(), rule.previousMonthMinKrw(),
            rule.previousMonthMaxExclusiveKrw(), unappliedLimits(rule));
    }

    /** 규칙에 있지만 이 계산기가 적용하지 않는 한도의 이름. */
    private static List<String> unappliedLimits(CalculationRule rule) {
        List<String> limits = new ArrayList<>();
        if (rule.dailyLimit() != null) {
            limits.add("일 한도");
        }
        if (rule.dailyCountLimit() != null || rule.monthlyCountLimit() != null || rule.annualCountLimit() != null) {
            limits.add("횟수 한도");
        }
        if (rule.eligibleSpendMonthlyLimit() != null) {
            limits.add("할인대상 결제금액 한도");
        }
        return limits;
    }

}
