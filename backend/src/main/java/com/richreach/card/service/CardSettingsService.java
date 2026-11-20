package com.richreach.card.service;

import com.richreach.card.dto.CardSettingsResponse;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.entity.OwnedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.codef.dto.LinkedAccountResponse;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.service.ConnectionAccountQueryService;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드 정보 화면(SETTINGS-03)에 필요한 카드 한 장의 정보를 모은다. 내 활성 카드가 아니거나 없는 카드는
 * {@code CARD-001}이다.
 *
 * <ul>
 *   <li>결제일은 카드사에서 아직 가져오지 않아서 {@code null}이다. 임의의 값을 만들지 않는다.</li>
 *   <li>실적 이용기간은 전월 1일부터 말일까지로 계산한다. 카드사별로 다른 기준은 반영하지 않는다.</li>
 *   <li>연결 계정은 카드사 연동 도메인의 서비스로 읽는다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CardSettingsService {

    private final OwnedCardRepository ownedCards;
    private final ConnectionAccountQueryService accounts;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CardSettingsResponse get(long userId, long cardId) {
        OwnedCard card = ownedCards.findById(cardId)
            .filter(found -> found.getUserId().equals(userId) && found.isActive())
            .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

        YearMonth previous = YearMonth.now(clock).minusMonths(1);
        LinkedAccountResponse account = accounts.find(userId, card.getOrgAccountId()).orElse(null);
        return new CardSettingsResponse(
            card.getId(),
            CardCompany.fromOrganizationCode(card.getOrganization()),
            card.getCardName(),
            OwnedCard.lastFourDigits(card.getMaskedNumber()),
            card.isAnalysisTarget(),
            null,
            new CardSettingsResponse.Period(previous.atDay(1), previous.atEndOfMonth()),
            account);
    }

}
