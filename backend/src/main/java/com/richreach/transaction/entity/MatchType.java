package com.richreach.transaction.entity;

/** 가맹점명 분류 규칙이 가맹점명과 비교하는 방식. 비교 전에 둘 다 같은 방식으로 정규화한다. */
public enum MatchType {

    /** 가맹점명에 문구가 들어 있으면 일치 */
    CONTAINS,
    /** 가맹점명이 문구로 시작하면 일치. 짧은 영문 상호가 다른 단어에 섞여 오탐하지 않게 할 때 쓴다. */
    PREFIX,
    /** 가맹점명이 문구와 정확히 같으면 일치 */
    EXACT

}
