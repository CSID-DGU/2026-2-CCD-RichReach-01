package com.richreach.plan.service;

import com.richreach.plan.config.PlanProperties;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 승인 거래가 반영된 뒤 이번 달 월간 플랜의 남은 기간을 다시 계산하는 작업을 시작한다. 계산은 {@link PlanRecalculationWorker}가
 * 한다.
 *
 * <ul>
 *   <li><b>이번 달 플랜만</b> 다시 계산한다. 지난 달 거래가 늦게 들어와도 끝난 달의 플랜은 건드리지 않는다.</li>
 *   <li>그 달에 보여줄 플랜이 없으면 아무것도 하지 않는다. (플랜을 처음 만드는 것은 플랜 생성의 몫이다)</li>
 *   <li>같은 사용자의 같은 달에 재계산이 진행 중이면 새 작업을 만들지 않고 진행 중인 작업에 "끝나기 전에 한 번 더 계산"만
 *       표시한다. 판단과 표시는 사용자 행을 잠근 상태에서 하고, 작업은 끝나기 직전에 같은 잠금 아래에서 그 표시를 확인한다.
 *       그래서 계산 도중에 들어온 거래가 반영되지 않은 채 끝나는 일이 없다.</li>
 *   <li>서버가 작업 도중 멈춰 진행 중으로 남은 기록은 {@code plan.generation.timeout}이 지나면 멈춘 것으로 보고 실패로 기록한 뒤
 *       새 작업을 만든다.</li>
 * </ul>
 * 재계산이 실패해도 이전 플랜은 그대로 남고, 다음에 거래가 바뀔 때 다시 시도한다. 예산과 배분 금액은 소비 정보라서 로그에 남기지
 * 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanRecalculationService {

    private final UserLockService userLock;
    private final PlanBaselineReader baselines;
    private final PlanJobRepository jobs;
    private final PlanRecalculationWorker worker;
    private final TransactionTemplate transaction;
    private final PlanProperties properties;
    private final Clock clock;

    /**
     * 재계산이 필요하면 작업을 만들고 이 스레드에서 바로 실행한다. 거래를 바꾼 요청을 붙잡지 않도록 호출하는 쪽이 별도 스레드에서
     * 불러야 한다. 진행 중인 작업이 있으면 표시만 하고 바로 돌아온다.
     */
    public void requestAndRun(long userId, YearMonth month, PlanChangeReason reason) {
        if (!month.equals(YearMonth.now(clock))) {
            return;
        }
        Optional<Long> started = transaction.execute(status -> register(userId, month, reason));
        if (started != null && started.isPresent()) {
            worker.run(started.get());
        }
    }

    /** 작업을 만들었으면 그 id를, 만들 필요가 없거나 진행 중인 작업에 표시만 했으면 비어 있다. */
    private Optional<Long> register(long userId, YearMonth month, PlanChangeReason reason) {
        userLock.lock(userId);

        Optional<PlanBaselineReader.Baseline> baseline = baselines.read(userId, month);
        if (baseline.isEmpty()) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Optional<PlanJob> running = jobs.findFirstByUserIdAndJobTypeAndPlanMonthAndStatusOrderByIdDesc(userId,
            PlanJob.TYPE_RECALCULATE, month.toString(), PlanJob.STATUS_IN_PROGRESS);
        if (running.isPresent()) {
            PlanJob job = running.get();
            if (job.isTimedOut(properties.timeout(), now)) {
                jobs.failIfTimedOut(job.getId(), PlanRecalculationWorker.CODE_TIMEOUT,
                    PlanRecalculationWorker.TIMEOUT_REASON, now, now.minus(properties.timeout()));
            } else {
                jobs.requestRerun(job.getId());
                return Optional.empty();
            }
        }

        PlanBudget budget = baseline.get().budget();
        PlanJob job = jobs.save(PlanJob.startRecalculation(userId, UUID.randomUUID().toString(), month.toString(),
            budget.total(), budget.encode(), budget.fingerprint(), reason, now));
        return Optional.of(job.getId());
    }

}
