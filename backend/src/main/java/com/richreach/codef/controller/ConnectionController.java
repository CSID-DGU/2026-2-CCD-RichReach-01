package com.richreach.codef.controller;

import com.richreach.codef.dto.ConnectionAgreementRequest;
import com.richreach.codef.dto.ConnectionAgreementResponse;
import com.richreach.codef.service.ConnectionAgreementService;
import com.richreach.global.response.ApiResponse;
import com.richreach.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 카드 연동 API. 로그인이 필요하다. */
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionAgreementService agreementService;

    public ConnectionController(ConnectionAgreementService agreementService) {
        this.agreementService = agreementService;
    }

    /**
     * 카드 연동 동의(카드정보 조회, 거래내역 조회, 개인정보 수집 및 이용)를 저장한다. 3개 모두 필요하고
     * 하나라도 빠지면 400({@code AUTH-008})이다. 같은 약관 버전에 다시 동의해도 오류 없이 같은 결과를 돌려준다.
     */
    @PostMapping("/agreements")
    public ApiResponse<ConnectionAgreementResponse> agree(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @Valid @RequestBody ConnectionAgreementRequest request) {
        return ApiResponse.ok(agreementService.agree(user.userId(), request));
    }

}
