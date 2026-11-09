package com.richreach.plan.service;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.dto.PlanCreateRequest;
import com.richreach.plan.dto.PlanJobStartResponse;
import com.richreach.plan.dto.PlanJobStatusResponse;
import com.richreach.plan.entity.PlanJob;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanJobRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 월간 플랜 생성을 시작하고 진행 상태를 알려준다.
 *
 * <p>시작할 때는 예산을 검증하고 작업(IN_PROGRESS)을 기록한 뒤 바로 응답한다. 실제 계산은 별도 스레드
 * ({@link PlanGenerationWorker})가 하고, 화면은 {@code jobId}로 상태를 주기적으로 조회한다. 카드 동기화와 같은 방식이다.
 *
 * <ul>
 *   <li>같은 사용자의 요청은 사용자 행을 잠가서 겹치지 않는다.</li>
 *   <li>분석 대상 카드가 없으면 {@code PLAN-006}, 그 달의 플랜이 이미 있으면 {@code PLAN-004}다.</li>
 *   <li><b>멱등성</b>: 진행 중인 작업이 있을 때 <b>같은 요청</b>(월과 예산이 같음)이 다시 오면 새 작업을 만들지 않고 진행 중인
 *       작업의 {@code jobId}를 돌려준다. 다른 요청이면 {@code PLAN-005}다. 실패한 작업 뒤의 같은 요청은 새 작업으로 처리한다.</li>
 *   <li>서버가 작업 도중 멈춰 진행 중으로 남은 기록은 {@code plan.generation.timeout}이 지나면 멈춘 것으로 보고 실패로 알리며,
 *       새 요청을 막지 않는다.</li>
 * </ul>
 * 예산 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanGenerationService {

    private final PlanUserLock userLock;
    private final PlanInputAssembler inputs;
    private final PlanJobRepository jobs;
    private final MonthlyPlanRepository plans;
    private final PlanGenerationWorker worker;
    @Qualifier("planJobExecutor")
    private final TaskExecutor executor;
    private final TransactionTemplate transaction;
    private final PlanProperties properties;
    private final Clock clock;

    /** 예산이 올바르지 않으면 {@code PLAN-003}이다. 작업 기록이 저장된 뒤에 계산을 실행기에 넘긴다. */
    public PlanJobStartResponse start(long userId, PlanCreateRequest request) {
        PlanBudget budget = PlanBudget.parse(request);
        Started started = transaction.execute(status -> register(userId, budget));
        if (started.dispatch()) {
            dispatch(started.id());
        }
        return new PlanJobStartResponse(started.jobId());
    }

    private Started register(long userId, PlanBudget budget) {
        userLock.lock(userId);

        if (inputs.analysisTargets(userId).isEmpty()) {
            throw new BusinessException(ErrorCode.PLAN_NO_ANALYSIS_CARD);
        }
        if (!plans.findByUserIdAndPlanMonth(userId, budget.month().toString()).isEmpty()) {
            throw new BusinessException(ErrorCode.PLAN_ALREADY_EXISTS);
        }

        LocalDateTime now = now();
        Optional<PlanJob> running = jobs.findFirstByUserIdAndStatusOrderByIdDesc(userId, PlanJob.STATUS_IN_PROGRESS)
            .filter(job -> !job.isTimedOut(properties.timeout(), now));
        if (running.isPresent()) {
            PlanJob job = running.get();
            if (job.getPlanMonth().equals(budget.month().toString()) && job.getRequestHash().equals(budget.fingerprint())) {
                return new Started(job.getId(), job.getJobId(), false);
            }
            throw new BusinessException(ErrorCode.PLAN_JOB_ALREADY_RUNNING);
        }

        PlanJob job = jobs.save(PlanJob.start(userId, UUID.randomUUID().toString(), budget.month().toString(),
            budget.total(), budget.encode(), budget.fingerprint(), now));
        return new Started(job.getId(), job.getJobId(), true);
    }

    private void dispatch(long id) {
        try {
            executor.execute(() -> {
                try {
                    worker.run(id);
                } catch (RuntimeException e) {
                    // 작업을 기록하는 데 실패한 경우. 진행 중으로 남은 기록은 timeout 뒤에 실패로 보인다.
                    log.error("플랜 생성 실행 실패: id={}, type={}", id, e.getClass().getSimpleName());
                }
            });
        } catch (TaskRejectedException e) {
            log.error("플랜 생성 대기열이 가득 차 작업을 시작하지 못했습니다: id={}", id);
            worker.markRejected(id);
        }
    }

    /** 플랜 생성 진행 상태. 없거나 다른 사용자의 jobId면 {@code PLAN-002}다. */
    @Transactional(readOnly = true)
    public PlanJobStatusResponse status(long userId, String jobId) {
        PlanJob job = jobs.findByJobIdAndUserId(jobId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_JOB_NOT_FOUND));
        if (job.isTimedOut(properties.timeout(), now())) {
            return new PlanJobStatusResponse(PlanJob.STATUS_FAILED, job.getProgress(), null,
                PlanGenerationWorker.CODE_TIMEOUT, "플랜 생성이 제한 시간 안에 끝나지 않았습니다. 다시 시도해 주세요.");
        }
        return new PlanJobStatusResponse(job.getStatus(), job.getProgress(), job.getPlanId(), job.getFailureCode(),
            job.getFailureReason());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** 시작한 작업. {@code dispatch}가 false이면 진행 중인 같은 요청이라 새로 실행하지 않는다. */
    private record Started(long id, String jobId, boolean dispatch) {

    }

}
