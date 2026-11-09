package com.richreach.codef.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/**
 * CODEF 응답의 "구조"만 글로 보여준다. 값은 보여주지 않는다. (응답 구조를 확인할 때 쓴다.)
 *
 * <p>응답에는 카드번호, 이름, connectedId 같은 민감한 값이 섞일 수 있어서 필드 경로와 타입만 출력한다.
 * <ul>
 *   <li>문자열: 길이만 출력 (원소마다 길이가 다르면 모두 출력)</li>
 *   <li>숫자: 타입만 출력</li>
 *   <li>불리언: 값 출력 (민감하지 않음)</li>
 *   <li>이름이 {@code code}인 필드: 값이 CODEF 결과 코드 형식({@code CF-xxxxx})일 때만 값을 출력한다. 서로 다른 값이
 *       여러 개면 {@value #MAX_CODE_VALUES}개까지 출력한다. 형식이 다른 값이 하나라도 있으면 길이만 출력한다.</li>
 *   <li>배열: 길이를 출력하고, 모든 원소를 모아서 보여준다. 원소마다 필드가 달라도, 중첩된 객체나 배열이어도,
 *       첫 원소가 객체가 아니어도 빠지지 않는다.</li>
 * </ul>
 * 출력은 {@value #MAX_LINES}줄까지만 만들고, 넘으면 생략했다고 표시한다.
 * 출력 예: {@code data.cards[].resCardName : string(len=9)}
 */
final class CodefResponseShape {

    /** 값을 보여줄 수 있는 필드 이름. 결과 코드와 오류 코드만 해당한다. */
    private static final Set<String> VALUE_KEYS = Set.of("code");
    /** 값을 보여줄 수 있는 결과 코드의 형식. 이 형식이 아니면 이름이 code여도 값을 보여주지 않는다. */
    private static final Pattern CODE_VALUE = Pattern.compile("CF-\\d{5}");
    private static final int MAX_CODE_VALUES = 10;
    private static final int MAX_DEPTH = 20;
    /** 출력 줄 수 상한. 필드가 많은 응답에서 순회량과 로그 크기가 불어나지 않게 한다. */
    private static final int MAX_LINES = 300;

    private CodefResponseShape() {
    }

    static String describe(JsonNode root) {
        Lines out = new Lines();
        write(out, List.of(root), "$", "", 0);
        return out.result();
    }

