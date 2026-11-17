package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.richreach.global.event.TransactionsChangedEvent;
import com.richreach.global.event.TransactionsChangedEvent.Reason;
import com.richreach.plan.entity.PlanChangeReason;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

/** 거래 변경 알림이 재계산 요청으로 이어지는지, 실패해도 알림을 보낸 쪽에 영향이 없는지 확인한다. */
class PlanRecalculationTriggerTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 11);

    private final PlanRecalculationService service = mock(PlanRecalculationService.class);

    @Test
    @DisplayName("알림의 이유를 플랜 변경 원인으로 바꿔 재계산을 요청한다")
    void mapsTheReason() {
        PlanRecalculationTrigger trigger = new PlanRecalculationTrigger(service, Runnable::run);

        trigger.on(new TransactionsChangedEvent(1L, MONTH, Reason.SYNCED));
        trigger.on(new TransactionsChangedEvent(2L, MONTH, Reason.CATEGORY_EDITED));

        verify(service).requestAndRun(1L, MONTH, PlanChangeReason.TRANSACTION_SYNCED);
        verify(service).requestAndRun(2L, MONTH, PlanChangeReason.CATEGORY_EDITED);
    }

    @Test
    @DisplayName("재계산을 시작하다 예외가 나도 알림을 보낸 쪽으로 전파하지 않는다")
    void swallowsFailures() {
        doThrow(new IllegalStateException("boom")).when(service).requestAndRun(any(Long.class), any(), any());
        PlanRecalculationTrigger trigger = new PlanRecalculationTrigger(service, Runnable::run);

        assertThatCode(() -> trigger.on(new TransactionsChangedEvent(1L, MONTH, Reason.SYNCED)))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("실행기가 가득 차 작업을 받지 못해도 예외를 전파하지 않는다")
    void swallowsRejection() {
        TaskExecutor full = task -> {
            throw new TaskRejectedException("full");
        };
        PlanRecalculationTrigger trigger = new PlanRecalculationTrigger(service, full);

        assertThatCode(() -> trigger.on(new TransactionsChangedEvent(1L, MONTH, Reason.SYNCED)))
            .doesNotThrowAnyException();
    }

}
