package com.richreach.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.service.AppliedRuleEvidenceService;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.dto.OwnedCardResponse.AnalysisStatus;
import com.richreach.card.dto.OwnedCardResponse.CardKind;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.codef.entity.CardCompany;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.recommendation.dto.ManualRecommendationRequest;
import com.richreach.recommendation.dto.ManualRecommendationResponse;
import com.richreach.recommendation.dto.ManualRecommendationResponse.Type;
import com.richreach.transaction.service.TransactionSummaryService;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 수동 입력 추천이 늘어나는 혜택으로 카드를 고르고, 한도·전월 실적·입력 오류·월 경계를 어떻게 다루는지 확인한다. */
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
                List.of("테스트 메모"), benefit == 0 ? Map.of() : Map.of(String.valueOf(query.cardProductId()), benefit));
        }

    }

    private OwnedCardQueryService ownedCards;
    private TransactionSummaryService transactions;
    private FakeCalculator calculator;
    private AppliedRuleEvidenceService evidence;

    @BeforeEach
    void setUp() {
        ownedCards = mock(OwnedCardQueryService.class);
        transactions = mock(TransactionSummaryService.class);
        calculator = new FakeCalculator();
        evidence = mock(AppliedRuleEvidenceService.class);
        when(evidence.find(anyLong(), any(), any())).thenReturn(List.of());
        when(transactions.cardCategorySpend(anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(Map.of());
        when(transactions.cardSpend(anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(Map.of());
    }

    private ManualRecommendationService service() {
        return new ManualRecommendationService(ownedCards, transactions, calculator, evidence, CLOCK);
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

    private static void assertInvalid(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT);
    }

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
        assertThat(response.alternatives()).extracting(ManualRecommendationResponse.Card::cardId)
            .containsExactly(3L, 1L);
        assertThat(response.parsed().category()).isEqualTo(SpendingCategory.CAFE);
        assertThat(response.parsed().amount()).isEqualTo(10_000);
        assertThat(response.message()).contains("B 카드").contains("500원");
        assertThat(response.calculationScope()).isEqualTo(ManualRecommendationService.SCOPE);
        verify(evidence).find(eq(200L), eq(Map.of()), eq(Map.of("200", 500L)));
    }

    @Test
    @DisplayName("혜택이 같으면 보유카드 ID가 작은 카드가 먼저다")
    void breaksTiesByCardId() {
        cards(card(7, 700L, "나중 카드", true), card(3, 300L, "먼저 카드", true));
        calculator.rule(700L, 5, 100_000, 0);
        calculator.rule(300L, 5, 100_000, 0);

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().cardId()).isEqualTo(3);
        assertThat(response.alternatives()).extracting(ManualRecommendationResponse.Card::cardId).containsExactly(7L);
    }

    @Test
    @DisplayName("월 한도가 이미 찬 카드는 이번 결제로 늘어나는 혜택이 0이라 빠지고, 다음 카드가 추천된다")
    void skipsACardWhoseLimitIsAlreadyReached() {
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
    @DisplayName("한도가 일부 남았으면 남은 만큼만 늘어난다")
    void capsTheGainAtTheRemainingLimit() {
        cards(card(1, 100L, "한도 근접 카드", true));
        calculator.rule(100L, 10, 1_000, 0);
        // 이미 5,000원을 써서 500원 혜택을 받았다. 이번 10,000원은 한도(1,000원)까지 500원만 더 늘린다.
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(
            Map.of(1L, Map.of(SpendingCategory.CAFE, 5_000L)));

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().expectedBenefit()).isEqualTo(500);
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
    }

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
    @DisplayName("금액이 없거나 1원 미만, 1억 원 초과면 RECOMMEND-001이고, 경계값은 받는다")
    void validatesTheAmountRange() {
        cards(card(1, 100L, "카드", true));
        calculator.rule(100L, 5, 100_000_000, 0);

        assertInvalid(() -> service().recommend(USER, request("CAFE", null)));
        assertInvalid(() -> service().recommend(USER, request("CAFE", 0L)));
        assertInvalid(() -> service().recommend(USER, request("CAFE", -5L)));
        assertInvalid(() -> service().recommend(USER, request("CAFE", 100_000_001L)));
        assertThat(service().recommend(USER, request("CAFE", 1L)).parsed().amount()).isEqualTo(1);
        assertThat(service().recommend(USER, request("CAFE", 100_000_000L)).card().expectedBenefit())
            .isEqualTo(5_000_000);
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
        assertThat(response.calculationScope()).endsWith(ManualRecommendationService.UNCONFIRMED_SCOPE_SUFFIX);
    }

    @Test
    @DisplayName("대안 카드는 최대 3장이다")
    void limitsAlternatives() {
        cards(card(1, 100L, "1", true), card(2, 200L, "2", true), card(3, 300L, "3", true), card(4, 400L, "4", true),
            card(5, 500L, "5", true));
        for (long product = 100; product <= 500; product += 100) {
            calculator.rule(product, product / 100.0, 100_000, 0);
        }

        ManualRecommendationResponse response = service().recommend(USER, request("CAFE", 10_000L));

        assertThat(response.card().cardId()).isEqualTo(5);
        assertThat(response.alternatives()).extracting(ManualRecommendationResponse.Card::cardId)
            .containsExactly(4L, 3L, 2L);
    }

    @Test
    @DisplayName("달이 바뀌는 날에는 이번 달 사용액과 지난달 실적을 새 달 기준으로 읽고, 1월에는 지난달이 전년 12월이다")
    void usesTheMonthFromTheClock() {
        cards(card(1, 100L, "카드", true));
        calculator.rule(100L, 5, 100_000, 0);
        // 2027-01-01 00:10 KST
        ManualRecommendationService january = new ManualRecommendationService(ownedCards, transactions, calculator, evidence,
            Clock.fixed(Instant.parse("2026-12-31T15:10:00Z"), ZoneId.of("Asia/Seoul")));

        january.recommend(USER, request("CAFE", 10_000L));

        verify(transactions).cardCategorySpend(USER, YearMonth.of(2027, 1));
        verify(transactions).cardSpend(USER, YearMonth.of(2026, 12));
    }

}
