package com.richreach.global.logging;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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

    /** Spring Boot 4가 쓰는 Jackson 3 (tools.jackson). 이 클래스는 JSON을 읽고 다시 쓰는 용도로만 쓴다. */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String UNREADABLE_BODY = "[본문을 읽을 수 없어 가렸습니다]";

    private SensitiveDataMasker() {
    }

    /**
     * JSON 본문을 읽어서 민감한 키의 값을 가린 뒤 한 줄 JSON으로 돌려준다.
     * 값이 문자열이든 숫자든 배열/객체든 통째로 가리고, 중첩된 객체/배열 안의 키도 가린다.
     * 키를 유니코드 이스케이프({@code "password"})로 써도 읽은 뒤의 이름으로 비교하므로 가려진다.
     * JSON이 아니거나 잘려서 끝까지 읽을 수 없으면 일부만 가려지는 것을 막기 위해 본문 전체를 가린다.
     */
    public static String maskJson(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            if (!root.isObject() && !root.isArray()) {
                return UNREADABLE_BODY;
            }
            maskNode(root);
            return MAPPER.writeValueAsString(root);
        } catch (JacksonException e) {
            return UNREADABLE_BODY;
        }
    }

    private static void maskNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            // 순회하면서 값을 바꾸면 안 되므로 목록을 복사해서 돈다.
            for (Map.Entry<String, JsonNode> field : new ArrayList<>(object.properties())) {
                if (isSensitive(field.getKey())) {
                    object.put(field.getKey(), MASK);
                } else {
                    maskNode(field.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(SensitiveDataMasker::maskNode);
        }
    }

    /**
     * 로그 한 줄에 그대로 남겨도 되게 줄바꿈 등 제어 문자를 공백으로 바꾼다.
     * 요청 본문에 줄바꿈과 가짜 로그 줄을 넣어 로그를 위조하는 것(CWE-117)을 막는다.
     */
    public static String oneLine(String value) {
        return value == null ? null : value.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", " ");
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
            masked.append(key).append('=').append(isSensitiveQueryKey(key) ? MASK : pair.substring(eq + 1));
        }
        return masked.toString();
    }

    /**
     * getQueryString()은 디코딩 전 값이라 {@code pass%77ord}처럼 쓴 키도 들어온다.
     * 디코딩한 이름으로 판별하고, 디코딩할 수 없는 키는 판별할 수 없으므로 가리는 쪽으로 처리한다.
     */
    private static boolean isSensitiveQueryKey(String rawKey) {
        try {
            return isSensitive(URLDecoder.decode(rawKey, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return true;
        }
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
