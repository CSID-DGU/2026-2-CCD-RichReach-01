package com.richreach.plan.optimizer.cpsat;

import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.optimizer.PlanStrategyInput;
import com.richreach.plan.optimizer.PlanStrategyResult;
import com.richreach.plan.optimizer.PlanStrategyRunner;
import org.springframework.stereotype.Component;

/**
 * <h1>CP-SAT 플랜 전략 (구현 대기)</h1>
 *
 * <p><b>CP-SAT 담당자는 이 파일의 {@link #run}을 채우면 된다.</b> 지금은 "아직 구현되지 않았다"는 실패 결과만 돌려주는 빈 틀이다.
 * 이 클래스가 이미 스프링 빈으로 등록돼 있어서, {@code run}만 채우면 플랜 생성에 자동으로 꽂힌다.
 * (플랜 생성 서비스는 CP-SAT가 실패하면 GREEDY 결과를 쓰므로, 구현 전에도 서비스는 정상 동작한다.)
 *
 * <h2>풀어야 할 문제</h2>
 * <pre>
 * 입력: 업종별 예산 B[c] (사용자가 확정한 이번 달 업종별 소비 계획)
 *       카드 목록 K (사용자가 분석 대상으로 고른 카드, 카드마다 전월 실적 포함)
 * 결정: x[k][c] = 카드 k로 업종 c에 쓸 금액(원, 0 이상 정수)
 * 제약: 모든 업종 c에서  sum_k x[k][c] == B[c]        ← 총소비 불변. 예산을 늘리거나 줄이면 안 된다.
 *       카드 혜택의 한도, 전월실적 조건, 공유 한도 등 카드 규칙
 * 목표: 모든 카드의 예상 혜택 합을 최대로
 * </pre>
 * 추가 지출이나 신규 카드는 가정하지 않는다. 입력 카드 안에서 같은 소비를 다시 나누는 문제다.
 *
 * <h2>구현 순서</h2>
 * <ol>
 *   <li><b>혜택 규칙 읽기</b>: 카드마다 {@code BenefitRuleQueryService#findForCalculation(cardProductId)}로 규칙
 *       ({@code CalculationRule})을 얻는다. 카드 엔티티나 리포지토리를 직접 읽지 않는다. (다른 도메인이다.)
 *       규칙에는 혜택 비율, 월 한도, 공유 한도(capGroupId), 전월실적 구간, 적용 업종({@code categories})이 이미 화면용 업종으로 변환돼 있다.</li>
 *   <li><b>모델 만들기</b>: 변수 x[k][c]를 만들고 위 제약을 건다. CP-SAT은 정수만 다루므로 금액은 원 단위 정수로,
 *       비율(%)은 정수 비율(예: 1.5% → 15/1000)로 바꿔서 곱한다.</li>
 *   <li><b>풀기</b>: {@code input.timeLimit()}를 풀이 시간 제한으로 쓰고, 시드는 고정한다. (같은 입력이면 같은 결과)</li>
 *   <li><b>결과 돌려주기</b>: 아래 "결과 돌려주기" 표를 따른다.</li>
 *   <li><b>검증(권장)</b>: 풀이가 낸 배분을 {@code BenefitCalculator}로 카드별로 다시 계산해서 예상 혜택을 확인한다.
 *       임시 계산기와 값이 크게 다르면 모델이 규칙을 잘못 옮긴 것이다.</li>
 * </ol>
 *
 * <h2>결과 돌려주기</h2>
 * <table>
 *   <caption>풀이 상태별 반환</caption>
 *   <tr><th>상황</th><th>반환</th></tr>
 *   <tr><td>최적해를 찾음</td><td>{@code PlanStrategyResult.success(allocations, SolverStatus.OPTIMAL, 걸린시간ms, ...)}</td></tr>
 *   <tr><td>시간 안에 최적은 못 찾았지만 해는 있음</td><td>{@code success(..., SolverStatus.FEASIBLE, ...)}</td></tr>
 *   <tr><td>조건이 충돌해 해가 없음</td><td>{@code failure(SolverStatus.INFEASIBLE, 걸린시간ms, "어떤 조건이 문제인지")}</td></tr>
 *   <tr><td>시간 안에 해를 못 찾음</td><td>{@code failure(null, 걸린시간ms, "시간 제한 안에 해를 찾지 못했습니다.")}</td></tr>
 *   <tr><td>혜택 규칙이 하나도 없음</td><td>{@code failure(null, null, "...")} 또는 혜택 0인 균등 배분 성공 (팀과 합의)</td></tr>
 * </table>
 * 예외를 던지지 말고 실패 결과로 알린다. 실패하면 플랜 생성이 GREEDY 결과로 대신한다.
 *
 * <h2>꼭 지킬 것</h2>
 * <ul>
 *   <li>업종마다 {@code sum_k targetAmount == input.budgetByCategory()[c]}. 어기면 플랜 생성이 이 결과를 버린다.</li>
 *   <li>배분 결과에는 {@code CardInput#userCardId()}(보유카드 ID)를 쓴다. 카드 상품 ID가 아니다.</li>
 *   <li>같은 (카드, 업종) 조합은 한 번만 낸다.</li>
 *   <li>{@code confirmedOnly}: 사람이 원문과 대조해 확인한 규칙({@code ruleEngineReady})만 썼으면 true. 확인 전 규칙이 섞였으면 false.
 *       어떤 규칙을 쓸지는 혜택 계산 설정({@code richreach.benefit.rule-selection})을 따라가는 것을 권장한다.</li>
 *   <li>계산할 수 없어서 뺀 규칙은 {@code notes}에 이유를 남긴다. (빈 값을 0이나 무제한으로 해석하지 않는다.)</li>
 *   <li>소비 금액, 가맹점, 카드 번호를 로그에 남기지 않는다. (카드 ID, 개수, 풀이 상태, 시간은 괜찮다.)</li>
 * </ul>
 *
 * <h2>의존성과 PR</h2>
 * <ul>
 *   <li>OR-Tools는 {@code backend/build.gradle}에 추가하고, 버전과 이유를 PR 본문에 적는다.</li>
 *   <li>테스트는 {@code src/test/java/com/richreach/plan/optimizer/cpsat/}에 둔다. 최소한 총소비 불변, 시간 제한, 같은 입력의 같은 결과,
 *       해가 없을 때의 실패 결과를 확인한다. 총소비 불변과 카드 범위는
 *       {@code assertThat(result.violations(input)).isEmpty()} 한 줄로 확인할 수 있다.</li>
 *   <li>PR 대상은 develop이다. 이 파일과 같은 패키지의 새 파일만 고치는 것이 가장 안전하다.</li>
 * </ul>
 */
