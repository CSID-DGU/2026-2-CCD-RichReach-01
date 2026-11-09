package com.richreach.auth.controller;

import com.richreach.auth.dto.FindEmailRequest;
import com.richreach.auth.dto.FindEmailResponse;
import com.richreach.auth.dto.PasswordCodeRequest;
import com.richreach.auth.dto.PasswordCodeResponse;
import com.richreach.auth.dto.PasswordResetRequest;
import com.richreach.auth.dto.PasswordVerifyRequest;
import com.richreach.auth.dto.PasswordVerifyResponse;
import com.richreach.auth.service.FindEmailService;
import com.richreach.auth.service.PasswordResetService;
import com.richreach.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 계정 복구 API: 이메일 찾기와 비밀번호 재설정. 로그인 없이 쓴다. ({@code /api/auth/**}는 공개 경로다.) */
@RestController
@RequestMapping("/api/auth")
public class AccountRecoveryController {

    private final FindEmailService findEmailService;
    private final PasswordResetService passwordResetService;

    public AccountRecoveryController(FindEmailService findEmailService, PasswordResetService passwordResetService) {
        this.findEmailService = findEmailService;
        this.passwordResetService = passwordResetService;
    }

    /** 이름과 전화번호로 가입한 이메일을 일부 가려서 알려준다. 계정이 없으면 404({@code AUTH-013})다. */
    @PostMapping("/find-email")
    public ApiResponse<FindEmailResponse> findEmail(@Valid @RequestBody FindEmailRequest request) {
        return ApiResponse.ok(findEmailService.findMaskedEmail(request));
    }

    /**
     * 비밀번호 재설정 인증번호를 발송한다. 가입 여부와 상관없이 같은 응답이고, 재전송 대기 시간 안이면 429다.
     * 개발 단계에서는 인증번호가 응답의 {@code devCode}와 서버 로그에 나온다.
     */
    @PostMapping("/password/code")
    public ApiResponse<PasswordCodeResponse> sendCode(@Valid @RequestBody PasswordCodeRequest request) {
        return ApiResponse.ok(passwordResetService.sendCode(request));
    }

    /** 인증번호를 확인하고 새 비밀번호 설정에 쓸 1회용 재설정 토큰을 준다. */
    @PostMapping("/password/verify")
    public ApiResponse<PasswordVerifyResponse> verifyCode(@Valid @RequestBody PasswordVerifyRequest request) {
        return ApiResponse.ok(passwordResetService.verifyCode(request));
    }

    /** 새 비밀번호를 설정한다. 성공하면 기존 로그인은 모두 끊기고 로그인 실패 잠금도 풀린다. */
    @PatchMapping("/password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.resetPassword(request);
        return ApiResponse.ok();
    }

}
