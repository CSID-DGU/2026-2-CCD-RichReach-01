package com.richreach.transaction.dto;

import java.util.Locale;
import java.util.Optional;

/** 거래 요약·목록이 어느 카드의 거래를 볼지. 기본은 분석 대상 카드다. */
public enum CardScope {

    /** 분석 대상으로 고른 카드의 거래만. 고른 카드가 없으면 거래가 없는 것으로 본다. */
    SELECTED,
    /** 사용자의 모든 거래(동기화에서 사라진 카드의 거래도 포함). 카드를 고르기 전 화면에서 쓴다. */
    ALL;

    public static final CardScope DEFAULT = SELECTED;

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
