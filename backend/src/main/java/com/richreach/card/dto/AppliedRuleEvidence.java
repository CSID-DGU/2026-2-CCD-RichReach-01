package com.richreach.card.dto;

import java.time.LocalDate;

/** 추천 전후 계산에 영향을 준 규칙과 그 규칙의 수집 원문. 금액 계산은 이 DTO가 하지 않는다. */
public record AppliedRuleEvidence(long ruleId, Impact impact, Status status, String sourceScopeId,
                                  String sourceUrl, String excerpt, String sourceType, LocalDate sourceSnapshotDate,
                                  String documentVersion) {
    public enum Impact { INCREASED, DECREASED }
    public enum Status { SOURCE_FOUND, SOURCE_NOT_FOUND }
}
