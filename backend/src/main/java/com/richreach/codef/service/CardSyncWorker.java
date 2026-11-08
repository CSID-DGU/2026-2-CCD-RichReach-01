package com.richreach.codef.service;

import com.richreach.card.dto.CollectedCard;
import com.richreach.card.service.OwnedCardService;
import com.richreach.codef.client.CodefCardClient;
import com.richreach.codef.client.CodefCardClient.CardListResult;
import com.richreach.codef.config.CodefSyncProperties;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.entity.CodefSyncJob;
import com.richreach.codef.repository.CodefSyncJobRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.util.Times;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 동기화 작업 하나(카드사 계정 하나)를 실행한다. 별도 스레드에서 호출된다.
 * CODEF를 부르는 동안에는 DB 트랜잭션을 열어 두지 않고, 결과를 기록할 때만 짧게 연다.
 * 작업을 읽는 단계부터 어떤 오류가 나도 작업이 진행 중으로 남지 않도록 실패로 기록한다.
 *
 * <p>카드 목록을 성공적으로 읽으면 작업 결과를 기록하는 같은 트랜잭션에서 보유카드로 저장한다.
 * 다음 경우에는 <b>보유카드를 건드리지 않는다.</b>
 * <ul>
 *   <li>응답에 있는 카드 수와 읽어낸 카드 수가 다르면 응답 형식이 예상과 다른 것이다. 저장하면 카드가 잘못 사라질 수
 *       있어서 저장하지 않고 동기화를 실패로 기록한다.</li>
 *   <li>카드가 0장이면 정말 0장인지 응답 형식이 어긋난 것인지 구분할 수 없어서 기존 카드를 그대로 둔다.</li>
 * </ul>
 *
 * <p>제한 시간({@code codef.sync.timeout})이 지난 작업은 상태 조회에서 이미 실패로 보고되므로, 늦게 끝난 결과는
 * 반영하지 않는다. 반영하면 보고된 상태가 실패에서 성공으로 뒤집히고, 뒤에 시작한 동기화의 기록을 덮어쓸 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardSyncWorker {

    private final CodefSyncJobRepository jobs;
    private final CodefCardClient cards;
    private final OwnedCardService ownedCards;
    private final TransactionTemplate transaction;
    private final CodefSyncProperties properties;
    private final Clock clock;

    public void run(long jobId) {
        Consumer<CodefSyncJob> update;
        boolean synced = false;
        CardsToSave toSave = null;
        try {
            // 작업을 읽는 일과 connectedId 복호화도 try 안에서 한다. 여기서 실패해도 작업이 진행 중으로 남지 않게 하려는 것이다.
            CodefSyncJob job = jobs.findWithAccountById(jobId).orElse(null);
            if (job == null || !job.isInProgress()) {
                return;
            }
            // connectedId는 로그에 남기지 않는다. 카드사 이름 대신 작업 ID로만 추적한다.
            CodefOrgAccount account = job.getOrgAccount();
            String connectedId = account.getConnection().getConnectedId();

            CardListResult result = cards.fetchCardList(connectedId, account.getOrganization());
            if (!result.success()) {
                log.warn("카드 동기화 실패: jobId={}, codefCode={}", jobId, result.code());
                update = j -> j.markFailed(result.code(), CodefFailures.isRetryable(result.code()), now());
            } else if (result.cards().size() != result.cardCount()) {
                // 카드 이름이나 개수를 보면 안 되므로 숫자만 남긴다.
                log.warn("카드 응답 형식이 예상과 다릅니다: jobId={}, 응답={}장, 읽은 카드={}장", jobId, result.cardCount(),
                    result.cards().size());
                update = j -> j.markFailed(SyncFailure.RESULT_ERROR, true, now());
            } else {
                update = j -> j.markFetched(result.cardCount(), result.code(), now());
                synced = true;
                if (!result.cards().isEmpty()) {
                    toSave = new CardsToSave(account.getConnection().getUser().getId(), account.getId(),
                        CardCompany.fromOrganizationCode(account.getOrganization()), result.cards().stream()
                            .map(card -> new CollectedCard(card.name(), card.number(), card.type())).toList());
                }
            }
        } catch (BusinessException e) {
            log.warn("카드 동기화 중 CODEF 호출 실패: jobId={}, error={}", jobId, e.getErrorCode().getCode());
            update = j -> j.markFailed(SyncFailure.RESULT_UNAVAILABLE, true, now());
        } catch (RuntimeException e) {
            // 예상하지 못한 오류. 메시지에 외부 응답이 섞일 수 있어서 종류만 남긴다.
            log.error("카드 동기화 중 예상하지 못한 오류: jobId={}, type={}", jobId, e.getClass().getSimpleName());
            update = j -> j.markFailed(SyncFailure.RESULT_ERROR, true, now());
        }

        try {
            record(jobId, update, synced, toSave);
        } catch (RuntimeException e) {
            // 보유카드를 저장하다 실패하면 같은 트랜잭션의 작업 결과도 함께 되돌려진다. 진행 중으로 남지 않게 실패로 다시 기록한다.
            log.error("카드 동기화 결과를 저장하지 못했습니다: jobId={}, type={}", jobId, e.getClass().getSimpleName());
            record(jobId, j -> j.markFailed(SyncFailure.RESULT_ERROR, true, now()), false, null);
        }
    }

    /** 실행기가 작업을 받지 못했을 때(대기열이 가득 참) 진행 중으로 남기지 않고 실패로 기록한다. */
    public void markRejected(long jobId) {
        record(jobId, job -> job.markFailed(SyncFailure.RESULT_ERROR, true, now()), false, null);
    }

    private void record(long jobId, Consumer<CodefSyncJob> update, boolean synced, CardsToSave toSave) {
        // 카드사 계정을 한 번에 읽는 조회를 써서, 결과를 기록할 때 계정을 따로 읽는 쿼리가 더 나가지 않게 한다.
        transaction.executeWithoutResult(status -> jobs.findWithAccountById(jobId).ifPresent(job -> {
            if (!job.isInProgress() || job.isTimedOut(properties.timeout(), now())) {
                log.warn("이미 끝났거나 제한 시간이 지난 작업이라 결과를 반영하지 않습니다: jobId={}", jobId);
                return;
            }
            update.accept(job);
            CodefOrgAccount account = job.getOrgAccount();
            if (synced) {
                account.markSynced(now());
            } else {
                account.recordSyncError(job.getResultCode());
            }
            if (toSave != null) {
                ownedCards.saveSynced(toSave.userId(), toSave.orgAccountId(), toSave.company(), toSave.cards());
            }
        }));
    }

    private LocalDateTime now() {
        return Times.nowSeconds(clock);
    }

    /** 성공한 동기화의 카드 목록. 작업 결과를 기록하는 트랜잭션에서 보유카드로 저장한다. */
    private record CardsToSave(long userId, long orgAccountId, CardCompany company, List<CollectedCard> cards) {

    }

}
