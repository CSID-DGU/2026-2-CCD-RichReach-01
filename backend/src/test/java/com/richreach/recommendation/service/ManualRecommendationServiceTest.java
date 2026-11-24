package com.richreach.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.dto.CalculationRule;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.dto.OwnedCardResponse.AnalysisStatus;
import com.richreach.card.dto.OwnedCardResponse.CardKind;
import com.richreach.card.service.BenefitRuleQueryService;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.codef.entity.CardCompany;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.recommendation.dto.ManualRecommendationRequest;
import com.richreach.recommendation.dto.ManualRecommendationResponse;
import com.richreach.recommendation.dto.ManualRecommendationResponse.Comparison;
import com.richreach.recommendation.dto.ManualRecommendationResponse.RangeOption;
import com.richreach.recommendation.dto.ManualRecommendationResponse.Type;
import com.richreach.transaction.dto.CardScope;
import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.dto.TransactionSummaryResponse;
import com.richreach.transaction.dto.TransactionSummaryResponse.CategorySummary;
import com.richreach.transaction.service.TransactionSummaryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 수동 입력 추천이 늘어나는 혜택으로 카드를 고르고, 한도·전월 실적·구간별 추천·입력 오류·월 경계를 어떻게 다루는지 확인한다. */
class ManualRecommendationServiceTest {

    private static final long USER = 1L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final YearMonth MONTH = YearMonth.of(2026, 11);

    /** 상품마다 정해 둔 율과 월 한도로 혜택을 계산하는 가짜 계산기. 전월 실적이 기준에 못 미치면 혜택이 없다. */
    private static final class FakeCalculator implements BenefitCalculator {

        final Map<Long, double[]> rules = new HashMap<>(); // productId -> {ratePercent, monthlyCap, minPreviousSpend}
        boolean confirmedOnly = true;
        final Map<Long, Long> lastPreviousSpend = new HashMap<>();

        void rule(long productId, double ratePercent, long cap, long minPreviousSpend) {
            rules.put(productId, new double[] {ratePercent, cap, minPreviousSpend});
        }

        @Override
        public BenefitResult calculate(BenefitQuery query) {
            lastPreviousSpend.put(query.cardProductId(), query.previousMonthSpend());
            double[] rule = rules.get(query.cardProductId());
            if (rule == null || query.previousMonthSpend() < rule[2]) {
                return BenefitResult.none(confirmedOnly, List.of());
            }
            long total = query.monthlySpend().values().stream().mapToLong(Long::longValue).sum();
            long benefit = Math.min((long) rule[1], (long) (total * rule[0] / 100));
            return new BenefitResult(benefit, Map.of(SpendingCategory.CAFE, benefit), confirmedOnly,
                List.of("테스트 메모"));
        }

    }

    private OwnedCardQueryService ownedCards;
    private TransactionSummaryService transactions;
    private BenefitRuleQueryService ruleQuery;
    private FakeCalculator calculator;

    @BeforeEach
    void setUp() {
        ownedCards = mock(OwnedCardQueryService.class);
        transactions = mock(TransactionSummaryService.class);
        ruleQuery = mock(BenefitRuleQueryService.class);
        calculator = new FakeCalculator();
        when(transactions.cardCategorySpend(anyLong(), any())).thenReturn(Map.of());
        when(transactions.cardSpend(anyLong(), any())).thenReturn(Map.of());
        when(ruleQuery.findForCalculation(anyLong())).thenReturn(List.of());
        typicalSpend(List.of());
    }

    private ManualRecommendationService service() {
        return new ManualRecommendationService(ownedCards, transactions, calculator, ruleQuery, CLOCK);
    }

    private void typicalSpend(List<CategorySummary> categories) {
        TransactionSummaryResponse summary = mock(TransactionSummaryResponse.class);
        when(summary.categories()).thenReturn(categories);
        when(transactions.summarize(USER, SummaryPeriod.THREE_MONTHS, CardScope.SELECTED)).thenReturn(summary);
    }

