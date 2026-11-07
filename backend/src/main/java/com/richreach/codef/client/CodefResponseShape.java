package com.richreach.codef.client;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * CODEF 응답의 "구조"만 글로 보여준다. 값은 보여주지 않는다. (응답 구조를 확인할 때 쓴다.)
 *
 * <p>응답에는 카드번호, 이름, connectedId 같은 민감한 값이 섞일 수 있어서 필드 경로와 타입만 출력한다.
 * <ul>
 *   <li>문자열: 길이만 출력</li>
 *   <li>숫자: 타입만 출력</li>
 *   <li>불리언: 값 출력 (민감하지 않음)</li>
 *   <li>이름이 {@code code}인 필드(결과 코드, 오류 코드): 값 출력</li>
 *   <li>배열: 길이를 출력하고, 객체 배열이면 모든 원소의 필드를 합쳐서 보여준다.</li>
 * </ul>
 * 출력 예: {@code data.cards[].resCardName : string(len=9)}
 */
final class CodefResponseShape {

    /** 값을 그대로 보여줄 필드 이름. 결과 코드와 오류 코드만 해당한다. */
    private static final Set<String> VALUE_KEYS = Set.of("code");
    private static final int MAX_DEPTH = 20;

    private CodefResponseShape() {
    }

    static String describe(JsonNode root) {
        StringBuilder out = new StringBuilder();
        write(out, root, "$", "", 0);
        return out.toString().stripTrailing();
    }

    private static void write(StringBuilder out, JsonNode node, String path, String key, int depth) {
        if (depth > MAX_DEPTH) {
            line(out, path, "... (too deep)");
        } else if (node == null || node.isMissingNode() || node.isNull()) {
            line(out, path, "null");
        } else if (node.isString()) {
            line(out, path, VALUE_KEYS.contains(key.toLowerCase(Locale.ROOT))
                ? "string = '" + node.asString() + "'" : "string(len=" + node.asString().length() + ")");
        } else if (node.isBoolean()) {
            line(out, path, "bool = " + node.asBoolean());
        } else if (node.isNumber()) {
            line(out, path, "number");
        } else if (node.isArray()) {
            writeArray(out, node, path, key, depth);
        } else if (node.isObject()) {
            writeObject(out, path, fieldsOf(node), depth);
        } else {
            line(out, path, node.getNodeType().name().toLowerCase(Locale.ROOT));
        }
    }

    private static void writeArray(StringBuilder out, JsonNode array, String path, String key, int depth) {
        line(out, path, "array[" + array.size() + "]");
        if (array.size() == 0) {
            return;
        }
        String elementPath = path + "[]";
        if (array.get(0).isObject()) {
            // 객체 배열은 원소마다 필드가 다를 수 있어서 모든 원소의 필드를 합쳐서 보여준다.
            Map<String, JsonNode> merged = new LinkedHashMap<>();
            for (JsonNode element : array) {
                if (element.isObject()) {
                    fieldsOf(element).forEach((name, value) -> {
                        JsonNode existing = merged.get(name);
                        if (existing == null || existing.isNull()) {
                            merged.put(name, value);
                        }
                    });
                }
            }
            writeObject(out, elementPath, merged, depth + 1);
        } else {
            write(out, array.get(0), elementPath, key, depth + 1);
        }
    }

    private static void writeObject(StringBuilder out, String path, Map<String, JsonNode> fields, int depth) {
        line(out, path, "object");
        fields.forEach((name, value) ->
            write(out, value, "$".equals(path) ? name : path + "." + name, name, depth + 1));
    }

    private static Map<String, JsonNode> fieldsOf(JsonNode object) {
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        object.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
        return fields;
    }

    private static void line(StringBuilder out, String path, String description) {
        out.append(path).append(" : ").append(description).append('\n');
    }

}
