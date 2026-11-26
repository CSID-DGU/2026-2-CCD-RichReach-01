package com.richreach.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.richreach.card.entity.BenefitRule;
import com.richreach.card.repository.BenefitRuleRepository;
import com.richreach.card.repository.PerformanceTierRepository;
import com.richreach.benefit.service.BenefitRuleSupport;
import com.richreach.global.category.SpendingCategory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BenefitRuleQueryServiceTest {

    @Test
    void paymentMethodRestrictedRuleRequiresTransactionDetail() {
        BenefitRuleRepository rules = mock(BenefitRuleRepository.class);
        PerformanceTierRepository tiers = mock(PerformanceTierRepository.class);
        BenefitCategoryMapper categories = mock(BenefitCategoryMapper.class);
        BenefitRule rule = mock(BenefitRule.class);
        when(rule.getId()).thenReturn(5L);
        when(rule.getCategory()).thenReturn("CAFE");
        when(rule.getPaymentMethods()).thenReturn("KB_PAY");
        when(rule.getPerTransactionMinKrw()).thenReturn(10_000L);
        when(rules.findByCardProductId(9L)).thenReturn(List.of(rule));
        when(tiers.findByCardProductId(9L)).thenReturn(List.of());
        when(categories.map("CAFE")).thenReturn(new BenefitCategoryMapper.MappedCategories(
            Set.of(SpendingCategory.CAFE), false));

        var found = new BenefitRuleQueryService(rules, tiers, categories).findForCalculation(9L);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).merchantSpecific()).isTrue();
        assertThat(found.get(0).perTransactionMinKrw()).isEqualTo(10_000L);
        assertThat(BenefitRuleSupport.hasUnmodelledLimits(found.get(0))).isTrue();
    }
}
