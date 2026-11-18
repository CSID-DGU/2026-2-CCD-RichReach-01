package com.richreach.codef.service;

/**
 * 화면에 "연결 계정"으로 보여줄 카드사 아이디를 가린다. 앞 2자만 남기고 나머지는 길이와 상관없이 {@code ***}로 바꿔서, 가린
 * 값만으로는 아이디의 길이를 알 수 없다. 2자 이하이면 앞 1자만 남긴다. 카드사 아이디 원문은 저장하거나 로그에 남기지 않는다.
 */
final class LoginIdMasker {

    static final String MASK = "***";
    private static final int VISIBLE = 2;

    private LoginIdMasker() {
    }

    /** 가린 아이디. 비어 있으면 null이다. */
    static String mask(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return null;
        }
        String id = loginId.strip();
        int visible = id.length() <= VISIBLE ? 1 : VISIBLE;
        // 이모지처럼 두 칸을 쓰는 글자가 중간에서 잘리지 않게 글자(코드 포인트) 단위로 자른다.
        int end = id.offsetByCodePoints(0, Math.min(visible, id.codePointCount(0, id.length())));
        return id.substring(0, end) + MASK;
    }

}
