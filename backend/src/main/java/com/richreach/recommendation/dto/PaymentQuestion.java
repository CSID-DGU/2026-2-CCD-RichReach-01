package com.richreach.recommendation.dto;

import com.richreach.global.category.SpendingCategory;

/** 모델이 질문에서 추출한 정보. 카드 혜택이나 추천 금액은 포함하지 않는다. */
public record PaymentQuestion(SpendingCategory category, String merchantName, Long amount,
                              boolean cardQuestion) {
}