    /**
     * 같은 자리에 있는 값들을 함께 설명한다. 배열의 원소는 모양이 서로 다를 수 있어서 첫 원소만 보지 않고 전부 모은다.
     * 값의 종류(객체, 배열, 문자열 등)마다 한 줄씩 보여주므로, 객체와 문자열이 섞인 배열은 둘 다 나타난다.
     */
    private static void write(Lines out, List<JsonNode> nodes, String path, String key, int depth) {
        if (out.isFull()) {
            out.markTruncated();
            return;
        }
        if (depth > MAX_DEPTH) {
            out.add(path, "... (too deep)");
            return;
        }
        List<JsonNode> objects = new ArrayList<>();
        List<JsonNode> arrays = new ArrayList<>();
        List<JsonNode> strings = new ArrayList<>();
        Set<Boolean> booleans = new LinkedHashSet<>();
        boolean hasNumber = false;
        boolean hasNull = false;
        Set<String> others = new LinkedHashSet<>();
        for (JsonNode node : nodes) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                hasNull = true;
            } else if (node.isObject()) {
                objects.add(node);
            } else if (node.isArray()) {
                arrays.add(node);
            } else if (node.isString()) {
                strings.add(node);
            } else if (node.isBoolean()) {
                booleans.add(node.asBoolean());
            } else if (node.isNumber()) {
                hasNumber = true;
            } else {
                others.add(node.getNodeType().name().toLowerCase(Locale.ROOT));
            }
        }

        if (!objects.isEmpty()) {
            writeObjects(out, objects, path, depth);
        }
        if (!arrays.isEmpty()) {
            writeArrays(out, arrays, path, key, depth);
        }
        if (!strings.isEmpty()) {
            out.add(path, describeStrings(strings, key));
        }
        if (!booleans.isEmpty()) {
            out.add(path, "bool = " + booleans.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        }
        if (hasNumber) {
            out.add(path, "number");
        }
        others.forEach(type -> out.add(path, type));
        // 값이 있는 원소와 섞여 있으면 널은 따로 보여주지 않는다. 전부 널일 때만 보여준다.
        if (hasNull && objects.isEmpty() && arrays.isEmpty() && strings.isEmpty() && booleans.isEmpty()
            && !hasNumber && others.isEmpty()) {
            out.add(path, "null");
        }
    }

    /** 객체가 여러 개면 모든 객체의 필드를 합쳐서, 필드마다 그 필드의 값들을 다시 함께 설명한다. (중첩된 객체도 합쳐진다.) */
    private static void writeObjects(Lines out, List<JsonNode> objects, String path, int depth) {
        out.add(path, "object");
        Map<String, List<JsonNode>> fields = new LinkedHashMap<>();
        for (JsonNode object : objects) {
            object.properties().forEach(entry ->
                fields.computeIfAbsent(entry.getKey(), name -> new ArrayList<>()).add(entry.getValue()));
        }
        for (Map.Entry<String, List<JsonNode>> field : fields.entrySet()) {
            if (out.isFull()) {
                out.markTruncated();
                return;
            }
            String name = field.getKey();
            write(out, field.getValue(), "$".equals(path) ? name : path + "." + name, name, depth + 1);
        }
    }

    /** 배열이 여러 개면(배열 원소 안의 배열) 길이를 모두 보여주고, 모든 배열의 원소를 모아서 설명한다. */
    private static void writeArrays(Lines out, List<JsonNode> arrays, String path, String key, int depth) {
        Set<Integer> sizes = new LinkedHashSet<>();
        List<JsonNode> elements = new ArrayList<>();
        for (JsonNode array : arrays) {
            sizes.add(array.size());
            array.forEach(elements::add);
        }
        out.add(path, "array[" + sizes.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]");
        if (!elements.isEmpty()) {
            write(out, elements, path + "[]", key, depth + 1);
        }
    }

    private static String describeStrings(List<JsonNode> strings, String key) {
        if (VALUE_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
            Set<String> values = new LinkedHashSet<>();
            strings.forEach(node -> values.add(node.asString()));
            // 결과 코드 형식이 아닌 값이 하나라도 있으면 코드가 아닌 값으로 보고 길이만 보여준다.
            if (values.stream().allMatch(value -> CODE_VALUE.matcher(value).matches())) {
                String shown = values.stream().limit(MAX_CODE_VALUES)
                    .map(value -> "'" + value + "'").collect(Collectors.joining(", "));
                int hidden = values.size() - MAX_CODE_VALUES;
                return "string = " + shown + (hidden > 0 ? " (+" + hidden + ")" : "");
            }
        }
        Set<Integer> lengths = new TreeSet<>();
        strings.forEach(node -> lengths.add(node.asString().length()));
        return "string(len=" + lengths.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
    }

    /** 출력할 줄을 모으되 {@value #MAX_LINES}줄을 넘으면 더 만들지 않고 생략했다고 표시한다. */
    private static final class Lines {

        private final StringBuilder text = new StringBuilder();
        private int count;
        private boolean truncated;

        boolean isFull() {
            return count >= MAX_LINES;
        }

        void markTruncated() {
            truncated = true;
        }

        void add(String path, String description) {
            if (isFull()) {
                truncated = true;
                return;
            }
            text.append(path).append(" : ").append(description).append('\n');
            count++;
        }

        String result() {
            if (truncated) {
                text.append("... (").append(MAX_LINES).append("줄을 넘어 이후는 생략했습니다)");
            }
            return text.toString().stripTrailing();
        }

    }

}
