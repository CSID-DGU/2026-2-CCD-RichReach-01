package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CodefResponseShapeTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static String shapeOf(String json) throws Exception {
        JsonNode node = JSON.readTree(json);
        return CodefResponseShape.describe(node);
    }

    @Test
    @DisplayName("문자열은 길이만, 숫자는 타입만 보여주고 불리언은 값을 보여준다")
    void showsTypesNotValues() throws Exception {
        String shape = shapeOf("{\"name\":\"secret-card-holder\",\"count\":3,\"active\":true}");

        assertThat(shape).contains("$ : object")
            .contains("name : string(len=18)")
            .contains("count : number")
            .contains("active : bool = true");
        assertThat(shape).doesNotContain("secret-card-holder");
    }

    @Test
    @DisplayName("이름이 code인 필드만 값을 보여준다 (결과 코드, 오류 코드)")
    void showsOnlyCodeValues() throws Exception {
        String shape = shapeOf(
            "{\"result\":{\"code\":\"CF-00000\",\"message\":\"secret-message\"},\"data\":{\"connectedId\":\"secret-id\"}}");

        assertThat(shape).contains("result.code : string = 'CF-00000'")
            .contains("result.message : string(len=14)")
            .contains("data.connectedId : string(len=9)");
        assertThat(shape).doesNotContain("secret-message").doesNotContain("secret-id");
    }

    @Test
    @DisplayName("객체 배열은 길이와 모든 원소의 필드를 합쳐서 보여준다")
    void mergesObjectArrayElements() throws Exception {
        String shape = shapeOf("{\"cards\":[{\"a\":\"x\",\"b\":1},{\"a\":\"y\",\"c\":false}]}");

        assertThat(shape).contains("cards : array[2]")
            .contains("cards[] : object")
            .contains("cards[].a : string(len=1)")
            .contains("cards[].b : number")
            .contains("cards[].c : bool = false");
    }

    @Test
    @DisplayName("오류 목록의 code 값은 보여준다")
    void showsCodeInsideArrays() throws Exception {
        String shape = shapeOf("{\"data\":{\"errorList\":[{\"code\":\"CF-12345\",\"extra\":\"detail\"}]}}");

        assertThat(shape).contains("data.errorList : array[1]")
            .contains("data.errorList[].code : string = 'CF-12345'")
            .contains("data.errorList[].extra : string(len=6)");
        assertThat(shape).doesNotContain("detail");
    }

    @Test
    @DisplayName("빈 배열, 최상위 배열, 널, 값 없는 객체를 처리한다")
    void handlesEdgeShapes() throws Exception {
        assertThat(shapeOf("[]")).isEqualTo("$ : array[0]");
        assertThat(shapeOf("[{\"n\":1}]")).contains("$ : array[1]").contains("$[].n : number");
        assertThat(shapeOf("{\"x\":null,\"y\":{}}")).contains("x : null").contains("y : object");
    }

    @Test
    @DisplayName("단일 객체(카드가 한 장일 때)와 배열(여러 장일 때)이 구분되어 보인다")
    void distinguishesObjectFromArray() throws Exception {
        assertThat(shapeOf("{\"data\":{\"resCardName\":\"x\"}}")).contains("data : object");
        assertThat(shapeOf("{\"data\":[{\"resCardName\":\"x\"}]}")).contains("data : array[1]");
    }

    @Test
    @DisplayName("첫 원소가 널이거나 문자열이어도 뒤에 있는 객체의 필드가 빠지지 않고, 문자열 원소도 함께 보인다")
    void keepsObjectFieldsWhenFirstElementIsNotAnObject() throws Exception {
        String shape = shapeOf("{\"items\":[null,\"abc\",{\"a\":\"x\"},{\"b\":1}]}");

        assertThat(shape).contains("items : array[4]")
            .contains("items[] : object")
            .contains("items[].a : string(len=1)")
            .contains("items[].b : number")
            .contains("items[] : string(len=3)");
        assertThat(shape).doesNotContain("abc");
    }

    @Test
    @DisplayName("배열 원소마다 중첩된 객체의 필드가 달라도 모두 합쳐서 보여준다")
    void mergesNestedObjectsAcrossElements() throws Exception {
        String shape = shapeOf("{\"list\":[{\"inner\":{\"a\":\"x\"}},{\"inner\":{\"b\":2}}]}");

        assertThat(shape).contains("list[].inner : object")
            .contains("list[].inner.a : string(len=1)")
            .contains("list[].inner.b : number");
    }

    @Test
    @DisplayName("원소 안의 배열은 길이를 모두 보여주고, 모든 원소의 필드를 합쳐서 보여준다")
    void mergesNestedArraysAcrossElements() throws Exception {
        String shape = shapeOf("{\"a\":[{\"b\":[{\"c\":1}]},{\"b\":[{\"d\":\"x\"},{\"c\":2}]}]}");

        assertThat(shape).contains("a[].b : array[1,2]")
            .contains("a[].b[].c : number")
            .contains("a[].b[].d : string(len=1)");
    }

    @Test
    @DisplayName("오류 목록에 서로 다른 code가 여러 개 있으면 모두 보여준다")
    void showsEveryDistinctCode() throws Exception {
        String shape = shapeOf("{\"data\":{\"errorList\":[{\"code\":\"CF-1\"},{\"code\":\"CF-2\"},{\"code\":\"CF-1\"}]}}");

        assertThat(shape).contains("data.errorList : array[3]")
            .contains("data.errorList[].code : string = 'CF-1', 'CF-2'");
    }

    @Test
    @DisplayName("원소마다 문자열 길이가 다르면 모든 길이를 보여주고 값은 보여주지 않는다")
    void showsEveryStringLength() throws Exception {
        String shape = shapeOf("{\"n\":[{\"s\":\"ab\"},{\"s\":\"abcd\"}]}");

        assertThat(shape).contains("n[].s : string(len=2,4)");
        assertThat(shape).doesNotContain("abcd");
    }

    @Test
    @DisplayName("문자열 배열은 값 없이 타입만 보여준다")
    void stringArraysHideValues() throws Exception {
        String shape = shapeOf("{\"tags\":[\"secret-a\",\"secret-b\"]}");

        assertThat(shape).contains("tags : array[2]").contains("tags[] : string(len=8)");
        assertThat(shape).doesNotContain("secret-a").doesNotContain("secret-b");
    }

}
