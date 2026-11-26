package com.richreach.benefit.dto;

import com.richreach.global.category.SpendingCategory;
import java.time.LocalDateTime;

/** One approved or prospective direct-card purchase. An unidentified merchant is never reward eligible. */
public record BenefitPurchase(long id, LocalDateTime approvedAt, SpendingCategory category, long amount,
                              boolean merchantIdentified, boolean forecast) {

    public BenefitPurchase(long id, LocalDateTime approvedAt, SpendingCategory category, long amount,
                           boolean merchantIdentified) {
        this(id, approvedAt, category, amount, merchantIdentified, false);
    }

    public BenefitPurchase {
        if (approvedAt == null || category == null || amount < 0) {
            throw new IllegalArgumentException("거래 일시, 업종, 0원 이상의 금액이 필요합니다.");
        }
    }
}
