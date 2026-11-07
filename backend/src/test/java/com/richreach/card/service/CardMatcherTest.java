package com.richreach.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.card.entity.CardProduct;
import com.richreach.card.repository.CardProductRepository;
import com.richreach.codef.entity.CardCompany;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardMatcherTest {

    private CardProductRepository products;
    private CardMatcher matcher;

    @BeforeEach
    void setUp() {
        products = mock(CardProductRepository.class);
        matcher = new CardMatcher(products);
    }

    private static CardProduct product(String cardName) {
        CardProduct product = mock(CardProduct.class);
        when(product.getCardName()).thenReturn(cardName);
        return product;
    }

    private void productsOf(String issuerCode, CardProduct... candidates) {
        when(products.findByIssuerCode(issuerCode)).thenReturn(List.of(candidates));
    }

    // ---- 정규화 ----

    @Test
    @DisplayName("정규화는 대소문자, 공백, 특수문자를 무시하고 앞의 카드사 이름을 뗀다")
    void normalizeIgnoresCaseSpacesAndIssuerPrefix() {
        assertThat(CardMatcher.normalize("현대카드 ZERO Edition3", CardCompany.HYUNDAI)).isEqualTo("zeroedition3");
        assertThat(CardMatcher.normalize("ZERO  edition-3", CardCompany.HYUNDAI)).isEqualTo("zeroedition3");
        assertThat(CardMatcher.normalize("ＺＥＲＯ　Ｅdition3", CardCompany.HYUNDAI)).isEqualTo("zeroedition3");
    }

    @Test
    @DisplayName("다른 카드사 이름은 떼지 않는다")
    void doesNotStripOtherIssuerName() {
        assertThat(CardMatcher.normalize("신한카드 Deep Dream", CardCompany.KB)).isEqualTo("신한카드deepdream");
    }

    @Test
    @DisplayName("null은 빈 문자열이다")
    void normalizeNull() {
        assertThat(CardMatcher.normalize(null, CardCompany.KB)).isEmpty();
    }

    @Test
    @DisplayName("상품의 카드사 코드는 카드사 이름의 소문자다")
    void issuerCodeIsLowerCaseName() {
        assertThat(CardMatcher.issuerCodeOf(CardCompany.HYUNDAI)).isEqualTo("hyundai");
        assertThat(CardMatcher.issuerCodeOf(CardCompany.KB)).isEqualTo("kb");
    }

    // ---- 매칭 ----

    @Test
    @DisplayName("같은 카드사의 상품만 후보로 조회한다")
    void looksUpOnlyTheSameIssuer() {
        productsOf("hyundai", product("ZERO Edition3"));

        matcher.match(CardCompany.HYUNDAI, List.of("현대카드 ZERO Edition3"));

        verify(products).findByIssuerCode("hyundai");
    }

    @Test
    @DisplayName("정규화한 이름이 같은 상품이 하나면 매칭한다")
    void matchesUniqueExactName() {
        CardProduct zero = product("ZERO Edition3");
        productsOf("hyundai", zero, product("M Boost"));

        Map<String, CardProduct> matched = matcher.match(CardCompany.HYUNDAI, List.of("현대카드 ZERO Edition3"));

        assertThat(matched).containsEntry("현대카드 ZERO Edition3", zero);
    }

    @Test
    @DisplayName("정규화한 이름이 같은 상품이 둘 이상이면 매칭하지 않는다")
    void ambiguousExactNameIsNotMatched() {
        productsOf("hyundai", product("ZERO Edition3"), product("zero edition3"));

        assertThat(matcher.match(CardCompany.HYUNDAI, List.of("ZERO Edition3"))).isEmpty();
    }

    @Test
    @DisplayName("한쪽 이름이 다른 쪽을 포함하고 후보가 하나뿐이면 매칭한다")
    void matchesUniqueContainment() {
        CardProduct zero = product("ZERO Edition3");
        productsOf("hyundai", zero, product("M Boost"));

        Map<String, CardProduct> matched = matcher.match(CardCompany.HYUNDAI, List.of("ZERO Edition3 (할인형)"));

        assertThat(matched).containsEntry("ZERO Edition3 (할인형)", zero);
    }

    @Test
    @DisplayName("포함 관계 후보가 둘 이상이면 매칭하지 않는다")
    void ambiguousContainmentIsNotMatched() {
        productsOf("hyundai", product("ZERO Edition3"), product("ZERO Edition3 Plus"));

        assertThat(matcher.match(CardCompany.HYUNDAI, List.of("ZERO Edition3 Plus Special"))).hasSize(0)
            .doesNotContainKey("ZERO Edition3 Plus Special");
    }

    // ---- 상품을 구분하는 숫자와 기호 ----

    @Test
    @DisplayName("에디션 번호가 다르면 포함 관계여도 매칭하지 않는다 (Edition3과 Edition30은 다른 상품)")
    void doesNotMatchWhenDigitRunIsSplit() {
        productsOf("hyundai", product("ZERO Edition3"));

        assertThat(matcher.match(CardCompany.HYUNDAI, List.of("ZERO Edition30"))).isEmpty();
    }

    @Test
    @DisplayName("반대로 카탈로그 상품이 더 긴 번호여도 매칭하지 않는다")
    void doesNotMatchWhenCatalogHasLongerNumber() {
        productsOf("hyundai", product("ZERO Edition30"));

        assertThat(matcher.match(CardCompany.HYUNDAI, List.of("ZERO Edition3"))).isEmpty();
    }

    @Test
    @DisplayName("숫자 뒤에 글자가 이어지는 경우는 포함 매칭을 허용한다 (Edition3 (할인형))")
    void allowsSuffixAfterDigits() {
        CardProduct zero = product("ZERO Edition3");
        productsOf("hyundai", zero);

        assertThat(matcher.match(CardCompany.HYUNDAI, List.of("ZERO Edition3 할인형"))).containsValue(zero);
    }

    @Test
    @DisplayName("+ 기호가 다르면 다른 상품이다 (챌린지+카드와 챌린지카드)")
    void plusSignDistinguishesProducts() {
        CardProduct plain = product("KB Pay 챌린지카드");
        productsOf("kb", plain);

        assertThat(matcher.match(CardCompany.KB, List.of("KB Pay 챌린지+카드"))).isEmpty();
    }

    @Test
    @DisplayName("+ 기호가 같으면 매칭한다")
    void samePlusSignMatches() {
        CardProduct plus = product("KB Pay 챌린지+카드");
        productsOf("kb", plus);

        assertThat(matcher.match(CardCompany.KB, List.of("KB Pay 챌린지+카드"))).containsValue(plus);
    }

    @Test
    @DisplayName("+ 바로 앞에서 끊기는 포함 관계는 매칭하지 않는다")
    void doesNotMatchWhenPlusFollowsTheMatch() {
        productsOf("kb", product("KB Pay 챌린지"));

        assertThat(matcher.match(CardCompany.KB, List.of("KB Pay 챌린지+카드"))).isEmpty();
    }

    @Test
    @DisplayName("정규화는 + 기호를 남긴다")
    void normalizeKeepsPlus() {
        assertThat(CardMatcher.normalize("KB Pay 챌린지+카드", CardCompany.KB)).isEqualTo("kbpay챌린지+카드");
        assertThat(CardMatcher.normalize("KB국민카드 노리+체크", CardCompany.KB)).isEqualTo("노리+체크");
    }

    @Test
    @DisplayName("경계 판단: 숫자가 쪼개지거나 + 옆에서 끊기면 포함이 아니고, 글자 사이는 포함이다")
    void boundaryRules() {
        assertThat(CardMatcher.containsAtBoundary("zeroedition30", "zeroedition3")).isFalse();
        assertThat(CardMatcher.containsAtBoundary("a1zeroedition3", "zeroedition3")).isTrue();
        assertThat(CardMatcher.containsAtBoundary("12zeroedition3", "2zeroedition3")).isFalse();
        assertThat(CardMatcher.containsAtBoundary("노리체크카드", "노리체크")).isTrue();
        assertThat(CardMatcher.containsAtBoundary("챌린지+카드", "챌린지")).isFalse();
        assertThat(CardMatcher.containsAtBoundary("+챌린지카드", "챌린지카드")).isFalse();
        // 첫 번째 위치는 숫자를 쪼개지만 두 번째 위치는 온전한 경우
        assertThat(CardMatcher.containsAtBoundary("zeroedition30zeroedition3", "zeroedition3")).isTrue();
    }

    @Test
    @DisplayName("너무 짧은 이름은 포함 관계로 매칭하지 않는다")
    void shortNamesAreNotMatchedByContainment() {
        productsOf("kb", product("노리"));

        assertThat(matcher.match(CardCompany.KB, List.of("노리 체크카드"))).isEmpty();
    }

    @Test
    @DisplayName("이름이 겹치지 않으면 매칭하지 않는다")
    void unrelatedNameIsNotMatched() {
        productsOf("kb", product("노리 체크카드"));

        assertThat(matcher.match(CardCompany.KB, List.of("전혀 다른 카드"))).isEmpty();
    }

    @Test
    @DisplayName("후보 상품이 없거나 카드명이 없으면 빈 결과다")
    void noCandidatesOrNames() {
        when(products.findByIssuerCode("woori")).thenReturn(List.of());

        assertThat(matcher.match(CardCompany.WOORI, List.of("COOKIE CHECK"))).isEmpty();
        assertThat(matcher.match(CardCompany.WOORI, List.of())).isEmpty();
    }

    @Test
    @DisplayName("여러 카드를 한 번에 매칭하고, 상품 조회는 한 번만 한다")
    void matchesManyWithOneLookup() {
        CardProduct a = product("카드 에이");
        CardProduct b = product("카드 비이");
        productsOf("kb", a, b);

        Map<String, CardProduct> matched = matcher.match(CardCompany.KB, List.of("KB국민카드 카드 에이", "카드 비이", "없는 카드"));

        assertThat(matched).hasSize(2).containsEntry("KB국민카드 카드 에이", a).containsEntry("카드 비이", b);
        verify(products).findByIssuerCode("kb");
    }

}
