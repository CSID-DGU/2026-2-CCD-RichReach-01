package com.richreach.codef.client;

import com.richreach.codef.entity.CardCompany;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * CODEF 계정 등록 API 호출. 카드사 계정을 처음 등록하면 connectedId가 만들어지고(create),
 * 이미 connectedId가 있으면 카드사를 더하거나(add) 로그인 정보를 바꾼다(update).
 *
 * <p>카드사 비밀번호는 CODEF 공개키로 암호화해서 보내고, 아이디와 비밀번호는 어디에도 저장하거나 로그에 남기지 않는다.
 * ({@link CodefApiClient}도 요청 본문은 로그에 남기지 않는다.)
 *
 * <p>계정 등록(create)은 CODEF 데모 환경의 실제 응답으로 확인했다(2026-11, 신한카드). 성공이면 {@code result.code}가
 * {@code CF-00000}이고 {@code data.connectedId}에 연동 ID가, {@code data.successList}에 카드사별 결과가 들어 있다.
 * 실패하면 {@code result.code}는 {@code CF-04000}(계정 등록 실패)으로 오고, 구체적인 이유는
 * {@code data.errorList[].code}에 있다. (예: {@code CF-04020}은 비밀번호 복호화 실패, 공개키 설정 문제)
 * 그 밖의 실패 코드(잘못된 비밀번호, 계정 잠김, 추가 인증)는 아직 확인하지 못했다.
 */
@Component
@RequiredArgsConstructor
public class CodefAccountClient {

    static final String CREATE_PATH = "/v1/account/create";
    static final String ADD_PATH = "/v1/account/add";
    static final String UPDATE_PATH = "/v1/account/update";

    private static final String SUCCESS_CODE = "CF-00000";

    private final CodefApiClient apiClient;
    private final CodefRsaEncryptor encryptor;

    /** 처음 연결하는 사용자. 성공하면 새 connectedId를 돌려준다. */
    public AccountResult create(CardCompany company, String loginId, String password) {
        return call(CREATE_PATH, null, company, loginId, password);
    }

    /** connectedId가 이미 있는 사용자가 다른 카드사를 더한다. */
    public AccountResult add(String connectedId, CardCompany company, String loginId, String password) {
        return call(ADD_PATH, connectedId, company, loginId, password);
    }

    /** 이미 등록한 카드사의 로그인 정보를 다시 보낸다. (실패했던 카드사를 다시 연결할 때) */
    public AccountResult update(String connectedId, CardCompany company, String loginId, String password) {
        return call(UPDATE_PATH, connectedId, company, loginId, password);
    }

    private AccountResult call(String path, String connectedId, CardCompany company, String loginId,
                               String password) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("countryCode", "KR");
        account.put("businessType", "CD");
        account.put("clientType", "P");
        account.put("organization", company.organizationCode());
        account.put("loginType", "1");
        account.put("id", loginId);
        account.put("password", encryptor.encrypt(password));

        Map<String, Object> body = new LinkedHashMap<>();
        if (connectedId != null) {
            body.put("connectedId", connectedId);
        }
        body.put("accountList", List.of(account));

        return parse(apiClient.post(path, body));
    }

    /**
     * 결과 코드가 CF-00000이고 실패 목록(errorList)이 비어 있어야 성공이다.
     * 실패하면 실패 목록의 첫 오류 코드를, 없으면 결과 코드를 담는다.
     */
    static AccountResult parse(JsonNode response) {
        String resultCode = response.path("result").path("code").asString("");
        JsonNode data = response.path("data");
        JsonNode errors = data.path("errorList");
        boolean hasErrors = errors.isArray() && errors.size() > 0;

        if (SUCCESS_CODE.equals(resultCode) && !hasErrors) {
            return AccountResult.success(data.path("connectedId").asString(""));
        }
        String errorCode = hasErrors ? errors.get(0).path("code").asString(resultCode) : resultCode;
        return AccountResult.failure(errorCode);
    }

    /**
     * 계정 등록 결과.
     *
     * @param connectedId 성공했을 때 CODEF가 돌려준 값. 없을 수 있다. (민감정보라서 {@code toString}에서 가린다.)
     * @param errorCode   실패했을 때 CODEF 오류 코드(CF-xxxxx)
     */
    public record AccountResult(boolean success, String connectedId, String errorCode) {

        static AccountResult success(String connectedId) {
            return new AccountResult(true, connectedId, null);
        }

        static AccountResult failure(String errorCode) {
            return new AccountResult(false, null, errorCode);
        }

        @Override
        public String toString() {
            return "AccountResult[success=" + success + ", connectedId=" + (connectedId == null ? null : "****")
                + ", errorCode=" + errorCode + "]";
        }

    }

}
