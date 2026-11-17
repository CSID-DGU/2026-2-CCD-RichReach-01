package com.richreach.plan.service;

import com.richreach.global.event.PlannedExpensesChangedEvent;
import com.richreach.global.event.TransactionsChangedEvent;
import com.richreach.plan.entity.PlanChangeReason;
import java.time.YearMonth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 거래나 예정지출이 바뀌었다는 알림({@link TransactionsChangedEvent}, {@link PlannedExpensesChangedEvent})을 받아 월간 플랜
 * 재계산을 시작한다. 거래를 바꾼 요청이 계산을 기다리지
 * 않도록 별도 스레드({@code planJobExecutor})에서 실행한다.
 *
 * <p>거래를 바꾸는 트랜잭션이 있으면 그 트랜잭션이 <b>커밋된 뒤에</b> 처리하고(롤백되면 처리하지 않는다), 트랜잭션 없이 알림이
 * 오면(카드 동기화) 바로 처리한다. 재계산이 시작되지 못해도 거래를 바꾼 요청은 영향을 받지 않는다. 다음에 거래가 바뀔 때
 * 다시 시도한다.
 */
@Slf4j
@Component
public class PlanRecalculationTrigger {

    private final PlanRecalculationService recalculation;
    private final TaskExecutor executor;

    public PlanRecalculationTrigger(PlanRecalculationService recalculation,
                                    @Qualifier("planJobExecutor") TaskExecutor executor) {
        this.recalculation = recalculation;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(TransactionsChangedEvent event) {
        PlanChangeReason reason = switch (event.reason()) {
            case SYNCED -> PlanChangeReason.TRANSACTION_SYNCED;
            case CATEGORY_EDITED -> PlanChangeReason.CATEGORY_EDITED;
        };
        start(event.userId(), event.month(), reason);
    }

    /** 예정지출이 바뀌었다는 알림({@link PlannedExpensesChangedEvent})을 받아 같은 방식으로 재계산을 시작한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(PlannedExpensesChangedEvent event) {
        start(event.userId(), event.month(), PlanChangeReason.PLANNED_EXPENSE_CHANGED);
    }

    private void start(long userId, YearMonth month, PlanChangeReason reason) {
        try {
            executor.execute(() -> {
                try {
                    recalculation.requestAndRun(userId, month, reason);
                } catch (RuntimeException e) {
                    // 재계산을 시작하지 못한 경우. 메시지에 금액이 섞일 수 있어서 종류만 남긴다.
                    log.error("플랜 재계산 시작 실패: type={}", e.getClass().getSimpleName());
                }
            });
        } catch (TaskRejectedException e) {
            log.warn("플랜 재계산 대기열이 가득 차 이번 요청은 건너뜁니다.");
        }
    }

}
