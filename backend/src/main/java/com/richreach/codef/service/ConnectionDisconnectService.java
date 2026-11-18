package com.richreach.codef.service;

import com.richreach.card.service.OwnedCardService;
import com.richreach.codef.config.CodefSyncProperties;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.codef.repository.CodefSyncJobRepository;
import com.richreach.global.event.TransactionsChangedEvent;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.util.Times;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드사 연결 하나를 해제한다. 순서가 중요하다.
 *
 * <ol>
 *   <li>CODEF에서 그 카드사 계정을 지운다. 지우지 못하면(CODEF 장애 등) {@code CONN-005}로 끝내고 이쪽 데이터는 그대로 둔다.
 *       CODEF에 로그인 정보가 남은 채 연결이 해제된 것처럼 보이는 일이 없게 하려는 것이다. 사용자는 다시 해제를 시도하면 된다.</li>
 *   <li>그 카드사에서 수집한 <b>카드와 거래, 플랜 배분을 지운다.</b> (다시 연결했을 때 같은 카드와 거래가 겹쳐 쌓이지 않는다)
 *       이미 만든 월간 플랜 자체와 변경 기록은 남는다.</li>
 *   <li>계정은 지우지 않고 해제 상태({@code DELETED})로 표시한다. (동기화 작업 기록이 참조한다) 같은 카드사를 다시 연결하면
 *       새 계정이 만들어진다.</li>
 *   <li>이번 달 플랜의 남은 기간을 다시 계산하도록 알린다. (커밋된 뒤에 처리된다) 분석 대상 카드가 하나도 남지 않으면
 *       재계산은 실패로 기록되고, 이전 플랜이 그대로 남는다.</li>
 * </ol>
 * 없거나 이미 해제했거나 다른 사용자의 계정은 {@code CONN-001}로 같게 응답한다. 동기화가 진행 중이면 {@code CONN-007}이다.
 * 계정이 잠긴 상태여도 해제할 수 있다. 카드사 아이디와 비밀번호는 다루지 않고, 로그에는 카드사만 남긴다.
 *
 * <p>CODEF를 부르는 동안 트랜잭션이 열려 있다. 연결 생성과 같은 이유로 감수한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionDisconnectService {

    private final ConnectionUserLock userLock;
    private final CodefOrgAccountRepository orgAccounts;
    private final CodefSyncJobRepository jobs;
    private final CardCompanyAuthenticator authenticator;
    private final OwnedCardService ownedCards;
    private final CodefSyncProperties syncProperties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void disconnect(long userId, long accountId) {
        userLock.lock(userId);
        CodefOrgAccount account = orgAccounts.findActiveByUserIdAndIdIn(userId, List.of(accountId)).stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.CONNECTION_NOT_FOUND));
        CardCompany company = CardCompany.fromOrganizationCode(account.getOrganization());
        if (company == null || !company.supported()) {
            throw new BusinessException(ErrorCode.CARD_COMPANY_NOT_SUPPORTED);
        }

        // 같은 connectedId를 함께 쓰는 동기화가 진행 중이면 막는다. (동기화를 시작할 때와 같은 기준이다)
        LocalDateTime now = Times.nowSeconds(clock);
        if (jobs.existsRunningSince(userId, now.minus(syncProperties.timeout()))) {
            throw new BusinessException(ErrorCode.SYNC_ALREADY_RUNNING);
        }

        authenticator.disconnect(account.getConnection().getConnectedId(), company);

        int deleted = ownedCards.deleteAllOfAccount(userId, account.getId());
        account.disconnect();
        orgAccounts.save(account);
        log.info("카드사 연결 해제: company={}, cards={}", company, deleted);

        events.publishEvent(new TransactionsChangedEvent(userId, YearMonth.now(clock),
            TransactionsChangedEvent.Reason.CARD_DISCONNECTED));
    }

}
