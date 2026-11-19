package com.richreach.recommendation.service;

import com.richreach.global.category.SpendingCategory;
import java.util.Optional;

/** 추천 결과의 문장만 제안한다. 카드 선택과 금액 계산에는 관여하지 않는다. */
public interface RecommendationExplanationClient {

    Optional<String> explain(SpendingCategory category, long benefit, long nextBenefit);
}
