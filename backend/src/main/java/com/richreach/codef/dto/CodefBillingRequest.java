package com.richreach.codef.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 청구내역(명세서) 조회 요청.
 * 현대카드는 아이디 로그인 시 cardNo/cardPassword가 필수이고, KB카드는 카드소지확인이 필요할 때만 넣는다.
 */
public record CodefBillingRequest(
    @NotBlank
    @Schema(description = "계정 등록으로 발급받은 connectedId")
    String connectedId,

    @NotBlank
    @Schema(description = "카드사 기관코드")
    String organization,

    @Pattern(regexp = "^(\\d{6})?$", message = "YYYYMM 형식이어야 합니다.")
    @Schema(description = "청구년월 (YYYYMM). 비우면 가장 최근 명세서를 조회한다.", example = "")
    String startDate,

    @Schema(description = "가맹점정보 포함 여부. 0: 미포함, 1: 포함 (비우면 0)", example = "0")
    String memberStoreInfoYN,

    @Pattern(regexp = "^(\\d{8})?$", message = "YYYYMMDD 형식이어야 합니다.")
    @Schema(description = "생년월일 (YYYYMMDD). 카드사에 따라 필요")
    String birthDate,

    @Schema(description = "카드번호. 현대카드(아이디 로그인) 필수, KB 카드소지확인 시 필요")
    String cardNo,

    @Schema(description = "카드 비밀번호 (현대 4자리, KB 앞 2자리). 서버에서 RSA 암호화 후 전송", format = "password")
    String cardPassword
) {

    @Override
    public String toString() {
        return "CodefBillingRequest[organization=" + organization + ", startDate=" + startDate + "]";
    }

}
