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
 * 카드사 연결 하나를 해제한다. 순서가 중요하다. <b>되돌릴 수 없는 CODEF 호출을 마지막 단계로 두어서</b>, 그 앞의 이쪽
 * 변경이 실패하면 CODEF는 건드리지 않고, CODEF가 실패하면 이쪽 변경을 모두 되돌린다.
 *
 * <ol>
 *   <li>그 카드사에서 수집한 <b>카드와 거래, 플랜 배분을 지운다.</b> (다시 연결했을 때 같은 카드와 거래가 겹쳐 쌓이지 않는다)
 *       이미 만든 월간 플랜 자체와 변경 기록은 남는다.</li>
 *   <li>계정은 지우지 않고 해제 상태({@code DELETED})로 표시한다. (동기화 작업 기록이 참조한다) 같은 카드사를 다시 연결하면
 *       새 계정이 만들어진다. 이 시점에 DB에 반영해서(flush), DB 쪽 오류가 CODEF를 부르기 전에 드러나게 한다.</li>
 *   <li>CODEF에서 그 카드사 계정을 지운다. 지우지 못하면(CODEF 장애 등) {@code CONN-005}로 끝내고, 트랜잭션이 위의 변경을
 *       모두 되돌려서 데이터는 그대로 남는다. CODEF에 로그인 정보가 남은 채 연결이 해제된 것처럼 보이는 일이 없다. 사용자는
 *       다시 해제를 시도하면 된다.</li>
 *   <li>이번 달 플랜의 남은 기간을 다시 계산하도록 알린다. (커밋된 뒤에 처리된다) 분석 대상 카드가 하나도 남지 않으면
 *       재계산은 실패로 기록되고, 이전 플랜이 그대로 남는다.</li>
 * </ol>
 * <b>남은 위험</b>: CODEF 삭제에 성공한 직후 커밋이 실패하면 CODEF에서는 지워졌는데 이쪽은 연결 상태로 남는다. 이때 다시 해제하면
 * CODEF가 이미 삭제된 계정에 어떤 응답을 주는지에 따라 실패할 수 있다. 그 응답은 아직 확인하지 못해서, 확인한 뒤 "이미 삭제됨"을
 * 성공으로 처리하는 것을 후속으로 한다.
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

        int deleted = ownedCards.deleteAllOfAccount(userId, account.getId());
        account.disconnect();
        // 지금 DB에 반영해서, DB 쪽 오류가 있으면 되돌릴 수 없는 CODEF 호출 전에 드러나게 한다.
        orgAccounts.saveAndFlush(account);

        // 마지막 단계. 실패하면 예외가 트랜잭션을 되돌려 위의 삭제와 해제 표시가 모두 취소된다.
        authenticator.disconnect(account.getConnection().getConnectedId(), company);
        log.info("카드사 연결 해제: company={}, cards={}", company, deleted);

        events.publishEvent(new TransactionsChangedEvent(userId, YearMonth.now(clock),
            TransactionsChangedEvent.Reason.CARD_DISCONNECTED));
    }

}
