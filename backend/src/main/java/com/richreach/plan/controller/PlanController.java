package com.richreach.plan.controller;

import com.richreach.global.response.ApiResponse;
import com.richreach.global.security.AuthenticatedUser;
import com.richreach.plan.dto.PlanCategoriesResponse;
import com.richreach.plan.dto.PlanCardTargetsResponse;
import com.richreach.plan.dto.PlanChangesResponse;
import com.richreach.plan.dto.PlanCreateRequest;
import com.richreach.plan.dto.PlanJobStartResponse;
import com.richreach.plan.dto.PlanJobStatusResponse;
import com.richreach.plan.dto.PlanMonthDraftResponse;
import com.richreach.plan.dto.PlanResponse;
import com.richreach.plan.service.PlanBudgetService;
import com.richreach.plan.service.PlanCardTargetsService;
import com.richreach.plan.service.PlanChangeQueryService;
import com.richreach.plan.service.PlanGenerationService;
import com.richreach.plan.service.PlanMonthDraftService;
import com.richreach.plan.service.PlanQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 월간 플랜 생성·조회 API. 로그인이 필요하다. */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanGenerationService generationService;
    private final PlanQueryService queryService;
    private final PlanChangeQueryService changeService;
    private final PlanBudgetService budgetService;
    private final PlanCardTargetsService cardTargetsService;
    private final PlanMonthDraftService monthDraftService;

    /**
     * 월간 플랜 생성을 시작한다. 계산은 오래 걸릴 수 있어서 작업을 시작하고 바로 {@code jobId}를 돌려주며, 진행 상태는
     * {@code GET /api/plans/jobs/{jobId}}로 조회한다.
     * <ul>
     *   <li>400 {@code COMMON-400}: 월이 {@code yyyy-MM} 형식이 아님</li>
     *   <li>400 {@code PLAN-003}: 예산이 올바르지 않음 (총예산 범위, 업종 코드, 중복, 업종 예산의 합이 총예산과 다름)</li>
     *   <li>400 {@code PLAN-006}: 분석 대상으로 고른 카드가 없음</li>
     *   <li>409 {@code PLAN-004}: 그 달의 플랜이 이미 있음</li>
     *   <li>409 {@code PLAN-005}: 이미 다른 요청의 플랜 생성이 진행 중. (같은 요청이 다시 오면 진행 중인 작업의 {@code jobId}를
     *       그대로 돌려준다.)</li>
     * </ul>
     */
    @PostMapping
    public ApiResponse<PlanJobStartResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @RequestBody PlanCreateRequest request) {
        return ApiResponse.ok(generationService.start(user.userId(), request));
    }

    /**
     * 이번 달 소비예산을 고치고, 새 예산으로 남은 기간의 플랜을 다시 계산하는 작업을 시작한다. 요청 본문은 플랜 생성과 같고 계산은 오래
     * 걸릴 수 있어서 {@code jobId}를 바로 돌려주며, 진행 상태는 {@code GET /api/plans/jobs/{jobId}}로 조회한다. 이미 쓴 금액은
     * 그 카드에 고정되고 새 플랜 버전으로 저장되며 변경 기록에 남는다.
     * <ul>
     *   <li>400 {@code COMMON-400}: 월이 {@code yyyy-MM} 형식이 아니거나 이번 달이 아님</li>
     *   <li>400 {@code PLAN-003}: 예산이 올바르지 않음 (총예산 범위, 업종 코드, 중복, 업종 예산의 합이 총예산과 다름)</li>
     *   <li>400 {@code PLAN-006}: 분석 대상으로 고른 카드가 없음</li>
     *   <li>404 {@code PLAN-001}: 그 달의 플랜이 없음 (처음 만들 때는 {@code POST /api/plans})</li>
     *   <li>409 {@code PLAN-005}: 같은 달에 다른 재계산이 진행 중. 같은 예산 수정 요청이 다시 오면 진행 중인 작업의
     *       {@code jobId}를 그대로 돌려준다.</li>
     * </ul>
     */
    @PutMapping("/budget")
    public ApiResponse<PlanJobStartResponse> updateBudget(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @RequestBody PlanCreateRequest request) {
        return ApiResponse.ok(budgetService.start(user.userId(), request));
    }

    /**
     * 그 달에 사용자에게 보여줄 플랜 한 개. 읽기 전용이다.
     * <ul>
     *   <li>400 {@code COMMON-400}: 월이 없거나 {@code yyyy-MM} 형식이 아님</li>
     *   <li>404 {@code PLAN-001}: 그 달의 플랜이 없음</li>
     * </ul>
     */
    @GetMapping
    public ApiResponse<PlanResponse> monthly(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestParam(required = false) String month) {
        return ApiResponse.ok(queryService.getMonthlyPlan(user.userId(), month));
    }

    /**
     * 새 달 예산 초안. 새 달이 되면 이번 달 플랜이 없으므로({@code GET /api/plans}가 404 {@code PLAN-001}), 지난달에 확정한
     * 예산(없으면 최근 소비로 추천한 예산)을 이번 달 기본값으로 돌려준다. 저장하지 않고 플랜도 자동으로 만들지 않는다. 사용자가
     * 고친 뒤 {@code POST /api/plans}로 만든다. 지난달 소비와 예산 어느 쪽도 없으면 {@code source=NONE}, {@code budget=null}이다.
     */
    @GetMapping("/month-draft")
    public ApiResponse<PlanMonthDraftResponse> monthDraft(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(monthDraftService.draft(user.userId()));
    }

    /** 플랜의 업종별 사용계획. 없거나 다른 사용자의 플랜이면 404({@code PLAN-001})이다. */
    @GetMapping("/{planId}/categories")
    public ApiResponse<PlanCategoriesResponse> categories(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @PathVariable long planId) {
        return ApiResponse.ok(queryService.getCategories(user.userId(), planId));
    }

    /**
     * 플랜의 업종 하나의 사용계획(PLAN-03). 배분이 없는 업종은 오류가 아니라 목표와 혜택이 0이고 카드가 없는 응답이다.
     * 업종 코드가 11개 업종이 아니면 400({@code COMMON-400}), 없거나 다른 사용자의 플랜이면 404({@code PLAN-001})이다.
     */
    @GetMapping("/{planId}/categories/{category}")
    public ApiResponse<PlanCategoriesResponse.CategoryPlan> category(@AuthenticationPrincipal AuthenticatedUser user,
                                                                     @PathVariable long planId,
                                                                     @PathVariable String category) {
        return ApiResponse.ok(queryService.getCategory(user.userId(), planId, category));
    }

    /**
     * 플랜의 카드별 상세(PLAN-02). 카드마다 목표와 이번 달 사용 금액, 전월실적 구간, 월 혜택 한도, 업종별 사용 금액을 준다.
     * 실적과 한도, 혜택은 카드의 혜택 규칙으로 계산한 추정값이고 알 수 없는 값은 null이다. 없거나 다른 사용자의 플랜이면
     * 404({@code PLAN-001})이다.
     */
    @GetMapping("/{planId}/card-targets")
    public ApiResponse<PlanCardTargetsResponse> cardTargets(@AuthenticationPrincipal AuthenticatedUser user,
                                                            @PathVariable long planId) {
        return ApiResponse.ok(cardTargetsService.cardTargets(user.userId(), planId));
    }

    /**
     * 플랜의 변경 기록. 거래가 반영되어 남은 기간의 플랜이 다시 계산될 때마다 하나씩 쌓이고, 가장 최근 것이 먼저다. 없거나 다른
     * 사용자의 플랜이면 404({@code PLAN-001})이다.
     */
    @GetMapping("/{planId}/changes")
    public ApiResponse<PlanChangesResponse> changes(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @PathVariable long planId) {
        return ApiResponse.ok(changeService.list(user.userId(), planId));
    }

    /** 변경 내용을 확인한 것으로 표시하고 그 기록을 돌려준다. 없거나 다른 사용자의 기록이면 404({@code PLAN-009})이다. */
    @PatchMapping("/changes/{changeId}/confirm")
    public ApiResponse<PlanChangesResponse.Change> confirmChange(@AuthenticationPrincipal AuthenticatedUser user,
                                                                @PathVariable long changeId) {
        return ApiResponse.ok(changeService.confirm(user.userId(), changeId));
    }

    /**
     * 플랜 생성 또는 승인 거래 반영 재계산의 진행 상태. 화면은 {@code status}가 {@code IN_PROGRESS}가 아닐 때까지 1~2초마다 조회한다.
     * 없거나 다른 사용자의 {@code jobId}면 404({@code PLAN-002})이다.
     */
    @GetMapping("/jobs/{jobId}")
    public ApiResponse<PlanJobStatusResponse> status(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable String jobId) {
        return ApiResponse.ok(generationService.status(user.userId(), jobId));
    }

}
