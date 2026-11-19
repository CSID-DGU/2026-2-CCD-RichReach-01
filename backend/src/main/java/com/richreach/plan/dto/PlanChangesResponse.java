package com.richreach.plan.dto;

import com.richreach.global.category.SpendingCategory;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 월간 플랜의 변경 기록. 거래가 반영되어 플랜이 다시 계산될 때마다 하나씩 쌓이고, 홈의 "플랜이 변경됐어요" 안내와 변경 내용
 * 확인창이 읽는다. 금액은 원 단위 정수다.
 *
 * @param planId  요청한 플랜 ID
 * @param month   플랜 대상 월({@code yyyy-MM})
 * @param changes 그 달의 변경 기록. 가장 최근 것이 먼저다. 바뀐 적이 없으면 빈 목록이다.
 */
public record PlanChangesResponse(long planId, String month, List<Change> changes) {

    /**
     * 변경 기록 한 건.
     *
     * @param changeId        변경 기록 ID. 확인 처리에 쓴다.
     * @param planId          바뀐 뒤의 플랜 ID
     * @param previousPlanId  바뀌기 전의 플랜 ID. 지워졌으면 null
     * @param reasonCode      변경 원인 코드(TRANSACTION_SYNCED, CATEGORY_EDITED, BUDGET_CHANGED, PLANNED_EXPENSE_CHANGED)
     * @param reasonText      사용자에게 그대로 보여줄 변경 이유. 고정 문구다.
     * @param previousBenefit 바뀌기 전 예상 혜택(원)
     * @param currentBenefit  바뀐 뒤 예상 혜택(원)
     * @param benefitDelta    예상 혜택 변화({@code currentBenefit - previousBenefit}). 줄었으면 음수
     * @param confirmed       사용자가 변경 내용을 확인했는지
     * @param changedAt       바뀐 시각
     * @param items           바뀐 내용. 업종 순서이고 같은 업종에서는 우선 카드 변경이 먼저다.
     */
    public record Change(
        long changeId,
        long planId,
        Long previousPlanId,
        String reasonCode,
        String reasonText,
        long previousBenefit,
        long currentBenefit,
        long benefitDelta,
        boolean confirmed,
        LocalDateTime changedAt,
        List<Item> items) {

    }

    /**
     * 바뀐 내용 한 줄. 카드 이름은 카드가 활성일 때만 있고 사라진 카드는 null이다.
     *
     * @param type             AMOUNT(카드·업종별 목표 금액 변경), PRIORITY(업종의 우선 카드 변경)
     * @param category         업종
     * @param cardId           AMOUNT: 금액이 바뀐 카드, PRIORITY: 새로 우선이 된 카드
     * @param cardName         {@code cardId} 카드의 이름
     * @param previousCardId   PRIORITY: 이전에 우선이던 카드. AMOUNT이면 null
     * @param previousCardName {@code previousCardId} 카드의 이름
     * @param previousAmount   AMOUNT: 이전 목표 금액, PRIORITY: 이전 우선 카드의 목표 금액
     * @param currentAmount    AMOUNT: 현재 목표 금액, PRIORITY: 새 우선 카드의 목표 금액
     */
    public record Item(
        String type,
        SpendingCategory category,
        Long cardId,
        String cardName,
        Long previousCardId,
        String previousCardName,
        long previousAmount,
        long currentAmount) {

    }

}
