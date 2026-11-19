package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.codef.client.CodefApprovalClient.ApprovalListResult;
import com.richreach.codef.client.CodefApprovalClient.CodefApproval;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** 승인내역 요청과 응답 읽기를 가짜 응답으로 확인한다. (필드 이름은 실제 응답으로 확인하기 전의 가정이다.) */
class CodefApprovalClientTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static ApprovalListResult parse(String json) throws Exception {
        return CodefApprovalClient.parse(JSON.readTree(json));
    }

    private static String entry(String date, String amount, String extra) {
        return "{\"resUsedDate\":\"" + date + "\",\"resUsedAmount\":\"" + amount + "\"" + extra + "}";
    }

    @Test
    @DisplayName("승인내역 조회는 연동 ID, 기관 코드, 기간(YYYYMMDD), 전체 카드·가맹점 정보 포함 옵션을 보낸다")
    @SuppressWarnings("unchecked")
    void sendsRequestFields() throws Exception {
        CodefApiClient apiClient = mock(CodefApiClient.class);
        when(apiClient.post(any(), any())).thenReturn(JSON.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}"));

        new CodefApprovalClient(apiClient).fetchApprovals("test-connected-id", "0301", LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 9, 26));

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(apiClient).post(eq("/v1/kr/card/p/account/approval-list"), body.capture());
        assertThat((Map<String, Object>) body.getValue())
            .containsEntry("connectedId", "test-connected-id").containsEntry("organization", "0301")
            .containsEntry("startDate", "20260801").containsEntry("endDate", "20260926")
            .containsEntry("orderBy", "0").containsEntry("inquiryType", "1")
            .containsEntry("memberStoreInfoType", "1");
    }

    @Test
    @DisplayName("data가 배열이면 원소마다 승인내역으로 읽는다")
    void readsArray() throws Exception {
        ApprovalListResult result = parse("""
            {"result":{"code":"CF-00000"},"data":[
              {"resUsedDate":"20260920","resUsedTime":"123045","resMemberStoreName":"테스트 상점A",
               "resUsedAmount":"22000","resApprovalNo":"12345678","resInstallmentMonth":"0",
               "resCancelYN":"0","resCancelAmount":"0","resMemberStoreType":"음식점"},
              {"resUsedDate":"20260921","resUsedTime":"","resMemberStoreName":"테스트 상점B",
               "resUsedAmount":"1,500","resApprovalNo":"","resInstallmentMonth":"3",
               "resCancelYN":"0","resCancelAmount":"","resMemberStoreType":""}]}
            """);

        assertThat(result.success()).isTrue();
        assertThat(result.approvalCount()).isEqualTo(2);
        assertThat(result.approvals()).containsExactly(
            new CodefApproval("12345678", LocalDate.of(2026, 9, 20), LocalTime.of(12, 30, 45), "테스트 상점A", 22_000, 0,
                0, "음식점", "", "0", "", "", ""),
            new CodefApproval("", LocalDate.of(2026, 9, 21), null, "테스트 상점B", 1_500, 0, 3, "", "", "0", "", "", ""));
    }

    @Test
    @DisplayName("data가 객체 한 건이면 한 건으로 읽는다")
    void readsSingleObject() throws Exception {
        ApprovalListResult result = parse("{\"result\":{\"code\":\"CF-00000\"},\"data\":"
            + entry("20260920", "5000", "") + "}");

        assertThat(result.approvalCount()).isEqualTo(1);
        assertThat(result.approvals()).hasSize(1);
        assertThat(result.approvals().get(0).amount()).isEqualTo(5_000);
    }

    @Test
    @DisplayName("거래가 없으면 성공이고 목록이 비어 있다 (빈 배열, 빈 객체, data 없음)")
    void emptyIsSuccess() throws Exception {
        for (String data : new String[] {"[]", "{}", "null"}) {
            ApprovalListResult result = parse("{\"result\":{\"code\":\"CF-00000\"},\"data\":" + data + "}");
            assertThat(result.success()).isTrue();
            assertThat(result.approvalCount()).isZero();
            assertThat(result.approvals()).isEmpty();
        }
        assertThat(parse("{\"result\":{\"code\":\"CF-00000\"}}").approvals()).isEmpty();
    }

    // ---- 취소 ----

    @Test
    @DisplayName("전체 취소 표시이고 취소 금액이 없으면 승인 금액 전체를 취소로 본다")
    void fullCancelWithoutAmount() throws Exception {
        ApprovalListResult result = parse("{\"result\":{\"code\":\"CF-00000\"},\"data\":["
            + entry("20260920", "30000", ",\"resCancelYN\":\"1\"") + "]}");

        assertThat(result.approvals().get(0).canceledAmount()).isEqualTo(30_000);
    }

    @Test
    @DisplayName("취소 금액이 오면 그 값을 쓴다 (부분 취소)")
    void partialCancel() throws Exception {
        ApprovalListResult result = parse("{\"result\":{\"code\":\"CF-00000\"},\"data\":["
            + entry("20260920", "30000", ",\"resCancelYN\":\"2\",\"resCancelAmount\":\"10000\"") + "]}");

        assertThat(result.approvals().get(0).amount()).isEqualTo(30_000);
        assertThat(result.approvals().get(0).canceledAmount()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("취소가 아니면 취소 금액은 0이다")
    void notCanceled() throws Exception {
        ApprovalListResult result = parse("{\"result\":{\"code\":\"CF-00000\"},\"data\":["
            + entry("20260920", "30000", ",\"resCancelYN\":\"0\"") + "]}");

        assertThat(result.approvals().get(0).canceledAmount()).isZero();
    }

    // ---- 읽을 수 없는 값 ----

    @Test
    @DisplayName("일자나 금액을 읽을 수 없는 항목은 목록에서 빠지고 응답 건수에는 센다")
    void skipsUnreadableEntries() throws Exception {
        ApprovalListResult result = parse("{\"result\":{\"code\":\"CF-00000\"},\"data\":["
            + entry("20260920", "5000", "") + ","
            + entry("", "5000", "") + ","                 // 일자 없음
            + entry("2026-09-20", "5000", "") + ","       // 일자 형식이 다름
            + entry("20260920", "abc", "") + ","          // 금액이 숫자가 아님
            + entry("20260920", "-100", "") + ","         // 음수 금액
            + "{}]}");

        assertThat(result.approvalCount()).isEqualTo(6);
        assertThat(result.approvals()).hasSize(1);
    }

    @Test
    @DisplayName("금액의 쉼표와 소수점 이하는 허용하고, 할부 개월을 읽을 수 없으면 일시불(0)로 본다")
    void tolerantAmountAndInstallment() throws Exception {
        ApprovalListResult result = parse("{\"result\":{\"code\":\"CF-00000\"},\"data\":["
            + entry("20260920", "12,000.00", ",\"resInstallmentMonth\":\"일시불\"") + "]}");

        assertThat(result.approvals().get(0).amount()).isEqualTo(12_000);
        assertThat(result.approvals().get(0).installmentMonths()).isZero();
    }

    // ---- 실패 ----

    @Test
    @DisplayName("결과 코드가 CF-00000이 아니면 실패이고 코드를 돌려준다")
    void failureKeepsCode() throws Exception {
        ApprovalListResult result = parse("{\"result\":{\"code\":\"CF-12401\"},\"data\":[]}");

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo("CF-12401");
        assertThat(result.approvals()).isEmpty();
    }

    @Test
    @DisplayName("결과가 없는 응답도 실패로 읽는다")
    void missingResultIsFailure() throws Exception {
        assertThat(parse("{}").success()).isFalse();
    }

    @Test
    @DisplayName("카드번호, 결제 구분, 국내/해외 구분, 통화, 취소 구분은 카드사가 준 값 그대로 읽는다")
    void readsRawCodes() throws Exception {
        ApprovalListResult result = parse("{\"result\":{\"code\":\"CF-00000\"},\"data\":["
            + entry("20260920", "3000", ",\"resCardNo\":\"1234567890123456\",\"resPaymentType\":\"1\","
                + "\"resHomeForeignType\":\"1\",\"resAccountCurrency\":\"KRW\",\"resCancelYN\":\"0\"") + "]}");

        CodefApproval approval = result.approvals().get(0);
        assertThat(approval.cardNumber()).isEqualTo("1234567890123456");
        assertThat(approval.paymentType()).isEqualTo("1");
        assertThat(approval.homeForeignType()).isEqualTo("1");
        assertThat(approval.currency()).isEqualTo("KRW");
        assertThat(approval.cancelStatus()).isEqualTo("0");
    }

    @Test
    @DisplayName("toString은 가맹점명과 금액을 드러내지 않는다")
    void toStringHidesValues() {
        CodefApproval approval = new CodefApproval("1", LocalDate.of(2026, 9, 20), null, "비밀상점", 99_999, 0, 0, "", "1234567890123456", "0", "1", "1", "KRW");

        assertThat(approval.toString()).doesNotContain("비밀상점").doesNotContain("99999")
            .doesNotContain("1234567890123456");
    }

}
