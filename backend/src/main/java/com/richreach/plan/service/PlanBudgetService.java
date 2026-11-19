package com.richreach.plan.service;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.dto.PlanCreateRequest;
import com.richreach.plan.dto.PlanJobStartResponse;
import com.richreach.plan.entity.PlanChangeReason;
import com.richreach.plan.entity.PlanJob;
import com.richreach.plan.repository.PlanJobRepository;
import com.richreach.user.service.UserLockService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사용자가 이번 달 소비예산을 고치면, 새 예산으로 남은 기간의 월간 플랜을 다시 계산하는 작업을 시작한다. 계산은 거래 반영 재계산과
 * 같은 {@link PlanRecalculationWorker}가 하고, 화면은 {@code jobId}로 {@code GET /api/plans/jobs/{jobId}}를 조회한다.
 * ({@code POST /api/plans}는 같은 달에 다시 만들 수 없다.)
 *
 * <ul>
 *   <li>예산 검증은 플랜 생성과 같은 규칙({@link PlanBudget})이라 틀리면 {@code PLAN-003}이다.</li>
 *   <li><b>이번 달 예산만</b> 고칠 수 있다. 다른 달이면 {@code COMMON-400}이다.</li>
 *   <li>보여줄 플랜이 없으면 {@code PLAN-001}(처음 만들 때는 플랜 생성), 분석 대상 카드가 없으면 {@code PLAN-006}이다.</li>
 *   <li>이미 쓴 금액보다 낮은 예산도 받는다. 재계산 엔진이 그 업종의 남은 목표를 0원으로 두고, 예산을 늘려서 맞추지 않는다.</li>
 *   <li>새 예산은 요청한 작업에 담겨 있다가 <b>계산이 성공했을 때만</b> 확정 예산(소비 프로필)으로 저장된다. 실패하면 이전
 *       플랜과 이전 예산이 그대로 남는다.</li>
 *   <li>같은 달에 다른 재계산이 진행 중이면 {@code PLAN-005}다. (거래 반영 재계산은 보통 몇 초라 잠시 뒤 다시 보내면 된다) 같은
 *       예산 수정 요청이 다시 오면 진행 중인 작업의 {@code jobId}를 그대로 돌려준다. 멈춘 작업은 {@code plan.generation.timeout}이
 *       지나면 실패로 기록하고 새 요청을 받는다.</li>
 * </ul>
 * 예산 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanBudgetService {

    private final UserLockService userLock;
    private final PlanBaselineReader baselines;
    private final PlanInputAssembler inputs;
    private final PlanJobRepository jobs;
    private final PlanRecalculationWorker worker;
    @Qualifier("planJobExecutor")
    private final TaskExecutor executor;
    private final TransactionTemplate transaction;
    private final PlanProperties properties;
    private final Clock clock;

    /** 예산이 올바르지 않으면 {@code PLAN-003}이다. 작업 기록이 저장된 뒤에 계산을 실행기에 넘긴다. */
    public PlanJobStartResponse start(long userId, PlanCreateRequest request) {
        PlanBudget budget = PlanBudget.parse(request);
        if (!budget.month().equals(YearMonth.now(clock))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이번 달 예산만 수정할 수 있습니다.");
        }
        Started started = transaction.execute(status -> register(userId, budget));
        if (started.dispatch()) {
            dispatch(started.id());
        }
        return new PlanJobStartResponse(started.jobId());
    }

    private Started register(long userId, PlanBudget budget) {
        userLock.lock(userId);

        if (baselines.read(userId, budget.month()).isEmpty()) {
            throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);
        }
        if (inputs.analysisTargets(userId).isEmpty()) {
            throw new BusinessException(ErrorCode.PLAN_NO_ANALYSIS_CARD);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Optional<PlanJob> running = jobs.findFirstByUserIdAndJobTypeAndPlanMonthAndStatusOrderByIdDesc(userId,
            PlanJob.TYPE_RECALCULATE, budget.month().toString(), PlanJob.STATUS_IN_PROGRESS);
        if (running.isPresent()) {
            PlanJob job = running.get();
            if (job.isTimedOut(properties.timeout(), now)) {
                // 멈춘 작업은 실패로 기록해 두고 새 요청을 받는다. (늦게 끝나도 완료로 바뀌지 않는다.)
                jobs.failIfTimedOut(job.getId(), PlanRecalculationWorker.CODE_TIMEOUT,
                    PlanRecalculationWorker.TIMEOUT_REASON, now, now.minus(properties.timeout()));
            } else if (PlanChangeReason.BUDGET_CHANGED.name().equals(job.getRecalcReason())
                && job.getRequestHash().equals(budget.fingerprint())) {
                return new Started(job.getId(), job.getJobId(), false);
            } else {
                throw new BusinessException(ErrorCode.PLAN_JOB_ALREADY_RUNNING);
            }
        }

        PlanJob job = jobs.save(PlanJob.startRecalculation(userId, UUID.randomUUID().toString(),
            budget.month().toString(), budget.total(), budget.encode(), budget.fingerprint(),
            PlanChangeReason.BUDGET_CHANGED, now));
        return new Started(job.getId(), job.getJobId(), true);
    }

    private void dispatch(long id) {
        try {
            executor.execute(() -> {
                try {
                    worker.run(id);
                } catch (RuntimeException e) {
                    // 작업을 기록하는 데 실패한 경우. 진행 중으로 남은 기록은 timeout 뒤에 실패로 보인다.
                    log.error("플랜 예산 수정 실행 실패: id={}, type={}", id, e.getClass().getSimpleName());
                }
            });
        } catch (TaskRejectedException e) {
            log.error("플랜 재계산 대기열이 가득 차 예산 수정 작업을 시작하지 못했습니다: id={}", id);
            worker.markRejected(id);
        }
    }

    /** 시작한 작업. {@code dispatch}가 false이면 진행 중인 같은 요청이라 새로 실행하지 않는다. */
    private record Started(long id, String jobId, boolean dispatch) {

    }

}
