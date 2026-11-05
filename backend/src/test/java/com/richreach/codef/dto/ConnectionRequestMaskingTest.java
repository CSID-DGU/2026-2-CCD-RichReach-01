package com.richreach.codef.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.codef.entity.CardCompany;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 카드사 아이디와 비밀번호가 담긴 요청 객체를 실수로 로그에 찍어도 값이 나오지 않는지 확인한다.
 * 서비스가 요청 객체를 로그에 남기지 않아도, 나중에 누군가 남기는 경우를 대비한 마지막 방어선이다.
 */
class ConnectionRequestMaskingTest {

    private static final String LOGIN_ID = "test-card-login-id";
    private static final String PASSWORD = "test-card-secret-pw-9876";

    @Test
    @DisplayName("재시도 요청의 toString에는 아이디와 비밀번호가 나오지 않는다")
    void retryRequestIsMasked() {
        String text = new ConnectionRetryRequest(LOGIN_ID, PASSWORD).toString();

        assertThat(text).doesNotContain(LOGIN_ID).doesNotContain(PASSWORD);
        assertThat(text).contains("****");
    }

    @Test
    @DisplayName("연결 생성 요청의 toString에도 아이디와 비밀번호가 나오지 않고, 카드사만 보인다")
    void createRequestIsMasked() {
        String text = new ConnectionCreateRequest(CardCompany.KB, LOGIN_ID, PASSWORD).toString();

        assertThat(text).doesNotContain(LOGIN_ID).doesNotContain(PASSWORD);
        assertThat(text).contains("KB").contains("****");
    }

    @Test
    @DisplayName("문자열 연결로 로그를 만들어도 값이 나오지 않는다")
    void stringConcatenationIsMasked() {
        ConnectionRetryRequest retry = new ConnectionRetryRequest(LOGIN_ID, PASSWORD);
        ConnectionCreateRequest create = new ConnectionCreateRequest(CardCompany.KB, LOGIN_ID, PASSWORD);

        assertThat("request=" + retry + ", " + create).doesNotContain(LOGIN_ID).doesNotContain(PASSWORD);
    }

}
