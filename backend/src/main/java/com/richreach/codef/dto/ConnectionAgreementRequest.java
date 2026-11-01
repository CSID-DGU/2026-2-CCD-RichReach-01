package com.richreach.codef.dto;

import com.richreach.user.entity.TermType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 카드 연동 동의 요청. 카드정보 조회({@code CARD_INFO}), 거래내역 조회({@code TRANSACTION}),
 * 개인정보 수집 및 이용({@code PRIVACY}) 3개가 모두 필수라서 셋 모두 있어야 한다.
 * 약관 버전은 서버가 현재 버전을 기록하므로 받지 않는다. (프론트가 {@code termsVersion}을 보내도 무시한다.)
 */
public record ConnectionAgreementRequest(
    @NotEmpty(message = "동의 항목을 보내 주세요.")
    List<@NotNull(message = "동의 항목에 빈 값이 있습니다.") TermType> types) {
}
