package com.richreach.transaction.service;

import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.entity.MatchType;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 가맹점명에 분류 규칙을 적용한다. DB 없이 규칙 목록만으로 동작해서 규칙의 적용 방식을 따로 확인할 수 있다.
 *
 * <ul>
 *   <li>규칙은 {@code priority}가 작은 것부터, 같으면 문구가 긴(더 구체적인) 것부터 보고, 먼저 걸린 규칙이 이긴다.</li>
 *   <li>가맹점명과 규칙 문구는 같은 방식으로 정규화한 뒤 비교한다. ({@link #normalize})</li>
 * </ul>
 */
final class MerchantMatcher {

    /** 정규화한 문구로 만든 규칙. */
    record Rule(String pattern, MatchType matchType, SpendingCategory category, int priority) {

        boolean matches(String normalizedName) {
            return switch (matchType) {
                case CONTAINS -> normalizedName.contains(pattern);
                case PREFIX -> normalizedName.startsWith(pattern);
                case EXACT -> normalizedName.equals(pattern);
            };
        }

    }

    private static final Comparator<Rule> ORDER = Comparator
        .comparingInt(Rule::priority)
        .thenComparing(rule -> rule.pattern().length(), Comparator.reverseOrder())
        .thenComparing(Rule::pattern)
        .thenComparing(Rule::matchType);

    private final List<Rule> rules;

    /** 규칙 문구는 이미 정규화되어 있어야 한다. */
    MerchantMatcher(List<Rule> rules) {
        this.rules = rules.stream().sorted(ORDER).toList();
    }

    /** 가맹점명에 처음 걸리는 규칙의 업종. 가맹점명이 없거나 걸리는 규칙이 없으면 비어 있다. */
    Optional<SpendingCategory> match(String merchantName) {
        String normalized = normalize(merchantName);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        for (Rule rule : rules) {
            if (rule.matches(normalized)) {
                return Optional.of(rule.category());
            }
        }
        return Optional.empty();
    }

    /**
     * 표기만 다른 같은 가맹점이 같게 보이도록 정리한다: 전각·반각과 대소문자를 맞추고, (주)와 주식회사 표기를 지우고,
     * 글자와 숫자만 남긴다. (공백과 특수문자 제거) 없으면 빈 문자열이다.
     */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String unified = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
            .replace("(주)", "").replace("주식회사", "").replace("유한회사", "");
        return unified.replaceAll("[^\\p{L}\\p{N}]", "");
    }

}
