package com.richreach.benefit.service;

import com.richreach.card.dto.CalculationRule;

/** Conditions that a monthly category aggregate cannot evaluate exactly. */
public final class BenefitRuleSupport {

    private BenefitRuleSupport() {
    }

    public static boolean hasUnmodelledLimits(CalculationRule rule) {
        return rule.dailyLimit() != null || rule.dailyCountLimit() != null
            || rule.monthlyCountLimit() != null || rule.annualCountLimit() != null
            || rule.eligibleSpendMonthlyLimit() != null;
    }

    /** Aggregated spend cannot prove these source rule conditions. */
    public static boolean hasUnmodelledConditions(CalculationRule rule) {
        return hasUnmodelledLimits(rule) || rule.unsupportedCondition();
    }
}
