package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.richreach.codef.client.CodefAccountClient;
import com.richreach.codef.client.CodefAccountClient.AccountResult;
import com.richreach.codef.entity.CardCompany;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardCompanyAuthenticatorTest {

    private static final String ID = "test-card-login-id";
    private static final String PW = "test-card-secret-pw";
    private static final String CONNECTED = "test-connected-id";

    private CodefAccountClient codef;
    private CardCompanyAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        codef = mock(CodefAccountClient.class);
        authenticator = new CardCompanyAuthenticator(codef);
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("성공하면 CODEF의 결과를 그대로 돌려준다")
    void returnsSuccessfulResult() {
        AccountResult ok = new AccountResult(true, CONNECTED, null);
        when(codef.create(CardCompany.KB, ID, PW)).thenReturn(ok);
        when(codef.add(CONNECTED, CardCompany.KB, ID, PW)).thenReturn(ok);
        when(codef.update(CONNECTED, CardCompany.KB, ID, PW)).thenReturn(ok);

        assertThat(authenticator.create(CardCompany.KB, ID, PW)).isSameAs(ok);
        assertThat(authenticator.add(CONNECTED, CardCompany.KB, ID, PW)).isSameAs(ok);
        assertThat(authenticator.update(CONNECTED, CardCompany.KB, ID, PW)).isSameAs(ok);
    }

    @Test
    @DisplayName("인증에 실패하면 CONN-003이다 (세 가지 호출 모두)")
    void authFailure() {
        AccountResult fail = new AccountResult(false, null, "CF-12345");
        when(codef.create(CardCompany.KB, ID, PW)).thenReturn(fail);
        when(codef.add(CONNECTED, CardCompany.KB, ID, PW)).thenReturn(fail);
        when(codef.update(CONNECTED, CardCompany.KB, ID, PW)).thenReturn(fail);

        assertError(() -> authenticator.create(CardCompany.KB, ID, PW), ErrorCode.CARD_COMPANY_AUTH_FAILED);
        assertError(() -> authenticator.add(CONNECTED, CardCompany.KB, ID, PW), ErrorCode.CARD_COMPANY_AUTH_FAILED);
        assertError(() -> authenticator.update(CONNECTED, CardCompany.KB, ID, PW), ErrorCode.CARD_COMPANY_AUTH_FAILED);
    }

    @Test
    @DisplayName("아이디 또는 비밀번호 오류(CF-12803)는 사용자가 고칠 수 있는 입력 오류라서 CONN-003이다")
    void wrongLoginIsAuthFailure() {
        when(codef.update(CONNECTED, CardCompany.KB, ID, PW)).thenReturn(new AccountResult(false, null, "CF-12803"));

        assertError(() -> authenticator.update(CONNECTED, CardCompany.KB, ID, PW), ErrorCode.CARD_COMPANY_AUTH_FAILED);
    }

    @Test
    @DisplayName("비밀번호 복호화 실패(CF-04020)는 사용자의 입력이 아니라 우리 설정 문제라서 CONN-005다")
    void passwordDecryptErrorIsServiceError() {
        when(codef.create(CardCompany.KB, ID, PW)).thenReturn(new AccountResult(false, null, "CF-04020"));

        assertError(() -> authenticator.create(CardCompany.KB, ID, PW), ErrorCode.CODEF_UNAVAILABLE);
    }

    @Test
    @DisplayName("반복 로그인으로 계정이 일시 제한되면(CF-12207) 다시 시도하지 않도록 CONN-008로 막는다 (세 가지 호출 모두)")
    void restrictedAccountIsBlocked() {
        AccountResult restricted = new AccountResult(false, null, "CF-12207");
        when(codef.create(CardCompany.KB, ID, PW)).thenReturn(restricted);
        when(codef.add(CONNECTED, CardCompany.KB, ID, PW)).thenReturn(restricted);
        when(codef.update(CONNECTED, CardCompany.KB, ID, PW)).thenReturn(restricted);

        assertError(() -> authenticator.create(CardCompany.KB, ID, PW), ErrorCode.CONNECTION_RETRY_BLOCKED);
        assertError(() -> authenticator.add(CONNECTED, CardCompany.KB, ID, PW), ErrorCode.CONNECTION_RETRY_BLOCKED);
        assertError(() -> authenticator.update(CONNECTED, CardCompany.KB, ID, PW), ErrorCode.CONNECTION_RETRY_BLOCKED);
    }

    @Test
    @DisplayName("추가 인증이 필요한 계정(CF-04034)은 아직 지원하지 않아서 CONN-009다")
    void additionalAuthIsUnsupported() {
        when(codef.create(CardCompany.KB, ID, PW)).thenReturn(new AccountResult(false, null, "CF-04034"));

        assertError(() -> authenticator.create(CardCompany.KB, ID, PW),
            ErrorCode.CARD_COMPANY_ADDITIONAL_AUTH_UNSUPPORTED);
    }

    @Test
    @DisplayName("CODEF 서버 오류 코드(CF-09999)는 CONN-005다")
    void serverErrorCode() {
        when(codef.update(CONNECTED, CardCompany.KB, ID, PW)).thenReturn(new AccountResult(false, null, "CF-09999"));

        assertError(() -> authenticator.update(CONNECTED, CardCompany.KB, ID, PW), ErrorCode.CODEF_UNAVAILABLE);
    }

    @Test
    @DisplayName("CODEF에 연결하지 못하면(외부 API 오류) CONN-005로 바꾼다")
    void externalApiErrorBecomesUnavailable() {
        when(codef.update(CONNECTED, CardCompany.KB, ID, PW))
            .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));

        assertError(() -> authenticator.update(CONNECTED, CardCompany.KB, ID, PW), ErrorCode.CODEF_UNAVAILABLE);
    }

    @Test
    @DisplayName("그 밖의 서버 내부 오류(예: 암호화 실패)는 바꾸지 않고 그대로 던진다")
    void otherBusinessErrorPassesThrough() {
        when(codef.update(CONNECTED, CardCompany.KB, ID, PW))
            .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        assertError(() -> authenticator.update(CONNECTED, CardCompany.KB, ID, PW), ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("새 연동을 만들었는데 connectedId가 비어 있으면 CONN-005다")
    void createWithoutConnectedId() {
        when(codef.create(CardCompany.KB, ID, PW)).thenReturn(new AccountResult(true, " ", null));

        assertError(() -> authenticator.create(CardCompany.KB, ID, PW), ErrorCode.CODEF_UNAVAILABLE);
    }

    @Test
    @DisplayName("카드사를 더하거나 갱신할 때는 응답에 connectedId가 없어도 성공이다")
    void addAndUpdateDoNotRequireConnectedId() {
        AccountResult noId = new AccountResult(true, null, null);
        when(codef.add(CONNECTED, CardCompany.KB, ID, PW)).thenReturn(noId);
        when(codef.update(CONNECTED, CardCompany.KB, ID, PW)).thenReturn(noId);

        assertThat(authenticator.add(CONNECTED, CardCompany.KB, ID, PW).success()).isTrue();
        assertThat(authenticator.update(CONNECTED, CardCompany.KB, ID, PW).success()).isTrue();
    }

}
