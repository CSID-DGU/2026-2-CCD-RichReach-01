package com.richreach.recommendation.dto;

import com.richreach.card.dto.AppliedRuleEvidence;
import com.richreach.codef.entity.CardCompany;
import com.richreach.global.category.SpendingCategory;
import java.util.List;

/**
 * 결제 전 수동 입력 추천 결과. 카드, 금액, 순위는 모두 서버가 계산한 값이고 문구도 서버가 정한 고정 문구다. 금액은 원 단위
 * 정수다. 읽기 전용이라 거래나 월간 플랜을 바꾸지 않는다.
 *
 * @param type             {@code CARD}: 이번 결제에 추가 혜택이 있는 카드가 있다. {@code RANGE}: 금액을 보내지 않아 금액 구간별로
 *                         유리한 카드를 준다. {@code TEXT}: 현재 확인된 혜택 규칙으로는 추가 혜택을 확인하지 못했다. (카드에 혜택이
 *                         전혀 없다는 뜻이 아니다)
 * @param message          사용자에게 보여줄 문구
 * @param parsed           서버가 받아들인 입력. {@code RANGE}이면 {@code amount}가 {@code null}이다.
 * @param card             가장 유리한 카드. {@code CARD}일 때만 있다.
 * @param alternatives     {@code CARD}일 때 나머지 후보 중 늘어나는 혜택이 양수인 카드(최대 10장). 혜택이 큰 순서이고, 없으면 빈 목록이다.
 * @param ranges           {@code RANGE}일 때 금액 구간별로 가장 유리한 카드. 유리한 카드가 없는 구간은 빠진다.
 * @param followUp         {@code RANGE}일 때 구간 아래에 이어서 보여줄 안내
 * @param calculationScope 계산에 포함한 혜택의 범위 안내. 화면의 안내 문구로 쓴다.
 */
