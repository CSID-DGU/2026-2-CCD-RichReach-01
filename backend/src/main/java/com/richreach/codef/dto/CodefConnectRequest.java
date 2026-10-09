package com.richreach.codef.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 카드사 계정 등록(connectedId 발급) 요청. 현재는 아이디 로그인 방식만 지원한다.
 * 비밀번호는 서버에서 RSA로 암호화해서 CODEF에 전달하며, 로그에 남기지 않는다.
 */
public record CodefConnectRequest(
    @NotBlank
    @Schema(description = "카드사 기관코드 (CODEF 개발가이드의 기관코드 표 참고)")
    String organization,

    @NotBlank
    @Schema(description = "카드사 웹사이트 로그인 아이디")
    String loginId,

    @NotBlank
    @Schema(description = "카드사 웹사이트 로그인 비밀번호 (서버에서 RSA 암호화 후 전송, 저장/로그 없음)", format = "password")
    String loginPassword,

    @Schema(description = "업종 구분. 카드는 CD (비우면 CD)", example = "CD")
    String businessType,

    @Schema(description = "고객 구분. 개인은 P (비우면 P)", example = "P")
    String clientType,

    @Schema(description = "로그인 방식. 1: 아이디 로그인 (비우면 1). 공동인증서(0)는 아직 지원하지 않는다.", example = "1")
    String loginType
) {

    /** 비밀번호가 로그에 출력되지 않도록 마스킹한다. */
    @Override
    public String toString() {
        return "CodefConnectRequest[organization=" + organization + ", loginId=****, loginPassword=****]";
    }

}
