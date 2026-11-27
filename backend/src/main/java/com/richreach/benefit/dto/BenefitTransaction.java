package com.richreach.benefit.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** A single event. Missing facts are unknown, not false. Amounts are KRW integers. */
public record BenefitTransaction(String id, LocalDateTime approvedAt, LocalDateTime postedAt,
                                 long amount, String merchant, String category, String event,
                                 Map<String, String> facts) {
    public BenefitTransaction {
        if (id == null || approvedAt == null || amount < 0) throw new IllegalArgumentException("Invalid transaction");
        facts = facts == null ? Map.of() : Map.copyOf(facts);
        merchant = merchant == null ? "" : merchant;
        event = event == null ? "PURCHASE" : event;
    }
}
