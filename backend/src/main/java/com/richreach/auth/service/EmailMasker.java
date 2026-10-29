package com.richreach.auth.service;

/** 이메일의 앞부분 일부를 가린다. 예: {@code rich@example.com} → {@code ri***@example.com} */
public final class EmailMasker {

    private static final String MASK = "***";

    private EmailMasker() {
    }

    /**
     * 아이디 부분은 앞 두 글자만 보여주고(한두 글자뿐이면 첫 글자만) 나머지는 {@code ***}로 가린다. 도메인은 그대로 둔다.
     * '@'가 없는 값은 이메일이 아니므로 전체를 가린다.
     */
    public static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return MASK;
        }
        String local = email.substring(0, at);
        int visible = local.length() >= 3 ? 2 : 1;
        return local.substring(0, visible) + MASK + email.substring(at);
    }

}
