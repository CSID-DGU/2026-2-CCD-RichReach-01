package com.richreach.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.dto.OwnedCardResponse.AnalysisStatus;
import com.richreach.card.dto.OwnedCardResponse.CardKind;
import com.richreach.codef.entity.CardCompany;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.plan.optimizer.ImmediateDiscountSelector;
import com.richreach.plan.optimizer.ImmediateDiscountSelector.CardState;
import com.richreach.recommendation.dto.ManualRecommendationRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManualRecommendationServiceTest {

    @Test
    void comparesOnlySelectedOwnedProductsUsingCurrentSpendAndReturnsServerAmounts() {
        FakeInputs inputs = new FakeInputs(new ManualRecommendationInputs.Snapshot(
            List.of(card(1, 331, "첫째 카드"), card(2, 200, "둘째 카드")),
            List.of(new CardState(1, 331, 300_000, Map.of(SpendingCategory.CAFE, 50_000L)),
                new CardState(2, 200, 300_000, Map.of()))));
        ManualRecommendationService service = service(inputs, (category, benefit, nextBenefit) ->
            Optional.of("계산 가능한 혜택을 비교하면 이 카드가 유리해요."));

        var result = service.recommend(9, new ManualRecommendationRequest("CAFE", 10_000L));

        assertThat(result.type()).isEqualTo("CARD");
        assertThat(result.card().cardProductId()).isEqualTo(200);
        assertThat(result.card().expectedBenefit()).isEqualTo(500);
        assertThat(result.alternatives()).hasSize(1);
        assertThat(result.alternatives().get(0).expectedBenefit()).isZero();
        assertThat(result.message()).contains("500원");
    }

    @Test
    void doesNotPretendToRecommendWhenNoBenefitCanBeCalculated() {
        FakeInputs inputs = new FakeInputs(new ManualRecommendationInputs.Snapshot(
            List.of(card(1, 331, "첫째 카드")),
            List.of(new CardState(1, 331, 0, Map.of(SpendingCategory.CAFE, 50_000L)))));
        ManualRecommendationService service = service(inputs, (category, benefit, nextBenefit) -> {
            throw new AssertionError("추가 혜택이 없으면 AI를 호출해서는 안 됩니다.");
        });

        var result = service.recommend(9, new ManualRecommendationRequest("CAFE", 10_000L));

        assertThat(result.type()).isEqualTo("TEXT");
        assertThat(result.card()).isNull();
    }

    @Test
    void rejectsUnknownCategoryBeforeReadingUserData() {
        FakeInputs inputs = new FakeInputs(null);
        ManualRecommendationService service = service(inputs, (category, benefit, nextBenefit) -> Optional.empty());

        assertThatThrownBy(() -> service.recommend(9, new ManualRecommendationRequest("MOVIE", 10_000L)))
            .isInstanceOf(BusinessException.class);
        assertThat(inputs.loadCalls).isZero();
    }

    @Test
    void doesNotShowUnreviewedBenefitEvenIfDevelopmentSettingCalculatesIt() {
        FakeInputs inputs = new FakeInputs(new ManualRecommendationInputs.Snapshot(
            List.of(card(1, 331, "첫째 카드")), List.of(new CardState(1, 331, 0, Map.of()))));
        BenefitCalculator unreviewed = query -> new BenefitResult(
            query.monthlySpend().getOrDefault(SpendingCategory.CAFE, 0L) / 10,
            Map.of(), false, List.of());
        ManualRecommendationService service = new ManualRecommendationService(inputs,
            new ImmediateDiscountSelector(unreviewed), (category, benefit, nextBenefit) -> Optional.empty());

        var result = service.recommend(9, new ManualRecommendationRequest("CAFE", 10_000L));

        assertThat(result.type()).isEqualTo("TEXT");
        assertThat(result.card()).isNull();
    }

    private static ManualRecommendationService service(FakeInputs inputs, RecommendationExplanationClient explanation) {
        BenefitCalculator calculator = ManualRecommendationServiceTest::calculate;
        return new ManualRecommendationService(inputs, new ImmediateDiscountSelector(calculator), explanation);
    }

    private static BenefitResult calculate(BenefitQuery query) {
        long cafe = query.monthlySpend().getOrDefault(SpendingCategory.CAFE, 0L);
        long benefit = query.cardProductId() == 331L ? Math.min(cafe / 10, 5_000L) : cafe / 20;
        return new BenefitResult(benefit, Map.of(), true, List.of());
    }

    private static OwnedCardResponse card(long id, long productId, String name) {
        return new OwnedCardResponse(id, productId, CardCompany.BC, name, "1234", CardKind.CHECK,
            AnalysisStatus.AVAILABLE, true, null);
    }

    private static final class FakeInputs extends ManualRecommendationInputs {
        private final Snapshot snapshot;
        private int loadCalls;

        private FakeInputs(Snapshot snapshot) {
            super(null, null, null);
            this.snapshot = snapshot;
        }

        @Override
        public Snapshot load(long userId) {
            loadCalls++;
            return snapshot;
        }
    }
}
