package com.richreach.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionKeysTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 5, 12, 30, 0);

    @Test
    @DisplayName("키는 SHA-256 16진수 64자이고, 같은 입력은 항상 같은 키다")
    void keyIsStableHex() {
        String key = TransactionKeys.key(TransactionKeys.base("AP-1", AT, 4500, "카페"), 0);

        assertThat(key).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(TransactionKeys.key(TransactionKeys.base("AP-1", AT, 4500, "카페"), 0)).isEqualTo(key);
    }

    @Test
    @DisplayName("승인번호가 있으면 금액과 가맹점명이 달라도 같은 기준이다")
    void approvalNumberIgnoresAmountAndMerchant() {
        assertThat(TransactionKeys.base("AP-1", AT, 4500, "카페"))
            .isEqualTo(TransactionKeys.base("AP-1", AT, 9000, "다른 가맹점"));
    }

    @Test
    @DisplayName("승인번호가 다르거나 승인일시가 다르면 다른 기준이다")
    void approvalNumberAndTimeDistinguish() {
        assertThat(TransactionKeys.base("AP-1", AT, 4500, "카페")).isNotEqualTo(TransactionKeys.base("AP-2", AT, 4500, "카페"));
        assertThat(TransactionKeys.base("AP-1", AT, 4500, "카페"))
            .isNotEqualTo(TransactionKeys.base("AP-1", AT.plusSeconds(1), 4500, "카페"));
    }

    @Test
    @DisplayName("승인번호가 없으면 가맹점명의 대소문자·공백·전각 차이를 무시하지만, 금액이 다르면 다른 기준이다")
    void withoutApprovalNumberNormalizesMerchant() {
        assertThat(TransactionKeys.base("", AT, 4500, "Test  Cafe")).isEqualTo(TransactionKeys.base("", AT, 4500, "test cafe"));
        assertThat(TransactionKeys.base("", AT, 4500, "ＴＥＳＴ ＣＡＦＥ")).isEqualTo(TransactionKeys.base("", AT, 4500, "test cafe"));
        assertThat(TransactionKeys.base("", AT, 4500, "카페")).isNotEqualTo(TransactionKeys.base("", AT, 4600, "카페"));
    }

    @Test
    @DisplayName("같은 기준이라도 순번이 다르면 다른 키다")
    void occurrenceDistinguishesIdenticalTransactions() {
        String base = TransactionKeys.base("", AT, 4500, "카페");

        assertThat(TransactionKeys.key(base, 0)).isNotEqualTo(TransactionKeys.key(base, 1));
    }

}
