package com.richreach.codef.entity;

/**
 * 카드 연동 화면(ONB-02A)에 보여줄 카드사. 순서는 화면 순서와 같다.
 * 코드는 프론트와 API가 주고받는 값이다. CODEF 기관 코드는 카드사 연결 생성 때 따로 매핑한다.
 *
 * <p>{@code supported}는 연결을 지원하는지다. 지금은 7개 모두 지원으로 두고, 지원하지 않는 카드사가
 * 생기면 {@code false}로 바꾼다. (프론트는 {@code false}면 선택할 수 없게 보여준다.)
 */
public enum CardCompany {

    KB("KB국민카드", true),
    HYUNDAI("현대카드", true),
    NH("NH농협카드", true),
    CITI("씨티카드", true),
    WOORI("우리카드", true),
    BC("BC카드", true),
    SHINHAN("신한카드", true);

    private final String displayName;
    private final boolean supported;

    CardCompany(String displayName, boolean supported) {
        this.displayName = displayName;
        this.supported = supported;
    }

    public String displayName() {
        return displayName;
    }

    public boolean supported() {
        return supported;
    }

}
