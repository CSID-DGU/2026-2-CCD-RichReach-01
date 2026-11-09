package com.richreach.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 현재 적용 중인 약관 버전 ({@code app.terms.*}). 약관 본문은 앱에 두고, 서버는 동의한 버전만 기록한다.
 * 약관을 고치면 이 값을 바꾼다.
 *
 * @param signupVersion         회원가입 때 받는 서비스 이용약관 및 개인정보 수집 동의의 버전
 * @param cardConnectionVersion 카드 연동 동의(카드정보 조회, 거래내역 조회, 개인정보 수집 및 이용)의 버전
 */
@ConfigurationProperties("app.terms")
public record TermsProperties(
    @DefaultValue("2026-10-01") String signupVersion,
    @DefaultValue("2026-10-01") String cardConnectionVersion) {
}
