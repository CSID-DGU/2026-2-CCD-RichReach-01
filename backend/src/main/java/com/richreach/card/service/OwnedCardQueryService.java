package com.richreach.card.service;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.repository.OwnedCardRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자의 보유카드 목록. 가장 최근 동기화에서 확인된(활성) 카드만 보여준다. 동기화에서 사라진 카드는 지우지 않고
 * 비활성으로 남겨 두지만 목록에는 나오지 않는다.
 */
@Service
@RequiredArgsConstructor
public class OwnedCardQueryService {

    private final OwnedCardRepository ownedCards;

    @Transactional(readOnly = true)
    public List<OwnedCardResponse> list(long userId) {
        return ownedCards.findActiveByUserId(userId).stream().map(OwnedCardResponse::from).toList();
    }

}
