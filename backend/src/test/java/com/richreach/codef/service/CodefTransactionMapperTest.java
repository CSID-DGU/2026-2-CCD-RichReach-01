package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.codef.client.CodefApprovalClient.CodefApproval;
import com.richreach.transaction.dto.CollectedTransaction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CODEF 승인내역을 거래 저장 입력으로 바꾸는 규칙을 DB 없이 확인한다. */
class CodefTransactionMapperTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 3);

    private static CodefApproval approval(LocalTime time, long amount, long canceled, int installment,
                                          String homeForeign) {
        return new CodefApproval("12345678", DATE, time, "테스트 상점", amount, canceled, installment, "음식점",
            "1234567890123456", "0", "1", homeForeign, "KRW");
    }

    @Test
    @DisplayName("승인일시는 이용 일자와 시각이고, 승인번호·가맹점명·금액·할부를 그대로 담는다")
    void mapsFields() {
        CollectedTransaction transaction = CodefTransactionMapper.toCollected(
            approval(LocalTime.of(23, 8, 0), 3_000, 0, 3, "1"));

        assertThat(transaction.approvalNo()).isEqualTo("12345678");
        assertThat(transaction.approvedAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 23, 8, 0));
        assertThat(transaction.merchantName()).isEqualTo("테스트 상점");
        assertThat(transaction.amount()).isEqualTo(3_000);
        assertThat(transaction.canceledAmount()).isZero();
        assertThat(transaction.installmentMonths()).isEqualTo(3);
    }

    @Test
    @DisplayName("시각이 없으면 그 날 00:00:00이다")
    void midnightWithoutTime() {
        assertThat(CodefTransactionMapper.toCollected(approval(null, 1_000, 0, 0, "1")).approvedAt())
            .isEqualTo(LocalDateTime.of(2026, 11, 3, 0, 0, 0));
    }

    @Test
    @DisplayName("업종은 비워 둔다 (거래 저장이 가맹점명으로 자동 분류한다)")
    void categoryIsLeftEmpty() {
        assertThat(CodefTransactionMapper.toCollected(approval(null, 1_000, 0, 0, "1")).category()).isNull();
    }

    @Test
    @DisplayName("취소 금액을 그대로 담는다")
    void keepsCanceledAmount() {
        assertThat(CodefTransactionMapper.toCollected(approval(null, 30_000, 30_000, 0, "1")).canceledAmount())
            .isEqualTo(30_000);
    }

    @Test
    @DisplayName("국내/해외 구분이 2일 때만 해외로 본다")
    void overseasOnlyForCodeTwo() {
        assertThat(CodefTransactionMapper.toCollected(approval(null, 1_000, 0, 0, "1")).overseas()).isFalse();
        assertThat(CodefTransactionMapper.toCollected(approval(null, 1_000, 0, 0, "")).overseas()).isFalse();
        assertThat(CodefTransactionMapper.toCollected(approval(null, 1_000, 0, 0, "2")).overseas()).isTrue();
    }

}
