package com.richreach.recommendation.service;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.recommendation.dto.ManualRecommendationRequest;
import com.richreach.recommendation.dto.ManualRecommendationResponse;
import com.richreach.recommendation.dto.ManualRecommendationResponse.Card;
import com.richreach.recommendation.dto.ManualRecommendationResponse.Parsed;
import com.richreach.recommendation.dto.ManualRecommendationResponse.Type;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 전 수동 입력 추천(1단계). 사용자가 고른 금액과 업종으로 <b>지금 결제 1건</b>을 어느 카드로 하면 혜택이 가장 크게 늘어나는지
 * 계산한다. 카드, 금액, 순위, 문구를 모두 서버가 정하고 모델(LLM)은 쓰지 않는다. 읽기만 하고 아무것도 저장하지 않는다.
 *
 * <ul>
 *   <li>후보는 로그인 사용자의 <b>분석 대상으로 고른 보유카드</b>(활성, 선택, 카드 상품에 매칭)뿐이다. 수집한 카드 상품 전체와
 *       비교하지 않는다. 후보가 없으면 {@code RECOMMEND-001}이다.</li>
 *   <li>카드별 추가 혜택은 {@code 계산(이번 달 지금까지 쓴 금액 + 이번 결제) - 계산(지금까지 쓴 금액)}이다. 혜택 계산기가 월 한도와
 *       전월 실적 조건을 이미 반영하므로, 한도가 거의 찬 카드는 늘어나는 혜택이 작거나 0이 된다. 이번 달 사용액은 승인일 기준으로
 *       취소·환불을 뺀 값이고, 전월 실적은 수집한 지난달 거래로 계산한 추정값이다.</li>
 *   <li>늘어나는 혜택이 양수인 카드만 후보로 남긴다. 큰 순서이고, 같으면 보유카드 ID가 작은 쪽이 먼저다. 하나도 없으면
 *       {@code TEXT}다. 이것은 "카드에 혜택이 없다"가 아니라 "현재 확인된 규칙으로 계산할 수 있는 혜택이 없다"는 뜻이다.</li>
 *   <li>혜택 계산기의 규칙 선택({@code richreach.benefit.rule-selection})을 그대로 따른다. 기본은 사람이 원문과 대조해 승인한
 *       규칙만 쓰므로 승인된 규칙이 없으면 항상 {@code TEXT}다. {@code ALL}은 개발·시연용이고 결과의 {@code confirmedOnly}가
 *       false가 되어 참고용으로 표시한다.</li>
 * </ul>
 * 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ManualRecommendationService {

    /** 결제 금액의 상한(원). */
    static final long MAX_AMOUNT = 100_000_000L;
    /** 응답에 담는 대안 카드 수의 상한. */
    public static final int MAX_ALTERNATIVES = 3;

    static final String TEXT_MESSAGE = "현재 확인된 혜택 규칙으로는 이번 결제의 추가 혜택을 확인하지 못했어요.";
    static final String SCOPE = "카드 혜택 규칙 중 업종과 월간 사용액으로 계산할 수 있는 비율 혜택만 비교했어요. "
        + "전월 실적은 수집한 거래로 계산한 추정값이에요.";
    static final String UNCONFIRMED_SCOPE_SUFFIX = " 아직 원문 확인 전인 규칙이 포함돼 있어 참고용이에요.";

    private final OwnedCardQueryService ownedCards;
    private final TransactionSummaryService transactions;
    private final BenefitCalculator calculator;
    private final Clock clock;

    /** 카드 한 장과 이번 결제로 늘어나는 혜택. */
    private record Scored(OwnedCardResponse card, long gain, BenefitResult result) {

    }

    @Transactional(readOnly = true)
    public ManualRecommendationResponse recommend(long userId, ManualRecommendationRequest request) {
        SpendingCategory category = parseCategory(request);
        long amount = parseAmount(request);

        List<OwnedCardResponse> candidates = ownedCards.list(userId).stream()
            .filter(card -> card.selected() && card.cardProductId() != null).toList();
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT,
                "분석 대상으로 고른 카드가 없어 추천할 수 없습니다.");
        }

        YearMonth month = YearMonth.now(clock);
        Map<Long, Map<SpendingCategory, Long>> spent = transactions.cardCategorySpend(userId, month);
        Map<Long, Long> previousSpend = transactions.cardSpend(userId, month.minusMonths(1));

        List<Scored> scored = new ArrayList<>();
        for (OwnedCardResponse card : candidates) {
            Scored result = score(card, category, amount, spent.getOrDefault(card.cardId(), Map.of()),
                previousSpend.getOrDefault(card.cardId(), 0L));
            if (result.gain() > 0) {
                scored.add(result);
            }
        }
        scored.sort(Comparator.comparingLong(Scored::gain).reversed()
            .thenComparingLong(entry -> entry.card().cardId()));

        Parsed parsed = new Parsed(category, amount);
        if (scored.isEmpty()) {
            return new ManualRecommendationResponse(Type.TEXT, TEXT_MESSAGE, parsed, null, List.of(), SCOPE);
        }
        Scored best = scored.get(0);
        List<Card> alternatives = scored.stream().skip(1).limit(MAX_ALTERNATIVES).map(ManualRecommendationService::toCard)
            .toList();
        String scope = best.result().confirmedOnly() ? SCOPE : SCOPE + UNCONFIRMED_SCOPE_SUFFIX;
        return new ManualRecommendationResponse(Type.CARD, message(best), parsed, toCard(best), alternatives, scope);
    }

    /** 이번 결제를 그 카드로 하기 전과 후의 혜택 계산 결과를 비교해 늘어나는 혜택을 구한다. */
    private Scored score(OwnedCardResponse card, SpendingCategory category, long amount,
                         Map<SpendingCategory, Long> spentByCategory, long previousMonthSpend) {
        long productId = card.cardProductId();
        long before = calculator.calculate(new BenefitQuery(productId, spentByCategory, previousMonthSpend))
            .totalBenefit();
        Map<SpendingCategory, Long> afterSpend = new EnumMap<>(SpendingCategory.class);
        afterSpend.putAll(spentByCategory);
        afterSpend.merge(category, amount, Math::addExact);
        BenefitResult after = calculator.calculate(new BenefitQuery(productId, afterSpend, previousMonthSpend));
        return new Scored(card, after.totalBenefit() - before, after);
    }

    private static Card toCard(Scored scored) {
        OwnedCardResponse card = scored.card();
        return new Card(card.cardId(), card.cardProductId(), card.cardCompany(), card.cardName(), card.imageUrl(),
            scored.gain(), scored.result().confirmedOnly(), scored.result().notes());
    }

    private static String message(Scored best) {
        return "추천 카드는 '%s'이고, 이번 결제의 예상 혜택은 %s원이에요."
            .formatted(best.card().cardName(), NumberFormat.getIntegerInstance(Locale.KOREA).format(best.gain()));
    }

    private static SpendingCategory parseCategory(ManualRecommendationRequest request) {
        if (request == null || request.category() == null) {
            throw invalid("업종이 필요합니다.");
        }
        return SpendingCategory.fromCode(request.category())
            .orElseThrow(() -> invalid("지원하지 않는 업종입니다."));
    }

    private static long parseAmount(ManualRecommendationRequest request) {
        Long amount = request.amount();
        if (amount == null || amount < 1 || amount > MAX_AMOUNT) {
            throw invalid("결제 금액은 1원 이상 " + NumberFormat.getIntegerInstance(Locale.KOREA).format(MAX_AMOUNT)
                + "원 이하여야 합니다.");
        }
        return amount;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT, message);
    }

}
