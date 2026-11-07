package com.richreach.card.service;

import com.richreach.card.dto.CollectedCard;
import com.richreach.card.entity.CardProduct;
import com.richreach.card.entity.OwnedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.codef.entity.CardCompany;
import com.richreach.global.util.Times;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동기화로 수집한 카드를 보유카드로 저장한다.
 *
 * <ul>
 *   <li>처음 보는 카드는 새로 저장한다. (분석 대상으로는 선택하지 않는다)</li>
 *   <li>이미 있는 카드는 새로 만들지 않고 갱신한다. 사용자가 고른 선택은 바꾸지 않는다.</li>
 *   <li>이번 동기화에서 보이지 않는 카드는 지우지 않고 {@code active=false}로 표시한다. 다시 보이면 되살린다.</li>
 *   <li>카드명으로 카드 상품을 매칭하고, 못 찾으면 상품 없이 저장한다. 매칭은 동기화할 때마다 다시 한다.</li>
 *   <li>카드 번호는 저장 전에 마지막 4자리만 남기고 가린다.</li>
 * </ul>
 *
 * <p>카드사의 카드 목록을 <b>성공적으로 전부 가져온 경우에만</b> 불러야 한다. 일부만 가져왔거나 실패한 결과를 넘기면
 * 빠진 카드가 사라진 것으로 표시된다.
 */
@Service
@RequiredArgsConstructor
public class OwnedCardService {

    private final OwnedCardRepository ownedCards;
    private final CardMatcher matcher;
    private final Clock clock;

    /**
     * @param userId       카드의 주인
     * @param orgAccountId 카드를 수집한 카드사 계정
     * @param company      카드사
     * @param collected    이번 동기화에서 가져온 카드 전체
     */
    @Transactional
    public SaveResult saveSynced(long userId, long orgAccountId, CardCompany company, List<CollectedCard> collected) {
        LocalDateTime now = Times.nowSeconds(clock);
        Map<String, CollectedCard> incoming = distinct(collected);
        Map<String, CardProduct> matched = matcher.match(company,
            incoming.values().stream().map(CollectedCard::cardName).toList());

        Map<String, OwnedCard> existing = ownedCards.findByOrgAccountId(orgAccountId).stream()
            .collect(Collectors.toMap(OwnedCard::identityKey, Function.identity(), (first, second) -> first));

        List<OwnedCard> toCreate = new ArrayList<>();
        int updated = 0;
        for (Map.Entry<String, CollectedCard> entry : incoming.entrySet()) {
            CollectedCard card = entry.getValue();
            CardProduct product = matched.get(card.cardName());
            OwnedCard current = existing.get(entry.getKey());
            if (current == null) {
                toCreate.add(OwnedCard.discovered(userId, orgAccountId, company.organizationCode(), card.cardName(),
                    card.maskedNumber(), card.cardType(), product, now));
            } else {
                current.refresh(card.cardType(), product, now);
                updated++;
            }
        }

        int deactivated = 0;
        for (OwnedCard card : existing.values()) {
            if (card.isActive() && !incoming.containsKey(card.identityKey())) {
                card.deactivate();
                deactivated++;
            }
        }
        ownedCards.saveAll(toCreate);

        int matchedCount = (int) incoming.values().stream().filter(c -> matched.containsKey(c.cardName())).count();
        return new SaveResult(toCreate.size(), updated, deactivated, matchedCount, incoming.size() - matchedCount);
    }

    /**
     * 같은 카드가 두 번 오면 뒤의 것을 쓴다. 카드명은 앞뒤 공백을 떼고 카드 번호는 가린다. 카드명이 없는 항목은 버린다.
     * 키는 저장된 카드를 찾는 키와 같다.
     */
    private Map<String, CollectedCard> distinct(List<CollectedCard> collected) {
        Map<String, CollectedCard> result = new LinkedHashMap<>();
        for (CollectedCard card : collected) {
            if (card == null || card.cardName() == null || card.cardName().isBlank()) {
                continue;
            }
            // 컬럼 길이를 넘는 값은 잘라서 저장이 실패하지 않게 한다. (이상한 카드 하나가 동기화 전체를 막지 않도록)
            CollectedCard normalized = new CollectedCard(
                OwnedCard.fit(card.cardName().strip(), OwnedCard.CARD_NAME_MAX_LENGTH),
                CardNumberMasker.mask(card.maskedNumber()),
                OwnedCard.fit(card.cardType(), OwnedCard.CARD_TYPE_MAX_LENGTH));
            result.put(OwnedCard.identityKey(normalized.maskedNumber(), normalized.cardName()), normalized);
        }
        return result;
    }

    /**
     * 저장 결과.
     *
     * @param created     새로 저장한 카드 수
     * @param updated     기존 카드를 갱신한 수
     * @param deactivated 이번 동기화에서 보이지 않아 비활성으로 표시한 수
     * @param matched     카드 상품에 매칭된 수(이번 동기화에서 확인된 카드 중)
     * @param unmatched   매칭하지 못한 수
     */
    public record SaveResult(int created, int updated, int deactivated, int matched, int unmatched) {

    }

}
