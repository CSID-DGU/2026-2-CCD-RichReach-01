package com.richreach.plan.optimizer;

import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.SolverStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 플랜 전략이 돌려주는 결과. 성공이면 "어느 카드로 어느 업종을 얼마 쓸지"({@code allocations}), 실패면 이유를 담는다.
 * 플랜 생성 서비스가 이 값을 {@code monthly_plans}와 {@code plan_card_allocations}에 저장한다.
 * 결과는 {@link #success}와 {@link #failure}로만 만든다.
 *
 * <p>성공한 결과가 지켜야 할 것 (플랜 생성 서비스가 저장 전에 검증하고, 어기면 그 전략의 결과를 버린다)
 * <ol>
 *   <li><b>총소비 불변</b>: 업종마다, 그 업종의 배분 금액({@code targetAmount})을 모든 카드에 걸쳐 더한 값이
 *       입력의 업종 예산과 정확히 같아야 한다. (예산을 늘리거나 줄이면 안 된다.)</li>
 *   <li>입력에 없는 카드는 쓰지 않는다. (신규 카드를 가정하지 않는다.)</li>
 *   <li>같은 (카드, 업종) 조합은 한 번만 나온다. (DB의 유일 제약)</li>
 * </ol>
 *
 * @param success        성공 여부. 실패이면 {@code allocations}는 비어 있고 {@code failureReason}이 있다.
 * @param allocations    카드·업종별 배분. 금액이 0인 배분은 넣지 않아도 된다.
 * @param expectedBenefit 예상 혜택 합계(원). 항상 {@code allocations}의 예상 혜택의 합이다.
 * @param solverStatus   최적화 풀이 상태(OPTIMAL, FEASIBLE, INFEASIBLE). 최적화를 쓰지 않는 전략(HABIT, GREEDY)은 null이다.
 * @param solveTimeMs    계산에 걸린 시간(ms). 모르면 null이다.
 * @param failureReason  실패 이유. 성공이면 null이다. (조건이 충돌해 풀 수 없을 때 어떤 조건이 문제였는지 적는다.)
 *                       <b>이 문장은 플랜 생성 작업에 저장되어 상태 조회 응답으로 사용자에게 그대로 내려간다.</b> 그러므로 금액,
 *                       카드 번호, 가맹점 같은 소비 정보와 서버 내부 정보(클래스 이름, 스택, 접속 주소 등)를 넣지 않는다.
 * @param confirmedOnly  사람이 원문과 대조해 확인한 혜택 규칙만 써서 계산했는지. false이면 참고용이다.
 * @param notes          계산에서 빼거나 단순화한 내용(예: "가맹점 지정 혜택은 제외"). 없으면 빈 목록이다.
 */
public record PlanStrategyResult(
    boolean success,
    List<Allocation> allocations,
    long expectedBenefit,
    SolverStatus solverStatus,
    Integer solveTimeMs,
    String failureReason,
    boolean confirmedOnly,
    List<String> notes) {

    public PlanStrategyResult {
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
        notes = notes == null ? List.of() : List.copyOf(notes);
        if (success) {
            if (failureReason != null) {
                throw new IllegalArgumentException("성공한 결과에는 실패 이유가 없어야 합니다.");
            }
            Set<String> seen = new HashSet<>();
            long sum = 0;
            for (Allocation allocation : allocations) {
                if (!seen.add(allocation.userCardId() + ":" + allocation.category())) {
                    throw new IllegalArgumentException("같은 카드와 업종의 배분이 두 번 들어 있습니다.");
                }
                sum += allocation.expectedBenefit();
            }
            if (sum != expectedBenefit) {
                throw new IllegalArgumentException("예상 혜택 합계가 배분의 예상 혜택 합과 다릅니다.");
            }
        } else {
            if (failureReason == null || failureReason.isBlank()) {
                throw new IllegalArgumentException("실패한 결과에는 이유가 있어야 합니다.");
            }
            if (!allocations.isEmpty() || expectedBenefit != 0) {
                throw new IllegalArgumentException("실패한 결과에는 배분과 혜택이 없어야 합니다.");
            }
        }
    }

    /**
     * 이 결과가 입력에 대한 약속을 어겼는지 확인한다. 어긴 내용을 문장으로 돌려주고, 지켰으면(또는 실패 결과이면) 빈 목록이다.
     * 플랜 생성 서비스가 저장 전에 쓰고, 전략을 만드는 사람은 테스트에서
     * {@code assertThat(result.violations(input)).isEmpty()}로 확인하면 된다.
     *
     * <p>확인하는 것: (1) 업종마다 배분 합이 업종 예산과 같은가(총소비 불변), (2) 입력에 없는 카드를 쓰지 않았는가.
     */
    public List<String> violations(PlanStrategyInput input) {
        if (!success) {
            return List.of();
        }
        List<String> problems = new java.util.ArrayList<>();
        Set<Long> allowedCards = new HashSet<>();
        input.cards().forEach(card -> allowedCards.add(card.userCardId()));
        java.util.Map<SpendingCategory, Long> allocated = new java.util.EnumMap<>(SpendingCategory.class);
        for (Allocation allocation : allocations) {
            if (!allowedCards.contains(allocation.userCardId())) {
                problems.add("입력에 없는 카드를 썼습니다: 카드 " + allocation.userCardId());
            }
            allocated.merge(allocation.category(), allocation.targetAmount(), Long::sum);
        }
        for (SpendingCategory category : SpendingCategory.values()) {
            long budget = input.budgetByCategory().getOrDefault(category, 0L);
            long given = allocated.getOrDefault(category, 0L);
            if (budget != given) {
                problems.add("총소비가 달라졌습니다: " + category + " 예산 " + budget + "원, 배분 " + given + "원");
            }
        }
        return problems;
    }

    /**
     * 성공 결과. 예상 혜택 합계는 {@code allocations}의 예상 혜택을 더해서 구한다.
     *
     * @param solverStatus 최적화를 쓰면 OPTIMAL 또는 FEASIBLE, 쓰지 않으면 null
     */
    public static PlanStrategyResult success(List<Allocation> allocations, SolverStatus solverStatus,
                                             Integer solveTimeMs, boolean confirmedOnly, List<String> notes) {
        long benefit = allocations.stream().mapToLong(Allocation::expectedBenefit).sum();
        return new PlanStrategyResult(true, allocations, benefit, solverStatus, solveTimeMs, null, confirmedOnly, notes);
    }

    /**
     * 실패 결과. 풀 수 없거나 시간 안에 못 풀었을 때 예외를 던지지 말고 이것을 돌려준다.
     *
     * @param solverStatus 풀이 상태를 알면 (예: INFEASIBLE) 그 값, 모르면 null
     * @param reason       실패 이유. 비워 둘 수 없다.
     */
    public static PlanStrategyResult failure(SolverStatus solverStatus, Integer solveTimeMs, String reason) {
        return new PlanStrategyResult(false, List.of(), 0, solverStatus, solveTimeMs, reason, false, List.of());
    }

    /**
     * 카드·업종 한 칸의 배분.
     *
     * @param userCardId      보유카드 ID ({@link PlanStrategyInput.CardInput#userCardId()}와 같은 값)
     * @param category        업종
     * @param targetAmount    이 카드로 이 업종에 쓸 목표 금액(원). 0 이상
     * @param expectedBenefit 이 배분의 예상 혜택(원). 0 이상
     */
    public record Allocation(long userCardId, SpendingCategory category, long targetAmount, long expectedBenefit) {

        public Allocation {
            if (targetAmount < 0 || expectedBenefit < 0) {
                throw new IllegalArgumentException("금액은 0 이상이어야 합니다.");
            }
        }
    }

}
