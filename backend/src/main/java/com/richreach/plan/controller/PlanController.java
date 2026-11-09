package com.richreach.plan.controller;

import com.richreach.global.response.ApiResponse;
import com.richreach.global.security.AuthenticatedUser;
import com.richreach.plan.dto.PlanCreateRequest;
import com.richreach.plan.dto.PlanJobStartResponse;
import com.richreach.plan.dto.PlanJobStatusResponse;
import com.richreach.plan.service.PlanGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 월간 플랜 생성 API. 로그인이 필요하다. */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanGenerationService generationService;

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
     * 플랜 생성 진행 상태. 화면은 {@code status}가 {@code IN_PROGRESS}가 아닐 때까지 1~2초마다 조회한다.
     * 없거나 다른 사용자의 {@code jobId}면 404({@code PLAN-002})이다.
     */
    @GetMapping("/jobs/{jobId}")
    public ApiResponse<PlanJobStatusResponse> status(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable String jobId) {
        return ApiResponse.ok(generationService.status(user.userId(), jobId));
    }

}
