package com.richreach.global.category;

import java.util.Locale;
import java.util.Optional;

/**
 * 화면에 쓰는 11개 소비 업종. 거래 분류와 혜택 계산이 같은 코드를 쓴다.
 * 응답과 저장에는 이 코드만 쓰고, 세부 업종(영화, 여행 등)은 노출하지 않는다.
 * 선언 순서가 응답에서 업종을 내려주는 순서다.
 */
public enum SpendingCategory {

    /** 음식점, 배달앱 */
    DINING,
    /** 카페, 베이커리 */
    CAFE,
    /** 편의점, 슈퍼마켓, 대형마트, 식료품 */
    CONVENIENCE_STORE,
    /** 온라인쇼핑, 백화점, 아울렛, 의류·잡화 */
    SHOPPING,
    /** 버스, 지하철, 택시, 철도, 시외교통 */
    TRANSPORT,
    /** 주유, 전기·수소차 충전, 자동차 관련 */
    FUEL_VEHICLE,
    /** 병원, 약국, 안경, 뷰티, 반려동물 */
    LIFE_HEALTH,
    /** 통신, 공과금, 관리비, 구독, 보험 */
    TELECOM_SUBSCRIPTION,
    /** 영화, 공연, 레저, 여행, 숙박, 항공 */
    CULTURE_TRAVEL,
    /** 학원, 교육, 서점, 도서 */
    EDUCATION,
    /** 위에 없는 업종. 어디에도 맞지 않는 거래도 여기에 속한다. */
    ETC;

    /** 코드 이름으로 찾는다. 대소문자와 앞뒤 공백은 무시하고, 없으면 비어 있다. */
    public static Optional<SpendingCategory> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        for (SpendingCategory category : values()) {
            if (category.name().equals(normalized)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

}
