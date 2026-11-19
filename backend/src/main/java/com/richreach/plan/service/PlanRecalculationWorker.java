package com.richreach.plan.service;

import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.entity.PlanChangeReason;
import com.richreach.plan.entity.PlanJob;
import com.richreach.plan.repository.PlanJobRepository;
import com.richreach.plan.service.PlanRecalculationComputer.Computation;
import com.richreach.user.service.UserLockService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재계산 작업 하나를 실행한다. 계산하는 동안에는 DB 트랜잭션을 열어 두지 않고, 결과를 저장할 때만 짧게 연다.
 * 어떤 오류가 나도 작업이 진행 중으로 남지 않도록 실패로 기록하고, 실패해도 이전 플랜은 그대로 남는다.
 *
 * <p>끝나기 직전에 사용자 행을 잠근 상태에서 "한 번 더 계산" 표시를 확인한다. 표시가 켜져 있으면(계산 도중에 거래가 또
 * 바뀌었다는 뜻) 최신 거래로 처음부터 다시 계산한다. 잠금 때문에 새 요청의 판단은 이 확인이 끝난 뒤에 이뤄지므로, 어느 쪽이
 * 먼저든 늦게 들어온 거래가 반영되지 않은 채 끝나지 않는다. 계속 거래가 바뀌어도 {@value #MAX_RUNS}번까지만 다시 계산하고, 그래도
 * 끝나지 않으면 실패로 기록한다. 다음에 거래가 바뀔 때 다시 시도한다.
 *
 * <p>제한 시간이 지난 작업은 조회에서 이미 멈춘 것으로 보이므로 늦게 끝난 결과는 반영하지 않는다.
 * 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanRecalculationWorker {

    static final String CODE_INTERNAL_ERROR = "INTERNAL_ERROR";
    static final String CODE_BUSY = "BUSY";
    static final String CODE_TIMEOUT = "TIMEOUT";
    static final String CODE_TOO_MANY_RERUNS = "TOO_MANY_RERUNS";
    static final String TIMEOUT_REASON = "플랜을 다시 계산하는 데 시간이 너무 오래 걸렸습니다.";

    /** 계산하는 동안 거래가 계속 바뀔 때 다시 계산하는 최대 횟수(처음 계산 포함). */
    static final int MAX_RUNS = 5;

    private final PlanJobRepository jobs;
    private final PlanRecalculationComputer computer;
    private final PlanRecalculationSaver saver;
    private final UserLockService userLock;
    private final TransactionTemplate transaction;
    private final PlanProperties properties;
    private final Clock clock;

    /** 끝난 뒤의 상태. */
    private enum Outcome { DONE, RERUN, ENDED }

    public void run(long id) {
        try {
            execute(id);
        } catch (RuntimeException e) {
            log.error("플랜 재계산 중 오류: id={}, type={}", id, e.getClass().getSimpleName());
            fail(id, CODE_INTERNAL_ERROR, "플랜을 다시 계산하는 중 오류가 발생했습니다.");
        }
    }

    /** 실행기가 가득 차 시작하지 못한 작업을 실패로 기록한다. */
    public void markRejected(long id) {
        fail(id, CODE_BUSY, "요청이 많아 플랜을 다시 계산하지 못했습니다. 잠시 뒤 다시 시도해 주세요.");
    }

    private void execute(long id) {
        PlanJob job = transaction.execute(status -> jobs.findById(id).orElse(null));
        if (job == null || !job.isInProgress()) {
            return;
        }
        if (job.isTimedOut(properties.timeout(), now())) {
            timeOut(id);
            return;
        }
        long userId = job.getUserId();
        YearMonth month = YearMonth.parse(job.getPlanMonth());
        PlanChangeReason reason = PlanChangeReason.valueOf(job.getRecalcReason());
        // 예산 수정 작업은 요청한 예산으로 계산한다. 거래 반영 작업은 지금 플랜의 확정 예산을 쓴다.
        PlanBudget budgetOverride = reason == PlanChangeReason.BUDGET_CHANGED
            ? PlanBudget.decode(month, job.getBudgetTotal(), job.getBudgetEncoded()) : null;

        for (int run = 1; run <= MAX_RUNS; run++) {
            progress(id, 10);
            Computation computation = computer.compute(userId, month, budgetOverride);
            if (!computation.success()) {
                log.info("플랜 재계산 실패: id={}, code={}", id, computation.failureCode());
                // 실패로 끝내기 전에 대기 중인 재계산 요청이 있는지 본다. 있으면 그 요청의 거래를 반영해 다시 계산한다.
                Outcome failed = transaction.execute(status -> failOrRerun(id, userId, computation.failureCode(),
                    computation.failureReason()));
                if (failed != Outcome.RERUN) {
                    return;
                }
                log.info("재계산 실패 뒤 대기 중인 요청이 있어 다시 계산합니다: id={}, run={}", id, run);
                continue;
            }
            progress(id, 80);
            Outcome outcome = transaction.execute(status -> finish(status, id, userId, month, computation, reason));
            if (outcome != Outcome.RERUN) {
                return;
            }
            log.info("재계산 도중 거래가 바뀌어 다시 계산합니다: id={}, run={}", id, run);
        }
        fail(id, CODE_TOO_MANY_RERUNS, "거래가 계속 바뀌어 플랜을 다시 계산하지 못했습니다.");
    }

    /**
     * 결과를 저장하고 작업을 끝낸다. 한 트랜잭션이다. 사용자 행을 잠근 뒤 "한 번 더 계산" 표시가 켜져 있으면 아무것도 저장하지
     * 않고 다시 계산하라고 알린다.
     */
    private Outcome finish(org.springframework.transaction.TransactionStatus status, long id, long userId,
                           YearMonth month, Computation computation, PlanChangeReason reason) {
        userLock.lock(userId);
        if (jobs.consumeRerun(id) == 1) {
            return Outcome.RERUN;
        }
        long planId = saver.save(userId, month, computation, reason);
        LocalDateTime now = now();
        // 완료는 "진행 중이고 제한 시간이 지나지 않은" 작업에만 적용되는 조건부 UPDATE다. 이미 끝난 작업이면 0행이라 방금 저장한
        // 플랜과 변경 기록을 모두 되돌려, 늦게 끝난 결과가 남지 않게 한다.
        if (jobs.completeIfInTime(id, planId, now, now.minus(properties.timeout())) == 0) {
            status.setRollbackOnly();
            log.info("플랜 재계산 결과를 반영하지 않았습니다(이미 끝난 작업): id={}", id);
            return Outcome.ENDED;
        }
        log.info("플랜 재계산 완료: id={}", id);
        return Outcome.DONE;
    }

    /**
     * 계산이 실패했을 때 작업을 끝낸다. 한 트랜잭션이고 사용자 행을 잠근다. "한 번 더 계산" 표시가 켜져 있으면 끄고 다시
     * 계산하라고 알린다. 표시가 없으면 진행 중인 작업을 실패로 기록한다. 같은 잠금 아래에서 하므로, 실패로 기록하는 동안 들어온
     * 요청은 이 트랜잭션이 끝난 뒤에 판단되어 새 작업으로 이어진다.
     */
    private Outcome failOrRerun(long id, long userId, String code, String reason) {
        userLock.lock(userId);
        if (jobs.consumeRerun(id) == 1) {
            return Outcome.RERUN;
        }
        jobs.failIfInProgress(id, code, trim(reason), now());
        return Outcome.ENDED;
    }

    private void progress(long id, int value) {
        transaction.executeWithoutResult(status -> jobs.updateProgress(id, Math.min(value, 99)));
    }

    /** 진행 중인 작업만 실패로 기록한다. 이미 끝난 작업은 바뀌지 않는다. */
    private void fail(long id, String code, String reason) {
        String trimmed = trim(reason);
        transaction.executeWithoutResult(status -> jobs.failIfInProgress(id, code, trimmed, now()));
    }

    private static String trim(String reason) {
        return reason == null || reason.length() <= PlanJob.FAILURE_REASON_MAX_LENGTH
            ? reason : reason.substring(0, PlanJob.FAILURE_REASON_MAX_LENGTH);
    }

    /** 제한 시간이 지난 진행 중 작업을 시간 초과로 기록한다. */
    private void timeOut(long id) {
        LocalDateTime now = now();
        transaction.executeWithoutResult(status -> jobs.failIfTimedOut(id, CODE_TIMEOUT, TIMEOUT_REASON, now,
            now.minus(properties.timeout())));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

}
