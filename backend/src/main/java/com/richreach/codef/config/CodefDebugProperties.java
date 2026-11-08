package com.richreach.codef.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * CODEF 연동을 개발·확인할 때만 켜는 진단 설정 ({@code codef.debug.*}). 기본은 모두 꺼져 있다.
 *
 * @param logShape 켜면 CODEF 응답의 구조(필드 경로와 타입)를 로그에 남긴다. 값은 남기지 않는다. (결과 코드만 값을 남긴다)
 *                 환경변수로는 {@code CODEF_DEBUG_LOGSHAPE=true}다.
 */
@ConfigurationProperties("codef.debug")
public record CodefDebugProperties(@DefaultValue("false") boolean logShape) {

}
