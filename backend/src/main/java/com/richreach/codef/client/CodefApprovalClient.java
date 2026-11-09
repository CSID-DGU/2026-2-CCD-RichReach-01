package com.richreach.codef.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * CODEF 개인카드 승인내역 조회와 응답 읽기. 카드사 응답의 필드 이름과 구조는 이 클래스만 알고, 다른 곳은
 * {@link CodefApproval}만 본다.
 *
 * <p><b>필드 이름은 CODEF 개발가이드 기준의 가정이다.</b> 카드 목록({@link CodefCardClient})과 달리 아직 데모 환경의 실제
 * 응답으로 확인하지 못했다. 실제 응답과 다르면 아래 {@code FIELD_*} 상수만 고치면 된다. (응답 구조는
 * {@code CODEF_DEBUG_LOGSHAPE=true}로 값 없이 확인할 수 있다.)
 *
 * <p>승인내역에는 가맹점명, 금액 같은 소비 정보가 들어 있어서 로그에 남기지 않는다. 읽은 항목은 {@code toString}에서 가린다.
 */
@Component
@RequiredArgsConstructor
public class CodefApprovalClient {

    static final String APPROVAL_LIST_PATH = "/v1/kr/card/p/account/approval-list";

    static final String FIELD_USED_DATE = "resUsedDate";
    static final String FIELD_USED_TIME = "resUsedTime";
    static final String FIELD_MERCHANT_NAME = "resMemberStoreName";
    static final String FIELD_USED_AMOUNT = "resUsedAmount";
    static final String FIELD_APPROVAL_NO = "resApprovalNo";
    static final String FIELD_INSTALLMENT_MONTH = "resInstallmentMonth";
    static final String FIELD_CANCEL_STATUS = "resCancelYN";
    static final String FIELD_CANCEL_AMOUNT = "resCancelAmount";
    static final String FIELD_MERCHANT_TYPE = "resMemberStoreType";

    /** 취소 구분. 0이면 정상, 1이면 전체 취소, 2이면 부분 취소로 읽는다. (확인 필요) */
    static final String CANCEL_NONE = "0";
    static final String CANCEL_FULL = "1";

    private static final String SUCCESS_CODE = "CF-00000";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");

    private final CodefApiClient apiClient;

