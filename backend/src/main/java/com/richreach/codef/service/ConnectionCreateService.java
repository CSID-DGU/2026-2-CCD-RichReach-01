package com.richreach.codef.service;

import com.richreach.auth.config.TermsProperties;
import com.richreach.codef.client.CodefAccountClient;
import com.richreach.codef.client.CodefAccountClient.AccountResult;
import com.richreach.codef.config.CodefProperties;
import com.richreach.codef.dto.ConnectionCreateRequest;
import com.richreach.codef.dto.ConnectionResponse;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.user.entity.TermType;
import com.richreach.user.entity.User;
import com.richreach.user.entity.UserTermAgreement;
import com.richreach.user.repository.UserRepository;
import com.richreach.user.repository.UserTermAgreementRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드사 한 곳을 CODEF에 연결한다. 사용자당 CODEF 연동(connectedId)은 하나이고, 카드사는 그 안에 하나씩 더해진다.
 *
 * <ul>
 *   <li>첫 연결: connectedId를 새로 만들어 암호화해서 저장하고 카드사 계정을 등록한다.</li>
 *   <li>다른 카드사: 기존 connectedId에 카드사를 더한다.</li>
 *   <li>이미 연결한 카드사: 로그인 정보를 다시 보내 재연결로 처리하고, 계정을 새로 만들지 않는다.</li>
 * </ul>
 *
 * <p>카드사 아이디와 비밀번호는 CODEF에 전달하는 데만 쓰고 저장하거나 로그에 남기지 않는다.
 *
 * <p>같은 사용자의 요청이 겹치면(버튼 중복 클릭) connectedId가 두 번 만들어질 수 있어서, 사용자 행을 잠가
 * 차례대로 처리한다. 이 때문에 CODEF를 부르는 동안 트랜잭션이 열려 있다. 요청이 사용자별로 순차이고 규모가 작아서 감수한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionCreateService {

    /** CODEF 서버 쪽 일시 오류. 사용자의 입력과 상관없으므로 인증 실패가 아니라 연동 서버 오류로 알린다. */
    private static final String CODEF_SERVER_ERROR = "CF-09999";

    private final UserRepository users;
    private final UserTermAgreementRepository agreements;
    private final CodefConnectionRepository connections;
    private final CodefOrgAccountRepository orgAccounts;
    private final CodefAccountClient codef;
    private final CodefProperties codefProperties;
    private final TermsProperties terms;
    private final Clock clock;

    @Transactional
    public ConnectionResponse create(long userId, ConnectionCreateRequest request) {
        CardCompany company = request.cardCompany();
        if (!company.supported()) {
            throw new BusinessException(ErrorCode.CARD_COMPANY_NOT_SUPPORTED);
        }

        User user = users.findByIdForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        Map<TermType, LocalDateTime> agreedAt = requireAgreements(userId);

        Optional<CodefConnection> connection = connections.findByUserId(userId);
        Optional<CodefOrgAccount> account = connection.flatMap(c -> orgAccounts
            .findByConnectionIdAndOrganizationAndStatusNot(c.getId(), company.organizationCode(),
                CodefOrgAccount.STATUS_DELETED));

        AccountResult result = register(connection, account.isPresent(), request);
        if (!result.success()) {
            // 로그에는 카드사와 CODEF 오류 코드만 남긴다.
            log.warn("카드사 연결 실패: company={}, codefCode={}", company, result.errorCode());
            throw failureOf(result.errorCode());
        }

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        CodefConnection saved = connection.orElseGet(() -> connections.save(newConnection(user, result, agreedAt, now)));
        CodefOrgAccount orgAccount = account.orElseGet(() -> new CodefOrgAccount(saved, null,
            company.organizationCode(), "CD", "P", "1", CodefOrgAccount.STATUS_ACTIVE, now));
        orgAccount.markConnected();
        orgAccounts.save(orgAccount);

        return new ConnectionResponse(orgAccount.getId(), company, ConnectionResponse.CONNECTED);
    }

    /** 연동이 없으면 새로 만들고, 있으면 카드사를 더하거나(처음 연결하는 카드사) 로그인 정보를 갱신한다(이미 있는 카드사). */
    private AccountResult register(Optional<CodefConnection> connection, boolean accountExists,
                                   ConnectionCreateRequest request) {
        CardCompany company = request.cardCompany();
        try {
            if (connection.isEmpty()) {
                return requireConnectedId(codef.create(company, request.loginId(), request.password()));
            }
            String connectedId = connection.get().getConnectedId();
            return accountExists
                ? codef.update(connectedId, company, request.loginId(), request.password())
                : codef.add(connectedId, company, request.loginId(), request.password());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.EXTERNAL_API_ERROR) {
                throw new BusinessException(ErrorCode.CODEF_UNAVAILABLE);
            }
            throw e;
        }
    }

    /** 새 연동을 만들었는데 connectedId가 없으면 저장할 수 없으므로 연동 서버 오류로 본다. */
    private AccountResult requireConnectedId(AccountResult result) {
        if (result.success() && (result.connectedId() == null || result.connectedId().isBlank())) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
        return result;
    }

    private CodefConnection newConnection(User user, AccountResult result, Map<TermType, LocalDateTime> agreedAt,
                                          LocalDateTime now) {
        return new CodefConnection(user, result.connectedId(), codefProperties.env(),
            agreedAt.get(TermType.CARD_INFO), agreedAt.get(TermType.TRANSACTION), agreedAt.get(TermType.PRIVACY), now);
    }

    /** 현재 약관 버전에 카드 연동 동의 3개가 모두 있어야 한다. 동의 시각은 연동 기록에 그대로 옮긴다. */
    private Map<TermType, LocalDateTime> requireAgreements(long userId) {
        Map<TermType, LocalDateTime> agreedAt = new HashMap<>();
        for (UserTermAgreement agreement : agreements.findByUserIdAndTermVersionAndTermTypeIn(userId,
            terms.cardConnectionVersion(),
            ConnectionAgreementService.REQUIRED.stream().map(Enum::name).collect(Collectors.toSet()))) {
            agreedAt.put(TermType.valueOf(agreement.getTermType()), agreement.getAgreedAt());
        }
        if (!agreedAt.keySet().containsAll(ConnectionAgreementService.REQUIRED)) {
            throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
        }
        return agreedAt;
    }

    /**
     * CODEF 실패 코드를 사용자에게 보여줄 오류로 바꾼다. CODEF 서버 오류만 연동 서버 오류로 보고, 나머지는 인증 실패로 본다.
     * 추가 인증(CF-03002)과 계정 잠김처럼 따로 안내해야 하는 코드는 샌드박스 응답을 확인한 뒤 나눈다.
     */
    private BusinessException failureOf(String codefCode) {
        if (CODEF_SERVER_ERROR.equals(codefCode)) {
            return new BusinessException(ErrorCode.CODEF_UNAVAILABLE);
        }
        return new BusinessException(ErrorCode.CARD_COMPANY_AUTH_FAILED);
    }

}
