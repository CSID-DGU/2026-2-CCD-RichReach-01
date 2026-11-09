package com.richreach.plan.service;

import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanCardAllocation;
import com.richreach.plan.entity.PlanJob;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SpendingProfile;
import com.richreach.plan.entity.SpendingProfileItem;
import com.richreach.plan.optimizer.PlanStrategyInput;
import com.richreach.plan.optimizer.PlanStrategyRegistry;
import com.richreach.plan.optimizer.PlanStrategyResult;
import com.richreach.plan.optimizer.PlanStrategyRunner;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanCardAllocationRepository;
import com.richreach.plan.repository.PlanJobRepository;
import com.richreach.plan.repository.SpendingProfileItemRepository;
import com.richreach.plan.repository.SpendingProfileRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 플랜 생성 작업 하나를 실행한다. 별도 스레드에서 호출된다.
 * 계산하는 동안에는 DB 트랜잭션을 열어 두지 않고, 진행률과 결과를 기록할 때만 짧게 연다.
 * 어떤 오류가 나도 작업이 진행 중으로 남지 않도록 실패로 기록한다.
 *
 * <p>등록된 전략을 우선순위({@link PlanPrimarySelector#PRIORITY}) 순서로 모두 실행한다. 결과는 <b>총소비 불변 검증</b>
 * ({@link PlanStrategyResult#violations})을 통과한 것만 저장한다. 실패한 전략이나 검증에 실패한 결과는 버리고 이유만 남긴다.
 * 성공한 전략이 하나도 없으면 작업을 실패로 기록한다. 저장한 전략 중 사용자에게 보여줄 플랜은 {@link PlanPrimarySelector}가 고른다.
 *
 * <p>확정한 예산으로 소비 프로필을 저장하고, 전략마다 플랜(version 1)과 카드·업종별 배분을 저장한다. 프로필, 플랜, 배분과
 * 작업 완료 기록은 한 트랜잭션이라 일부만 저장되지 않는다. 제한 시간이 지난 작업은 상태 조회에서 이미 실패로 보고되므로, 늦게
 * 끝난 결과는 반영하지 않는다.
 *
 * <p>예산과 배분 금액은 소비 정보라서 로그에 남기지 않는다. (전략 이름과 개수, 상태만 남긴다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanGenerationWorker {

    static final String CODE_NO_ANALYSIS_CARD = "NO_ANALYSIS_CARD";
    static final String CODE_NO_STRATEGY_RESULT = "NO_STRATEGY_RESULT";
    static final String CODE_INTERNAL_ERROR = "INTERNAL_ERROR";
    static final String CODE_BUSY = "BUSY";
    static final String CODE_TIMEOUT = "TIMEOUT";

    private final PlanJobRepository jobs;
    private final PlanInputAssembler inputs;
    private final PlanStrategyRegistry registry;
    private final SpendingProfileRepository profiles;
    private final SpendingProfileItemRepository profileItems;
    private final MonthlyPlanRepository plans;
    private final PlanCardAllocationRepository allocations;
    private final TransactionTemplate transaction;
    private final PlanProperties properties;
    private final Clock clock;

    public void run(long id) {
        try {
            execute(id);
        } catch (RuntimeException e) {
            log.error("플랜 생성 중 오류: id={}, type={}", id, e.getClass().getSimpleName());
            fail(id, CODE_INTERNAL_ERROR, "플랜을 만드는 중 오류가 발생했습니다.");
        }
    }

    /** 실행기가 가득 차 시작하지 못한 작업을 실패로 기록한다. */
    public void markRejected(long id) {
        fail(id, CODE_BUSY, "요청이 많아 플랜 생성을 시작하지 못했습니다. 잠시 뒤 다시 시도해 주세요.");
    }

    private void execute(long id) {
        PlanJob job = transaction.execute(status -> jobs.findById(id).orElse(null));
        if (job == null || !job.isInProgress() || job.isTimedOut(properties.timeout(), now())) {
            return;
        }
        long userId = job.getUserId();
        PlanBudget budget = PlanBudget.decode(YearMonth.parse(job.getPlanMonth()), job.getBudgetTotal(),
            job.getBudgetEncoded());

        progress(id, 5);
        Optional<PlanStrategyInput> assembled = inputs.assemble(userId, budget, properties.strategyTimeLimit());
        if (assembled.isEmpty()) {
            fail(id, CODE_NO_ANALYSIS_CARD, "분석 대상으로 고른 카드가 없습니다.");
            return;
        }
        PlanStrategyInput input = assembled.get();
        progress(id, 20);

        List<PlanStrategy> runnable = PlanPrimarySelector.PRIORITY.stream()
            .filter(strategy -> registry.find(strategy).isPresent()).toList();
        Map<PlanStrategy, PlanStrategyResult> succeeded = new EnumMap<>(PlanStrategy.class);
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < runnable.size(); i++) {
            PlanStrategy strategy = runnable.get(i);
            PlanStrategyResult result = runSafely(registry.find(strategy).orElseThrow(), input);
            if (!result.success()) {
                failures.add(strategy + ": " + result.failureReason());
                log.info("플랜 전략 실패: strategy={}, status={}", strategy, result.solverStatus());
            } else if (!result.violations(input).isEmpty()) {
                failures.add(strategy + ": 총소비가 달라져 결과를 쓰지 않았습니다.");
                log.warn("플랜 전략 결과가 약속을 어겨 버렸습니다: strategy={}", strategy);
            } else {
                succeeded.put(strategy, result);
            }
            progress(id, 20 + 60 * (i + 1) / runnable.size());
        }
        if (succeeded.isEmpty()) {
            fail(id, CODE_NO_STRATEGY_RESULT, failures.isEmpty()
                ? "사용할 수 있는 플랜 전략이 없습니다." : String.join(" / ", failures));
            return;
        }

        save(id, userId, budget, succeeded);
    }

    /** 전략이 예외를 던져도 작업 전체가 죽지 않고 그 전략만 실패로 본다. */
    private PlanStrategyResult runSafely(PlanStrategyRunner runner, PlanStrategyInput input) {
        try {
            return runner.run(input);
        } catch (RuntimeException e) {
            log.error("플랜 전략 실행 중 오류: strategy={}, type={}", runner.strategy(), e.getClass().getSimpleName());
            return PlanStrategyResult.failure(null, null, "전략을 실행하는 중 오류가 발생했습니다.");
        }
    }

    /** 프로필, 전략별 플랜, 배분을 저장하고 작업을 완료한다. 한 트랜잭션이다. */
    private void save(long id, long userId, PlanBudget budget, Map<PlanStrategy, PlanStrategyResult> succeeded) {
        transaction.executeWithoutResult(status -> {
            PlanJob job = jobs.findById(id).orElse(null);
            // 제한 시간이 지난 작업이거나 이미 끝난 작업이면 늦게 끝난 결과를 반영하지 않는다.
            if (job == null || !job.isInProgress() || job.isTimedOut(properties.timeout(), now())) {
                return;
            }
            YearMonth month = budget.month();
            // 확정한 예산으로 만든 프로필이다. 근거 기간은 플랜 대상 월 직전 두 달로 둔다.
            SpendingProfile profile = profiles.save(SpendingProfile.create(userId, month,
                month.minusMonths(2).atDay(1), month.minusMonths(1).atEndOfMonth()));
            budget.byCategory().forEach((category, amount) -> profileItems.save(
                SpendingProfileItem.create(profile.getId(), category, amount, 0, BigDecimal.ONE, BigDecimal.ONE)));

            Map<PlanStrategy, Long> savedPlanIds = new EnumMap<>(PlanStrategy.class);
            succeeded.forEach((strategy, result) -> {
                MonthlyPlan plan = MonthlyPlan.create(userId, profile.getId(), month, strategy, 1, budget.total(),
                    result.expectedBenefit());
                plan.recordSolve(result.solverStatus(), result.solveTimeMs());
                plan = plans.save(plan);
                Long planId = plan.getId();
                allocations.saveAll(result.allocations().stream().filter(allocation -> allocation.targetAmount() > 0)
                    .map(allocation -> PlanCardAllocation.create(planId, allocation.userCardId(), allocation.category(),
                        allocation.targetAmount(), allocation.expectedBenefit())).toList());
                savedPlanIds.put(strategy, planId);
            });

            PlanStrategy primary = PlanPrimarySelector.select(savedPlanIds.keySet()).orElseThrow();
            job.complete(savedPlanIds.get(primary), now());
            log.info("플랜 생성 완료: id={}, strategies={}, primary={}", id, savedPlanIds.keySet(), primary);
        });
    }

    private void progress(long id, int value) {
        transaction.executeWithoutResult(status -> jobs.findById(id).ifPresent(job -> job.updateProgress(value)));
    }

    /** 진행 중이고 제한 시간이 지나지 않은 작업만 실패로 기록한다. */
    private void fail(long id, String code, String reason) {
        transaction.executeWithoutResult(status -> jobs.findById(id).ifPresent(job -> {
            if (job.isInProgress() && !job.isTimedOut(properties.timeout(), now())) {
                job.fail(code, reason, now());
            }
        }));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

}
