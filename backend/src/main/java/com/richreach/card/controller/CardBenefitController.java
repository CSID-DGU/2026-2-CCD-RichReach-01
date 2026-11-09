package com.richreach.card.controller;

import com.richreach.card.dto.CardBenefitsResponse;
import com.richreach.card.service.CardBenefitService;
import com.richreach.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 카드 혜택 조회 API. 로그인이 필요하다. */
@RestController
@RequestMapping("/api/cards")
public class CardBenefitController {

    private final CardBenefitService cardBenefitService;

    public CardBenefitController(CardBenefitService cardBenefitService) {
        this.cardBenefitService = cardBenefitService;
    }

    /**
     * 카드 상품의 기본 정보와 혜택, 전월 실적 조건, 제외 조건. {@code cardId}는 카드 상품 ID다.
     * 빈칸은 {@code null}로 내려가며 0이나 무제한이 아니다. 없는 카드는 404({@code CARD-001})다.
     */
    @GetMapping("/{cardId}/benefits")
    public ApiResponse<CardBenefitsResponse> benefits(@PathVariable Long cardId) {
        return ApiResponse.ok(cardBenefitService.getBenefits(cardId));
    }

}
