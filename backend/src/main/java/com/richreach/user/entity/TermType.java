package com.richreach.user.entity;

/** 동의를 받는 약관의 종류. 종류는 {@code user_term_agreements.term_type}에 문자열로 저장해서 추가해도 스키마를 바꾸지 않는다. */
public enum TermType {

    /** 회원가입 때 받는 서비스 이용약관 및 개인정보 수집 동의 */
    SERVICE_AND_PRIVACY,

    /** 카드 연동 동의: 카드정보 조회 */
    CARD_INFO,

    /** 카드 연동 동의: 거래내역 조회 */
    TRANSACTION,

    /** 카드 연동 동의: 개인정보 수집 및 이용 */
    PRIVACY

}
