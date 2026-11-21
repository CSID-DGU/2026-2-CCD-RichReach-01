package com.richreach.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.richreach.card.dto.CardSettingsResponse;
import com.richreach.card.entity.OwnedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.codef.service.ConnectionAccountQueryService;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 카드 정보의 실적 이용기간(전월 1일~말일)이 시계 기준으로 정확히 계산되는지 고정한 시각으로 확인한다. */
class CardSettingsServiceTest {

    private static final long USER = 1L;
    private static final long CARD = 7L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private OwnedCardRepository ownedCards;
    private ConnectionAccountQueryService accounts;

    @BeforeEach
    void setUp() {
        ownedCards = mock(OwnedCardRepository.class);
        accounts = mock(ConnectionAccountQueryService.class);
        OwnedCard card = OwnedCard.discovered(USER, 5L, "0301", "테스트 카드", "1111", "check", null,
            LocalDateTime.of(2026, 11, 1, 9, 0));
        ReflectionTestUtils.setField(card, "id", CARD);
        when(ownedCards.findById(CARD)).thenReturn(Optional.of(card));
        when(accounts.find(USER, 5L)).thenReturn(Optional.empty());
    }

    private CardSettingsResponse at(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), SEOUL);
        return new CardSettingsService(ownedCards, accounts, clock).get(USER, CARD);
    }

    @Test
    @DisplayName("이용기간은 전월 1일부터 말일까지다")
    void previousMonth() {
        CardSettingsResponse response = at("2026-11-18T03:00:00Z");

        assertThat(response.performancePeriod().from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(response.performancePeriod().to()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(response.paymentDay()).isNull();
    }

    @Test
    @DisplayName("월초(1일 0시, 한국 시간)에는 바로 그 달의 전월이다")
    void firstMomentOfTheMonth() {
        // 2026-12-01 00:00 KST = 2026-11-30T15:00:00Z
        CardSettingsResponse response = at("2026-11-30T15:00:00Z");

        assertThat(response.performancePeriod().from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(response.performancePeriod().to()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("월말 마지막 순간(한국 시간)에는 아직 그 달이다")
    void lastMomentOfTheMonth() {
        // 2026-11-30 23:59 KST = 2026-11-30T14:59:00Z
        CardSettingsResponse response = at("2026-11-30T14:59:00Z");

        assertThat(response.performancePeriod().from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(response.performancePeriod().to()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("윤년 2월은 29일까지, 평년 2월은 28일까지다")
    void leapYearFebruary() {
        assertThat(at("2028-03-05T03:00:00Z").performancePeriod().to()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(at("2027-03-05T03:00:00Z").performancePeriod().to()).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    @DisplayName("1월이면 전월은 지난해 12월이다")
    void januaryRollsBackToDecember() {
        CardSettingsResponse response = at("2027-01-10T03:00:00Z");

        assertThat(response.performancePeriod().from()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(response.performancePeriod().to()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("남의 카드나 사라진 카드는 CARD-001이다")
    void notFound() {
        OwnedCard others = OwnedCard.discovered(2L, 6L, "0301", "남의 카드", "2222", "check", null,
            LocalDateTime.of(2026, 11, 1, 9, 0));
        ReflectionTestUtils.setField(others, "id", 8L);
        when(ownedCards.findById(8L)).thenReturn(Optional.of(others));
        OwnedCard gone = OwnedCard.discovered(USER, 5L, "0301", "사라진 카드", "3333", "check", null,
            LocalDateTime.of(2026, 11, 1, 9, 0));
        ReflectionTestUtils.setField(gone, "id", 9L);
        gone.deactivate();
        when(ownedCards.findById(9L)).thenReturn(Optional.of(gone));
        CardSettingsService service = new CardSettingsService(ownedCards, accounts,
            Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), SEOUL));

        assertThatThrownBy(() -> service.get(USER, 8L)).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CARD_NOT_FOUND);
        assertThatThrownBy(() -> service.get(USER, 9L)).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CARD_NOT_FOUND);
        assertThatThrownBy(() -> service.get(USER, 404L)).isInstanceOf(BusinessException.class);
    }

}
