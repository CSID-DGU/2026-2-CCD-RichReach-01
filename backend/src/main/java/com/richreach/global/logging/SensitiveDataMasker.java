package com.richreach.global.logging;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그에 남기는 요청 정보에서 비밀번호, 토큰, 카드사 자격증명 같은 민감한 값을 가린다.
 * 가릴지 말지 애매하면 가리는 쪽으로 판단하고, JSON으로 읽을 수 없는 본문은 통째로 가린다.
 */
public final class SensitiveDataMasker {

    public static final String MASK = "****";

    /** 소문자로 바꾸고 '_', '-'를 뺀 이름으로 비교한다. (refresh_token, refreshToken, Refresh-Token 모두 해당) */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "password", "passwordconfirm", "currentpassword", "newpassword", "oldpassword", "loginpassword",
        "cardpassword", "cardpw", "pw", "pin",
        "accesstoken", "refreshtoken", "idtoken", "token", "resettoken", "authorization", "cookie", "setcookie",
        "secret", "clientsecret", "apikey", "privatekey", "connectedid",
        "verificationcode", "authcode", "otp",
        "cardnumber", "accountnumber", "residentnumber", "ssn");

    /** JSON의 "키": 값 한 쌍. 값은 문자열이거나 따옴표 없는 값(숫자, true, null 등)이다. */
    private static final Pattern JSON_PAIR = Pattern.compile(
        "\"([^\"\\\\]+)\"(\\s*:\\s*)(\"(?:[^\"\\\\]|\\\\.)*\"|[^,}\\]\\s][^,}\\]]*)");

    private static final String UNREADABLE_BODY = "[본문을 읽을 수 없어 가렸습니다]";

    private SensitiveDataMasker() {
    }

    /**
     * JSON 본문에서 민감한 키의 값을 가린다. 중첩된 객체/배열 안의 키도 가린다.
     * JSON 모양이 아니거나 잘려서 끝까지 읽을 수 없으면 일부만 가려지는 것을 막기 위해 본문 전체를 가린다.
     */
    public static String maskJson(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        String trimmed = body.trim();
        boolean looksLikeJson = (trimmed.startsWith("{") && trimmed.endsWith("}"))
            || (trimmed.startsWith("[") && trimmed.endsWith("]"));
        if (!looksLikeJson) {
            return UNREADABLE_BODY;
        }
        Matcher matcher = JSON_PAIR.matcher(body);
        StringBuilder masked = new StringBuilder();
        while (matcher.find()) {
            String replacement = isSensitive(matcher.group(1))
                ? "\"" + matcher.group(1) + "\"" + matcher.group(2) + "\"" + MASK + "\""
                : matcher.group(0);
            matcher.appendReplacement(masked, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }

    /** {@code a=1&token=xyz} 형태의 쿼리 문자열에서 민감한 파라미터의 값을 가린다. */
    public static String maskQuery(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }
        StringBuilder masked = new StringBuilder();
        for (String pair : query.split("&", -1)) {
            if (masked.length() > 0) {
                masked.append('&');
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                masked.append(pair);
                continue;
            }
            String key = pair.substring(0, eq);
            masked.append(key).append('=').append(isSensitive(key) ? MASK : pair.substring(eq + 1));
        }
        return masked.toString();
    }

    /** HTTP 헤더 값을 로그용으로 바꾼다. Authorization, Cookie 같은 헤더는 가린다. */
    public static String maskHeader(String name, String value) {
        return isSensitive(name) ? MASK : value;
    }

    public static boolean isSensitive(String name) {
        if (name == null) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return SENSITIVE_KEYS.contains(normalized);
    }

}
