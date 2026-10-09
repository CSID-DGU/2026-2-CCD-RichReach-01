package com.richreach.codef.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 보유카드 조회 요청 */
public record CodefCardListRequest(
    @NotBlank
    @Schema(description = "계정 등록으로 발급받은 connectedId")
    String connectedId,

    @NotBlank
    @Schema(description = "카드사 기관코드")
    String organization
) {

    @Override
    public String toString() {
        return "CodefCardListRequest[connectedId=****, organization=" + organization + "]";
    }

}
