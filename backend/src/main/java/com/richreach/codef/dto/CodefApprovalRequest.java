package com.richreach.codef.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 개인카드 승인내역 조회 요청.
 * 카드사마다 필수 값이 다르다. (KB: 카드소지확인, 현대: 카드번호/카드비밀번호 등은 CODEF 개발가이드 참고)
 */
public record CodefApprovalRequest(
    @NotBlank
    @Schema(description = "계정 등록으로 발급받은 connectedId")
    String connectedId,

    @NotBlank
    @Schema(description = "카드사 기관코드")
    String organization,

    @NotBlank
    @Pattern(regexp = "\\d{8}", message = "YYYYMMDD 형식이어야 합니다.")
    @Schema(description = "조회 시작일 (YYYYMMDD)", example = "20260801")
    String startDate,

    @NotBlank
    @Pattern(regexp = "\\d{8}", message = "YYYYMMDD 형식이어야 합니다.")
    @Schema(description = "조회 종료일 (YYYYMMDD)", example = "20260926")
    String endDate,

    @Schema(description = "정렬. 0: 최신순, 1: 과거순 (비우면 0)", example = "0")
    String orderBy,

    @Schema(description = "조회 구분. 0: 카드별, 1: 전체 (비우면 1, 카드별 조회는 cardNo 필요)", example = "1")
    String inquiryType,

    @Schema(description = "가맹점 정보 포함. 0: 미포함, 1: 가맹점(업종 등), 2: 부가세, 3: 전체 (비우면 1)", example = "1")
    String memberStoreInfoType,

    @Schema(description = "생년월일 (카드사에 따라 필요)")
    String birthDate,

    @Schema(description = "카드명 (카드별 조회 시, 보유카드 조회 결과의 카드명)")
    String cardName,

    @Schema(description = "카드번호 (카드별 조회 시 필수)")
    String cardNo,

    @Schema(description = "중복카드 일련번호 (카드별 조회 시)")
    String duplicateCardIdx,

    @Schema(description = "카드 비밀번호 (현대카드 4자리, KB 앞 2자리 등. 서버에서 RSA 암호화 후 전송)", format = "password")
    String cardPassword
) {

    @Override
    public String toString() {
        return "CodefApprovalRequest[organization=" + organization + ", startDate=" + startDate
            + ", endDate=" + endDate + "]";
    }

}
