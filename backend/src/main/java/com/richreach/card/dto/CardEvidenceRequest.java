package com.richreach.card.dto;

/** 카드사 원문에서 근거를 검색할 질문. 스테이징 전체 카탈로그 조회 시 카드사·상품 코드를 함께 보낸다. */
public record CardEvidenceRequest(String issuerCode, String sourceCardId, String question) {
}
