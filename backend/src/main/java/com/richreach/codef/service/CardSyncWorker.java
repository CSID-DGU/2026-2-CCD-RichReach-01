package com.richreach.codef.service;

import com.richreach.card.dto.CollectedCard;
import com.richreach.card.service.OwnedCardService;
import com.richreach.codef.client.CodefCardClient;
import com.richreach.codef.client.CodefCardClient.CardListResult;
import com.richreach.codef.config.CodefProperties;
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
 * <p>카드 목록을 성공적으로 읽으면 보유카드로 먼저 저장하고, 이어서 승인내역을 수집한 뒤에 작업 결과를 기록한다.
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

    /** 마지막 호출이 끝난 뒤 저장과 결과 기록에 쓰려고 남겨 두는 여유. */
    private static final java.time.Duration COLLECT_MARGIN = java.time.Duration.ofSeconds(15);

    private final CodefSyncJobRepository jobs;
    private final ConnectionUserLock userLock;
    private final CodefCardClient cards;
    private final OwnedCardService ownedCards;
    private final ApprovalCollector collector;
    private final TransactionTemplate transaction;
    private final CodefSyncProperties properties;
    private final CodefProperties codefProperties;
    private final Clock clock;

    public void run(long jobId) {
        Consumer<CodefSyncJob> update;
        boolean synced = false;
        CardsToSave toSave = null;
        CollectTarget target = null;
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
                    target = new CollectTarget(CardCompany.fromOrganizationCode(account.getOrganization()), connectedId,
                        account.getOrganization(), previousRange(account), collectDeadline(job));
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
            ApprovalCollector.Result collection = null;
            if (toSave != null) {
                // 카드를 먼저 저장해야 승인내역을 카드에 짝지을 수 있다. 작업은 수집이 끝날 때까지 진행 중으로 둔다.
                if (!saveCards(jobId, toSave)) {
                    return;
                }
                collection = collector.collect(toSave.userId(), target.company(), target.connectedId(),
                    target.organization(), target.previous(), target.deadline());
            }
            record(jobId, update, synced, collection);
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

    /** 보유카드를 저장한다. 이미 끝났거나 제한 시간이 지난 작업이면 저장하지 않고 false를 돌려준다. */
    private boolean saveCards(long jobId, CardsToSave toSave) {
        Boolean saved = transaction.execute(status -> jobs.findWithAccountById(jobId).map(job -> {
            if (!job.isInProgress() || job.isTimedOut(properties.timeout(), now())) {
                log.warn("이미 끝났거나 제한 시간이 지난 작업이라 결과를 반영하지 않습니다: jobId={}", jobId);
                return false;
            }
            ownedCards.saveSynced(toSave.userId(), toSave.orgAccountId(), toSave.company(), toSave.cards());
            return true;
        }).orElse(false));
        return Boolean.TRUE.equals(saved);
    }

    private void record(long jobId, Consumer<CodefSyncJob> update, boolean synced,
                        ApprovalCollector.Result collection) {
        // 계정을 읽기 전에 같은 사용자 잠금을 잡아 자동 동기화 설정 변경과 저장 순서를 직렬화한다.
        transaction.executeWithoutResult(status -> jobs.findUserIdById(jobId).ifPresent(userId -> {
            userLock.lock(userId);
            jobs.findWithAccountById(jobId).ifPresent(job -> {
                if (!job.isInProgress() || job.isTimedOut(properties.timeout(), now())) {
                    log.warn("이미 끝났거나 제한 시간이 지난 작업이라 결과를 반영하지 않습니다: jobId={}", jobId);
                    return;
                }
                update.accept(job);
                CodefOrgAccount account = job.getOrgAccount();
                if (collection != null) {
                    job.recordTransactions(collection.status(), collection.saved(), collection.unmatched(),
                        collection.from(), collection.to());
                    // 수집을 마친 기간을 카드사 계정에 기록한다. 다음 동기화가 이어서 수집하고, 거래 요약이 부분 수집 여부를 가리는 데 쓴다.
                    ApprovalCollector.Range range = collection.range();
                    account.recordCollectedRange(range == null ? null : range.from(), range == null ? null : range.to(),
                        range == null ? null : range.cardIds());
                }
                if (synced) {
                    account.markSynced(now());
                } else {
                    account.recordSyncError(job.getResultCode());
                }
            });
        }));
    }

    /**
     * 이 시각이 지나면 승인내역을 더 부르지 않는다. 마지막 호출이 응답을 최대로 기다려도({@code api-read-timeout}) 작업 제한 시간
     * 안에 끝나서 결과를 기록할 수 있도록, 제한 시각에서 호출 대기 시간과 여유를 뺀다.
     */
    private LocalDateTime collectDeadline(CodefSyncJob job) {
        return job.getStartedAt().plus(properties.timeout()).minus(codefProperties.apiReadTimeout())
            .minus(COLLECT_MARGIN);
    }

    /** 카드사 계정에 기록된 수집 범위. 기록이 없으면 null. */
    private static ApprovalCollector.Range previousRange(CodefOrgAccount account) {
        if (account.getTxCollectedFrom() == null || account.getTxCollectedTo() == null) {
            return null;
        }
        return new ApprovalCollector.Range(account.getTxCollectedFrom(), account.getTxCollectedTo(),
            account.getTxCollectedCardIds());
    }

    private LocalDateTime now() {
        return Times.nowSeconds(clock);
    }

    /** 승인내역을 수집할 대상. connectedId가 들어 있어서 로그에 남기지 않는다. */
    private record CollectTarget(CardCompany company, String connectedId, String organization,
                                 ApprovalCollector.Range previous, LocalDateTime deadline) {

        @Override
        public String toString() {
            return "CollectTarget[company=" + company + "]";
        }

    }

    /** 성공한 동기화의 카드 목록. 작업 결과를 기록하는 트랜잭션에서 보유카드로 저장한다. */
    private record CardsToSave(long userId, long orgAccountId, CardCompany company, List<CollectedCard> cards) {

    }

}
