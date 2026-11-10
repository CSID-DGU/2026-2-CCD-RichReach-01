package com.richreach.plan.optimizer;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.global.category.SpendingCategory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 한 건의 결제에 대해 현재까지 사용한 월간 금액을 고정하고, 각 보유카드로
 * 결제했을 때 증가하는 혜택을 비교한다. 카드 혜택 계산은 기존 계산기에 맡긴다.
 * 이 선택은 다음 달 실적 가치까지 고려한 월간 플랜과 별개다.
 */
@Component
@RequiredArgsConstructor
public class ImmediateDiscountSelector {

    private final BenefitCalculator benefitCalculator;

    public TransactionCardOptimizer.Choice chooseTransaction(
        com.richreach.benefit.dto.BenefitTransaction current,List<TransactionCardOptimizer.Card> cards,boolean allowUnverified) {
        return new TransactionCardOptimizer(new com.richreach.benefit.service.TransactionBenefitCalculator())
            .immediate(current,cards,allowUnverified);
    }

    public Choice choose(SpendingCategory category, long amount, List<CardState> cards) {
        if (category == null || amount <= 0 || cards == null || cards.isEmpty()) {
            throw new IllegalArgumentException("업종, 양수 결제금액, 보유카드가 필요합니다.");
        }
        Set<Long> seen = new HashSet<>();
        List<Candidate> candidates = new ArrayList<>();
        for (CardState card : cards) {
            if (!seen.add(card.userCardId())) {
                throw new IllegalArgumentException("보유카드 ID가 중복되었습니다.");
            }
            Map<SpendingCategory, Long> after = new EnumMap<>(SpendingCategory.class);
            after.putAll(card.monthlySpend());
            after.merge(category, amount, Math::addExact);
            BenefitResult beforeResult = benefitCalculator.calculate(
                new BenefitQuery(card.cardProductId(), card.monthlySpend(), card.previousMonthSpend()));
            BenefitResult afterResult = benefitCalculator.calculate(
                new BenefitQuery(card.cardProductId(), after, card.previousMonthSpend()));
            long benefit = Math.subtractExact(afterResult.totalBenefit(), beforeResult.totalBenefit());
            candidates.add(new Candidate(card.userCardId(), benefit,
                beforeResult.confirmedOnly() && afterResult.confirmedOnly(),
                afterResult.notes()));
        }
        candidates.sort(Comparator.comparingLong(Candidate::incrementalBenefit).reversed()
            .thenComparingLong(Candidate::userCardId));
        return new Choice(candidates.get(0), candidates);
    }

    /** 이번 달 이미 결제한 금액. 카드번호나 CODEF 연결정보는 받지 않는다. */
    public record CardState(long userCardId, long cardProductId, long previousMonthSpend,
                            Map<SpendingCategory, Long> monthlySpend) {
        public CardState {
            if (userCardId <= 0 || cardProductId <= 0 || previousMonthSpend < 0) {
                throw new IllegalArgumentException("카드 ID와 전월 실적이 올바르지 않습니다.");
            }
            monthlySpend = monthlySpend == null ? Map.of() : Map.copyOf(monthlySpend);
            monthlySpend.forEach((category, spend) -> {
                if (category == null || spend == null || spend < 0) {
                    throw new IllegalArgumentException("월간 사용액은 0 이상이어야 합니다.");
                }
            });
        }
    }

    public record Candidate(long userCardId, long incrementalBenefit,
                            boolean confirmedOnly, List<String> notes) {
        public Candidate {
            notes = List.copyOf(notes);
        }
    }

    /** candidates는 추가 혜택 내림차순이고, 동률이면 보유카드 ID 오름차순이다. */
    public record Choice(Candidate selected, List<Candidate> candidates) {
        public Choice {
            candidates = List.copyOf(candidates);
        }
    }
}
