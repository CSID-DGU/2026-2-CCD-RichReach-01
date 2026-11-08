package com.richreach.plan.entity;

/** 월간 플랜을 만든 전략. */
public enum PlanStrategy {
    /** 기존 소비 습관 그대로 (비교 기준) */
    HABIT,
    /** 탐욕 방식 */
    GREEDY,
    /** CP-SAT 최적화 */
    CP_SAT
}
