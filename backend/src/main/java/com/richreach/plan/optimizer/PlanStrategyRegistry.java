package com.richreach.plan.optimizer;

import com.richreach.plan.entity.PlanStrategy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 전략 이름으로 구현체를 찾는 곳. 스프링이 {@link PlanStrategyRunner} 빈을 모두 모아서 넘겨주므로,
 * 새 전략은 빈만 만들면 자동으로 여기에 등록된다. (따로 등록하는 코드는 없다.)
 * 같은 전략을 맡은 구현체가 둘 이상이면 앱을 시작할 때 오류로 알린다.
 */
@Component
public class PlanStrategyRegistry {

    private final Map<PlanStrategy, PlanStrategyRunner> runners = new EnumMap<>(PlanStrategy.class);

    public PlanStrategyRegistry(List<PlanStrategyRunner> found) {
        for (PlanStrategyRunner runner : found) {
            PlanStrategyRunner previous = runners.put(runner.strategy(), runner);
            if (previous != null) {
                throw new IllegalStateException("같은 전략을 맡은 구현체가 둘 이상입니다: " + runner.strategy());
            }
        }
    }

    /** 전략의 구현체. 없으면 비어 있다. */
    public Optional<PlanStrategyRunner> find(PlanStrategy strategy) {
        return Optional.ofNullable(runners.get(strategy));
    }

}
