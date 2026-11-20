package com.richreach.card.dto;

import com.richreach.codef.dto.LinkedAccountResponse;
import com.richreach.codef.entity.CardCompany;
import java.time.LocalDate;

/**
 * 카드 정보 화면(SETTINGS-03)에 필요한 카드 한 장의 정보.
 *
 * @param cardId            보유카드 ID
 * @param cardCompany       카드사
 * @param cardName          카드 이름
 * @param maskedNumber      카드 번호의 마지막 4자리. 알 수 없으면 빈 문자열
 * @param selected          분석 대상인지. 바꾸는 API는 {@code PUT /api/cards/selection}
 * @param paymentDay        결제일(매월 몇 일). 카드사에서 아직 가져오지 않아서 항상 {@code null}이다. (화면은 "-")
 * @param performancePeriod 전월실적을 세는 기간. 모든 카드를 "전월 1일부터 말일까지"로 보고 계산한 기본 기간이고, 카드사별로
 *                          다른 기준(결제 주기 등)은 반영하지 않는다.
 * @param account           이 카드가 연결된 카드사 계정. 계정을 찾을 수 없으면 {@code null}이다.
 */
public record CardSettingsResponse(
    Long cardId,
    CardCompany cardCompany,
    String cardName,
    String maskedNumber,
    boolean selected,
    Integer paymentDay,
    Period performancePeriod,
    LinkedAccountResponse account) {

    /** 날짜 범위(양 끝 포함). */
    public record Period(LocalDate from, LocalDate to) {

    }

}
