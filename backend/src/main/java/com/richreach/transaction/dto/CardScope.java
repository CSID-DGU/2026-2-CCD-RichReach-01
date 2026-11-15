package com.richreach.transaction.dto;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/** 거래 요약·목록이 어느 카드의 거래를 볼지. 기본은 분석 대상 카드다. */
public enum CardScope {

    /** 분석 대상으로 고른 카드의 거래만. 고른 카드가 없으면 거래가 없는 것으로 본다. */
    SELECTED,
    /** 사용자의 모든 거래(동기화에서 사라진 카드의 거래도 포함). 카드를 고르기 전 화면에서 쓴다. */
    ALL;

    public static final CardScope DEFAULT = SELECTED;

    /**
     * 요청 값을 카드 범위로 해석한다. 없으면(null) 기본 범위이고, 알 수 없는 값이면 400({@code COMMON-400})이다.
     * 거래 요약과 목록이 같은 규칙을 쓴다.
     */
    public static CardScope parse(String code) {
        if (code == null) {
            return DEFAULT;
        }
        return fromCode(code).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
            "지원하지 않는 카드 범위입니다. " + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "))
                + " 중 하나여야 합니다."));
    }

    /** API 값으로 찾는다. 대소문자와 앞뒤 공백은 무시하고, 없거나 지원하지 않는 값이면 비어 있다. */
    public static Optional<CardScope> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        for (CardScope scope : values()) {
            if (scope.name().equals(normalized)) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }

}
