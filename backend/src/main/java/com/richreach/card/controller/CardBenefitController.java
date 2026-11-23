package com.richreach.card.controller;

import com.richreach.card.dto.CardBenefitsResponse;
import com.richreach.card.dto.CardEvidenceRequest;
import com.richreach.card.dto.CardEvidenceResponse;
import com.richreach.card.service.CardBenefitService;
import com.richreach.card.service.CardEvidenceService;
import com.richreach.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 카드 혜택 조회 API. 로그인이 필요하다. */
@RestController
@RequestMapping("/api/cards")
public class CardBenefitController {

    private final CardBenefitService cardBenefitService;
    private final CardEvidenceService cardEvidenceService;

    public CardBenefitController(CardBenefitService cardBenefitService, CardEvidenceService cardEvidenceService) {
        this.cardBenefitService = cardBenefitService;
        this.cardEvidenceService = cardEvidenceService;
    }

    /**
     * 카드 상품의 기본 정보와 혜택, 전월 실적 조건, 제외 조건. {@code cardId}는 카드 상품 ID다.
     * 빈칸은 {@code null}로 내려가며 0이나 무제한이 아니다. 없는 카드는 404({@code CARD-001})다.
     */
    @GetMapping("/{cardId}/benefits")
    public ApiResponse<CardBenefitsResponse> benefits(@PathVariable Long cardId) {
        return ApiResponse.ok(cardBenefitService.getBenefits(cardId));
    }

    /** 해당 카드 상품의 수집된 원문만 검색한다. 추천 금액에 적용된 규칙의 증명은 아니다. */
    @PostMapping("/{cardId}/evidence")
    public ApiResponse<CardEvidenceResponse> evidence(@PathVariable Long cardId,
                                                       @RequestBody CardEvidenceRequest request) {
        return ApiResponse.ok(cardEvidenceService.explain(cardId, request.question()));
    }

    /** 카드 상품으로 승격되지 않은 원문도 포함해 331종 스테이징 카탈로그에서 검색한다. */
    @PostMapping("/catalog/evidence")
    public ApiResponse<CardEvidenceResponse> catalogEvidence(@RequestBody CardEvidenceRequest request) {
        return ApiResponse.ok(cardEvidenceService.explainCatalog(request.issuerCode(), request.sourceCardId(),
            request.question()));
    }

}
