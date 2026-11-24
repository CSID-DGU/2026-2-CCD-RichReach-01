package com.richreach.card.dto;

import java.time.LocalDate;
import java.util.List;

/** 카드사 상품 페이지 수집본의 근거. 문서 버전이 확인되지 않았으면 null이다. */
public record CardEvidenceResponse(Status status, String answer, LocalDate sourceSnapshotDate,
                                   String documentVersion, List<Citation> citations) {
    public enum Status {
        ANSWERED, SOURCE_FOUND, INSUFFICIENT_EVIDENCE
    }

    public record Citation(String citationId, String sourceUrl, String excerpt, String sourceType) {
    }
}
