/**
 * <h1>CP-SAT 월간 카드 배분 전략</h1>
 *
 * <p>업종별 월간 예산을 보유카드에 배분한다. 입력은 {@code PlanStrategyInput},
 * 출력은 {@code PlanStrategyResult}이며 DB에 직접 쓰지 않는다.
 *
 * <h2>어디에 무엇을 넣나</h2>
 * <table>
 *   <caption>CP-SAT 담당자의 작업 위치</caption>
 *   <tr><th>할 일</th><th>위치</th></tr>
 *   <tr><td>전략 진입점</td><td>{@code CpSatPlanStrategyRunner.java}</td></tr>
 *   <tr><td>모델 만들기·풀기 등 보조 클래스</td><td>이 패키지 안에 새 파일로 추가 (예: {@code CpSatModelBuilder.java})</td></tr>
 *   <tr><td>테스트</td><td>{@code src/test/java/com/richreach/plan/optimizer/cpsat/}</td></tr>
 *   <tr><td>OR-Tools 의존성</td><td>{@code backend/build.gradle}에 추가하고, PR 본문에 적는다</td></tr>
 * </table>
 *
 * <p>현재 최적화 모델은 업종별 비율 혜택, 전월실적 구간, 규칙별 월 한도와
 * 공유 월 한도를 다룬다. 가맹점·거래 순서·일/횟수 한도·다음 달 실적 가치는
 * 현재 입력만으로 확정할 수 없으므로 결과의 notes에 모델 범위를 남긴다.
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
