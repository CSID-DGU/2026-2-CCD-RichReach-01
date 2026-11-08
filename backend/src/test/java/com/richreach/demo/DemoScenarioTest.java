package com.richreach.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.transaction.dto.CollectedTransaction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 시연용 거래 시나리오가 항상 같은 결과를 내고, 필요한 경우(취소, 할부, 해외 등)를 담는지 DB 없이 확인한다. */
class DemoScenarioTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private static List<CollectedTransaction> all(LocalDate today, int cards) {
        return DemoScenario.generate(today, cards).stream().flatMap(List::stream).toList();
    }

    @Test
    @DisplayName("보유카드 수만큼의 목록을 돌려주고, 카드가 0장이면 거부한다")
    void returnsOneListPerCard() {
        assertThat(DemoScenario.generate(TODAY, 1)).hasSize(1);
        assertThat(DemoScenario.generate(TODAY, 3)).hasSize(3);
        assertThatThrownBy(() -> DemoScenario.generate(TODAY, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 날에는 항상 같은 거래를 만든다")
    void isDeterministic() {
        assertThat(DemoScenario.generate(TODAY, 3)).isEqualTo(DemoScenario.generate(TODAY, 3));
    }

    @Test
    @DisplayName("오늘 이후(오늘 포함)의 거래는 만들지 않는다")
    void noTransactionsFromTodayOn() {
        assertThat(all(TODAY, 3)).allSatisfy(tx -> assertThat(tx.approvedAt().toLocalDate()).isBefore(TODAY));
    }

    @Test
    @DisplayName("최근 7개 달에 걸쳐 거래가 있다")
    void coversSevenMonths() {
        Set<YearMonth> months = all(TODAY, 3).stream().map(tx -> YearMonth.from(tx.approvedAt()))
            .collect(Collectors.toSet());

        assertThat(months).hasSize(7).contains(YearMonth.from(TODAY), YearMonth.from(TODAY).minusMonths(6));
    }

    @Test
    @DisplayName("가장 오래된 거래가 6개월 전 기간 시작일보다 앞서서, 6개월 조회가 부분 데이터로 표시되지 않는다")
    void sixMonthWindowIsNotPartial() {
        // 말일이 서로 다른 날짜에서도 같아야 한다.
        for (LocalDate today : List.of(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 11, 20),
            LocalDate.of(2026, 12, 2))) {
            LocalDateTime earliest = all(today, 3).stream().map(CollectedTransaction::approvedAt)
                .min(LocalDateTime::compareTo).orElseThrow();

            assertThat(earliest.toLocalDate()).as("오늘 %s", today).isBeforeOrEqualTo(today.minusMonths(6));
        }
    }

    @Test
    @DisplayName("같은 달 안에서 나중에 다시 실행하면 앞서 만든 거래는 그대로이고 새로 지나간 날의 거래만 더해진다")
    void rerunLaterInTheSameMonthOnlyAddsNewDays() {
        List<CollectedTransaction> earlier = all(LocalDate.of(2026, 11, 15), 3);
        List<CollectedTransaction> later = all(LocalDate.of(2026, 11, 25), 3);

        assertThat(later).containsAll(earlier);
        assertThat(later.size()).isGreaterThan(earlier.size());
    }

    @Test
    @DisplayName("달이 바뀌어도 이미 만든 달의 거래는 바뀌지 않는다 (같은 승인번호, 같은 시각)")
    void monthRolloverKeepsExistingMonths() {
        YearMonth november = YearMonth.of(2026, 11);
        List<CollectedTransaction> novemberRun = all(LocalDate.of(2026, 11, 28), 3);
        List<CollectedTransaction> decemberRun = all(LocalDate.of(2026, 12, 5), 3);

        List<CollectedTransaction> keptMonths = novemberRun.stream()
            .filter(tx -> !YearMonth.from(tx.approvedAt()).isBefore(november.minusMonths(5))).toList();

        assertThat(decemberRun).containsAll(keptMonths);
    }

    @Test
    @DisplayName("승인번호는 DEMO-로 시작하고 전체에서 겹치지 않는다")
    void approvalNumbersAreUnique() {
        List<CollectedTransaction> transactions = all(TODAY, 3);
        Set<String> approvals = new HashSet<>();

        for (CollectedTransaction tx : transactions) {
            assertThat(tx.approvalNo()).matches("DEMO-\\d{6}-\\d{3}");
            assertThat(approvals.add(tx.approvalNo())).as(tx.approvalNo()).isTrue();
        }
    }

    @Test
    @DisplayName("전액 취소, 일부 취소, 할부, 해외 결제가 모두 들어 있다")
    void includesSpecialCases() {
        List<CollectedTransaction> transactions = all(TODAY, 3);

        assertThat(transactions).anyMatch(tx -> tx.canceledAmount() > 0 && tx.canceledAmount() == tx.amount());
        assertThat(transactions).anyMatch(tx -> tx.canceledAmount() > 0 && tx.canceledAmount() < tx.amount());
        assertThat(transactions).anyMatch(tx -> tx.installmentMonths() == 3);
        assertThat(transactions).anyMatch(CollectedTransaction::overseas);
    }

    @Test
    @DisplayName("금액은 0 이상이고 취소 금액은 승인 금액을 넘지 않는다")
    void amountsAreSane() {
        assertThat(all(TODAY, 3)).allSatisfy(tx -> {
            assertThat(tx.amount()).isGreaterThan(0);
            assertThat(tx.canceledAmount()).isBetween(0L, tx.amount());
            assertThat(tx.amount() % 100).isZero();
        });
    }

    @Test
    @DisplayName("업종은 지정하지 않아서 가맹점 분류 규칙이 정하게 한다")
    void categoryIsLeftToTheClassifier() {
        assertThat(all(TODAY, 3)).allSatisfy(tx -> assertThat(tx.category()).isNull());
    }

    @Test
    @DisplayName("카드가 여럿이면 모든 카드에 거래가 나뉘고, 한 장이면 모두 그 카드에 담긴다")
    void transactionsAreSpreadAcrossCards() {
        assertThat(DemoScenario.generate(TODAY, 3)).allSatisfy(list -> assertThat(list).isNotEmpty());

        List<List<CollectedTransaction>> single = DemoScenario.generate(TODAY, 1);
        assertThat(single.get(0)).hasSameSizeAs(all(TODAY, 1));
        assertThat(single.get(0).size()).isEqualTo(all(TODAY, 3).size());
    }

}
