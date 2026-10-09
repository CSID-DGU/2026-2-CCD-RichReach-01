package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.codef.client.CodefApiClient;
import com.richreach.codef.client.CodefRsaEncryptor;
import com.richreach.codef.dto.CodefApprovalRequest;
import com.richreach.codef.dto.CodefBillingRequest;
import com.richreach.codef.dto.CodefCardListRequest;
import com.richreach.codef.dto.CodefConnectRequest;
import com.richreach.codef.dto.CodefResultCheckRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CodefTestServiceTest {

    private CodefApiClient apiClient;
    private CodefRsaEncryptor rsaEncryptor;
    private CodefTestService service;

    @BeforeEach
    void setUp() {
        apiClient = mock(CodefApiClient.class);
        rsaEncryptor = mock(CodefRsaEncryptor.class);
        when(rsaEncryptor.encrypt(any())).thenAnswer(invocation -> "ENC(" + invocation.getArgument(0) + ")");
        service = new CodefTestService(apiClient, rsaEncryptor);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedBody(String path) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(apiClient).post(eq(path), captor.capture());
        return (Map<String, Object>) captor.getValue();
    }

    @Test
    @DisplayName("계정 등록: 기본값(CD, P, 아이디 로그인)을 채우고 비밀번호는 암호화해서 보낸다")
    @SuppressWarnings("unchecked")
    void createConnectedId() {
        service.createConnectedId(new CodefConnectRequest("0301", "my-id", "my-plain-password", null, "", null));

        Map<String, Object> body = capturedBody("/v1/account/create");
        Map<String, Object> account = ((List<Map<String, Object>>) body.get("accountList")).get(0);

        assertThat(account).containsEntry("countryCode", "KR")
            .containsEntry("businessType", "CD")
            .containsEntry("clientType", "P")
            .containsEntry("loginType", "1")
            .containsEntry("organization", "0301")
            .containsEntry("id", "my-id");
        assertThat(account.get("password")).isEqualTo("ENC(my-plain-password)");
        // 비밀번호 원문이 암호화된 password 필드 외의 다른 필드로 전달되지 않는다.
        assertThat(account.entrySet().stream()
            .filter(entry -> !"password".equals(entry.getKey()))
            .map(Map.Entry::getValue))
            .doesNotContain("my-plain-password");
    }

    @Test
    @DisplayName("보유카드 조회: connectedId와 기관코드를 전달한다")
    void cardList() {
        service.cardList(new CodefCardListRequest("cid", "0301"));

        assertThat(capturedBody("/v1/kr/card/p/account/card-list"))
            .containsEntry("connectedId", "cid")
            .containsEntry("organization", "0301");
    }

    @Test
    @DisplayName("승인내역 조회: 기본값을 채우고 값이 없는 선택 항목은 보내지 않는다")
    void approvalsDefaults() {
        service.approvals(new CodefApprovalRequest("cid", "0301", "20260801", "20260926",
            null, "", null, null, null, null, null, null));

        Map<String, Object> body = capturedBody("/v1/kr/card/p/account/approval-list");
        assertThat(body).containsEntry("startDate", "20260801")
            .containsEntry("endDate", "20260926")
            .containsEntry("orderBy", "0")
            .containsEntry("inquiryType", "1")
            .containsEntry("memberStoreInfoType", "1")
            .doesNotContainKeys("cardName", "cardNo", "duplicateCardIdx", "birthDate", "cardPassword");
    }

    @Test
    @DisplayName("실적조회: 신한처럼 필수 값만 있으면 connectedId와 기관코드만 보낸다")
    void resultCheckRequiredOnly() {
        service.resultCheck(new CodefResultCheckRequest("cid", "0306", "", null, "  ", null));

        Map<String, Object> body = capturedBody("/v1/kr/card/p/account/result-check-list");
        assertThat(body).containsOnlyKeys("connectedId", "organization")
            .containsEntry("connectedId", "cid")
            .containsEntry("organization", "0306");
    }

    @Test
    @DisplayName("실적조회: 선택 값이 있으면 전달하고 카드 비밀번호는 암호화한다")
    void resultCheckWithOptionalValues() {
        service.resultCheck(new CodefResultCheckRequest("cid", "0301", "202608", "19900101", "1234567890123456", "12"));

        Map<String, Object> body = capturedBody("/v1/kr/card/p/account/result-check-list");
        assertThat(body).containsEntry("startDate", "202608")
            .containsEntry("birthDate", "19900101")
            .containsEntry("cardNo", "1234567890123456")
            .containsEntry("cardPassword", "ENC(12)");
    }

    @Test
    @DisplayName("청구내역 조회: 기본값(memberStoreInfoYN=0)을 채우고 값이 없는 선택 항목은 보내지 않는다")
    void billingDefaults() {
        service.billing(new CodefBillingRequest("cid", "0301", "", null, "  ", null, null));

        Map<String, Object> body = capturedBody("/v1/kr/card/p/account/billing-list");
        assertThat(body).containsOnlyKeys("connectedId", "organization", "memberStoreInfoYN")
            .containsEntry("connectedId", "cid")
            .containsEntry("organization", "0301")
            .containsEntry("memberStoreInfoYN", "0");
    }

    @Test
    @DisplayName("청구내역 조회: 선택 값이 있으면 전달하고 카드 비밀번호는 암호화한다 (현대카드 케이스)")
    void billingWithOptionalValues() {
        service.billing(new CodefBillingRequest("cid", "0302", "202609", "1", "19900101", "1234567890123456", "1234"));

        Map<String, Object> body = capturedBody("/v1/kr/card/p/account/billing-list");
        assertThat(body).containsEntry("startDate", "202609")
            .containsEntry("memberStoreInfoYN", "1")
            .containsEntry("birthDate", "19900101")
            .containsEntry("cardNo", "1234567890123456")
            .containsEntry("cardPassword", "ENC(1234)");
    }

    @Test
    @DisplayName("승인내역 조회: 카드 비밀번호는 암호화해서 보낸다")
    void approvalsEncryptsCardPassword() {
        service.approvals(new CodefApprovalRequest("cid", "0301", "20260801", "20260926",
            "1", "0", "3", null, "카드명", "1234567890123456", "0", "1234"));

        Map<String, Object> body = capturedBody("/v1/kr/card/p/account/approval-list");
        assertThat(body).containsEntry("cardPassword", "ENC(1234)")
            .containsEntry("cardName", "카드명")
            .containsEntry("inquiryType", "0")
            .containsEntry("memberStoreInfoType", "3");
    }

}
