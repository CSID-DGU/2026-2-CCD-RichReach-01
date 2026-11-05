package com.richreach.codef.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 연결에 실패한 카드사를 다시 연결하는 요청. 카드사는 경로의 카드사 계정으로 정해지므로 받지 않는다.
 * 카드사 아이디와 비밀번호는 CODEF에 전달하는 데만 쓰고 저장하지 않으며, 로그에 찍히지 않도록
 * {@link #toString()}에서 값을 가린다.
 *
 * @param loginId  카드사 홈페이지 아이디
 * @param password 카드사 홈페이지 비밀번호
 */
public record ConnectionRetryRequest(
    @NotBlank(message = "카드사 아이디를 입력해 주세요.")
    @Size(max = 100, message = "카드사 아이디가 너무 깁니다.")
    String loginId,

    @NotBlank(message = "카드사 비밀번호를 입력해 주세요.")
    @Size(max = 200, message = "카드사 비밀번호가 너무 깁니다.")
    String password) {

    @Override
    public String toString() {
        return "ConnectionRetryRequest[loginId=****, password=****]";
    }

}
