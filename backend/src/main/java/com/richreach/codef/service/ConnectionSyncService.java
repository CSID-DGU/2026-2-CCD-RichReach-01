package com.richreach.codef.service;

import com.richreach.codef.config.CodefSyncProperties;
import com.richreach.codef.dto.ConnectionSummaryResponse;
import com.richreach.codef.dto.ConnectionSyncRequest;
import com.richreach.codef.dto.ConnectionSyncStartResponse;
import com.richreach.codef.dto.ConnectionSyncStatusResponse;
import com.richreach.codef.dto.ConnectionSyncStatusResponse.Result;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.entity.CodefSyncJob;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.codef.repository.CodefSyncJobRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 연결한 카드사에서 카드 데이터를 가져오는 동기화를 시작하고, 진행 상태와 카드사별 연결 상태를 알려준다.
 *
 * <p>시작할 때는 카드사마다 작업(IN_PROGRESS)을 기록하고 바로 응답한다. 실제 수집은 별도 스레드({@link CardSyncWorker})가
 * 하고, 화면은 {@code jobId}로 상태를 주기적으로 조회한다. 이번 범위는 수집을 시작하고 상태를 알려주는 것까지이며,
 * 가져온 카드를 보유카드로 저장하는 일은 하지 않는다.
 *
 * <p>같은 사용자가 동시에 시작하면 사용자 행을 잠가 한쪽이 {@code CONN-007}을 받게 한다. 서버가 작업 도중 멈춰 진행 중으로
 * 남은 기록은 {@code codef.sync.timeout}이 지나면 멈춘 것으로 보고 실패로 알리며, 새 동기화를 막지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionSyncService {

    static final String IN_PROGRESS = "IN_PROGRESS";
    static final String SUCCESS = "SUCCESS";
    static final String FAILED = "FAILED";
    static final String PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    static final String NOT_SYNCED = "NOT_SYNCED";

    private final UserRepository users;
    private final CodefOrgAccountRepository orgAccounts;
    private final CodefSyncJobRepository jobs;
    private final CardSyncWorker worker;
    @Qualifier("codefSyncExecutor")
    private final TaskExecutor executor;
    private final TransactionTemplate transaction;
    private final CodefSyncProperties properties;
    private final Clock clock;

    /** 동기화를 시작한다. 작업 기록이 저장된 뒤에 수집을 실행기에 넘기므로, 응답을 받은 시점에 상태를 조회할 수 있다. */
    public ConnectionSyncStartResponse start(long userId, ConnectionSyncRequest request) {
        Started started = transaction.execute(status -> register(userId, request));
        for (Long jobId : started.jobIds()) {
            dispatch(jobId);
        }
        return new ConnectionSyncStartResponse(started.batchId());
    }

    private Started register(long userId, ConnectionSyncRequest request) {
        users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        List<Long> requested = request == null || request.connectionIds() == null
            ? List.of() : request.connectionIds().stream().distinct().toList();
        List<CodefOrgAccount> accounts = (requested.isEmpty()
            ? orgAccounts.findActiveByUserId(userId)
            : orgAccounts.findActiveByUserIdAndIdIn(userId, requested))
            .stream().filter(CodefOrgAccount::isConnected).toList();
        // 연결한 카드사가 없거나, 지정한 연결 중 없는(또는 다른 사용자의, 연결되지 않은) 것이 있으면 연결 정보를 찾을 수 없다.
        if (accounts.isEmpty() || (!requested.isEmpty() && accounts.size() != requested.size())) {
            throw new BusinessException(ErrorCode.CONNECTION_NOT_FOUND);
        }

        LocalDateTime now = now();
        if (jobs.existsRunningSince(userId, now.minus(properties.timeout()))) {
            throw new BusinessException(ErrorCode.SYNC_ALREADY_RUNNING);
        }

        String batchId = UUID.randomUUID().toString();
        List<Long> jobIds = jobs.saveAll(accounts.stream()
                .map(account -> CodefSyncJob.startCardList(account, batchId, now)).toList())
            .stream().map(CodefSyncJob::getId).toList();
        return new Started(batchId, jobIds);
    }

    private void dispatch(long jobId) {
        try {
            executor.execute(() -> {
                try {
                    worker.run(jobId);
                } catch (RuntimeException e) {
                    // 작업을 기록하는 데 실패한 경우. 진행 중으로 남은 기록은 timeout 뒤에 실패로 보인다.
                    log.error("카드 동기화 실행 실패: jobId={}, type={}", jobId, e.getClass().getSimpleName());
                }
            });
        } catch (TaskRejectedException e) {
            log.error("카드 동기화 대기열이 가득 차 작업을 시작하지 못했습니다: jobId={}", jobId);
            worker.markRejected(jobId);
        }
    }

    /** 동기화 진행 상태. 없거나 다른 사용자의 jobId면 {@code CONN-006}이다. */
    @Transactional(readOnly = true)
    public ConnectionSyncStatusResponse status(long userId, String jobId) {
        List<CodefSyncJob> batch = jobs.findByBatchIdAndUserId(jobId, userId);
        if (batch.isEmpty()) {
            throw new BusinessException(ErrorCode.SYNC_JOB_NOT_FOUND);
        }
        LocalDateTime now = now();
        List<Result> results = batch.stream().map(job -> toResult(job, now)).toList();

        int completed = (int) results.stream().filter(result -> !IN_PROGRESS.equals(result.status())).count();
        long succeeded = results.stream().filter(result -> SUCCESS.equals(result.status())).count();
        String status;
        if (completed < results.size()) {
            status = IN_PROGRESS;
        } else if (succeeded == results.size()) {
            status = SUCCESS;
        } else if (succeeded == 0) {
            status = FAILED;
        } else {
            status = PARTIAL_SUCCESS;
        }
        return new ConnectionSyncStatusResponse(jobId, status, results.size(), completed, results);
    }

    /** 사용자가 연결한 카드사 목록과 카드사별 최근 동기화 상태. 연결한 카드사가 없으면 빈 목록이다. */
    @Transactional(readOnly = true)
    public List<ConnectionSummaryResponse> list(long userId) {
        Map<Long, CodefSyncJob> latest = jobs.findLatestCardListJobsByUserId(userId).stream()
            .collect(Collectors.toMap(job -> job.getOrgAccount().getId(), Function.identity()));
        LocalDateTime now = now();

        return orgAccounts.findActiveByUserId(userId).stream()
            .filter(account -> CardCompany.fromOrganizationCode(account.getOrganization()) != null)
            .map(account -> {
                CardCompany company = CardCompany.fromOrganizationCode(account.getOrganization());
                CodefSyncJob job = latest.get(account.getId());
                OffsetDateTime syncedAt = account.getLastSyncedAt() == null ? null
                    : account.getLastSyncedAt().atZone(clock.getZone()).toOffsetDateTime();
                if (job == null) {
                    return new ConnectionSummaryResponse(account.getId(), company, NOT_SYNCED, 0, null, null, syncedAt);
                }
                Result result = toResult(job, now);
                return new ConnectionSummaryResponse(account.getId(), company, result.status(), result.cardCount(),
                    result.failureCode(), result.failureReason(), syncedAt);
            })
            .toList();
    }

    private Result toResult(CodefSyncJob job, LocalDateTime now) {
        CardCompany company = CardCompany.fromOrganizationCode(job.getOrgAccount().getOrganization());
        Long connectionId = job.getOrgAccount().getId();
        boolean timedOut = job.isTimedOut(properties.timeout(), now);

        if (job.isInProgress() && !timedOut) {
            return new Result(connectionId, company, IN_PROGRESS, 0, null, null);
        }
        if (timedOut || CodefSyncJob.STATUS_FAIL.equals(job.getStatus())) {
            SyncFailure failure = SyncFailure.of(job, timedOut);
            return new Result(connectionId, company, FAILED, 0, failure.code(), failure.reason());
        }
        int count = job.getFetchedCount() == null ? 0 : job.getFetchedCount();
        return new Result(connectionId, company, SUCCESS, count, null, null);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private record Started(String batchId, List<Long> jobIds) {

    }

}
