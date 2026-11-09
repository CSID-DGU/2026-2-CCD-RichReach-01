package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.plan.entity.PlanStrategy;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 사용자에게 보여줄 플랜의 전략을 고르는 규칙을 확인한다. (CP_SAT, GREEDY, EVEN_SPLIT, HABIT 순) */
class PlanPrimarySelectorTest {

    @Test
    @DisplayName("CP_SAT가 있으면 CP_SAT, 없으면 GREEDY, 그것도 없으면 EVEN_SPLIT, 마지막으로 HABIT이다")
    void followsPriorityOrder() {
        assertThat(PlanPrimarySelector.select(Set.of(PlanStrategy.values()))).contains(PlanStrategy.CP_SAT);
        assertThat(PlanPrimarySelector.select(Set.of(PlanStrategy.GREEDY, PlanStrategy.EVEN_SPLIT, PlanStrategy.HABIT)))
            .contains(PlanStrategy.GREEDY);
        assertThat(PlanPrimarySelector.select(Set.of(PlanStrategy.EVEN_SPLIT, PlanStrategy.HABIT)))
            .contains(PlanStrategy.EVEN_SPLIT);
        assertThat(PlanPrimarySelector.select(Set.of(PlanStrategy.HABIT))).contains(PlanStrategy.HABIT);
    }

    @Test
    @DisplayName("저장된 전략이 없으면 비어 있다")
    void emptyWhenNothingSaved() {
        assertThat(PlanPrimarySelector.select(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("HABIT은 비교 기준이라 다른 전략이 있으면 고르지 않는다")
    void habitIsOnlyTheLastResort() {
        assertThat(PlanPrimarySelector.select(List.of(PlanStrategy.HABIT, PlanStrategy.EVEN_SPLIT)))
            .contains(PlanStrategy.EVEN_SPLIT);
    }

    @Test
    @DisplayName("모든 전략이 우선순위에 한 번씩 들어 있다 (전략을 추가하고 빠뜨리지 않도록)")
    void priorityCoversEveryStrategyOnce() {
        assertThat(PlanPrimarySelector.PRIORITY).containsExactlyInAnyOrder(PlanStrategy.values());
        assertThat(PlanPrimarySelector.PRIORITY).doesNotHaveDuplicates();
    }

}
