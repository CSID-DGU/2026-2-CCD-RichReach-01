package com.richreach.recommendation.controller;

import com.richreach.global.response.ApiResponse;
import com.richreach.global.security.AuthenticatedUser;
import com.richreach.recommendation.dto.ManualRecommendationRequest;
import com.richreach.recommendation.dto.ManualRecommendationResponse;
import com.richreach.recommendation.service.ManualRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 1단계: 금액과 업종을 직접 선택한 결제 전 카드 추천. */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class ManualRecommendationController {

    private final ManualRecommendationService service;

    @PostMapping("/manual")
    public ApiResponse<ManualRecommendationResponse> recommend(@AuthenticationPrincipal AuthenticatedUser user,
                                                               @RequestBody ManualRecommendationRequest request) {
        return ApiResponse.ok(service.recommend(user.userId(), request));
    }
}
