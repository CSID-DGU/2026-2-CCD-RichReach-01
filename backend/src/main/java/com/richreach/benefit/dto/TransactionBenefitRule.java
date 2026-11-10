package com.richreach.benefit.dto;

import java.math.BigDecimal;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Numeric terms stay in the original CSV columns; conditions contains versioned predicates. */
public record TransactionBenefitRule(String id, String family, BigDecimal ratePercent, Long amount,
                                     Long monthlyLimit, Long dailyLimit, Integer dailyCountLimit,
                                     Integer monthlyCountLimit, Integer annualCountLimit,
                                     Long previousMonthMin, String capGroupId, Long sharedMonthlyLimit,
                                     String rewardUnit, boolean ready, JsonNode spec) {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    public static JsonNode parse(String conditions) {
        if (conditions == null || !conditions.stripLeading().startsWith("{"))
            throw new IllegalArgumentException("Transaction rule requires structured conditions");
        JsonNode node = JSON.readTree(conditions);
        if (!"card-benefit-v1".equals(node.path("schema").asText()))
            throw new IllegalArgumentException("Unsupported benefit rule schema");
        return node;
    }
}
