package com.richreach.recommendation.service;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.benefit.service.BenefitRuleSupport;
import com.richreach.benefit.service.CardPerformanceAnalyzer;
import com.richreach.card.dto.CalculationRule;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.AppliedRuleEvidenceService;
import com.richreach.card.service.BenefitRuleQueryService;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.service.PlanQueryService;
import com.richreach.recommendation.dto.ManualRecommendationRequest;
import com.richreach.recommendation.dto.ManualRecommendationResponse;
import com.richreach.recommendation.dto.ManualRecommendationResponse.Card;
import com.richreach.recommendation.dto.ManualRecommendationResponse.Comparison;
import com.richreach.recommendation.dto.ManualRecommendationResponse.Parsed;
import com.richreach.recommendation.dto.ManualRecommendationResponse.Performance;
import com.richreach.recommendation.dto.ManualRecommendationResponse.PlanImpact;
import com.richreach.recommendation.dto.ManualRecommendationResponse.RangeOption;
import com.richreach.recommendation.dto.ManualRecommendationResponse.Type;
import com.richreach.transaction.dto.CardScope;
import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.dto.TransactionSummaryResponse.CategorySummary;
import com.richreach.transaction.service.TransactionSummaryService;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 전 수동 입력 추천(1단계). 사용자가 고른 업종과 금액으로 <b>지금 결제 1건</b>을 어느 카드로 하면 혜택이 가장 크게 늘어나는지
 * 계산한다. 카드, 금액, 순위, 문구를 모두 서버가 정하고 모델(LLM)은 쓰지 않는다. 읽기만 하고 아무것도 저장하지 않는다. 질문 글자에서
 * 업종과 금액을 읽는 일은 앱이 한다.
 *
 * <ul>
 *   <li>후보는 로그인 사용자의 <b>분석 대상으로 고른 보유카드</b>(활성, 선택, 카드 상품에 매칭)뿐이다. 수집한 카드 상품 전체와
 *       비교하지 않는다. 후보가 없으면 {@code RECOMMEND-001}이다.</li>
 *   <li>카드별 추가 혜택은 {@code 계산(이번 달 지금까지 쓴 금액 + 이번 결제) - 계산(지금까지 쓴 금액)}이다. 혜택 계산기가 월 한도와
 *       전월 실적 조건을 이미 반영하므로, 한도가 거의 찬 카드는 늘어나는 혜택이 작거나 0이 된다. 이번 달 사용액은 승인일 기준으로
 *       취소·환불을 뺀 값이고, 전월 실적은 수집한 지난달 거래로 계산한 추정값이다.</li>
 *   <li><b>금액을 보내면</b> 가장 유리한 카드({@code CARD})와 나머지 후보 중 혜택이 양수인 카드(대안)를 준다. 혜택이 큰 순서이고,
 *       같으면 보유카드 ID가 작은 쪽이 먼저다. 늘어나는 혜택이 양수인 카드가 하나도 없으면 {@code TEXT}다. 이것은 "카드에 혜택이
 *       없다"가 아니라 "현재 확인된 규칙으로 계산할 수 있는 혜택이 없다"는 뜻이다.</li>
 *   <li><b>금액을 보내지 않으면</b> 대표 금액 세 개(5천, 2만, 5만 원)로 각각 계산해 금액 구간별로 가장 유리한 카드({@code RANGE})를
 *       준다. 평소 결제 금액이 든 구간(최근 3개월 그 업종의 결제 한 건 평균)에 {@code best}를 표시한다.</li>
 *   <li>가장 유리한 카드에는 이번 결제 금액 대비 예상 혜택 비율, 이번 결제를 반영한 이번 달 실적과 다음 실적 구간, 추천 이유를 함께
 *       준다. 이유는 서버가 정한 고정 문구다.</li>
 *   <li>혜택 계산기의 규칙 선택({@code richreach.benefit.rule-selection})을 그대로 따른다. 기본은 사람이 원문과 대조해 승인한
 *       규칙만 쓰므로 승인된 규칙이 없으면 항상 {@code TEXT}다. 또 규칙의 가맹점 칸에 값이 있으면 계산기가 가맹점 지정 혜택으로 보고
 *       빼므로, 전 가맹점 혜택이라도 그 칸을 비워야 계산된다. {@code ALL}은 개발·시연용이고 결과의 {@code confirmedOnly}가
 *       false가 되어 참고용으로 표시한다.</li>
 * </ul>
 * 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualRecommendationService {

    /** 결제 금액의 상한(원). */
    static final long MAX_AMOUNT = 100_000_000L;
    /** 응답에 담는 대안 카드 수의 상한. */
    public static final int MAX_ALTERNATIVES = 10;

    /** 금액 구간별 추천의 대표 금액, 구간 이름, 구간 끝(이 금액 이하). 마지막 구간은 끝이 없다. */
    static final long[] RANGE_AMOUNTS = {5_000, 20_000, 50_000};
    static final String[] RANGE_LABELS = {"10,000원 이하", "10,000원 초과 ~ 30,000원", "30,000원 초과"};
    private static final long[] RANGE_ENDS = {10_000, 30_000, Long.MAX_VALUE};

    static final String TEXT_MESSAGE = "현재 확인된 혜택 규칙으로는 이번 결제의 추가 혜택을 확인하지 못했어요.";
    static final String RANGE_MESSAGE = "결제 금액을 아직 모른다면 금액 구간별로 가장 유리한 카드를 알려드릴게요.";
    static final String RANGE_FOLLOW_UP = "정확한 금액을 입력하면 카드 한 장과 예상 혜택을 바로 추천해 드려요.";
    static final String SCOPE = "카드 혜택 규칙 중 업종과 월간 사용액으로 계산할 수 있는 비율 혜택만 비교했어요. "
        + "전월 실적은 수집한 거래로 계산한 추정값이에요.";
    static final String UNCONFIRMED_SCOPE_SUFFIX = " 아직 원문 확인 전인 규칙이 포함돼 있어 참고용이에요.";

    static final String REASON_BEST = "이번 결제로 늘어나는 예상 혜택이 가장 커요.";
    static final String REASON_LIMIT_REACHED = "이번 결제로 월 혜택 한도에 도달해요.";
    static final String REASON_LIMIT_LEFT = "월 혜택 한도가 남아 있어요.";
    static final String REASON_PERFORMANCE_DONE = "실적 구간을 모두 채웠어요.";
    static final String REASON_UNCONFIRMED = "원문 확인 전 규칙이 포함돼 있어 참고용이에요.";

    private final OwnedCardQueryService ownedCards;
    private final TransactionSummaryService transactions;
    private final BenefitCalculator calculator;
    private final BenefitRuleQueryService rules;
    private final PlanQueryService plans;
    private final AppliedRuleEvidenceService evidence;
    private final Clock clock;

    /**
     * 한 번의 요청에서 모든 카드가 같이 쓰는 이번 달 사용액과 전월 실적.
     *
     * @param thisMonth      이번 달 카드·업종별 사용액. 혜택 계산에 쓴다. 순사용액이 0 이하인 업종은 빠져 있다.
     * @param thisMonthTotal 이번 달 카드별 순사용액. 실적 구간 계산에 쓴다. 환불로 음수가 된 업종도 합계에 반영돼 있다.
     * @param previousMonth  지난달 카드별 순사용액(전월 실적)
     */
    private record Spending(Map<Long, Map<SpendingCategory, Long>> thisMonth, Map<Long, Long> thisMonthTotal,
                            Map<Long, Long> previousMonth) {

        Map<SpendingCategory, Long> of(long cardId) {
            return thisMonth.getOrDefault(cardId, Map.of());
        }

        long totalOf(long cardId) {
            return thisMonthTotal.getOrDefault(cardId, 0L);
        }

        long previousOf(long cardId) {
            return previousMonth.getOrDefault(cardId, 0L);
        }

    }

    /** 카드 한 장과 이번 결제로 늘어나는 혜택. {@code gain}이 0 이하면 혜택이 없는 카드다. */
    private record Scored(OwnedCardResponse card, long gain, BenefitResult before, BenefitResult result) {

    }

    @Transactional(readOnly = true)
    public ManualRecommendationResponse recommend(long userId, ManualRecommendationRequest request) {
        SpendingCategory category = parseCategory(request);
        Long amount = parseAmount(request);
        long startedAt = System.nanoTime();

        List<OwnedCardResponse> candidates = ownedCards.list(userId).stream()
            .filter(card -> card.selected() && card.cardProductId() != null).toList();
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT,
                "분석 대상으로 고른 카드가 없어 추천할 수 없습니다.");
        }

        YearMonth month = YearMonth.now(clock);
        Spending spending = new Spending(transactions.cardCategorySpend(userId, month),
            transactions.cardSpend(userId, month), transactions.cardSpend(userId, month.minusMonths(1)));

        long loadedAt = System.nanoTime();

        ManualRecommendationResponse response = amount == null
            ? ranges(userId, candidates, category, spending)
            : single(userId, month, candidates, category, amount, spending);
        // 구간별 소요 시간만 남긴다. 금액과 업종은 남기지 않는다.
        long finishedAt = System.nanoTime();
        log.info("recommend.manual type={} cards={} loadMs={} calcMs={} totalMs={}", response.type(), candidates.size(),
            millis(startedAt, loadedAt), millis(loadedAt, finishedAt), millis(startedAt, finishedAt));
        return response;
    }

    private static long millis(long fromNanos, long toNanos) {
        return (toNanos - fromNanos) / 1_000_000;
    }

    /** 금액을 알 때: 가장 유리한 카드 한 장과 나머지 카드의 예상 혜택. */
    private ManualRecommendationResponse single(long userId, YearMonth month, List<OwnedCardResponse> candidates,
                                                SpendingCategory category, long amount, Spending spending) {
        List<Scored> ranked = rank(candidates, category, amount, spending);
        Parsed parsed = new Parsed(category, amount);
        Scored best = ranked.get(0);
        if (best.gain() <= 0) {
            return new ManualRecommendationResponse(Type.TEXT, TEXT_MESSAGE, parsed, null, List.of(), List.of(), null,
                SCOPE);
        }
        // 대안은 늘어나는 혜택이 양수인 카드만이다. 추천 채팅이 "계산 가능한 다른 카드가 있는지"를 이 목록으로 판단한다.
        List<Comparison> alternatives = ranked.stream().skip(1).filter(entry -> entry.gain() > 0).limit(MAX_ALTERNATIVES)
            .map(ManualRecommendationService::comparison).toList();
        String scope = best.result().confirmedOnly() ? SCOPE : SCOPE + UNCONFIRMED_SCOPE_SUFFIX;
        return new ManualRecommendationResponse(Type.CARD, message(best), parsed, detail(best, userId, month, category, amount, spending),
            alternatives, List.of(), null, scope);
    }

    /** 금액을 모를 때: 대표 금액으로 구간마다 가장 유리한 카드를 구한다. 유리한 카드가 없는 구간은 뺀다. */
    private ManualRecommendationResponse ranges(long userId, List<OwnedCardResponse> candidates,
                                                SpendingCategory category, Spending spending) {
        int bestIndex = typicalRangeIndex(userId, category);
        List<RangeOption> options = new ArrayList<>();
        boolean confirmed = true;
        for (int i = 0; i < RANGE_AMOUNTS.length; i++) {
            Scored best = rank(candidates, category, RANGE_AMOUNTS[i], spending).get(0);
            if (best.gain() > 0) {
                options.add(new RangeOption(RANGE_LABELS[i], RANGE_AMOUNTS[i], i == bestIndex, comparison(best)));
                confirmed &= best.result().confirmedOnly();
            }
        }
        Parsed parsed = new Parsed(category, null);
        if (options.isEmpty()) {
            return new ManualRecommendationResponse(Type.TEXT, TEXT_MESSAGE, parsed, null, List.of(), List.of(), null,
                SCOPE);
        }
        return new ManualRecommendationResponse(Type.RANGE, RANGE_MESSAGE, parsed, null, List.of(), options,
            RANGE_FOLLOW_UP, confirmed ? SCOPE : SCOPE + UNCONFIRMED_SCOPE_SUFFIX);
    }

    /** 모든 후보 카드의 늘어나는 혜택을 구해 큰 순서로 돌려준다. 같으면 보유카드 ID가 작은 쪽이 먼저다. */
    private List<Scored> rank(List<OwnedCardResponse> candidates, SpendingCategory category, long amount,
                              Spending spending) {
        List<Scored> scored = new ArrayList<>();
        for (OwnedCardResponse card : candidates) {
            scored.add(score(card, category, amount, spending.of(card.cardId()), spending.previousOf(card.cardId())));
        }
        scored.sort(Comparator.comparingLong(Scored::gain).reversed()
            .thenComparingLong(entry -> entry.card().cardId()));
        return scored;
    }

    /** 이번 결제를 그 카드로 하기 전과 후의 혜택 계산 결과를 비교해 늘어나는 혜택을 구한다. */
    private Scored score(OwnedCardResponse card, SpendingCategory category, long amount,
                         Map<SpendingCategory, Long> spentByCategory, long previousMonthSpend) {
        long productId = card.cardProductId();
        BenefitResult before = calculator.calculate(new BenefitQuery(productId, spentByCategory, previousMonthSpend));
        Map<SpendingCategory, Long> afterSpend = new EnumMap<>(SpendingCategory.class);
        afterSpend.putAll(spentByCategory);
        afterSpend.merge(category, amount, Math::addExact);
        BenefitResult after = calculator.calculate(new BenefitQuery(productId, afterSpend, previousMonthSpend));
        return new Scored(card, after.totalBenefit() - before.totalBenefit(), before, after);
    }

    /** 가장 유리한 카드의 혜택 비율, 이번 달 실적, 추천 이유. */
    private Card detail(Scored best, long userId, YearMonth month, SpendingCategory category, long amount,
                        Spending spending) {
        OwnedCardResponse card = best.card();
        List<CalculationRule> cardRules = rules.findForCalculation(card.cardProductId());
        // 실적은 카드별 순사용액으로 센다. 업종별 합계는 순사용액이 0 이하인 업종을 버려서, 환불이 있으면 실제보다 커진다.
        long monthSpendAfter = Math.addExact(spending.totalOf(card.cardId()), amount);

        List<String> reasons = new ArrayList<>();
        reasons.add(REASON_BEST);

        // 한도 사유는 혜택 계산기가 실제로 계산에 넣는 규칙의 한도만으로 판단한다. 계산기가 빼는 규칙(가맹점 지정, 정액, 마일리지,
        // 다른 혜택과의 관계를 따지는 규칙, 일·횟수 한도 규칙)의 한도가 합에 들어가면 한도가 이미 찼는데도 "남아 있다"고 안내한다.
        OptionalLong limit = CardPerformanceAnalyzer.monthlyLimit(
            cardRules.stream().filter(ManualRecommendationService::countedByCalculator).toList(),
            spending.previousOf(card.cardId()));
        if (limit.isPresent()) {
            reasons.add(best.result().totalBenefit() >= limit.getAsLong() ? REASON_LIMIT_REACHED : REASON_LIMIT_LEFT);
        }

        Performance performance = null;
        List<Long> tiers = CardPerformanceAnalyzer.tiers(cardRules);
        if (!tiers.isEmpty()) {
            OptionalLong next = CardPerformanceAnalyzer.nextTier(tiers, monthSpendAfter);
            Long nextTier = next.isPresent() ? next.getAsLong() : null;
            Long remaining = next.isPresent() ? next.getAsLong() - monthSpendAfter : null;
            performance = new Performance(monthSpendAfter, nextTier, remaining);
            reasons.add(remaining == null ? REASON_PERFORMANCE_DONE
                : "다음 실적 구간까지 %s원 남았어요.".formatted(won(remaining)));
        }
        if (!best.result().confirmedOnly()) {
            reasons.add(REASON_UNCONFIRMED);
        }
        return new Card(card.cardId(), card.cardProductId(), card.cardCompany(), card.cardName(), card.imageUrl(),
            best.gain(), ratePercent(best.gain(), amount), performance, reasons, best.result().confirmedOnly(),
            best.result().notes(), planImpact(userId, month, card, category, amount, spending),
            evidence.find(card.cardProductId(), best.before().benefitByRule(), best.result().benefitByRule()));
    }

    /**
     * 이번 결제를 이 카드로 한 뒤 이번 달 월간 플랜의 그 카드·업종 남은 목표가 어떻게 되는지. 이번 달 플랜이 없으면 null이다. 이미 쓴
     * 금액은 승인일 기준으로 취소·환불을 뺀 업종별 순사용액이다. (플랜 화면의 "사용 금액"과 같은 값)
     */
    private PlanImpact planImpact(long userId, YearMonth month, OwnedCardResponse card, SpendingCategory category,
                                  long amount, Spending spending) {
        OptionalLong target = plans.findCardCategoryTarget(userId, month, card.cardId(), category);
        if (target.isEmpty()) {
            return null;
        }
        long spent = spending.of(card.cardId()).getOrDefault(category, 0L);
        long used = Math.addExact(spent, amount);
        return new PlanImpact(category, target.getAsLong(), Math.max(0, target.getAsLong() - spent),
            Math.max(0, target.getAsLong() - used), Math.max(0, used - target.getAsLong()));
    }

    /**
     * 혜택 계산기가 이 규칙을 혜택 계산에 넣는지. {@code BenefitMath.skipReason}과 {@code SimpleBenefitCalculator}가 규칙을 빼는
     * 기준(전월실적 조건은 제외)과 같다. 계산기를 바꾸면 이 기준도 함께 봐야 한다.
     */
    static boolean countedByCalculator(CalculationRule rule) {
        String mode = rule.ruleMode();
        return !rule.merchantSpecific()
            && rule.ratePercent() != null
            && !"MILE".equals(rule.rewardUnit())
            && !("REPLACES_BASE".equals(mode) || "PREFER_HIGHER".equals(mode) || "ANNUAL_BONUS".equals(mode))
            && !BenefitRuleSupport.hasUnmodelledLimits(rule);
    }

    /**
     * 평소 결제 금액이 든 구간의 번호. 최근 3개월 그 업종의 결제 한 건 평균이고, 거래가 없으면 -1이다. 평균의 소수 부분이 구간
     * 경계를 넘는지가 결과를 바꾸므로(30,000.5원은 30,000원 초과), 나눗셈을 하지 않고 {@code 합계 <= 구간 끝 x 거래 수}로
     * 비교한다.
     */
    private int typicalRangeIndex(long userId, SpendingCategory category) {
        for (CategorySummary summary : transactions.summarize(userId, SummaryPeriod.THREE_MONTHS, CardScope.SELECTED)
            .categories()) {
            if (summary.category() == category && summary.transactionCount() > 0) {
                for (int i = 0; i < RANGE_ENDS.length; i++) {
                    if (RANGE_ENDS[i] == Long.MAX_VALUE
                        || summary.amount() <= Math.multiplyExact(RANGE_ENDS[i], summary.transactionCount())) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static Comparison comparison(Scored scored) {
        OwnedCardResponse card = scored.card();
        return new Comparison(card.cardId(), card.cardProductId(), card.cardCompany(), card.cardName(),
            card.imageUrl(), Math.max(0, scored.gain()));
    }

    /** 이번 결제 금액 대비 예상 혜택의 비율(%). 소수 둘째 자리까지. */
    static double ratePercent(long benefit, long amount) {
        return Math.round(benefit * 10_000.0 / amount) / 100.0;
    }

    private static String message(Scored best) {
        return "추천 카드는 '%s'이고, 이번 결제의 예상 혜택은 %s원이에요.".formatted(best.card().cardName(), won(best.gain()));
    }

    private static String won(long amount) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(amount);
    }

    private static SpendingCategory parseCategory(ManualRecommendationRequest request) {
        if (request == null || request.category() == null) {
            throw invalid("업종이 필요합니다.");
        }
        return SpendingCategory.fromCode(request.category())
            .orElseThrow(() -> invalid("지원하지 않는 업종입니다."));
    }

    /** 금액을 보내지 않았으면 null이다. 보냈는데 범위 밖이면 오류다. */
    private static Long parseAmount(ManualRecommendationRequest request) {
        Long amount = request.amount();
        if (amount != null && (amount < 1 || amount > MAX_AMOUNT)) {
            throw invalid("결제 금액은 1원 이상 " + won(MAX_AMOUNT) + "원 이하여야 합니다.");
        }
        return amount;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT, message);
    }

}