    /**
     * 기간의 승인내역을 전체 카드 기준으로 가져온다. (최신순, 가맹점 정보 포함)
     *
     * @param connectedId  CODEF 연동 ID (로그에 남기지 않는다)
     * @param organization CODEF 기관 코드
     * @param from         조회 시작일(포함)
     * @param to           조회 종료일(포함)
     */
    public ApprovalListResult fetchApprovals(String connectedId, String organization, LocalDate from, LocalDate to) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectedId", connectedId);
        body.put("organization", organization);
        body.put("startDate", DATE.format(from));
        body.put("endDate", DATE.format(to));
        body.put("orderBy", "0");
        body.put("inquiryType", "1");
        body.put("memberStoreInfoType", "1");
        return parse(apiClient.post(APPROVAL_LIST_PATH, body));
    }

    /**
     * 결과 코드가 CF-00000이면 성공이다. 승인내역이 여러 건이면 data가 배열이고, 한 건이면 객체로 올 수 있어서 둘 다 읽는다.
     * 일자나 금액을 읽을 수 없는 항목은 거래로 쓸 수 없으므로 읽어낸 목록에서 빠진다. (응답의 건수는 그대로 센다.)
     */
    static ApprovalListResult parse(JsonNode response) {
        String resultCode = response.path("result").path("code").asString("");
        if (!SUCCESS_CODE.equals(resultCode)) {
            return ApprovalListResult.failure(resultCode);
        }
        List<JsonNode> entries = entriesOf(response.path("data"));
        List<CodefApproval> approvals = entries.stream().map(CodefApprovalClient::toApproval).flatMap(Optional::stream)
            .toList();
        return new ApprovalListResult(true, entries.size(), resultCode, approvals);
    }

    private static List<JsonNode> entriesOf(JsonNode data) {
        if (data.isArray()) {
            List<JsonNode> entries = new ArrayList<>();
            data.forEach(entries::add);
            return entries;
        }
        return data.isObject() && data.size() > 0 ? List.of(data) : List.of();
    }

    private static Optional<CodefApproval> toApproval(JsonNode entry) {
        Optional<LocalDate> usedDate = parseDate(text(entry, FIELD_USED_DATE));
        Optional<Long> amount = parseAmount(text(entry, FIELD_USED_AMOUNT));
        if (usedDate.isEmpty() || amount.isEmpty()) {
            return Optional.empty();
        }
        String cancelStatus = text(entry, FIELD_CANCEL_STATUS);
        long amountValue = amount.get();
        // 취소 금액이 따로 오면 그 값을, 없는데 전체 취소 표시면 승인 금액 전체를 취소로 본다.
        long canceled = parseAmount(text(entry, FIELD_CANCEL_AMOUNT)).orElse(0L);
        if (canceled == 0 && CANCEL_FULL.equals(cancelStatus)) {
            canceled = amountValue;
        }
        return Optional.of(new CodefApproval(
            text(entry, FIELD_APPROVAL_NO),
            usedDate.get(),
            parseTime(text(entry, FIELD_USED_TIME)).orElse(null),
            text(entry, FIELD_MERCHANT_NAME),
            amountValue,
            canceled,
            parseInstallment(text(entry, FIELD_INSTALLMENT_MONTH)),
            text(entry, FIELD_MERCHANT_TYPE)));
    }

    private static String text(JsonNode entry, String field) {
        return entry.path(field).asString("").strip();
    }

    private static Optional<LocalDate> parseDate(String value) {
        try {
            return Optional.of(LocalDate.parse(value, DATE));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** 시각이 없거나 형식이 다르면 비어 있다. (일자만 쓴다.) */
    private static Optional<LocalTime> parseTime(String value) {
        try {
            return Optional.of(LocalTime.parse(value, TIME));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** 쉼표와 소수점 이하(원 단위 미만)를 허용한다. 숫자가 아니거나 음수이면 비어 있다. */
    private static Optional<Long> parseAmount(String value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            BigDecimal amount = new BigDecimal(value.replace(",", ""));
            return amount.signum() < 0 ? Optional.empty() : Optional.of(amount.longValueExact());
        } catch (NumberFormatException | ArithmeticException e) {
            return Optional.empty();
        }
    }

    /** 일시불이거나 읽을 수 없으면 0이다. */
    private static int parseInstallment(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 승인내역 조회 결과.
     *
     * @param approvalCount 응답에 들어 있던 승인내역 수. 읽어내지 못한 항목도 센다.
     * @param code          CODEF 결과 코드(CF-xxxxx). 성공이면 CF-00000
     * @param approvals     거래로 읽어낸 목록. 실패하면 비어 있다.
     */
    public record ApprovalListResult(boolean success, int approvalCount, String code, List<CodefApproval> approvals) {

        public static ApprovalListResult failure(String code) {
            return new ApprovalListResult(false, 0, code, List.of());
        }

    }

    /**
     * CODEF가 알려준 승인내역 한 건. 가맹점명과 금액은 소비 정보라서 {@code toString}에서 가린다.
     *
     * @param approvalNo      승인번호. 없으면 빈 문자열
     * @param usedDate        이용 일자
     * @param usedTime        이용 시각. 없으면 null
     * @param amount          승인 금액(원). 0 이상
     * @param canceledAmount  취소·환불된 금액(원). 취소가 없으면 0
     * @param installmentMonths 할부 개월. 일시불이면 0
     * @param merchantType    카드사가 준 가맹점 업종 구분. 없으면 빈 문자열
     */
    public record CodefApproval(String approvalNo, LocalDate usedDate, LocalTime usedTime, String merchantName,
                                long amount, long canceledAmount, int installmentMonths, String merchantType) {

        @Override
        public String toString() {
            return "CodefApproval[****]";
        }

    }

}
