package com.richreach.global.security;

/** 토큰 종류. access 토큰으로 refresh 토큰을 대신 쓰는 것을 막기 위해 토큰 안에 담는다. */
public enum TokenType {

    ACCESS("access"),
    REFRESH("refresh");

    private final String claimValue;

    TokenType(String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }

}