public record ManualRecommendationResponse(
    Type type,
    String message,
    Parsed parsed,
    Card card,
    List<Comparison> alternatives,
    List<RangeOption> ranges,
    String followUp,
    String calculationScope) {

    public enum Type {
        CARD, RANGE, TEXT
    }

    /** 서버가 받아들인 입력. */
    public record Parsed(SpendingCategory category, Long amount) {

    }

    /**
     * 가장 유리한 카드와 그 이유.
     *
     * @param cardId             보유카드 ID({@code GET /api/cards}의 {@code cardId})
     * @param cardProductId      카드 상품 ID. 혜택 상세 조회에 쓴다.
     * @param cardCompany        카드사
     * @param cardName           카드 이름
     * @param imageUrl           카드 이미지 주소. 모르면 {@code null}이다.
     * @param expectedBenefit    이번 결제 1건으로 늘어나는 예상 혜택(원). 월 한도와 전월 실적 조건을 반영한 값이다.
     * @param benefitRatePercent 이번 결제 금액 대비 예상 혜택의 비율(%, 소수 둘째 자리까지). 한도 때문에 줄어든 만큼 반영된
     *                           실제 비율이다. 화면은 "카페 0.2% 혜택"처럼 업종 이름과 합쳐 쓴다.
     * @param performance        이번 결제를 반영한 이번 달 실적과 다음 실적 구간. 카드의 실적 구간을 알 수 없으면 {@code null}이다.
     * @param reasons            추천 이유. 서버가 정한 고정 문구이고, 숫자는 서버가 계산한 값이다.
     * @param confirmedOnly      사람이 원문과 대조해 확인한 규칙만 썼는지. false이면 확인 전 규칙이 섞여 있어 참고용이다.
     * @param notes              계산에서 빼거나 단순화한 내용
     * @param planImpact         이 카드로 이번 결제를 한 뒤 이번 달 월간 플랜이 어떻게 바뀌는지. 이번 달 플랜이 없으면 {@code null}이다.
     * @param ruleEvidence       이번 결제 전후 계산값이 달라진 규칙과 수집 원문. 빈 목록은 추적 가능한 규칙이 없다는 뜻이다.
     */
    public record Card(
        long cardId,
        long cardProductId,
        CardCompany cardCompany,
        String cardName,
        String imageUrl,
        long expectedBenefit,
        double benefitRatePercent,
        Performance performance,
        List<String> reasons,
        boolean confirmedOnly,
        List<String> notes,
        PlanImpact planImpact,
        List<AppliedRuleEvidence> ruleEvidence) {

        /** 결제 후 플랜 변화를 모르는 카드. 플랜 변화가 없는 곳에서 쓰는 생성자다. */
        public Card(long cardId, long cardProductId, CardCompany cardCompany, String cardName, String imageUrl,
                    long expectedBenefit, double benefitRatePercent, Performance performance, List<String> reasons,
                    boolean confirmedOnly, List<String> notes) {
            this(cardId, cardProductId, cardCompany, cardName, imageUrl, expectedBenefit, benefitRatePercent,
                performance, reasons, confirmedOnly, notes, null, List.of());
        }

    }

    /**
     * 이번 결제가 이번 달 월간 플랜의 그 카드·업종 목표에 미치는 영향. 플랜의 목표 금액에서 이번 달 이미 쓴 금액을 뺀 "남은 목표"가
     * 이번 결제로 얼마나 줄어드는지를 보여준다. 읽기 전용이라 플랜은 바뀌지 않는다.
     *
     * @param category            결제 업종
     * @param targetKrw           플랜이 이 카드로 이 업종에 쓰기로 한 이번 달 목표(원). 플랜에 이 카드·업종 배분이 없으면 0이다.
     * @param remainingBeforeKrw  이번 결제 전 남은 목표(원). {@code max(0, 목표 - 이번 달 이미 쓴 금액)}
     * @param remainingAfterKrw   이번 결제 후 남은 목표(원). {@code max(0, 목표 - 이미 쓴 금액 - 이번 결제)}
     * @param overTargetKrw       이번 결제로 목표를 넘기게 되는 금액(원). 넘기지 않으면 0이다.
     */
    public record PlanImpact(
        SpendingCategory category,
        long targetKrw,
        long remainingBeforeKrw,
        long remainingAfterKrw,
        long overTargetKrw) {

    }

    /**
     * 이번 달 실적. 카드의 전월실적 구간(다음 달 혜택의 구간이 된다)을 기준으로 한다. 실적 제외 조건은 문장으로만 있어 금액에서 뺄
     * 수 없으므로 사용 금액 그대로다.
     *
     * @param monthSpendKrw 이번 결제를 반영한 이번 달 사용 금액(원)
     * @param nextTierKrw   아직 채우지 못한 가장 낮은 실적 구간(원). 모두 채웠으면 {@code null}이다.
     * @param remainingKrw  다음 구간까지 남은 금액(원). 모두 채웠으면 {@code null}이다.
     */
    public record Performance(long monthSpendKrw, Long nextTierKrw, Long remainingKrw) {

    }

    /** 카드 한 장과 이번 결제의 예상 혜택. 대안 카드와 금액 구간별 추천에 쓴다. */
    public record Comparison(
        long cardId,
        long cardProductId,
        CardCompany cardCompany,
        String cardName,
        String imageUrl,
        long expectedBenefit) {

    }

    /**
     * 금액 구간 하나와 그 구간에서 가장 유리한 카드.
     *
     * @param label  구간 이름 (예: "10,000원 이하")
     * @param amount 구간을 대표하는 금액(원). 이 금액으로 계산했다.
     * @param best   평소 결제 금액이 든 구간인지. 최근 3개월 그 업종의 결제 한 건 평균으로 정하고, 거래가 없으면 모두 false다.
     * @param card   그 구간에서 가장 유리한 카드
     */
    public record RangeOption(String label, long amount, boolean best, Comparison card) {

    }

}
