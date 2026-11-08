
package com.richreach.global.category;

import java.util.Locale;
import java.util.Optional;

/**
 * 화면에 쓰는 6개 소비 업종. 거래 분류와 혜택 계산이 같은 코드를 쓴다.
 * 응답과 저장에는 이 코드만 쓰고, 세부 업종(영화, 여행 등)은 노출하지 않는다.
 */
public enum SpendingCategory {

    /** 음식점, 배달앱 */
    DINING,
    /** 카페, 베이커리 */
    CAFE,
    /** 마트, 온라인쇼핑, 백화점, 도서, 뷰티 등 */
    SHOPPING,
    /** 대중교통, 택시, 철도, 주유, 충전, 자동차 */
    TRANSPORT,
    /** 편의점 */
    CONVENIENCE_STORE,
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