    private static OwnedCardResponse card(long cardId, Long productId, String name, boolean selected) {
        return new OwnedCardResponse(cardId, productId, CardCompany.KB, name, "1111", CardKind.CREDIT,
            productId == null ? AnalysisStatus.UNAVAILABLE : AnalysisStatus.AVAILABLE, selected, null);
    }

    private void cards(OwnedCardResponse... cards) {
        when(ownedCards.list(USER)).thenReturn(List.of(cards));
    }

    private static ManualRecommendationRequest request(String category, Long amount) {
        return new ManualRecommendationRequest(category, amount);
    }

    /** 승인된 규칙. 전월실적 구간(하한)과 월 한도만 의미가 있고 나머지는 비워 둔다. */
    private static CalculationRule rule(long id, Long previousMonthMin, Long monthlyLimit) {
        return new CalculationRule(id, Set.of(SpendingCategory.CAFE), false, false, BigDecimal.ONE, "KRW",
            monthlyLimit, null, null, null, null, null, null, null, null, previousMonthMin, null, true);
    }

    private static void assertInvalid(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT);
    }

    // ---- 금액을 아는 추천 ----

    @Test
    @DisplayName("늘어나는 혜택이 가장 큰 카드를 추천하고, 나머지는 큰 순서의 대안으로 준다")
    void picksTheLargestGain() {
        cards(card(1, 100L, "A 카드", true), card(2, 200L, "B 카드", true), card(3, 300L, "C 카드", true));
        calculator.rule(100L, 1, 100_000, 0);
        calculator.rule(200L, 5, 100_000, 0);
        calculator.rule(300L, 3, 100_000, 0);

        ManualRecommendationResponse response = service().recommend(USER, request("cafe", 10_000L));

        assertThat(response.type()).isEqualTo(Type.CARD);
        assertThat(response.card().cardId()).isEqualTo(2);
        assertThat(response.card().expectedBenefit()).isEqualTo(500);
        assertThat(response.alternatives()).extracting(Comparison::cardId).containsExactly(3L, 1L);
        assertThat(response.alternatives()).extracting(Comparison::expectedBenefit).containsExactly(300L, 100L);
        assertThat(response.ranges()).isEmpty();
        assertThat(response.parsed().category()).isEqualTo(SpendingCategory.CAFE);
        assertThat(response.parsed().amount()).isEqualTo(10_000);
        assertThat(response.message()).contains("B 카드").contains("500원");
        assertThat(response.calculationScope()).isEqualTo(ManualRecommendationService.SCOPE);
    }

    @Test
    @DisplayName("혜택이 같으면 보유카드 ID가 작은 카드가 먼저다")
    void breaksTiesByCardId() {
        cards(card(7, 700L, "나중 카드", true), card(3, 300L, "먼저 카드", true));
        calculator.rule(700L, 5, 100_000, 0);
        calculator.rule(300L, 5, 100_000, 0);

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().cardId()).isEqualTo(3);
        assertThat(response.alternatives()).extracting(Comparison::cardId).containsExactly(7L);
    }

    @Test
    @DisplayName("월 한도가 이미 찬 카드는 늘어나는 혜택이 0이라 추천에서도 대안에서도 빠진다")
    void aCardWhoseLimitIsAlreadyReachedHasNoGain() {
        cards(card(1, 100L, "한도 찬 카드", true), card(2, 200L, "여유 카드", true));
        calculator.rule(100L, 10, 1_000, 0); // 월 한도 1,000원
        calculator.rule(200L, 1, 100_000, 0);
        // 이번 달에 이미 10,000원을 써서 1번 카드의 한도(1,000원)가 찼다.
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(
            Map.of(1L, Map.of(SpendingCategory.CAFE, 10_000L)));

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().cardId()).isEqualTo(2);
        assertThat(response.card().expectedBenefit()).isEqualTo(100);
        assertThat(response.alternatives()).isEmpty();
    }

    @Test
    @DisplayName("한도가 일부 남았으면 남은 만큼만 늘어나고, 혜택 비율도 그만큼 줄어든다")
    void capsTheGainAtTheRemainingLimit() {
        cards(card(1, 100L, "한도 근접 카드", true));
        calculator.rule(100L, 10, 1_000, 0);
        // 이미 5,000원을 써서 500원 혜택을 받았다. 이번 10,000원은 한도(1,000원)까지 500원만 더 늘린다.
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(
            Map.of(1L, Map.of(SpendingCategory.CAFE, 5_000L)));

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().expectedBenefit()).isEqualTo(500);
        assertThat(response.card().benefitRatePercent()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("혜택 비율은 이번 결제 금액 대비 예상 혜택이고 소수 둘째 자리까지다")
    void reportsTheBenefitRate() {
        cards(card(1, 100L, "카드", true));
        calculator.rule(100L, 0.2, 100_000, 0);

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().expectedBenefit()).isEqualTo(20);
        assertThat(response.card().benefitRatePercent()).isEqualTo(0.2);
        assertThat(ManualRecommendationService.ratePercent(1, 3)).isEqualTo(33.33);
    }

    @Test
    @DisplayName("늘어나는 혜택이 양수인 카드가 없으면 TEXT이고 카드는 없다")
    void textWhenNothingGains() {
        cards(card(1, 100L, "규칙 없는 카드", true), card(2, 200L, "실적 부족 카드", true));
        calculator.rule(200L, 5, 100_000, 300_000); // 전월 실적 300,000원이 필요하다.

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.type()).isEqualTo(Type.TEXT);
        assertThat(response.card()).isNull();
        assertThat(response.alternatives()).isEmpty();
        assertThat(response.message()).isEqualTo(ManualRecommendationService.TEXT_MESSAGE);
        assertThat(response.parsed().amount()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("전월 실적을 계산기에 넘겨서, 실적을 채운 카드만 혜택이 있다")
    void passesThePreviousMonthSpend() {
        cards(card(1, 100L, "실적 채운 카드", true), card(2, 200L, "실적 못 채운 카드", true));
        calculator.rule(100L, 5, 100_000, 300_000);
        calculator.rule(200L, 9, 100_000, 300_000);
        when(transactions.cardSpend(USER, MONTH.minusMonths(1))).thenReturn(Map.of(1L, 400_000L, 2L, 100_000L));

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().cardId()).isEqualTo(1);
        assertThat(response.alternatives()).isEmpty();
        assertThat(calculator.lastPreviousSpend.get(100L)).isEqualTo(400_000);
        assertThat(calculator.lastPreviousSpend.get(200L)).isEqualTo(100_000);
    }

    @Test
    @DisplayName("분석 대상으로 고르지 않았거나 카드 상품에 매칭되지 않은 카드는 후보가 아니다")
    void ignoresUnselectedAndUnmatchedCards() {
        cards(card(1, 100L, "선택 안 한 카드", false), card(2, null, "매칭 안 된 카드", true),
            card(3, 300L, "대상 카드", true));
        calculator.rule(100L, 9, 100_000, 0);
        calculator.rule(300L, 1, 100_000, 0);

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().cardId()).isEqualTo(3);
        assertThat(response.alternatives()).isEmpty();
    }

    @Test
    @DisplayName("후보 카드가 하나도 없으면 RECOMMEND-001이다")
    void noCandidatesIsAnInputError() {
        cards(card(1, 100L, "선택 안 한 카드", false));

        assertInvalid(() -> service().recommend(USER, request("CAFE", 10_000L)));
        assertInvalid(() -> service().recommend(USER, request("CAFE", null)));
    }

    @Test
    @DisplayName("대안은 혜택이 양수인 카드만 최대 10장이다")
    void alternativesAreOnlyPositiveAndLimited() {
        OwnedCardResponse[] many = new OwnedCardResponse[14];
        for (int i = 0; i < many.length; i++) {
            many[i] = card(i + 1, 100L + i, "카드" + i, true);
        }
        cards(many);
        // 13장은 혜택이 양수이고(율이 높은 쪽이 앞), 마지막 1장은 규칙이 없어 혜택이 0이다.
        for (int i = 0; i < 13; i++) {
            calculator.rule(100L + i, 13 - i, 100_000, 0);
        }

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().cardId()).isEqualTo(1);
        assertThat(response.alternatives()).hasSize(ManualRecommendationService.MAX_ALTERNATIVES);
        assertThat(response.alternatives()).allSatisfy(alternative -> assertThat(alternative.expectedBenefit())
            .isPositive());
        assertThat(response.alternatives().get(0).cardId()).isEqualTo(2);
    }

    // ---- 추천 이유와 실적 ----

    @Test
    @DisplayName("추천 이유와 이번 달 실적을 준다. 이번 결제를 반영한 실적에서 다음 구간까지 남은 금액을 구한다")
    void explainsThePerformanceAndTheLimit() {
        cards(card(1, 100L, "카드", true));
        calculator.rule(100L, 5, 100_000, 0);
        when(ruleQuery.findForCalculation(100L)).thenReturn(List.of(rule(1, 200_000L, 1_000L),
            rule(2, 300_000L, null)));
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(
            Map.of(1L, Map.of(SpendingCategory.DINING, 250_000L)));
        when(transactions.cardSpend(USER, MONTH)).thenReturn(Map.of(1L, 250_000L));

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().performance().monthSpendKrw()).isEqualTo(260_000);
        assertThat(response.card().performance().nextTierKrw()).isEqualTo(300_000);
        assertThat(response.card().performance().remainingKrw()).isEqualTo(40_000);
        assertThat(response.card().reasons()).contains(ManualRecommendationService.REASON_BEST)
            .contains("다음 실적 구간까지 40,000원 남았어요.");
    }

    @Test
    @DisplayName("환불로 순사용액이 음수가 된 업종이 있어도 실적은 카드별 순사용액으로 센다")
    void countsPerformanceFromTheCardsNetSpend() {
        cards(card(1, 100L, "카드", true));
        calculator.rule(100L, 5, 100_000, 0);
        when(ruleQuery.findForCalculation(100L)).thenReturn(List.of(rule(1, 100_000L, null), rule(2, 200_000L, null)));
        // 외식 100,000원을 썼고 카페는 환불로 -20,000원이라 업종별 합계에서는 카페가 빠진다. 카드의 순사용액은 80,000원이다.
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(
            Map.of(1L, Map.of(SpendingCategory.DINING, 100_000L)));
        when(transactions.cardSpend(USER, MONTH)).thenReturn(Map.of(1L, 80_000L));

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        // 업종별 합계(100,000원)를 쓰면 110,000원이 되어 다음 구간이 200,000원이고 90,000원 남지만, 순사용액으로는 90,000원이다.
        assertThat(response.card().performance().monthSpendKrw()).isEqualTo(90_000);
        assertThat(response.card().performance().nextTierKrw()).isEqualTo(100_000);
        assertThat(response.card().performance().remainingKrw()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("실적 구간을 모두 채웠으면 남은 금액이 없고, 월 한도에 도달하면 그 이유를 준다")
    void explainsAReachedTierAndALimit() {
        cards(card(1, 100L, "카드", true));
        calculator.rule(100L, 50, 400, 0); // 한도 400원. 이미 쓴 700원으로 350원을 받았고, 이번 결제로 한도에 도달한다.
        when(ruleQuery.findForCalculation(100L)).thenReturn(List.of(rule(1, 5_000L, 400L)));
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(
            Map.of(1L, Map.of(SpendingCategory.DINING, 700L)));
        when(transactions.cardSpend(USER, MONTH)).thenReturn(Map.of(1L, 700L));
        when(transactions.cardSpend(USER, MONTH.minusMonths(1))).thenReturn(Map.of(1L, 100_000L));

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().expectedBenefit()).isEqualTo(50);
        assertThat(response.card().performance().nextTierKrw()).isNull();
        assertThat(response.card().performance().remainingKrw()).isNull();
        assertThat(response.card().reasons()).contains(ManualRecommendationService.REASON_PERFORMANCE_DONE)
            .contains(ManualRecommendationService.REASON_LIMIT_REACHED);
    }

    /** 계산기가 계산에 넣는지 여부만 바꿔 가며 쓰는 규칙. 월 한도를 가진다. */
    private static CalculationRule limitRule(long id, boolean merchantSpecific, BigDecimal rate, String unit,
                                             String mode, Long monthlyLimit) {
        return new CalculationRule(id, Set.of(SpendingCategory.CAFE), false, merchantSpecific, rate, unit, monthlyLimit,
            null, null, null, null, null, null, null, mode, null, null, true);
    }

    @Test
    @DisplayName("계산기가 빼는 규칙(가맹점 지정, 정액, 마일리지, 대체 혜택)의 한도는 한도 사유에 넣지 않는다")
    void limitReasonIgnoresRulesTheCalculatorSkips() {
        cards(card(1, 100L, "카드", true));
        calculator.rule(100L, 50, 1_000, 0); // 계산에 들어가는 일반 규칙의 월 한도는 1,000원
        when(ruleQuery.findForCalculation(100L)).thenReturn(List.of(
            limitRule(1, false, BigDecimal.ONE, "KRW", "BASE", 1_000L),           // 계산에 들어감
            limitRule(2, true, BigDecimal.ONE, "KRW", null, 9_000L),              // 가맹점 지정: 계산기가 뺌
            limitRule(3, false, null, "KRW", null, 9_000L),                       // 정액 혜택: 계산기가 뺌
            limitRule(4, false, BigDecimal.ONE, "MILE", null, 9_000L),            // 마일리지: 계산기가 뺌
            limitRule(5, false, BigDecimal.ONE, "KRW", "REPLACES_BASE", 9_000L))); // 대체 혜택: 계산기가 뺌
        // 이미 1,000원을 써서 500원을 받았고, 이번 결제로 일반 규칙의 한도(1,000원)에 도달한다.
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(
            Map.of(1L, Map.of(SpendingCategory.DINING, 1_000L)));
        when(transactions.cardSpend(USER, MONTH)).thenReturn(Map.of(1L, 1_000L));

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        // 빼는 규칙의 한도(9,000원 x 4)가 합에 들어가면 한도가 크게 나와 "남아 있어요"가 붙는다. 실제로는 한도에 도달했다.
        assertThat(response.card().reasons()).contains(ManualRecommendationService.REASON_LIMIT_REACHED)
            .doesNotContain(ManualRecommendationService.REASON_LIMIT_LEFT);
    }

    @Test
    @DisplayName("계산기가 계산에 넣는 규칙의 판단 기준")
    void countedByCalculatorMatchesTheCalculatorsExclusions() {
        assertThat(ManualRecommendationService.countedByCalculator(
            limitRule(1, false, BigDecimal.ONE, "KRW", null, 1_000L))).isTrue();
        assertThat(ManualRecommendationService.countedByCalculator(
            limitRule(1, false, BigDecimal.ONE, "POINT", "ADDITIONAL", 1_000L))).isTrue();
        assertThat(ManualRecommendationService.countedByCalculator(
            limitRule(1, true, BigDecimal.ONE, "KRW", null, 1_000L))).isFalse();
        assertThat(ManualRecommendationService.countedByCalculator(
            limitRule(1, false, null, "KRW", null, 1_000L))).isFalse();
        assertThat(ManualRecommendationService.countedByCalculator(
            limitRule(1, false, BigDecimal.ONE, "MILE", null, 1_000L))).isFalse();
        assertThat(ManualRecommendationService.countedByCalculator(
            limitRule(1, false, BigDecimal.ONE, "KRW", "PREFER_HIGHER", 1_000L))).isFalse();
        assertThat(ManualRecommendationService.countedByCalculator(
            limitRule(1, false, BigDecimal.ONE, "KRW", "ANNUAL_BONUS", 1_000L))).isFalse();
    }

    @Test
    @DisplayName("실적 구간 정보가 없으면 performance는 null이다")
    void noPerformanceWithoutTiers() {
        cards(card(1, 100L, "카드", true));
        calculator.rule(100L, 5, 100_000, 0);

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().performance()).isNull();
        assertThat(response.card().reasons()).containsExactly(ManualRecommendationService.REASON_BEST);
    }

    @Test
    @DisplayName("확인 전 규칙이 섞인 결과는 참고용 안내를 덧붙이고 confirmedOnly가 false다")
    void marksUnconfirmedRules() {
        cards(card(1, 100L, "카드", true));
        calculator.rule(100L, 5, 100_000, 0);
        calculator.confirmedOnly = false;

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().confirmedOnly()).isFalse();
        assertThat(response.card().notes()).containsExactly("테스트 메모");
        assertThat(response.card().reasons()).contains(ManualRecommendationService.REASON_UNCONFIRMED);
        assertThat(response.calculationScope()).endsWith(ManualRecommendationService.UNCONFIRMED_SCOPE_SUFFIX);
    }

    // ---- 금액 구간별 추천 ----

    @Test
    @DisplayName("금액을 보내지 않으면 대표 금액 세 개로 구간별 추천을 주고, 평소 결제 금액이 든 구간을 표시한다")
    void recommendsPerAmountRange() {
        cards(card(1, 100L, "작은 금액에 유리한 카드", true), card(2, 200L, "큰 금액에 유리한 카드", true));
        calculator.rule(100L, 10, 1_000, 0);   // 월 한도 1,000원이라 큰 금액에서는 한도에 막힌다.
        calculator.rule(200L, 3, 100_000, 0);
        typicalSpend(List.of(new CategorySummary(SpendingCategory.CAFE, 60_000, 1.0, 10))); // 한 건 평균 6,000원

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", null));

        assertThat(response.type()).isEqualTo(Type.RANGE);
        assertThat(response.card()).isNull();
        assertThat(response.parsed().amount()).isNull();
        assertThat(response.followUp()).isEqualTo(ManualRecommendationService.RANGE_FOLLOW_UP);
        assertThat(response.message()).isEqualTo(ManualRecommendationService.RANGE_MESSAGE);
        assertThat(response.ranges()).extracting(RangeOption::label)
            .containsExactly("10,000원 이하", "10,000원 초과 ~ 30,000원", "30,000원 초과");
        assertThat(response.ranges()).extracting(RangeOption::amount).containsExactly(5_000L, 20_000L, 50_000L);
        // 5,000원: 10% = 500원 vs 3% = 150원 → 1번. 20,000원: 10% = 2,000원인데 한도 1,000원 vs 3% = 600원 → 1번.
        // 50,000원: 한도 1,000원 vs 3% = 1,500원 → 2번.
        assertThat(response.ranges()).extracting(option -> option.card().cardId()).containsExactly(1L, 1L, 2L);
        assertThat(response.ranges()).extracting(RangeOption::best).containsExactly(true, false, false);
    }

    @Test
    @DisplayName("평소 결제 금액이 큰 구간에 들면 그 구간이 best이고, 거래가 없으면 best가 없다")
    void marksTheTypicalRange() {
        cards(card(1, 100L, "카드", true));
        calculator.rule(100L, 5, 100_000, 0);
        typicalSpend(List.of(new CategorySummary(SpendingCategory.CAFE, 400_000, 1.0, 10))); // 한 건 평균 40,000원

        assertThat(service().recommend(USER, request("CAFE", null)).ranges())
            .extracting(RangeOption::best).containsExactly(false, false, true);

        typicalSpend(List.of());
        assertThat(service().recommend(USER, request("CAFE", null)).ranges())
            .extracting(RangeOption::best).containsExactly(false, false, false);
    }

    @Test
    @DisplayName("평균이 구간 경계와 정확히 같으면 그 구간이고, 소수 부분이 경계를 넘으면 다음 구간이다")
    void keepsTheFractionOfTheAverageAtTheBoundary() {
        cards(card(1, 100L, "카드", true));
        calculator.rule(100L, 5, 100_000, 0);

        // 평균 10,000원: 첫 구간("10,000원 이하")
        typicalSpend(List.of(new CategorySummary(SpendingCategory.CAFE, 10_000, 1.0, 1)));
        assertThat(service().recommend(USER, request("CAFE", null)).ranges())
            .extracting(RangeOption::best).containsExactly(true, false, false);

        // 평균 10,000.5원(20,001 / 2): 둘째 구간
        typicalSpend(List.of(new CategorySummary(SpendingCategory.CAFE, 20_001, 1.0, 2)));
        assertThat(service().recommend(USER, request("CAFE", null)).ranges())
            .extracting(RangeOption::best).containsExactly(false, true, false);

        // 평균 30,000원(30,000과 30,000): 둘째 구간
        typicalSpend(List.of(new CategorySummary(SpendingCategory.CAFE, 60_000, 1.0, 2)));
        assertThat(service().recommend(USER, request("CAFE", null)).ranges())
            .extracting(RangeOption::best).containsExactly(false, true, false);

        // 평균 30,000.5원(30,000과 30,001): 정수 나눗셈이면 30,000으로 버려지지만 실제로는 "30,000원 초과"다.
        typicalSpend(List.of(new CategorySummary(SpendingCategory.CAFE, 60_001, 1.0, 2)));
        assertThat(service().recommend(USER, request("CAFE", null)).ranges())
            .extracting(RangeOption::best).containsExactly(false, false, true);
    }

    @Test
    @DisplayName("어느 구간에서도 늘어나는 혜택이 없으면 RANGE가 아니라 TEXT이다")
    void textWhenNoRangeHasAGain() {
        cards(card(1, 100L, "규칙 없는 카드", true));

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", null));

        assertThat(response.type()).isEqualTo(Type.TEXT);
        assertThat(response.ranges()).isEmpty();
        assertThat(response.parsed().amount()).isNull();
    }

    // ---- 입력 검증과 월 경계 ----

    @Test
    @DisplayName("업종이 없거나 모르는 값이면 RECOMMEND-001이다")
    void rejectsABadCategory() {
        cards(card(1, 100L, "카드", true));

        assertInvalid(() -> service().recommend(USER, request(null, 10_000L)));
        assertInvalid(() -> service().recommend(USER, request(" ", 10_000L)));
        assertInvalid(() -> service().recommend(USER, request("GAMBLING", 10_000L)));
        assertInvalid(() -> service().recommend(USER, null));
    }

    @Test
    @DisplayName("금액이 1원 미만이거나 1억 원 초과면 RECOMMEND-001이고, 경계값은 받는다. 금액이 없는 것은 오류가 아니다")
    void validatesTheAmountRange() {
        cards(card(1, 100L, "카드", true));
        calculator.rule(100L, 5, 100_000_000, 0);

        assertInvalid(() -> service().recommend(USER, request("CAFE", 0L)));
        assertInvalid(() -> service().recommend(USER, request("CAFE", -5L)));
        assertInvalid(() -> service().recommend(USER, request("CAFE", 100_000_001L)));
        assertThat(service().recommend(USER, request("CAFE", 1L)).parsed().amount()).isEqualTo(1);
        assertThat(service().recommend(USER, request("CAFE", 100_000_000L)).card().expectedBenefit())
            .isEqualTo(5_000_000);
        assertThat(service().recommend(USER, request("CAFE", null)).type()).isEqualTo(Type.RANGE);
    }

    @Test
    @DisplayName("달이 바뀌는 날에는 이번 달 사용액과 지난달 실적을 새 달 기준으로 읽고, 1월에는 지난달이 전년 12월이다")
    void usesTheMonthFromTheClock() {
        cards(card(1, 100L, "카드", true));
        calculator.rule(100L, 5, 100_000, 0);
        // 2027-01-01 00:10 KST
        ManualRecommendationService january = new ManualRecommendationService(ownedCards, transactions, calculator,
            ruleQuery, Clock.fixed(Instant.parse("2026-12-31T15:10:00Z"), ZoneId.of("Asia/Seoul")));

        january.recommend(USER, request("CAFE", 10_000L));

        verify(transactions).cardCategorySpend(USER, YearMonth.of(2027, 1));
        verify(transactions).cardSpend(USER, YearMonth.of(2026, 12));
    }

}
