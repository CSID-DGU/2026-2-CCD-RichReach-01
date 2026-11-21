package com.richreach.plan.service;

import com.richreach.benefit.service.TransactionBenefitCalculator;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.dto.PlanCreateRequest;
import com.richreach.plan.dto.TransactionPlanPreviewRequest;
import com.richreach.plan.optimizer.MonthlyTransactionPlanEngine;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Runs the same transaction-level calculator against DB inputs, without saving or changing a plan. */
@Service
@RequiredArgsConstructor
public class TransactionPlanPreviewService {
    private final MonthlyTransactionPlanAssembler assembler;
    private final PlanProperties properties;
    private final Clock clock;
    private final MonthlyTransactionPlanEngine engine =
        new MonthlyTransactionPlanEngine(new TransactionBenefitCalculator());

    public MonthlyTransactionPlanEngine.Result preview(long userId, TransactionPlanPreviewRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        PlanBudget budget = PlanBudget.parse(new PlanCreateRequest(request.month(), request.budget()));
        LocalDateTime asOf = request.asOf() == null ? LocalDateTime.now(clock) : request.asOf();
        if (!YearMonth.from(asOf).equals(budget.month()) || asOf.isAfter(LocalDateTime.now(clock)))
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                "기준 시각은 플랜 대상 월에 속하며 현재보다 늦지 않아야 합니다.");
        MonthlyTransactionPlanEngine.Input input = assembler.assemble(userId, budget.month(), asOf,
            budget.byCategory(), properties.strategyTimeLimit());
        if (input.history().isEmpty()) throw new BusinessException(ErrorCode.PLAN_NO_PREVIOUS_MONTH_TRANSACTIONS);
        return engine.create(input);
    }
}
