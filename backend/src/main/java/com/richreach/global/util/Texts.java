package com.richreach.global.util;

/** 문자열을 다루는 공통 도구. */
public final class Texts {

    private Texts() {
    }

    /**
     * 컬럼 길이(글자 수)를 넘는 값을 잘라서 저장이 실패하지 않게 한다. 같은 입력은 항상 같게 잘린다.
     * 글자 수는 MySQL처럼 유니코드 코드 포인트로 센다. null은 그대로 돌려준다.
     */
    public static String fit(String text, int maxLength) {
        if (text == null || text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxLength));
    }

}
