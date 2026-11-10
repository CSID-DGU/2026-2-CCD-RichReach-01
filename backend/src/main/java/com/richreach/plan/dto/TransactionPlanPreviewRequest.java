package com.richreach.plan.dto;

import java.time.LocalDateTime;

/** A read-only preview. asOf may be supplied for a historical replay; null means the server clock. */
public record TransactionPlanPreviewRequest(String month, LocalDateTime asOf,
                                            PlanCreateRequest.Budget budget) {}
