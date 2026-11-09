package com.richreach.plan.service;

import com.richreach.plan.entity.PlanStrategy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 한 달에 전략별로 저장된 플랜 중 사용자에게 보여줄 플랜의 전략을 고른다. DB 없이 전략 이름만으로 정하므로 따로 확인할 수 있다.
 * 플랜 생성이 완료 후 보여줄 플랜을 정할 때와 플랜 조회가 같은 규칙을 쓰도록 한 곳에 둔다.
 *
 * <p>우선순위: CP_SAT(최적화) → GREEDY → EVEN_SPLIT(임시) → HABIT. 성공한 결과가 저장된 전략만 후보이고,
 * 위에서부터 가장 먼저 있는 전략이 대표다. HABIT은 "지금처럼 쓸 때"를 보여주는 비교 기준이라 다른 전략이 하나도 없을 때만 고른다.
 */
final class PlanPrimarySelector {

    /** 앞에 있을수록 먼저 고른다. */
    static final List<PlanStrategy> PRIORITY = List.of(
        PlanStrategy.CP_SAT, PlanStrategy.GREEDY, PlanStrategy.EVEN_SPLIT, PlanStrategy.HABIT);

    private PlanPrimarySelector() {
    }

    /** 저장된 전략 중 사용자에게 보여줄 전략. 하나도 없으면 비어 있다. */
    static Optional<PlanStrategy> select(Collection<PlanStrategy> saved) {
        return PRIORITY.stream().filter(saved::contains).findFirst();
    }

}
