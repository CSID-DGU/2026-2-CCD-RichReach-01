package com.richreach.card.service;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.entity.OwnedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분석 대상으로 삼을 보유카드를 고른다. 요청한 카드 전체로 선택을 교체한다. 여기에 없는 카드는 선택이 해제된다.
 *
 * <p>검증이 모두 끝난 뒤에만 바꾸므로 일부만 바뀌는 일이 없다.
 * <ul>
 *   <li>내 활성 카드가 아닌 ID(없는 카드, 다른 사용자의 카드, 사라진 카드)는 {@code CARD-001}이다. 남의 카드가 있는지는
 *       알려주지 않으려고 없는 카드와 같게 응답한다.</li>
 *   <li>카드 상품에 매칭되지 않아 분석할 수 없는 카드는 {@code CARD-002}다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class OwnedCardSelectionService {

    private final OwnedCardRepository ownedCards;

    /** @return 선택을 바꾼 뒤의 보유카드 목록 */
    @Transactional
    public List<OwnedCardResponse> select(long userId, List<Long> cardIds) {
        Set<Long> requested = new LinkedHashSet<>(cardIds);
        List<OwnedCard> cards = ownedCards.findActiveByUserId(userId);
        Map<Long, OwnedCard> byId = cards.stream().collect(Collectors.toMap(OwnedCard::getId, Function.identity()));

        if (!byId.keySet().containsAll(requested)) {
            throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
        }
        if (requested.stream().anyMatch(id -> !byId.get(id).isAnalyzable())) {
            throw new BusinessException(ErrorCode.CARD_NOT_ANALYZABLE);
        }

        cards.forEach(card -> card.changeSelection(requested.contains(card.getId())));
        return cards.stream().map(OwnedCardResponse::from).toList();
    }

}
