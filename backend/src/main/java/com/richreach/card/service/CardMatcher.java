package com.richreach.card.service;

import com.richreach.card.entity.CardProduct;
import com.richreach.card.repository.CardProductRepository;
import com.richreach.codef.entity.CardCompany;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 카드사가 알려준 카드명으로 카드 혜택 DB의 카드 상품({@link CardProduct})을 찾는다.
 *
 * <p>같은 카드사의 상품만 후보로 삼고, 카드명을 정규화해서 비교한다. 정규화는 대소문자, 공백, 특수문자를 무시하고
 * 카드명 앞의 카드사 이름("현대카드" 등)을 뗀다.
 * <ol>
 *   <li>정규화한 이름이 같은 상품이 <b>하나</b>면 그것이다.</li>
 *   <li>없으면, 한쪽 이름이 다른 쪽을 포함하고 후보가 <b>하나</b>뿐일 때만 그것이다. (너무 짧은 이름은 제외)</li>
 *   <li>후보가 여러 개이면 잘못 연결하는 것보다 매칭하지 않는 쪽을 택한다.</li>
 * </ol>
 * 매칭하지 못한 카드는 상품 없이 저장되고 분석할 수 없다. 틀린 혜택으로 분석하는 것보다 안전하다.
 */
@Component
@RequiredArgsConstructor
public class CardMatcher {

    /** 포함 관계로 매칭할 때 이보다 짧은 이름은 우연히 겹칠 수 있어서 쓰지 않는다. */
    private static final int MIN_CONTAINS_LENGTH = 4;

    private final CardProductRepository products;

    /**
     * @return 카드명 → 매칭된 카드 상품. 매칭하지 못한 카드명은 들어 있지 않다.
     */
    public Map<String, CardProduct> match(CardCompany company, Collection<String> cardNames) {
        List<CardProduct> candidates = products.findByIssuerCode(issuerCodeOf(company));
        if (candidates.isEmpty() || cardNames.isEmpty()) {
            return Map.of();
        }

        Map<String, List<CardProduct>> byNormalizedName = new HashMap<>();
        for (CardProduct product : candidates) {
            byNormalizedName.computeIfAbsent(normalize(product.getCardName(), company), key -> new ArrayList<>())
                .add(product);
        }

        Map<String, CardProduct> matched = new HashMap<>();
        for (String cardName : cardNames) {
            findOne(normalize(cardName, company), byNormalizedName)
                .ifPresent(product -> matched.put(cardName, product));
        }
        return matched;
    }

    /** card_products.issuer_code는 카드사 이름의 소문자다. (kb, hyundai, nh, citi, woori 등) */
    static String issuerCodeOf(CardCompany company) {
        return company.name().toLowerCase(Locale.ROOT);
    }

    private Optional<CardProduct> findOne(String name, Map<String, List<CardProduct>> byNormalizedName) {
        if (name.isEmpty()) {
            return Optional.empty();
        }
        List<CardProduct> exact = byNormalizedName.get(name);
        if (exact != null) {
            return exact.size() == 1 ? Optional.of(exact.get(0)) : Optional.empty();
        }

        List<CardProduct> containing = new ArrayList<>();
        for (Map.Entry<String, List<CardProduct>> entry : byNormalizedName.entrySet()) {
            String candidate = entry.getKey();
            if (candidate.length() >= MIN_CONTAINS_LENGTH && name.length() >= MIN_CONTAINS_LENGTH
                && (containsAtBoundary(name, candidate) || containsAtBoundary(candidate, name))) {
                containing.addAll(entry.getValue());
            }
        }
        return containing.size() == 1 ? Optional.of(containing.get(0)) : Optional.empty();
    }

    /**
     * 대소문자, 공백, 특수문자를 무시하고, 카드명 앞의 카드사 이름을 뗀다.
     * 단 {@code +}는 상품을 구분하는 기호라서 남긴다. ({@code 챌린지+카드}와 {@code 챌린지카드}는 다른 상품이다.)
     */
    static String normalize(String name, CardCompany company) {
        if (name == null) {
            return "";
        }
        String normalized = keepLettersDigitsAndPlus(name);
        String issuerName = keepLettersDigitsAndPlus(company.displayName());
        return normalized.startsWith(issuerName) ? normalized.substring(issuerName.length()) : normalized;
    }

    private static String keepLettersDigitsAndPlus(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}+]", "");
    }

    /**
     * {@code longer}가 {@code shorter}를 포함하는지. 단 포함된 자리의 경계가 상품 구분을 쪼개면 포함으로 보지 않는다.
     * <ul>
     *   <li>숫자 사이를 가르는 경우: {@code zeroedition3}은 {@code zeroedition30}에 들어 있지만 다른 상품(에디션 3과 30)이다.</li>
     *   <li>{@code +} 바로 앞뒤에서 끊기는 경우: {@code kbpay챌린지}는 {@code kbpay챌린지+카드}의 일부가 아니다.</li>
     * </ul>
     * 글자 사이에서 끊기는 것({@code 노리체크}와 {@code 노리체크카드})은 허용한다. 카드명 뒤에 "카드"나 종류가 붙는 일이 흔해서다.
     */
    static boolean containsAtBoundary(String longer, String shorter) {
        int from = 0;
        while (true) {
            int start = longer.indexOf(shorter, from);
            if (start < 0) {
                return false;
            }
            int end = start + shorter.length();
            boolean splitsLeft = start > 0
                && (isDigitPair(longer.charAt(start - 1), longer.charAt(start)) || longer.charAt(start - 1) == '+');
            boolean splitsRight = end < longer.length()
                && (isDigitPair(longer.charAt(end - 1), longer.charAt(end)) || longer.charAt(end) == '+');
            if (!splitsLeft && !splitsRight) {
                return true;
            }
            from = start + 1;
        }
    }

    private static boolean isDigitPair(char left, char right) {
        return Character.isDigit(left) && Character.isDigit(right);
    }

}
