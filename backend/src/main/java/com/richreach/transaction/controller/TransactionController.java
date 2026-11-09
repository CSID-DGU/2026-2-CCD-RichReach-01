package com.richreach.transaction.controller;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.response.ApiResponse;
import com.richreach.global.response.PageResponse;
import com.richreach.global.security.AuthenticatedUser;
import com.richreach.transaction.dto.CardScope;
import com.richreach.transaction.dto.ChangeCategoryRequest;
import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.dto.TransactionResponse;
import com.richreach.transaction.dto.TransactionSummaryResponse;
import com.richreach.transaction.service.TransactionCategoryService;
import com.richreach.transaction.service.TransactionQueryService;
import com.richreach.transaction.service.TransactionSummaryService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 거래 API. 로그인이 필요하다. */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final TransactionSummaryService summaryService;
    private final TransactionQueryService queryService;
    private final TransactionCategoryService categoryService;

    /**
     * 기간별 소비 요약. {@code period}는 {@code 1M}, {@code 3M}, {@code 6M} 중 하나이고, 없거나 다른 값이면
     * 400({@code TRANS-002})이다. 금액은 취소·환불을 뺀 값이며, 수집된 거래가 요청한 기간보다 짧으면 {@code partial=true}다.
     * 거래가 없으면 합계와 6개 업종이 모두 0인 응답이다.
     *
     * <p>{@code cardScope}는 합계와 업종이 어느 카드의 거래를 볼지다. {@code SELECTED}(기본)는 분석 대상으로 고른 카드,
     * {@code ALL}은 모든 거래이고, 다른 값이면 400({@code COMMON-400})이다. 응답의 {@code cards}는 범위와 상관없이
     * 활성 카드 전체의 건수와 금액이다.
     */
    @GetMapping("/summary")
    public ApiResponse<TransactionSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser user,
                                                           @RequestParam(required = false) String period,
                                                           @RequestParam(required = false) String cardScope) {
        SummaryPeriod summaryPeriod = SummaryPeriod.fromCode(period)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PERIOD));
        return ApiResponse.ok(summaryService.summarize(user.userId(), summaryPeriod, parseScope(cardScope)));
    }

    /**
     * 거래 목록(승인일시 최신순). 조건은 모두 선택이다.
     * <ul>
     *   <li>{@code period}: {@code 1M}, {@code 3M}, {@code 6M}. 요약과 같은 기준(승인일). 다른 값이면 400({@code TRANS-002}).
     *       없으면 전체 기간이다.</li>
     *   <li>{@code cardId}: 이 보유카드의 거래만. 내 카드가 아니면 결과가 비어 있다. 지정하면 {@code cardScope}보다 우선한다.</li>
     *   <li>{@code cardScope}: {@code cardId}가 없을 때 볼 카드. {@code SELECTED}(기본)는 분석 대상으로 고른 카드,
     *       {@code ALL}은 모든 거래다. 다른 값이면 400({@code COMMON-400}).</li>
     *   <li>{@code category}: 화면용 6개 업종 중 하나(대소문자 무시). 다른 값이면 400({@code COMMON-400}).
     *       사용자가 고친 값 기준이고, {@code ETC}는 분류 전인 거래도 포함한다.</li>
     *   <li>{@code includeCanceled}: 기본 {@code true}. {@code false}이면 전액 취소된 거래를 뺀다. (부분 취소는 남는다.)</li>
     *   <li>{@code page}(0부터), {@code size}(1~100, 기본 20)</li>
     * </ul>
     * 합계는 요약 API가 맡고, 목록은 합계를 주지 않는다.
     */
    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                               @RequestParam(required = false) String period,
                                                               @RequestParam(required = false) Long cardId,
                                                               @RequestParam(required = false) String cardScope,
                                                               @RequestParam(required = false) String category,
                                                               @RequestParam(defaultValue = "true") boolean includeCanceled,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                "page는 0 이상, size는 1부터 " + MAX_PAGE_SIZE + "까지여야 합니다.");
        }
        SummaryPeriod summaryPeriod = period == null ? null : SummaryPeriod.fromCode(period)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PERIOD));
        SpendingCategory spendingCategory = category == null ? null : parseCategory(category);

        var filter = new TransactionQueryService.Filter(summaryPeriod, cardId, parseScope(cardScope), spendingCategory,
            includeCanceled);
        return ApiResponse.ok(PageResponse.from(queryService.list(user.userId(), filter, page, size)));
    }

    /** 거래 상세. 내 거래가 아니거나 없으면 404({@code TRANS-001})이다. */
    @GetMapping("/{transactionId}")
    public ApiResponse<TransactionResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable long transactionId) {
        return ApiResponse.ok(queryService.get(user.userId(), transactionId));
    }

    /**
     * 거래의 업종을 고친다. 화면용 6개 업종 중 하나여야 하고(아니면 400 {@code COMMON-400}), 내 거래가 아니거나 없으면
     * 404({@code TRANS-001})이다. 고친 값은 재동기화로 바뀌지 않고, 고친 뒤의 거래를 돌려준다.
     */
    @PatchMapping("/{transactionId}/category")
    public ApiResponse<TransactionResponse> changeCategory(@AuthenticationPrincipal AuthenticatedUser user,
                                                           @PathVariable long transactionId,
                                                           @Valid @RequestBody ChangeCategoryRequest request) {
        return ApiResponse.ok(categoryService.change(user.userId(), transactionId, parseCategory(request.category())));
    }

    /** 고친 업종을 지우고 자동 분류로 되돌린다. 내 거래가 아니거나 없으면 404({@code TRANS-001})이다. */
    @DeleteMapping("/{transactionId}/category")
    public ApiResponse<TransactionResponse> resetCategory(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @PathVariable long transactionId) {
        return ApiResponse.ok(categoryService.reset(user.userId(), transactionId));
    }

    /** 없으면 기본 범위, 알 수 없는 값이면 400이다. */
    private static CardScope parseScope(String code) {
        if (code == null) {
            return CardScope.DEFAULT;
        }
        return CardScope.fromCode(code).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
            "지원하지 않는 카드 범위입니다. " + Arrays.stream(CardScope.values()).map(Enum::name)
                .collect(Collectors.joining(", ")) + " 중 하나여야 합니다."));
    }

    private static SpendingCategory parseCategory(String code) {
        return SpendingCategory.fromCode(code).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
            "지원하지 않는 업종입니다. " + Arrays.stream(SpendingCategory.values()).map(Enum::name)
                .collect(Collectors.joining(", ")) + " 중 하나여야 합니다."));
    }

}
