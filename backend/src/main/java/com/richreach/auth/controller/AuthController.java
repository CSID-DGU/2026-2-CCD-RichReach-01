package com.richreach.auth.controller;

import com.richreach.auth.dto.CheckIdRequest;
import com.richreach.auth.dto.CheckIdResponse;
import com.richreach.auth.dto.SignupRequest;
import com.richreach.auth.dto.TokenPair;
import com.richreach.auth.service.SignupService;
import com.richreach.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 없이 쓰는 인증 API. ({@code /api/auth/**}는 보안 설정에서 공개 경로다.) */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SignupService signupService;

    public AuthController(SignupService signupService) {
        this.signupService = signupService;
    }

    /** 아이디(이메일) 중복 확인. 이미 가입된 아이디여도 오류가 아니라 {@code available=false}로 답한다. */
    @PostMapping("/check-id")
    public ApiResponse<CheckIdResponse> checkId(@Valid @RequestBody CheckIdRequest request) {
        return ApiResponse.ok(new CheckIdResponse(signupService.isLoginIdAvailable(request.loginId())));
    }

    /** 회원가입. 가입 직후 로그인한 상태가 되도록 토큰을 함께 내려준다. */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED) // ResponseEntity로 상태를 정하면 Swagger 문서에 200으로 표시된다.
    public ApiResponse<TokenPair> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(signupService.signup(request));
    }

}
