package com.richreach.codef.controller;

import com.richreach.codef.dto.ConnectionAgreementRequest;
import com.richreach.codef.dto.ConnectionAgreementResponse;
import com.richreach.codef.dto.ConnectionCreateRequest;
import com.richreach.codef.dto.ConnectionResponse;
import com.richreach.codef.service.ConnectionAgreementService;
import com.richreach.codef.service.ConnectionCreateService;
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
    private final ConnectionCreateService createService;

    public ConnectionController(ConnectionAgreementService agreementService, ConnectionCreateService createService) {
        this.agreementService = agreementService;
        this.createService = createService;
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

    /**
     * 카드사 한 곳을 연결한다. 여러 곳을 연결하려면 카드사마다 한 번씩 차례로 호출한다.
     * 카드 연동 동의가 없으면 400({@code AUTH-008}), 카드사 인증에 실패하면 400({@code CONN-003}),
     * CODEF 서버에 문제가 있으면 502({@code CONN-005})이다. 이미 연결한 카드사를 다시 보내면 재연결로 처리한다.
     * 카드사 아이디와 비밀번호는 저장하지 않는다.
     */
    @PostMapping
    public ApiResponse<ConnectionResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody ConnectionCreateRequest request) {
        return ApiResponse.ok(createService.create(user.userId(), request));
    }

}
