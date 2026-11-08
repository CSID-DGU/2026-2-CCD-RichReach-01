package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.codef.client.CodefAccountClient.AccountResult;
import com.richreach.codef.entity.CardCompany;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CodefAccountClientTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SUCCESS = "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"connectedId\":\"test-connected-id\"}}";

    private CodefApiClient apiClient;
    private CodefRsaEncryptor encryptor;
    private CodefAccountClient client;

    @BeforeEach
    void setUp() throws Exception {
        apiClient = mock(CodefApiClient.class);
        encryptor = mock(CodefRsaEncryptor.class);
        when(encryptor.encrypt("test-card-password")).thenReturn("ENCRYPTED");
        when(apiClient.post(any(), any())).thenReturn(JSON.readTree(SUCCESS));
        client = new CodefAccountClient(apiClient, encryptor);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentBody(String path) {
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(apiClient).post(eq(path), body.capture());
        return (Map<String, Object>) body.getValue();
    }

    @Test
    @DisplayName("처음 연결은 connectedId 없이 보내고, 비밀번호는 암호화하며 카드사 기관 코드를 넣는다")
    @SuppressWarnings("unchecked")
    void createSendsEncryptedPasswordWithoutConnectedId() {
        AccountResult result = client.create(CardCompany.KB, "test-card-id", "test-card-password");

        Map<String, Object> body = sentBody("/v1/account/create");
        assertThat(body).doesNotContainKey("connectedId");
        Map<String, Object> account = ((List<Map<String, Object>>) body.get("accountList")).get(0);
        assertThat(account).containsEntry("organization", "0301")
            .containsEntry("businessType", "CD")
            .containsEntry("clientType", "P")
            .containsEntry("loginType", "1")
            .containsEntry("id", "test-card-id")
            .containsEntry("password", "ENCRYPTED");
        assertThat(body.toString()).doesNotContain("test-card-password");
        assertThat(result.success()).isTrue();
        assertThat(result.connectedId()).isEqualTo("test-connected-id");
    }

    @Test
    @DisplayName("카드사를 더할 때는 기존 connectedId를 함께 보낸다")
    void addSendsConnectedId() {
        client.add("test-connected-id", CardCompany.SHINHAN, "test-card-id", "test-card-password");

        Map<String, Object> body = sentBody("/v1/account/add");
        assertThat(body).containsEntry("connectedId", "test-connected-id");
    }

    @Test
    @DisplayName("이미 연결한 카드사는 update 경로로 보낸다")
    void updateUsesUpdatePath() {
        client.update("test-connected-id", CardCompany.WOORI, "test-card-id", "test-card-password");

        assertThat(sentBody("/v1/account/update")).containsEntry("connectedId", "test-connected-id");
    }

    private static JsonNode json(String text) throws Exception {
        return JSON.readTree(text);
    }

    @Test
    @DisplayName("결과 코드가 CF-00000이고 실패 목록이 없으면 성공이다")
    void parsesSuccess() throws Exception {
        AccountResult result = CodefAccountClient.parse(json(SUCCESS));

        assertThat(result.success()).isTrue();
        assertThat(result.errorCode()).isNull();
    }

    @Test
    @DisplayName("데모 환경의 실제 성공 응답 모양(successList, 빈 errorList, data.connectedId)을 성공으로 읽는다")
    void parsesRealDemoSuccessShape() throws Exception {
        AccountResult result = CodefAccountClient.parse(json("""
            {"result":{"code":"CF-00000","extraMessage":"","message":"성공","transactionId":"test-transaction-id-001"},
             "data":{"successList":[{"clientType":"P","code":"CF-00000","loginType":"1","countryCode":"KR",
                                     "organization":"0306","extraMessage":"","businessType":"CD","message":"성공",
                                     "transactionId":"test-transaction-id-002"}],
                     "errorList":[],
                     "connectedId":"test-connected-id"}}
            """));

        assertThat(result.success()).isTrue();
        assertThat(result.connectedId()).isEqualTo("test-connected-id");
        assertThat(result.errorCode()).isNull();
    }

    @Test
    @DisplayName("데모 환경의 실제 실패 응답 모양은 결과 코드(CF-04000)가 아니라 실패 목록의 코드(CF-04020)로 실패 처리한다")
    void parsesRealDemoFailureShape() throws Exception {
        AccountResult result = CodefAccountClient.parse(json("""
            {"result":{"code":"CF-04000","extraMessage":"","message":"테스트 실패 메시지","transactionId":"test-transaction-id-001"},
             "data":{"successList":[],
                     "errorList":[{"clientType":"P","code":"CF-04020","loginType":"1","countryCode":"KR",
                                   "organization":"0306","extraMessage":"","businessType":"CD",
                                   "message":"테스트 오류 메시지","transactionId":"test-transaction-id-002"}]}}
            """));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CF-04020");
        assertThat(result.connectedId()).isNull();
    }

    @Test
    @DisplayName("결과 코드가 성공이어도 실패 목록에 항목이 있으면 첫 오류 코드로 실패 처리한다")
    void parsesErrorListAsFailure() throws Exception {
        AccountResult result = CodefAccountClient.parse(json(
            "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"errorList\":[{\"code\":\"CF-12345\"}]}}"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CF-12345");
    }

    @Test
    @DisplayName("성공이 아닌 결과 코드는 그 코드로 실패 처리한다")
    void parsesNonSuccessCode() throws Exception {
        AccountResult result = CodefAccountClient.parse(json("{\"result\":{\"code\":\"CF-04000\"}}"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CF-04000");
    }

    @Test
    @DisplayName("결과 객체를 로그에 찍어도 connectedId가 보이지 않는다")
    void resultToStringMasksConnectedId() throws Exception {
        AccountResult result = CodefAccountClient.parse(json(SUCCESS));

        assertThat(result.toString()).doesNotContain("test-connected-id");
    }

}
