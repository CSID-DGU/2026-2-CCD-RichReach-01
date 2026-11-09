package com.richreach.benefit.service;

import com.richreach.benefit.config.BenefitProperties;
import com.richreach.benefit.config.BenefitProperties.RuleSelection;
import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitMath.RuleInput;
import com.richreach.card.entity.BenefitRule;
import com.richreach.card.entity.PerformanceTier;
import com.richreach.card.repository.BenefitRuleRepository;
import com.richreach.card.repository.PerformanceTierRepository;
import com.richreach.card.service.BenefitCategoryMapper;
import com.richreach.card.service.BenefitCategoryMapper.MappedCategories;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 임시 혜택 계산기. 다른 계산기를 꽂기 전까지 월간 플랜이 돌아가게 하는 단순 구현이다. ({@link BenefitMath} 참고)
 *
 * <p>쓸 규칙은 설정({@code richreach.benefit.rule-selection})으로 정한다. 기본은 사람이 원문과 대조해 확인한 규칙만 쓰고,
 * ALL이면 확인 전 규칙도 써서 결과의 {@code confirmedOnly}가 false가 된다.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "richreach.benefit.engine", havingValue = "simple", matchIfMissing = true)
public class SimpleBenefitCalculator implements BenefitCalculator {

    private final BenefitRuleRepository rules;
    private final PerformanceTierRepository tiers;
    private final BenefitCategoryMapper categoryMapper;
    private final BenefitProperties properties;

    @Override
    @Transactional(readOnly = true)
    public BenefitResult calculate(BenefitQuery query) {
        boolean readyOnly = properties.ruleSelection() == RuleSelection.READY_ONLY;
        List<BenefitRule> candidates = rules.findByCardProductId(query.cardProductId()).stream()
            .filter(rule -> !readyOnly || rule.isRuleEngineReady()).toList();
        if (candidates.isEmpty()) {
            List<String> notes = readyOnly
                ? List.of("확인된 혜택 규칙이 없어 혜택을 계산하지 못했습니다.")
                : List.of("혜택 규칙이 없어 혜택을 계산하지 못했습니다.");
            return BenefitResult.none(readyOnly, notes);
        }

        Map<Long, PerformanceTier> tierByRule = new HashMap<>();
        for (PerformanceTier tier : tiers.findByCardProductId(query.cardProductId())) {
            tierByRule.put(tier.getBenefitRule().getId(), tier);
        }
        List<RuleInput> inputs = new ArrayList<>();
        for (BenefitRule rule : candidates) {
            inputs.add(toInput(rule, tierByRule.get(rule.getId())));
        }
        return BenefitMath.compute(inputs, query.monthlySpend(), query.previousMonthSpend(), readyOnly);
    }

    private RuleInput toInput(BenefitRule rule, PerformanceTier tier) {
        MappedCategories mapped = categoryMapper.map(rule.getCategory());
        boolean merchantSpecific = notBlank(rule.getMerchants()) || notBlank(rule.getTargetsNormalized());
        return new RuleInput(String.valueOf(rule.getId()), mapped.categories(), mapped.allMerchants(),
            merchantSpecific, rule.getRatePercent(), rule.getRewardUnit(), rule.getMonthlyLimit(),
            rule.getCapGroupId(), rule.getSharedMonthlyLimit(), rule.getRuleMode(),
            tier == null ? null : tier.getPreviousMonthMinKrw(),
            tier == null ? null : tier.getPreviousMonthMaxExclusiveKrw());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

}
