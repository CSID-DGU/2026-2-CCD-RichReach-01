package com.richreach.plan.service;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.dto.PlanChatConfirmRequest;
import com.richreach.plan.dto.PlanChatConfirmResponse;
import com.richreach.plan.entity.PlanChangeReason;
import com.richreach.plan.entity.PlanJob;
import com.richreach.plan.repository.PlanJobRepository;
import com.richreach.transaction.service.TransactionSummaryService;
import com.richreach.user.service.UserLockService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 사용자 확인 뒤 구조화된 수정안만 저장 작업에 넘긴다. LLM 호출과 거래 원문 전송은 하지 않는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanChatConfirmService {

    static final String STALE_REPLY = "플랜이 변경됐어요. 최신 플랜을 확인한 뒤 다시 요청해 주세요.";
    static final String BELOW_SPENT_REPLY = "이미 승인된 지출보다 낮은 업종 예산은 확정할 수 없어요. 예산을 다시 확인해 주세요.";
    static final String STARTED_REPLY = "수정안 확정 요청을 받았어요. 월간 플랜을 다시 계산하고 있어요.";
    private static final String MONTHLY_PLAN = "MONTHLY_PLAN";
    private static final String IMMEDIATE_DISCOUNT = "IMMEDIATE_DISCOUNT";

    private final UserLockService userLock;
    private final PlanBaselineReader baselines;
    private final PlanInputAssembler inputs;
    private final TransactionSummaryService transactions;
    private final PlanJobRepository jobs;
    private final PlanRecalculationWorker worker;
    @Qualifier("planJobExecutor")
    private final TaskExecutor executor;
    private final TransactionTemplate transaction;
    private final PlanProperties properties;
    private final Clock clock;

    public PlanChatConfirmResponse confirm(long userId, PlanChatConfirmRequest request) {
        if (request == null || !request.confirmed() || request.basePlanId() <= 0
            || !(MONTHLY_PLAN.equals(request.proposedPreference())
            || IMMEDIATE_DISCOUNT.equals(request.proposedPreference()))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        PlanBudget budget = PlanBudget.parse(request.proposedPlan());
        if (!budget.month().equals(YearMonth.now(clock))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이번 달 플랜만 수정할 수 있습니다.");
        }
        Registered registered = transaction.execute(status -> register(userId, request, budget));
        if (registered.reply() != null) {
            return new PlanChatConfirmResponse(registered.reply(), true, null);
        }
        if (registered.dispatch()) {
            dispatch(registered.id());
        }
        return new PlanChatConfirmResponse(STARTED_REPLY, false, registered.jobId());
    }

    private Registered register(long userId, PlanChatConfirmRequest request, PlanBudget budget) {
        userLock.lock(userId);
        var baseline = baselines.read(userId, budget.month())
            .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        if (!baseline.plan().getId().equals(request.basePlanId())) {
            return Registered.clarify(STALE_REPLY);
        }
        if (inputs.analysisTargets(userId).isEmpty()) {
            throw new BusinessException(ErrorCode.PLAN_NO_ANALYSIS_CARD);
        }
        if (PlanApprovedSpendGuard.belowBudget(transactions.cardCategorySpend(userId, budget.month()), budget)) {
            return Registered.clarify(BELOW_SPENT_REPLY);
        }

        String hash = fingerprint(budget, request.proposedPreference());
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<PlanJob> running = jobs.findFirstByUserIdAndJobTypeAndPlanMonthAndStatusOrderByIdDesc(userId,
            PlanJob.TYPE_RECALCULATE, budget.month().toString(), PlanJob.STATUS_IN_PROGRESS);
        if (running.isPresent()) {
            PlanJob job = running.get();
            if (job.isTimedOut(properties.timeout(), now)) {
                jobs.failIfTimedOut(job.getId(), PlanRecalculationWorker.CODE_TIMEOUT,
                    PlanRecalculationWorker.TIMEOUT_REASON, now, now.minus(properties.timeout()));
            } else if (PlanChangeReason.CHAT_CONFIRMED.name().equals(job.getRecalcReason())
                && hash.equals(job.getRequestHash())) {
                return new Registered(job.getId(), job.getJobId(), false, null);
            } else {
                throw new BusinessException(ErrorCode.PLAN_JOB_ALREADY_RUNNING);
            }
        }
        PlanJob job = jobs.save(PlanJob.startChatRecalculation(userId, UUID.randomUUID().toString(),
            budget.month().toString(), budget.total(), budget.encode(), hash, request.proposedPreference(), now));
        return new Registered(job.getId(), job.getJobId(), true, null);
    }

    private static String fingerprint(PlanBudget budget, String preference) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((budget.fingerprint() + "|" + preference).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }

    private void dispatch(long id) {
        try {
            executor.execute(() -> {
                try {
                    worker.run(id);
                } catch (RuntimeException e) {
                    log.error("챗봇 플랜 확정 실행 실패: id={}, type={}", id, e.getClass().getSimpleName());
                }
            });
        } catch (TaskRejectedException e) {
            log.error("플랜 재계산 대기열이 가득 차 확정 작업을 시작하지 못했습니다: id={}", id);
            worker.markRejected(id);
        }
    }

    private record Registered(Long id, String jobId, boolean dispatch, String reply) {
        static Registered clarify(String reply) {
            return new Registered(null, null, false, reply);
        }
    }
}
