package com.richreach.plan.optimizer;

import com.richreach.plan.entity.PlanStrategy;

/**
 * 월간 플랜 전략 하나. 카드와 업종별 예산을 받아서 "어느 카드로 어느 업종을 얼마 쓸지"를 정한다.
 * HABIT(기존 습관), GREEDY(탐욕), CP_SAT(최적화)가 각각 이 인터페이스를 구현한다.
 *
 * <p>플랜 생성 서비스는 {@link PlanStrategyRegistry}에서 {@link PlanStrategy} 값으로 구현체를 찾아 {@link #run}을 부르므로,
 * 새 전략은 <b>이 인터페이스를 구현한 스프링 빈을 하나 만들기만 하면</b> 플랜 생성에 꽂힌다. 플랜 생성 쪽 코드는 고치지 않는다.
 *
 * <h2>새 전략을 만드는 사람에게</h2>
 * <ol>
 *   <li>{@link #strategy()}로 자기 전략 이름을 알린다. (CP-SAT이면 {@code PlanStrategy.CP_SAT})</li>
 *   <li>{@link #run}에서 {@link PlanStrategyInput}을 받아 {@link PlanStrategyResult}를 돌려준다.</li>
 *   <li>같은 전략 이름의 구현체는 하나만 있어야 한다. (둘이면 앱이 시작되지 않는다.)</li>
 * </ol>
 * CP-SAT 구현은 {@code plan/optimizer/cpsat/CpSatPlanStrategyRunner.java}를 채우면 된다. 거기에 자세한 안내가 있다.
 *
 * <h2>모든 구현이 지킬 약속</h2>
 * <ul>
 *   <li><b>총소비 불변</b>: 업종마다 배분 금액의 합이 입력의 업종 예산과 정확히 같아야 한다. ({@link PlanStrategyResult} 참고)</li>
 *   <li><b>읽기만</b>: DB에 쓰지 않고, 입력을 바꾸지 않는다. 저장은 플랜 생성 서비스가 한다.</li>
 *   <li><b>예외 대신 실패 결과</b>: 풀 수 없거나 시간이 부족하면 예외를 던지지 말고 {@link PlanStrategyResult#failure}를 돌려준다.
 *       (예외는 예상하지 못한 오류에만 쓴다.)</li>
 *   <li><b>시간 제한 지키기</b>: {@link PlanStrategyInput#timeLimit()}을 넘기지 않는다.</li>
 *   <li><b>같은 입력이면 같은 결과</b>: 무작위를 쓰면 시드를 고정한다.</li>
 *   <li><b>소비 정보는 로그에 남기지 않는다</b>: 금액, 가맹점, 카드 번호를 로그에 찍지 않는다. (ID와 개수, 상태는 괜찮다.)</li>
 *   <li><b>다른 도메인의 엔티티와 리포지토리를 직접 읽지 않는다</b>: 카드 혜택은 카드 서비스({@code BenefitRuleQueryService})나
 *       혜택 계산기({@code BenefitCalculator})를 거쳐서 얻는다.</li>
 * </ul>
 */
public interface PlanStrategyRunner {

    /** 이 구현이 맡는 전략. 전략마다 구현체는 하나다. */
    PlanStrategy strategy();

    /**
     * 플랜을 계산한다. 풀 수 없으면 예외 대신 {@link PlanStrategyResult#failure}를 돌려준다.
     * 같은 입력이면 같은 결과를 내야 한다.
     */
    PlanStrategyResult run(PlanStrategyInput input);

}
