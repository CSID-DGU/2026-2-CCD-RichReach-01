package com.richreach.codef.service;

import com.richreach.codef.client.CodefApiClient;
import com.richreach.codef.client.CodefRsaEncryptor;
import com.richreach.codef.dto.CodefApprovalRequest;
import com.richreach.codef.dto.CodefBillingRequest;
import com.richreach.codef.dto.CodefCardListRequest;
import com.richreach.codef.dto.CodefConnectRequest;
import com.richreach.codef.dto.CodefResultCheckRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * [임시] CODEF 카드 API를 호출해 응답 구조를 확인하기 위한 서비스. 확인이 끝나면 실제 도메인 서비스로 옮긴다.
 * 응답은 가공하지 않고 CODEF 응답 그대로 돌려준다.
 */
@Service
public class CodefTestService {

    private static final String CREATE_ACCOUNT_PATH = "/v1/account/create";
    private static final String CARD_LIST_PATH = "/v1/kr/card/p/account/card-list";
    private static final String APPROVAL_LIST_PATH = "/v1/kr/card/p/account/approval-list";
    private static final String RESULT_CHECK_PATH = "/v1/kr/card/p/account/result-check-list";
    private static final String BILLING_PATH = "/v1/kr/card/p/account/billing-list";

    private final CodefApiClient apiClient;
    private final CodefRsaEncryptor rsaEncryptor;

    public CodefTestService(CodefApiClient apiClient, CodefRsaEncryptor rsaEncryptor) {
        this.apiClient = apiClient;
        this.rsaEncryptor = rsaEncryptor;
    }

    /** 카드사 계정을 등록하고 connectedId를 발급받는다. 비밀번호는 RSA로 암호화해서 전송한다. */
    public JsonNode createConnectedId(CodefConnectRequest request) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("countryCode", "KR");
        account.put("businessType", defaultIfBlank(request.businessType(), "CD"));
        account.put("clientType", defaultIfBlank(request.clientType(), "P"));
        account.put("organization", request.organization());
        account.put("loginType", defaultIfBlank(request.loginType(), "1"));
        account.put("id", request.loginId());
        account.put("password", rsaEncryptor.encrypt(request.loginPassword()));

        return apiClient.post(CREATE_ACCOUNT_PATH, Map.of("accountList", List.of(account)));
    }

    public JsonNode cardList(CodefCardListRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectedId", request.connectedId());
        body.put("organization", request.organization());

        return apiClient.post(CARD_LIST_PATH, body);
    }

    public JsonNode approvals(CodefApprovalRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectedId", request.connectedId());
        body.put("organization", request.organization());
        body.put("startDate", request.startDate());
        body.put("endDate", request.endDate());
        body.put("orderBy", defaultIfBlank(request.orderBy(), "0"));
        body.put("inquiryType", defaultIfBlank(request.inquiryType(), "1"));
        body.put("memberStoreInfoType", defaultIfBlank(request.memberStoreInfoType(), "1"));
        putIfPresent(body, "birthDate", request.birthDate());
        putIfPresent(body, "cardName", request.cardName());
        putIfPresent(body, "cardNo", request.cardNo());
        putIfPresent(body, "duplicateCardIdx", request.duplicateCardIdx());
        if (request.cardPassword() != null && !request.cardPassword().isBlank()) {
            body.put("cardPassword", rsaEncryptor.encrypt(request.cardPassword()));
        }

        return apiClient.post(APPROVAL_LIST_PATH, body);
    }

    /** 카드 실적 및 혜택 충족 현황 조회. 값이 있는 선택 항목만 전달한다. */
    public JsonNode resultCheck(CodefResultCheckRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectedId", request.connectedId());
        body.put("organization", request.organization());
        putIfPresent(body, "startDate", request.startDate());
        putIfPresent(body, "birthDate", request.birthDate());
        putIfPresent(body, "cardNo", request.cardNo());
        if (request.cardPassword() != null && !request.cardPassword().isBlank()) {
            body.put("cardPassword", rsaEncryptor.encrypt(request.cardPassword()));
        }

        return apiClient.post(RESULT_CHECK_PATH, body);
    }

    /** 청구내역(명세서) 조회. startDate를 비우면 CODEF가 가장 최근 명세서를 돌려준다. */
    public JsonNode billing(CodefBillingRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectedId", request.connectedId());
        body.put("organization", request.organization());
        body.put("memberStoreInfoYN", defaultIfBlank(request.memberStoreInfoYN(), "0"));
        putIfPresent(body, "startDate", request.startDate());
        putIfPresent(body, "birthDate", request.birthDate());
        putIfPresent(body, "cardNo", request.cardNo());
        if (request.cardPassword() != null && !request.cardPassword().isBlank()) {
            body.put("cardPassword", rsaEncryptor.encrypt(request.cardPassword()));
        }

        return apiClient.post(BILLING_PATH, body);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void putIfPresent(Map<String, Object> body, String key, String value) {
        if (value != null && !value.isBlank()) {
            body.put(key, value);
        }
    }

}
