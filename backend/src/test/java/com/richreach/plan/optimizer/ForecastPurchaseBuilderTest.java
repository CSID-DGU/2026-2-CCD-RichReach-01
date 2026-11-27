package com.richreach.plan.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.benefit.dto.BenefitPurchase;
import com.richreach.global.category.SpendingCategory;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ForecastPurchaseBuilderTest {
    @Test
    void scalesPreviousPurchasesWithoutChangingTheConfirmedBudget() {
        List<BenefitPurchase> history = List.of(
            new BenefitPurchase(1, LocalDateTime.of(2026, 10, 1, 12, 0), SpendingCategory.CAFE, 10_000, true),
            new BenefitPurchase(2, LocalDateTime.of(2026, 10, 2, 12, 0), SpendingCategory.CAFE, 20_000, true));
        List<BenefitPurchase> forecast = ForecastPurchaseBuilder.build(YearMonth.of(2026, 11),
            Map.of(SpendingCategory.CAFE, 12_001L, SpendingCategory.DINING, 5_000L), history,
            LocalDateTime.of(2026, 11, 1, 0, 0));

        assertThat(forecast.stream().filter(p -> p.category() == SpendingCategory.CAFE)
            .mapToLong(BenefitPurchase::amount).sum()).isEqualTo(12_001);
        assertThat(forecast.stream().filter(p -> p.category() == SpendingCategory.DINING)
            .mapToLong(BenefitPurchase::amount).sum()).isEqualTo(5_000);
        assertThat(forecast.stream().filter(p -> p.category() == SpendingCategory.DINING)
            .allMatch(p -> !p.merchantIdentified())).isTrue();
        assertThat(forecast).allMatch(BenefitPurchase::forecast);
    }
}
