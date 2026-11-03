package com.richreach.codef.entity;

/**
 * 카드 연동 화면(ONB-02A)에 보여줄 카드사. 순서는 화면 순서와 같다.
 * 코드는 프론트와 API가 주고받는 값이고, {@code organizationCode}는 CODEF에 보내는 기관 코드다.
 *
 * <p>{@code supported}는 연결을 지원하는지다. 지금은 7개 모두 지원으로 두고, 지원하지 않는 카드사가
 * 생기면 {@code false}로 바꾼다. (프론트는 {@code false}면 선택할 수 없게 보여준다.)
 */
public enum CardCompany {

    KB("KB국민카드", "0301", true),
    HYUNDAI("현대카드", "0302", true),
    NH("NH농협카드", "0304", true),
    CITI("씨티카드", "0307", true),
    WOORI("우리카드", "0309", true),
    BC("BC카드", "0305", true),
    SHINHAN("신한카드", "0306", true);

    private final String displayName;
    private final String organizationCode;
    private final boolean supported;

    CardCompany(String displayName, String organizationCode, boolean supported) {
        this.displayName = displayName;
        this.organizationCode = organizationCode;
        this.supported = supported;
    }

    public String displayName() {
        return displayName;
    }

    /** CODEF 기관 코드. codef_org_accounts.organization에도 같은 값을 저장한다. */
    public String organizationCode() {
        return organizationCode;
    }

    public boolean supported() {
        return supported;
    }

}