@Component
public class CpSatPlanStrategyRunner implements PlanStrategyRunner {

    /** 구현 전에 돌려주는 실패 이유. 구현하면서 이 상수와 아래 반환을 지운다. */
    static final String NOT_IMPLEMENTED = "CP-SAT 전략이 아직 구현되지 않았습니다.";

    @Override
    public PlanStrategy strategy() {
        return PlanStrategy.CP_SAT;
    }

    @Override
    public PlanStrategyResult run(PlanStrategyInput input) {
        // ▶▶▶ 여기를 구현하세요 (CP-SAT 담당자) ◀◀◀
        //
        // 1) input.cards()의 카드마다 혜택 규칙을 읽는다.            → BenefitRuleQueryService#findForCalculation(cardProductId)
        // 2) 변수 x[카드][업종]과 "업종별 합 == 예산" 제약, 카드 규칙 제약을 건다. → input.budgetByCategory()
        // 3) 예상 혜택 합을 최대화하도록 목적함수를 만들고, input.timeLimit() 안에서 푼다.
        // 4) 풀이 결과를 PlanStrategyResult.success(...) 또는 failure(...)로 돌려준다.
        //
        // 아래 반환은 구현 전 임시값이다. 구현하면 지운다.
        return PlanStrategyResult.failure(null, null, NOT_IMPLEMENTED);
    }

}
