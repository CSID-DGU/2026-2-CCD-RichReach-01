package com.richreach.plan.entity;

/** 월간 플랜을 만든 전략. */
public enum PlanStrategy {
    /** 기존 소비 습관 그대로 (비교 기준) */
    HABIT,
    /** 탐욕 방식 */
    GREEDY,
    /** CP-SAT 최적화 */
    CP_SAT,
    /**
     * 임시 전략: 업종 예산을 카드들에 똑같이 나눈다. 혜택을 고려해 카드를 고르지는 않고 혜택은 계산해서 보여줄 뿐이다.
     * 다른 전략이 아직 없을 때도 플랜 생성이 끝까지 동작하게 하려는 것이고, 다른 전략이 구현되면 사용자에게는 보이지 않는다.
     */
    EVEN_SPLIT
}
