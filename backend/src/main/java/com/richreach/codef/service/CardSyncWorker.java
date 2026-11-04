package com.richreach.codef.service;

import com.richreach.codef.client.CodefCardClient;
import com.richreach.codef.client.CodefCardClient.CardListResult;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.entity.CodefSyncJob;
import com.richreach.codef.repository.CodefSyncJobRepository;
import com.richreach.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 동기화 작업 하나(카드사 계정 하나)를 실행한다. 별도 스레드에서 호출된다.
 * CODEF를 부르는 동안에는 DB 트랜잭션을 열어 두지 않고, 결과를 기록할 때만 짧게 연다.
 * 어떤 오류가 나도 작업이 진행 중으로 남지 않도록 실패로 기록한다.
 */
@Slf4j
@Component
public class CardSyncWorker {

    private final CodefSyncJobRepository jobs;
    private final CodefCardClient cards;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public CardSyncWorker(CodefSyncJobRepository jobs, CodefCardClient cards, TransactionTemplate transaction,
                          Clock clock) {
        this.jobs = jobs;
        this.cards = cards;
        this.transaction = transaction;
        this.clock = clock;
    }

    public void run(long jobId) {
        CodefSyncJob job = jobs.findWithAccountById(jobId).orElse(null);
        if (job == null || !job.isInProgress()) {
            return;
        }
        // connectedId는 로그에 남기지 않는다. 카드사 이름 대신 작업 ID로만 추적한다.
        String connectedId = job.getOrgAccount().getConnection().getConnectedId();
        String organization = job.getOrgAccount().getOrganization();

        try {
            CardListResult result = cards.fetchCardList(connectedId, organization);
            if (result.success()) {
                record(jobId, j -> j.markFetched(result.cardCount(), result.code(), now()), true);
            } else {
                log.warn("카드 동기화 실패: jobId={}, codefCode={}", jobId, result.code());
                record(jobId, j -> j.markFailed(result.code(), true, now()), false);
            }
        } catch (BusinessException e) {
            log.warn("카드 동기화 중 CODEF 호출 실패: jobId={}, error={}", jobId, e.getErrorCode().getCode());
            record(jobId, j -> j.markFailed(SyncFailure.RESULT_UNAVAILABLE, true, now()), false);
        } catch (RuntimeException e) {
            // 예상하지 못한 오류. 메시지에 외부 응답이 섞일 수 있어서 종류만 남긴다.
            log.error("카드 동기화 중 예상하지 못한 오류: jobId={}, type={}", jobId, e.getClass().getSimpleName());
            record(jobId, j -> j.markFailed(SyncFailure.RESULT_ERROR, true, now()), false);
        }
    }

    /** 실행기가 작업을 받지 못했을 때(대기열이 가득 참) 진행 중으로 남기지 않고 실패로 기록한다. */
    public void markRejected(long jobId) {
        record(jobId, job -> job.markFailed(SyncFailure.RESULT_ERROR, true, now()), false);
    }

    private void record(long jobId, Consumer<CodefSyncJob> update, boolean synced) {
        transaction.executeWithoutResult(status -> jobs.findById(jobId).ifPresent(job -> {
            update.accept(job);
            CodefOrgAccount account = job.getOrgAccount();
            if (synced) {
                account.markSynced(now());
            } else {
                account.recordSyncError(job.getResultCode());
            }
        }));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

}
