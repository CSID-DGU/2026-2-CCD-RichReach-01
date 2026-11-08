package com.richreach.transaction.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 거래의 업종을 사용자가 고치는 요청. 화면용 6개 업종(DINING, CAFE, SHOPPING, TRANSPORT, CONVENIENCE_STORE, ETC)
 * 중 하나여야 한다. 대소문자는 구분하지 않는다. 자동 분류로 되돌리려면 카테고리를 지우는 API를 쓴다.
 *
 * @param category 바꿀 업종 코드
 */
public record ChangeCategoryRequest(
    @NotBlank(message = "업종을 입력해 주세요.")
    String category) {

}
