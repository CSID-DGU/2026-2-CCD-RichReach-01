package com.richreach.plan.controller;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.response.ApiResponse;
import com.richreach.global.security.AuthenticatedUser;
import com.richreach.plan.dto.PlanAnalysisResponse;
import com.richreach.plan.service.SpendingAnalysisService;
import com.richreach.transaction.dto.SummaryPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 월간 플랜 관련 조회 API. 로그인이 필요하다. */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanAnalysisController {

    private final SpendingAnalysisService analysisService;

    /**
     * 소비 패턴 분석과 월 예산 추천. {@code period}는 {@code 1M}, {@code 3M}, {@code 6M} 중 하나이고(대소문자 무시),
     * 없거나 다른 값이면 400({@code TRANS-002})이다. 분석 대상으로 고른 카드의 거래만 보며, 거래가 없으면 오류 없이
     * {@code hasData=false}이고 비율과 예산이 모두 0이다.
     */
    @GetMapping("/analysis")
    public ApiResponse<PlanAnalysisResponse> analysis(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @RequestParam(required = false) String period) {
        SummaryPeriod summaryPeriod = SummaryPeriod.fromCode(period)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PERIOD));
        return ApiResponse.ok(analysisService.analyze(user.userId(), summaryPeriod));
    }

}
