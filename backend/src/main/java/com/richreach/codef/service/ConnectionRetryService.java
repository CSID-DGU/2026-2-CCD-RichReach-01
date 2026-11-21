package com.richreach.codef.service;

import com.richreach.codef.config.CodefSyncProperties;
import com.richreach.codef.dto.ConnectionResponse;
import com.richreach.codef.dto.ConnectionRetryRequest;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.entity.CodefSyncJob;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.codef.repository.CodefSyncJobRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.util.Times;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연결에 실패한 카드사를 다시 연결한다. 카드사 아이디와 비밀번호를 다시 받아 CODEF의 로그인 정보를 갱신하고(update),
 * 성공하면 연결된 상태로 되돌린다. 데이터를 다시 불러오는 일은 하지 않는다. (성공한 뒤 동기화를 다시 시작한다.)
 *
 * <p>다시 연결할 수 있는 카드사는 가장 최근 동기화가 실패했거나(시간 초과 포함) 계정 상태가 연결 실패인 경우다.
 * 이미 연결과 동기화에 성공한 카드사, 없는 카드사, 다른 사용자의 카드사는 {@code CONN-001}로 같게 응답해
 * 다른 사용자의 계정이 있는지 알려주지 않는다. 동기화가 진행 중이면 {@code CONN-007}, 계정이 잠겼거나 잠길 위험이 있는
 * 경우에는 {@code CONN-008}로 막는다. 잘못된 비밀번호로 다시 시도하면 카드사 계정이 잠길 수 있어서다. 계정이 일시 제한되어
 * 다시 시도하면 안 되는 실패는 {@code codef.sync.retry-block}(기본 1시간) 동안만 막고, 그 뒤에는 다시 연결할 수 있다.
 *
 * <p>CODEF 호출과 실패 처리는 {@link CardCompanyAuthenticator}가, 요청이 겹치지 않게 하는 잠금은
 * {@link ConnectionUserLock}이 맡는다. 카드사 아이디와 비밀번호는 CODEF에 전달하는 데만 쓰고 저장하거나 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ConnectionRetryService {

    private final ConnectionUserLock userLock;
    private final CodefOrgAccountRepository orgAccounts;
    private final CodefSyncJobRepository jobs;
    private final CardCompanyAuthenticator authenticator;
    private final CodefSyncProperties syncProperties;
    private final Clock clock;

    @Transactional
    public ConnectionResponse retry(long userId, long accountId, ConnectionRetryRequest request) {
        userLock.lock(userId);
        CodefOrgAccount account = orgAccounts.findActiveByUserIdAndIdIn(userId, List.of(accountId)).stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.CONNECTION_NOT_FOUND));
        CardCompany company = CardCompany.fromOrganizationCode(account.getOrganization());
        if (company == null || !company.supported()) {
            throw new BusinessException(ErrorCode.CARD_COMPANY_NOT_SUPPORTED);
        }
        requireRetryable(userId, account);

        authenticator.update(account.getConnection().getConnectedId(), company, request.loginId(), request.password());

        account.markConnected();
        orgAccounts.save(account);
        return new ConnectionResponse(account.getId(), company, ConnectionResponse.CONNECTED);
    }

    private void requireRetryable(long userId, CodefOrgAccount account) {
        Duration timeout = syncProperties.timeout();
        LocalDateTime now = Times.nowSeconds(clock);

        // 이 카드사만이 아니라 같은 사용자의 어떤 카드사든 동기화가 진행 중이면 막는다. (같은 connectedId를 함께 쓰므로,
        // 동기화를 시작할 때와 같은 기준이다.)
        if (jobs.existsRunningSince(userId, now.minus(timeout))) {
            throw new BusinessException(ErrorCode.SYNC_ALREADY_RUNNING);
        }
        CodefSyncJob latest = jobs.findFirstByOrgAccountIdAndApiTypeOrderByIdDesc(account.getId(),
            CodefSyncJob.API_CARD_LIST).orElse(null);
        boolean syncFailed = latest != null && latest.hasFailed(timeout, now);

        // 다시 시도하면 안 되는 실패는 제한 시간(codef.sync.retry-block) 동안만 막는다. 그 뒤에는 다시 연결할 수 있다.
        if (account.isLocked() || (latest != null && latest.blocksRetry(syncProperties.retryBlock(), now))) {
            throw new BusinessException(ErrorCode.CONNECTION_RETRY_BLOCKED);
        }
        if (account.isConnected() && !syncFailed) {
            // 이미 연결과 동기화에 성공했거나 한 번도 실패하지 않은 카드사
            throw new BusinessException(ErrorCode.CONNECTION_NOT_FOUND);
        }
    }

}
