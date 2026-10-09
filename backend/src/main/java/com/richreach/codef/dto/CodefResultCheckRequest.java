package com.richreach.codef.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 개인카드 실적조회 요청.
 * 카드사마다 입력이 다르다. 신한카드는 connectedId와 기관코드만 사용하며 조회 기간(startDate)도 지원하지 않는다.
 * (KB/현대는 카드번호, 카드 비밀번호, 우리는 생년월일 등이 필요할 수 있다. 개발가이드의 카드사별 표 참고)
 */
public record CodefResultCheckRequest(
    @NotBlank
    @Schema(description = "계정 등록으로 발급받은 connectedId")
    String connectedId,

    @NotBlank
    @Schema(description = "카드사 기관코드")
    String organization,

    @Pattern(regexp = "^(\\d{6})?$", message = "YYYYMM 형식이어야 합니다.")
    @Schema(description = "조회 기간 (YYYYMM). 지원하는 카드사(KB, 비씨, 우리, 하나)만 사용. 신한은 비워둔다.", example = "")
    String startDate,

    @Schema(description = "생년월일 (YYYYMMDD). 우리카드 등 일부 카드사만 필요. 신한은 비워둔다.")
    String birthDate,

    @Schema(description = "카드번호. 현대카드(아이디 로그인), KB 카드소지확인 시에만 필요. 신한은 비워둔다.")
    String cardNo,

    @Schema(description = "카드 비밀번호 (현대 4자리, KB 앞 2자리). 서버에서 RSA 암호화 후 전송. 신한은 비워둔다.", format = "password")
    String cardPassword
) {

    @Override
    public String toString() {
        return "CodefResultCheckRequest[organization=" + organization + ", startDate=" + startDate + "]";
    }

}
