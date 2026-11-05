package com.richreach.codef.service;

import com.richreach.codef.client.CodefAccountClient;
import com.richreach.codef.client.CodefAccountClient.AccountResult;
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
import com.richreach.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연결에 실패한 카드사를 다시 연결한다. 카드사 아이디와 비밀번호를 다시 받아 CODEF의 로그인 정보를 갱신하고(update),
 * 성공하면 연결된 상태로 되돌린다. 데이터를 다시 불러오는 일은 하지 않는다. (성공한 뒤 동기화를 다시 시작한다.)
 *
 * <p>다시 연결할 수 있는 카드사는 가장 최근 동기화가 실패했거나(시간 초과 포함) 계정 상태가 연결 실패인 경우다.
 * 이미 연결과 동기화에 성공한 카드사, 없는 카드사, 다른 사용자의 카드사는 {@code CONN-001}로 같게 응답해
 * 다른 사용자의 계정이 있는지 알려주지 않는다. 동기화가 진행 중이면 {@code CONN-007}, 계정이 잠겼거나 잠길 위험이 있는
 * 경우에는 {@code CONN-008}로 막는다. 잘못된 비밀번호로 다시 시도하면 카드사 계정이 잠길 수 있어서다.
 *
 * <p>카드사 아이디와 비밀번호는 CODEF에 전달하는 데만 쓰고 저장하거나 로그에 남기지 않는다.
 * 같은 사용자의 요청이 겹치지 않도록 사용자 행을 잠가 차례대로 처리한다. (연결 생성과 같은 방식이다.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionRetryService {

    private final UserRepository users;
    private final CodefOrgAccountRepository orgAccounts;
    private final CodefSyncJobRepository jobs;
    private final CodefAccountClient codef;
    private final CodefSyncProperties syncProperties;
    private final Clock clock;

    @Transactional
    public ConnectionResponse retry(long userId, long accountId, ConnectionRetryRequest request) {
        users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        CodefOrgAccount account = orgAccounts.findActiveByUserIdAndIdIn(userId, List.of(accountId)).stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.CONNECTION_NOT_FOUND));
        CardCompany company = CardCompany.fromOrganizationCode(account.getOrganization());
        if (company == null || !company.supported()) {
            throw new BusinessException(ErrorCode.CARD_COMPANY_NOT_SUPPORTED);
        }
        requireRetryable(account);

        AccountResult result;
        try {
            result = codef.update(account.getConnection().getConnectedId(), company, request.loginId(),
                request.password());
        } catch (BusinessException e) {
            throw CodefFailures.translate(e);
        }
        if (!result.success()) {
            // 로그에는 카드사와 CODEF 오류 코드만 남긴다.
            log.warn("카드사 재연결 실패: company={}, codefCode={}", company, result.errorCode());
            throw CodefFailures.authFailure(result.errorCode());
        }

        account.markConnected();
        orgAccounts.save(account);
        return new ConnectionResponse(account.getId(), company, ConnectionResponse.CONNECTED);
    }

    private void requireRetryable(CodefOrgAccount account) {
        CodefSyncJob latest = jobs.findFirstByOrgAccountIdAndApiTypeOrderByIdDesc(account.getId(),
            CodefSyncJob.API_CARD_LIST).orElse(null);
        Duration timeout = syncProperties.timeout();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        if (latest != null && latest.isInProgress() && !latest.isTimedOut(timeout, now)) {
            throw new BusinessException(ErrorCode.SYNC_ALREADY_RUNNING);
        }
        boolean syncFailed = latest != null && (latest.isFailed() || latest.isTimedOut(timeout, now));

        if (account.isLocked() || (syncFailed && !latest.isRetryable())) {
            throw new BusinessException(ErrorCode.CONNECTION_RETRY_BLOCKED);
        }
        if (account.isConnected() && !syncFailed) {
            // 이미 연결과 동기화에 성공했거나 한 번도 실패하지 않은 카드사
            throw new BusinessException(ErrorCode.CONNECTION_NOT_FOUND);
        }
    }

}
