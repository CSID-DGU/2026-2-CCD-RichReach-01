package com.richreach.auth.controller;

import com.richreach.auth.dto.CheckIdRequest;
import com.richreach.auth.dto.CheckIdResponse;
import com.richreach.auth.dto.LoginRequest;
import com.richreach.auth.dto.RefreshTokenRequest;
import com.richreach.auth.dto.SignupRequest;
import com.richreach.auth.dto.TokenPair;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.auth.service.LoginService;
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
    private final LoginService loginService;
    private final AuthTokenService authTokenService;

    public AuthController(SignupService signupService, LoginService loginService,
                          AuthTokenService authTokenService) {
        this.signupService = signupService;
        this.loginService = loginService;
        this.authTokenService = authTokenService;
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

    /**
     * 이메일 + 비밀번호 로그인. 이메일/비밀번호 중 무엇이 틀렸는지는 알려주지 않고({@code AUTH-002}),
     * 연속으로 실패해 잠겼으면 {@code AUTH-003}(423)이다.
     */
    @PostMapping("/login")
    public ApiResponse<TokenPair> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(loginService.login(request));
    }

    /**
     * access 토큰이 만료됐을 때(AUTH-004) refresh 토큰으로 새 토큰 한 쌍을 받는다. 쓴 refresh 토큰은 폐기된다.
     * 실패하면 401이고, 클라이언트는 세션 만료 화면으로 보내면 된다.
     */
    @PostMapping("/reissue")
    public ApiResponse<TokenPair> reissue(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authTokenService.reissue(request.refreshToken()));
    }

    /** 로그아웃. 이 refresh 토큰을 폐기한다. 이미 폐기됐거나 알 수 없는 토큰이어도 성공으로 답한다. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authTokenService.revoke(request.refreshToken());
        return ApiResponse.ok();
    }

}
