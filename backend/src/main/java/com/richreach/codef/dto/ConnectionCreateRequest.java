package com.richreach.codef.dto;

import com.richreach.codef.entity.CardCompany;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 카드사 한 곳의 연결 요청. 카드사 아이디와 비밀번호는 CODEF에 전달하는 데만 쓰고 저장하지 않는다.
 * 로그에 찍히지 않도록 {@link #toString()}에서 값을 가린다.
 *
 * @param cardCompany 연결할 카드사
 * @param loginId     카드사 홈페이지 아이디
 * @param password    카드사 홈페이지 비밀번호
 */
public record ConnectionCreateRequest(
    @NotNull(message = "카드사를 선택해 주세요.")
    CardCompany cardCompany,

    @NotBlank(message = "카드사 아이디를 입력해 주세요.")
    @Size(max = 100, message = "카드사 아이디가 너무 깁니다.")
    String loginId,

    @NotBlank(message = "카드사 비밀번호를 입력해 주세요.")
    @Size(max = 200, message = "카드사 비밀번호가 너무 깁니다.")
    String password) {

    @Override
    public String toString() {
        return "ConnectionCreateRequest[cardCompany=" + cardCompany + ", loginId=****, password=****]";
    }

}
