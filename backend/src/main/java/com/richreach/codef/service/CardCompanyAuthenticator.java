package com.richreach.codef.service;

import com.richreach.codef.client.CodefAccountClient;
import com.richreach.codef.client.CodefAccountClient.AccountResult;
import com.richreach.codef.entity.CardCompany;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 카드사 계정을 CODEF에 등록하거나 갱신하고, 실패를 사용자에게 보여줄 오류로 바꾼다.
 * 카드사 연결 생성과 재시도가 같은 호출·오류 처리를 쓰도록 한 곳에 둔다.
 *
 * <p>성공하면 결과를 돌려주고, 실패하면 항상 예외를 던진다. 호출한 쪽은 성공만 다루면 된다.
 * <ul>
 *   <li>CODEF에 연결하지 못함(외부 API 오류): {@code CONN-005}</li>
 *   <li>카드사 인증 실패: {@code CONN-003}, CODEF 서버 오류 코드: {@code CONN-005}</li>
 * </ul>
 * 카드사 아이디와 비밀번호는 CODEF에 전달하는 데만 쓰고, 로그에는 카드사와 CODEF 오류 코드만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CardCompanyAuthenticator {

    private final CodefAccountClient codef;

    /** 처음 연결하는 사용자. 성공하면 새 connectedId가 들어 있다. */
    AccountResult create(CardCompany company, String loginId, String password) {
        return authenticate(company, () -> requireConnectedId(codef.create(company, loginId, password)));
    }

    /** connectedId가 있는 사용자가 다른 카드사를 더한다. */
    AccountResult add(String connectedId, CardCompany company, String loginId, String password) {
        return authenticate(company, () -> codef.add(connectedId, company, loginId, password));
    }

    /** 이미 등록한 카드사의 로그인 정보를 다시 보낸다. (재연결, 재시도) */
    AccountResult update(String connectedId, CardCompany company, String loginId, String password) {
        return authenticate(company, () -> codef.update(connectedId, company, loginId, password));
    }

    private AccountResult authenticate(CardCompany company, Supplier<AccountResult> request) {
        AccountResult result;
        try {
            result = request.get();
        } catch (BusinessException e) {
            throw CodefFailures.translate(e);
        }
        if (!result.success()) {
            log.warn("카드사 인증 실패: company={}, codefCode={}", company, result.errorCode());
            throw CodefFailures.authFailure(result.errorCode());
        }
        return result;
    }

    /** 새 연동을 만들었는데 connectedId가 없으면 저장할 수 없으므로 연동 서버 오류로 본다. */
    private AccountResult requireConnectedId(AccountResult result) {
        if (result.success() && (result.connectedId() == null || result.connectedId().isBlank())) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
        return result;
    }

}
