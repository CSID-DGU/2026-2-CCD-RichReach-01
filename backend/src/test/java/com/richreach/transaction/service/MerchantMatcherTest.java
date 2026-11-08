package com.richreach.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.entity.MatchType;
import com.richreach.transaction.service.MerchantMatcher.Rule;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 가맹점명 정규화와 규칙 적용 방식(일치 방식, 우선순위)을 DB 없이 확인한다. */
class MerchantMatcherTest {

    private static Rule contains(String pattern, SpendingCategory category, int priority) {
        return new Rule(MerchantMatcher.normalize(pattern), MatchType.CONTAINS, category, priority);
    }

    private static Rule prefix(String pattern, SpendingCategory category, int priority) {
        return new Rule(MerchantMatcher.normalize(pattern), MatchType.PREFIX, category, priority);
    }

    private static Rule exact(String pattern, SpendingCategory category, int priority) {
        return new Rule(MerchantMatcher.normalize(pattern), MatchType.EXACT, category, priority);
    }

    // ---- 정규화 ----

    @Test
    @DisplayName("대소문자, 공백, 특수문자, 전각 문자 차이를 없앤다")
    void normalizesSpellingDifferences() {
        assertThat(MerchantMatcher.normalize("GS25 강남점")).isEqualTo("gs25강남점");
        assertThat(MerchantMatcher.normalize("gs25-강남점")).isEqualTo("gs25강남점");
        assertThat(MerchantMatcher.normalize("ＧＳ２５　강남점")).isEqualTo("gs25강남점");
        assertThat(MerchantMatcher.normalize("S-OIL")).isEqualTo("soil");
        assertThat(MerchantMatcher.normalize("7-Eleven")).isEqualTo("7eleven");
    }

    @Test
    @DisplayName("(주), ㈜, 주식회사 표기는 지운다")
    void removesCorporateMarkers() {
        assertThat(MerchantMatcher.normalize("(주)스타벅스")).isEqualTo("스타벅스");
        assertThat(MerchantMatcher.normalize("㈜스타벅스")).isEqualTo("스타벅스");
        assertThat(MerchantMatcher.normalize("주식회사 스타벅스")).isEqualTo("스타벅스");
        assertThat(MerchantMatcher.normalize("스타벅스(주)")).isEqualTo("스타벅스");
    }

    @Test
    @DisplayName("없거나 글자가 하나도 없으면 빈 문자열이다")
    void emptyWhenNoLetters() {
        assertThat(MerchantMatcher.normalize(null)).isEmpty();
        assertThat(MerchantMatcher.normalize("   ")).isEmpty();
        assertThat(MerchantMatcher.normalize("-*-")).isEmpty();
    }

    // ---- 일치 방식 ----

    @Test
    @DisplayName("포함, 접두어, 완전 일치를 구분한다")
    void matchTypes() {
        MerchantMatcher matcher = new MerchantMatcher(List.of(
            contains("카페", SpendingCategory.CAFE, 100),
            prefix("cu", SpendingCategory.CONVENIENCE_STORE, 100),
            exact("토스", SpendingCategory.SHOPPING, 100)));

        assertThat(matcher.match("행복한 카페 강남점")).contains(SpendingCategory.CAFE);
        assertThat(matcher.match("CU 역삼점")).contains(SpendingCategory.CONVENIENCE_STORE);
        assertThat(matcher.match("abc CU")).isEmpty();
        assertThat(matcher.match("토스")).contains(SpendingCategory.SHOPPING);
        assertThat(matcher.match("토스뱅크")).isEmpty();
    }

    @Test
    @DisplayName("표기가 달라도 같은 가맹점으로 걸린다")
    void spellingDoesNotMatter() {
        MerchantMatcher matcher = new MerchantMatcher(List.of(contains("GS25", SpendingCategory.CONVENIENCE_STORE, 100)));

        assertThat(matcher.match("gs25강남점")).contains(SpendingCategory.CONVENIENCE_STORE);
        assertThat(matcher.match("GS 25")).contains(SpendingCategory.CONVENIENCE_STORE);
        assertThat(matcher.match("(주)지에스25 GS-25 역삼")).contains(SpendingCategory.CONVENIENCE_STORE);
        // 한글 표기는 문구가 다르므로 따로 규칙이 있어야 걸린다. (예: 지에스25)
        assertThat(matcher.match("(주)지에스25 역삼")).isEmpty();
    }

    @Test
    @DisplayName("가맹점명이 없거나 걸리는 규칙이 없으면 비어 있다")
    void noMatch() {
        MerchantMatcher matcher = new MerchantMatcher(List.of(contains("카페", SpendingCategory.CAFE, 100)));

        assertThat(matcher.match(null)).isEmpty();
        assertThat(matcher.match("")).isEmpty();
        assertThat(matcher.match("알 수 없는 상점")).isEmpty();
    }

    // ---- 우선순위 ----

    @Test
    @DisplayName("priority가 작은 규칙이 먼저 걸린다")
    void lowerPriorityNumberWins() {
        MerchantMatcher matcher = new MerchantMatcher(List.of(
            contains("마트", SpendingCategory.SHOPPING, 250),
            contains("스마트", SpendingCategory.ETC, 50)));

        assertThat(matcher.match("스마트스터디")).contains(SpendingCategory.ETC);
        assertThat(matcher.match("행복마트")).contains(SpendingCategory.SHOPPING);
    }

    @Test
    @DisplayName("priority가 같으면 문구가 긴(더 구체적인) 규칙이 먼저 걸린다")
    void longerPatternWinsOnTie() {
        MerchantMatcher matcher = new MerchantMatcher(List.of(
            contains("쿠팡", SpendingCategory.SHOPPING, 100),
            contains("쿠팡이츠", SpendingCategory.DINING, 100)));

        assertThat(matcher.match("쿠팡이츠")).contains(SpendingCategory.DINING);
        assertThat(matcher.match("쿠팡(주)")).contains(SpendingCategory.SHOPPING);
    }

    @Test
    @DisplayName("규칙을 넘기는 순서와 상관없이 같은 결과가 나온다")
    void orderOfRulesDoesNotMatter() {
        List<Rule> rules = List.of(
            contains("쿠팡", SpendingCategory.SHOPPING, 100),
            contains("쿠팡이츠", SpendingCategory.DINING, 100),
            contains("마트", SpendingCategory.SHOPPING, 250),
            contains("스마트", SpendingCategory.ETC, 50));

        MerchantMatcher forward = new MerchantMatcher(rules);
        MerchantMatcher backward = new MerchantMatcher(rules.reversed());

        for (String name : List.of("쿠팡이츠", "쿠팡", "스마트마트", "행복마트", "그 밖")) {
            assertThat(backward.match(name)).as(name).isEqualTo(forward.match(name));
        }
    }

}
