package com.richreach.transaction.controller;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.response.ApiResponse;
import com.richreach.global.security.AuthenticatedUser;
import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.dto.TransactionSummaryResponse;
import com.richreach.transaction.service.TransactionSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 거래 API. 로그인이 필요하다. */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionSummaryService summaryService;

    /**
     * 기간별 소비 요약. {@code period}는 {@code 1M}, {@code 3M}, {@code 6M} 중 하나이고, 없거나 다른 값이면
     * 400({@code TRANS-002})이다. 금액은 취소·환불을 뺀 값이며, 수집된 거래가 요청한 기간보다 짧으면 {@code partial=true}다.
     * 거래가 없으면 합계와 6개 업종이 모두 0인 응답이다.
     */
    @GetMapping("/summary")
    public ApiResponse<TransactionSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser user,
                                                           @RequestParam(required = false) String period) {
        SummaryPeriod summaryPeriod = SummaryPeriod.fromCode(period)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PERIOD));
        return ApiResponse.ok(summaryService.summarize(user.userId(), summaryPeriod));
    }

}
