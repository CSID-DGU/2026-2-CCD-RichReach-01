package com.richreach.codef.dto;

import com.richreach.codef.entity.CardCompany;

/**
 * 카드사 연결 결과.
 *
 * @param connectionId 카드사 계정 ID({@code codef_org_accounts.id}). 사용자당 연동은 하나이므로
 *                     카드사마다 하나씩 생기는 이 ID로 카드사 연결을 구분한다.
 * @param cardCompany  연결한 카드사
 * @param status       현재는 항상 {@code CONNECTED}. 인증에 실패하면 응답이 아니라 오류({@code CONN-xxx})로 알린다.
 */
public record ConnectionResponse(Long connectionId, CardCompany cardCompany, String status) {

    public static final String CONNECTED = "CONNECTED";

}
