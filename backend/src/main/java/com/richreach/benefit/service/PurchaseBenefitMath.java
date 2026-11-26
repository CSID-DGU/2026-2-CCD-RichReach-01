package com.richreach.benefit.service;

import com.richreach.benefit.dto.BenefitPurchase;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.card.dto.CalculationRule;
import com.richreach.global.category.SpendingCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Replays actual purchases against one card's rule and shared-cap wallets. No state is persisted. */
final class PurchaseBenefitMath {

    private PurchaseBenefitMath() {
    }

    private static final class RuleState {
        long monthBenefit;
        long eligibleSpend;
        int monthCount;
        LocalDate day;
        long dayBenefit;
        int dayCount;

        void startDay(LocalDate next) {
            if (!next.equals(day)) {
                day = next;
                dayBenefit = 0;
                dayCount = 0;
            }
        }
    }

    static Map<Long, BenefitResult> calculate(List<CalculationRule> source, List<BenefitPurchase> purchases,
                                               long previousMonthSpend, boolean confirmedOnly) {
        List<CalculationRule> rules = source.stream().filter(PurchaseBenefitMath::supported)
            .filter(rule -> performanceMet(rule, previousMonthSpend))
            .sorted(Comparator.comparing(CalculationRule::ratePercent).reversed()
                .thenComparing(CalculationRule::id)).toList();
        Map<String, Long> groupCaps = new HashMap<>();
        for (CalculationRule rule : rules) {
            if (rule.capGroupId() != null && !rule.capGroupId().isBlank() && rule.sharedMonthlyLimit() != null) {
                groupCaps.merge(rule.capGroupId(), rule.sharedMonthlyLimit(), Math::min);
            }
        }
        Map<Long, RuleState> states = new HashMap<>();
        Map<String, Long> groupUsed = new HashMap<>();
        Map<Long, BenefitResult> result = new LinkedHashMap<>();
        List<BenefitPurchase> ordered = new ArrayList<>(purchases);
        ordered.sort(Comparator.comparing(BenefitPurchase::approvedAt).thenComparingLong(BenefitPurchase::id));
        for (BenefitPurchase purchase : ordered) {
            if (result.containsKey(purchase.id())) {
                throw new IllegalArgumentException("거래 ID가 중복되었습니다.");
            }
            if (!purchase.merchantIdentified() || purchase.category() == SpendingCategory.ETC || purchase.amount() == 0) {
                List<String> notes = purchase.merchantIdentified() ? List.of()
                    : List.of("가맹점을 식별하지 못해 소비액만 기타로 집계하고 혜택은 계산하지 않았습니다.");
                result.put(purchase.id(), BenefitResult.none(confirmedOnly, notes));
                continue;
            }
            long total = 0;
            Map<String, Long> byRule = new LinkedHashMap<>();
            for (CalculationRule rule : rules) {
                // A forecast has an estimated amount, but its exact day and count are not confirmed.
                if (purchase.forecast() && (rule.dailyLimit() != null || rule.dailyCountLimit() != null
                    || rule.monthlyCountLimit() != null || rule.eligibleSpendMonthlyLimit() != null)) continue;
                if (!rule.allMerchants() && !rule.categories().contains(purchase.category())) continue;
                if (rule.perTransactionMinKrw() != null && purchase.amount() < rule.perTransactionMinKrw()) continue;
                RuleState state = states.computeIfAbsent(rule.id(), ignored -> new RuleState());
                state.startDay(purchase.approvedAt().toLocalDate());
                if (atLimit(state.monthCount, rule.monthlyCountLimit())
                    || atLimit(state.dayCount, rule.dailyCountLimit())) continue;
                long eligible = rule.eligibleSpendMonthlyLimit() == null ? purchase.amount()
                    : Math.min(purchase.amount(), Math.max(0, rule.eligibleSpendMonthlyLimit() - state.eligibleSpend));
                if (eligible == 0) continue;
                long raw = BigDecimal.valueOf(eligible).multiply(rule.ratePercent())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).longValueExact();
                if (rule.perTransactionMaxKrw() != null) {
                    raw = Math.min(raw, rule.perTransactionMaxKrw());
                }
                long applied = raw;
                if (rule.monthlyLimit() != null) {
                    applied = Math.min(applied, Math.max(0, rule.monthlyLimit() - state.monthBenefit));
                }
                if (rule.dailyLimit() != null) {
                    applied = Math.min(applied, Math.max(0, rule.dailyLimit() - state.dayBenefit));
                }
                String group = rule.capGroupId();
                Long sharedCap = group == null ? null : groupCaps.get(group);
                if (sharedCap != null) {
                    applied = Math.min(applied, Math.max(0, sharedCap - groupUsed.getOrDefault(group, 0L)));
                }
                if (applied <= 0) continue;
                state.monthBenefit = Math.addExact(state.monthBenefit, applied);
                state.dayBenefit = Math.addExact(state.dayBenefit, applied);
                state.eligibleSpend = Math.addExact(state.eligibleSpend, eligible);
                state.monthCount++;
                state.dayCount++;
                if (sharedCap != null) groupUsed.merge(group, applied, Math::addExact);
                byRule.put(String.valueOf(rule.id()), applied);
                total = Math.addExact(total, applied);
            }
            result.put(purchase.id(), new BenefitResult(total,
                total == 0 ? Map.of() : Map.of(purchase.category(), total), confirmedOnly, List.of(), byRule));
        }
        return result;
    }

    private static boolean atLimit(int used, Integer limit) {
        return limit != null && used >= limit;
    }

    private static boolean performanceMet(CalculationRule rule, long previous) {
        return (rule.previousMonthMinKrw() == null || previous >= rule.previousMonthMinKrw())
            && (rule.previousMonthMaxExclusiveKrw() == null || previous < rule.previousMonthMaxExclusiveKrw());
    }

    private static boolean supported(CalculationRule rule) {
        String mode = rule.ruleMode();
        return rule.id() != null && !rule.merchantSpecific()
            && rule.ratePercent() != null && rule.ratePercent().signum() >= 0
            && !"MILE".equals(rule.rewardUnit())
            && rule.annualCountLimit() == null
            && !("REPLACES_BASE".equals(mode) || "PREFER_HIGHER".equals(mode) || "ANNUAL_BONUS".equals(mode));
    }
}
