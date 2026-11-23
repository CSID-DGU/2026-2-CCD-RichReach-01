package com.richreach.recommendation.controller;

import com.richreach.global.response.ApiResponse;
import com.richreach.global.security.AuthenticatedUser;
import com.richreach.recommendation.dto.ManualRecommendationRequest;
import com.richreach.recommendation.dto.ManualRecommendationResponse;
import com.richreach.recommendation.dto.RecommendationChatRequest;
import com.richreach.recommendation.dto.RecommendationChatResponse;
import com.richreach.recommendation.service.ManualRecommendationService;
import com.richreach.recommendation.service.RecommendationChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 결제 전 카드 추천 API. 로그인이 필요하다. */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final ManualRecommendationService manualService;
    private final RecommendationChatService chatService;

    /**
     * 금액과 업종을 직접 골라 "이번 결제를 어느 카드로 하면 유리한가"를 묻는다. 분석 대상으로 고른 보유카드끼리 이번 결제 1건으로
     * 늘어나는 혜택을 비교한다. 카드, 금액, 순위는 서버가 계산하고 아무것도 저장하지 않는다. (거래 승인이나 플랜 재계산도 일으키지
     * 않는다)
     * <ul>
     *   <li>{@code type=CARD}: 늘어나는 혜택이 양수인 카드가 있다. {@code card}가 가장 유리한 카드이고 {@code alternatives}가 그
     *       다음 카드다.</li>
     *   <li>{@code type=TEXT}: 현재 확인된 혜택 규칙으로는 추가 혜택을 확인하지 못했다. (카드에 혜택이 없다는 뜻이 아니다)</li>
     *   <li>400 {@code RECOMMEND-001}: 업종이 없거나 지원하지 않는 값, 금액이 1~100,000,000원이 아님, 또는 분석 대상으로 고른 카드가
     *       없음</li>
     * </ul>
     */
    @PostMapping("/manual")
    public ApiResponse<ManualRecommendationResponse> manual(@AuthenticationPrincipal AuthenticatedUser user,
                                                            @RequestBody ManualRecommendationRequest request) {
        return ApiResponse.ok(manualService.recommend(user.userId(), request));
    }

    /** 자연어 질문을 해석한 뒤 검증된 금액·업종으로 기존 수동 추천을 실행한다. 저장은 하지 않는다. */
    @PostMapping("/chat")
    public ApiResponse<RecommendationChatResponse> chat(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @RequestBody RecommendationChatRequest request) {
        return ApiResponse.ok(chatService.recommend(user.userId(), request));
    }

}
