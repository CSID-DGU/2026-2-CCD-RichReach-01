package com.richreach.plannedexpense.entity;

import java.util.Locale;
import java.util.Optional;

/** 예정지출을 이번 달 예산에 반영하는 방식. */
public enum BudgetMethod {

    /** 총예산은 그대로 두고 다른 업종의 예산을 줄인다. */
    WITHIN_BUDGET,
    /** 이번 달 총예산에 지출 금액을 더한다. */
    EXTRA;

    /** 코드 이름으로 찾는다. 대소문자와 앞뒤 공백은 무시하고, 없으면 비어 있다. */
    public static Optional<BudgetMethod> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        for (BudgetMethod method : values()) {
            if (method.name().equals(normalized)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

}
