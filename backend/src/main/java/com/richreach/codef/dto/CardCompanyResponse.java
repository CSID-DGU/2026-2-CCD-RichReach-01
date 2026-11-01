package com.richreach.codef.dto;

import com.richreach.codef.entity.CardCompany;

/**
 * 카드사 목록의 한 항목.
 *
 * @param code      카드사 코드 (예: {@code KB})
 * @param name      화면에 보여줄 이름 (예: {@code KB국민카드})
 * @param supported 연결을 지원하는지. false면 선택할 수 없다.
 * @param connected 사용자가 이미 연결했는지. 카드사 연결 생성 API가 생기기 전까지는 항상 false다.
 */
public record CardCompanyResponse(String code, String name, boolean supported, boolean connected) {

    public static CardCompanyResponse of(CardCompany company, boolean connected) {
        return new CardCompanyResponse(company.name(), company.displayName(), company.supported(), connected);
    }

}
