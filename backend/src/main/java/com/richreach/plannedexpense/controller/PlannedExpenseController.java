package com.richreach.plannedexpense.controller;

import com.richreach.global.response.ApiResponse;
import com.richreach.global.security.AuthenticatedUser;
import com.richreach.plannedexpense.dto.PlannedExpenseRequest;
import com.richreach.plannedexpense.dto.PlannedExpenseResponse;
import com.richreach.plannedexpense.dto.PlannedExpensesResponse;
import com.richreach.plannedexpense.service.PlannedExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예정지출(HOME-06~10) API. 로그인이 필요하다. 추가·수정·삭제하면 이번 달 플랜의 남은 기간이 다시 계산되고, 결과는 플랜 변경
 * 기록({@code GET /api/plans/{planId}/changes})에 남는다. 재계산은 응답과 별개로 서버가 알아서 실행한다.
 */
@RestController
@RequestMapping("/api/planned-expenses")
@RequiredArgsConstructor
public class PlannedExpenseController {

    private final PlannedExpenseService service;

    /**
     * 그 달의 예정지출 목록과 반영 방식별 합계. 월을 생략하면 이번 달이다.
     * <ul>
     *   <li>400 {@code COMMON-400}: 월이 {@code yyyy-MM} 형식이 아님</li>
     * </ul>
     */
    @GetMapping
    public ApiResponse<PlannedExpensesResponse> listPlannedExpenses(@AuthenticationPrincipal AuthenticatedUser user,
                                                                    @RequestParam(required = false) String month) {
        return ApiResponse.ok(service.list(user.userId(), month));
    }

    /**
     * 이번 달 예정지출을 추가한다.
     * <ul>
     *   <li>400 {@code EXPENSE-002}: 이름(1~30자)·금액(0보다 큼)·할부(2, 3, 6, 12)·업종·반영 방식이 올바르지 않음</li>
     *   <li>409 {@code EXPENSE-003}: 한 달에 등록할 수 있는 개수(50개)를 넘음</li>
     * </ul>
     */
    @PostMapping
    public ApiResponse<PlannedExpenseResponse> createPlannedExpense(@AuthenticationPrincipal AuthenticatedUser user,
                                                                    @RequestBody PlannedExpenseRequest request) {
        return ApiResponse.ok(service.create(user.userId(), request));
    }

    /**
     * 예정지출을 고친다. 이번 달 것만 고칠 수 있다.
     * <ul>
     *   <li>400 {@code EXPENSE-002}: 입력이 올바르지 않거나 이번 달 예정지출이 아님</li>
     *   <li>404 {@code EXPENSE-001}: 없거나 다른 사용자의 예정지출</li>
     * </ul>
     */
    @PutMapping("/{expenseId}")
    public ApiResponse<PlannedExpenseResponse> updatePlannedExpense(@AuthenticationPrincipal AuthenticatedUser user,
                                                                    @PathVariable long expenseId,
                                                                    @RequestBody PlannedExpenseRequest request) {
        return ApiResponse.ok(service.update(user.userId(), expenseId, request));
    }

    /**
     * 예정지출을 지운다. 이번 달 것만 지울 수 있다.
     * <ul>
     *   <li>400 {@code EXPENSE-002}: 이번 달 예정지출이 아님</li>
     *   <li>404 {@code EXPENSE-001}: 없거나 다른 사용자의 예정지출</li>
     * </ul>
     */
    @DeleteMapping("/{expenseId}")
    public ApiResponse<Void> deletePlannedExpense(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable long expenseId) {
        service.delete(user.userId(), expenseId);
        return ApiResponse.ok();
    }

}
