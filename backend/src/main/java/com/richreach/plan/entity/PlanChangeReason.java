package com.richreach.plan.entity;

/** 월간 플랜이 바뀐 원인. 사용자에게 보여주는 문구는 고정이고 AI가 만들지 않는다. */
public enum PlanChangeReason {

    /** 카드 동기화로 새 거래가 반영되었다. */
    TRANSACTION_SYNCED("새로 반영된 거래를 기준으로 남은 기간의 카드 사용 계획을 다시 계산했어요."),
    /** 사용자가 거래의 업종을 고쳤다. */
    CATEGORY_EDITED("거래 업종이 바뀌어 남은 기간의 카드 사용 계획을 다시 계산했어요."),
    /** 사용자가 이번 달 소비예산을 고쳤다. */
    BUDGET_CHANGED("소비예산이 바뀌어 남은 기간의 카드 사용 계획을 다시 계산했어요."),
    /** 사용자가 이번 달 예정지출을 추가·수정·삭제했다. */
    PLANNED_EXPENSE_CHANGED("예정지출이 바뀌어 남은 기간의 카드 사용 계획을 다시 계산했어요."),
    /** 사용자가 카드사 연결을 해제했다. */
    CARD_DISCONNECTED("카드 연결이 해제되어 남은 기간의 카드 사용 계획을 다시 계산했어요.");

    private final String text;

    PlanChangeReason(String text) {
        this.text = text;
    }

    /** 사용자에게 그대로 보여줄 변경 이유. */
    public String text() {
        return text;
    }

}
