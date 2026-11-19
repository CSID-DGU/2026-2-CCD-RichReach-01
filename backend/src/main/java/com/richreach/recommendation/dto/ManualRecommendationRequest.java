package com.richreach.recommendation.dto;

/** 결제 전 수동 입력. category는 화면과 혜택 계산이 공유하는 11개 소비 업종 코드다. */
public record ManualRecommendationRequest(String category, Long amount) {
}
