package com.richreach.user.controller;

import com.richreach.global.response.ApiResponse;
import com.richreach.global.security.AuthenticatedUser;
import com.richreach.user.dto.WithdrawalRequest;
import com.richreach.user.service.UserWithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 계정 API. 로그인이 필요하다. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserWithdrawalService withdrawalService;

    /**
     * 회원 탈퇴. 계정과 카드·거래·플랜·예정지출 등 모든 데이터를 즉시 삭제하고, 연결한 카드사 계정은 CODEF에서도 지운다. 되돌릴 수
     * 없다. 같은 이메일로 다시 가입할 수 있다. 본문은 {@code {"agreed": true}}이고 비밀번호는 받지 않는다. 성공하면 앱은 저장한
     * 토큰을 지운다.
     * <ul>
     *   <li>400 {@code COMMON-400}: 본문이 없거나 {@code agreed}가 {@code true}가 아님</li>
     *   <li>409 {@code CONN-007}: 카드 동기화가 진행 중. 끝난 뒤 다시 시도</li>
     *   <li>502 {@code CONN-005} 등: CODEF에서 카드사 계정을 지우지 못함. 아무것도 지워지지 않았으니 다시 시도</li>
     * </ul>
     */
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody WithdrawalRequest request) {
        withdrawalService.withdraw(user.userId());
        return ApiResponse.ok();
    }

}
