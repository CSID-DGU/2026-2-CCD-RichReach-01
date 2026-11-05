package com.richreach.codef.service;

import com.richreach.auth.config.TermsProperties;
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
import com.richreach.global.util.Times;
import com.richreach.user.entity.TermType;
import com.richreach.user.entity.User;
import com.richreach.user.entity.UserTermAgreement;
import com.richreach.user.repository.UserTermAgreementRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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
 * <p>CODEF 호출과 실패 처리는 {@link CardCompanyAuthenticator}가, 같은 사용자의 요청이 겹치지 않게 하는 잠금은
 * {@link ConnectionUserLock}이 맡는다. 이 때문에 CODEF를 부르는 동안 트랜잭션이 열려 있다.
 * 요청이 사용자별로 순차이고 규모가 작아서 감수한다.
 *
 * <p>카드사 아이디와 비밀번호는 CODEF에 전달하는 데만 쓰고 저장하거나 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ConnectionCreateService {

    private final ConnectionUserLock userLock;
    private final UserTermAgreementRepository agreements;
    private final CodefConnectionRepository connections;
    private final CodefOrgAccountRepository orgAccounts;
    private final CardCompanyAuthenticator authenticator;
    private final CodefProperties codefProperties;
    private final TermsProperties terms;
    private final Clock clock;

    @Transactional
    public ConnectionResponse create(long userId, ConnectionCreateRequest request) {
        CardCompany company = request.cardCompany();
        if (!company.supported()) {
            throw new BusinessException(ErrorCode.CARD_COMPANY_NOT_SUPPORTED);
        }

        User user = userLock.lock(userId);
        Map<TermType, LocalDateTime> agreedAt = requireAgreements(userId);

        Optional<CodefConnection> connection = connections.findByUserId(userId);
        Optional<CodefOrgAccount> account = connection.flatMap(c -> orgAccounts
            .findByConnectionIdAndOrganizationAndStatusNot(c.getId(), company.organizationCode(),
                CodefOrgAccount.STATUS_DELETED));

        AccountResult result = authenticate(connection, account.isPresent(), request);

        LocalDateTime now = Times.nowSeconds(clock);
        CodefConnection saved = connection.orElseGet(() -> connections.save(newConnection(user, result, agreedAt, now)));
        CodefOrgAccount orgAccount = account.orElseGet(
            () -> CodefOrgAccount.connected(saved, company.organizationCode(), now));
        orgAccount.markConnected();
        orgAccounts.save(orgAccount);

        return new ConnectionResponse(orgAccount.getId(), company, ConnectionResponse.CONNECTED);
    }

    /** 연동이 없으면 새로 만들고, 있으면 카드사를 더하거나(처음 연결하는 카드사) 로그인 정보를 갱신한다(이미 있는 카드사). */
    private AccountResult authenticate(Optional<CodefConnection> connection, boolean accountExists,
                                       ConnectionCreateRequest request) {
        CardCompany company = request.cardCompany();
        if (connection.isEmpty()) {
            return authenticator.create(company, request.loginId(), request.password());
        }
        String connectedId = connection.get().getConnectedId();
        return accountExists
            ? authenticator.update(connectedId, company, request.loginId(), request.password())
            : authenticator.add(connectedId, company, request.loginId(), request.password());
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

}
