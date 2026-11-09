/**
 * <h1>CP-SAT 전략 코드를 넣는 곳</h1>
 *
 * <p>이 패키지({@code com.richreach.plan.optimizer.cpsat})는 CP-SAT(OR-Tools) 담당자가 자기 코드를 넣는 자리다.
 * 다른 전략(HABIT, GREEDY)이나 플랜 생성 서비스는 이 패키지를 건드리지 않고, 이 패키지도 그쪽을 건드리지 않는다.
 *
 * <h2>어디에 무엇을 넣나</h2>
 * <table>
 *   <caption>CP-SAT 담당자의 작업 위치</caption>
 *   <tr><th>할 일</th><th>위치</th></tr>
 *   <tr><td>전략 진입점 (여기부터 구현)</td><td>{@code CpSatPlanStrategyRunner.java} — 이미 만들어 둔 빈 틀을 채운다</td></tr>
 *   <tr><td>모델 만들기·풀기 등 보조 클래스</td><td>이 패키지 안에 새 파일로 추가 (예: {@code CpSatModelBuilder.java})</td></tr>
 *   <tr><td>테스트</td><td>{@code src/test/java/com/richreach/plan/optimizer/cpsat/}</td></tr>
 *   <tr><td>OR-Tools 의존성</td><td>{@code backend/build.gradle}에 추가하고, PR 본문에 적는다</td></tr>
 * </table>
 *
 * <h2>작업 순서 (권장)</h2>
 * <ol>
 *   <li>{@code PlanStrategyRunner}, {@code PlanStrategyInput}, {@code PlanStrategyResult}의 설명을 읽는다.
 *       (한 단계 위 패키지 {@code com.richreach.plan.optimizer})</li>
 *   <li>{@code CpSatPlanStrategyRunner#run}의 "여기를 구현하세요" 부분을 채운다.</li>
 *   <li>풀이 결과가 입력의 업종 예산과 정확히 맞는지(총소비 불변) 테스트로 확인한다.</li>
 *   <li>develop으로 PR을 올린다. (브랜치: {@code back/feature/{이슈번호}-...})</li>
 * </ol>
 *
 * <h2>하지 말아야 할 것</h2>
 * <ul>
 *   <li>플랜 생성 서비스, {@code PlanStrategyRegistry}, {@code monthly_plans} 저장 코드를 고치지 않는다. (연결은 자동이다.)</li>
 *   <li>DB에 쓰지 않는다. 읽기만 한다.</li>
 *   <li>비밀값(키, 접속 주소)이나 {@code .env}를 올리지 않는다.</li>
 *   <li>소비 금액, 가맹점, 카드 번호를 로그에 남기지 않는다.</li>
 * </ul>
 */
package com.richreach.plan.optimizer.cpsat;
