# 승인 거래 반영과 월간 플랜 재계산

## 이번 변경의 계산 범위

- `RollingPlanReplanner.replan(RollingPlanInput)`은 **이번 달 승인 거래 전체의 현재 스냅샷**을 받아 남은 업종 예산과 카드별 목표액을 다시 계산한다.
- 이미 승인된 거래의 사용 카드는 고정한다. CP-SAT은 해당 카드·업종 금액을 하한으로 두고 앞으로 쓸 금액만 배분한다.
- 실제 소비가 예산을 넘은 업종의 남은 목표액은 0원이다. 혜택을 위해 예산 밖 소비를 추가하지 않는다.
- 일별 한도, 이용 횟수 한도, 할인대상 결제금액 한도가 있으나 현재 업종별 월 합계로 검증할 수 없는 규칙은 혜택 예상액에서 제외한다. 기존의 가맹점 지정·정액·특수 관계 규칙 제외도 유지한다. 기본 설정 `BENEFIT_RULE_SELECTION=READY_ONLY`를 사용한다.
- 성공 결과에도 `solverStatus=FEASIBLE`이면 제한 시간 내 발견한 해이며 최적성 증명은 아니다. 실패하면 기존 플랜을 유지한다.

## 백엔드 연결 위치

1. CODEF 동기화가 `TransactionStoreService.saveCollected(...)`을 마치거나 거래 업종이 수정된 뒤, 해당 월에 확정된 플랜이 있을 때 재계산 작업을 예약한다. `created=0`이고 `updated=0`인 재동기화는 건너뛸 수 있다.
2. 해당 월의 **카드 ID × 화면 업종** 승인 순금액을 조회한다. 기간은 승인일 기준이며 업종은 `categoryOverride`가 있으면 이를 우선한다. 금액은 `amount - canceledAmount`로 집계한다. 전액 취소는 0원이다. 현재 `TransactionSummaryService`에는 업종별 합계와 카드별 합계가 각각 있지만 카드×업종 합계는 없으므로 조회 메서드를 추가해야 한다.
3. 사용자가 확정한 업종별 예산, 보유카드 ID·상품 ID, 카드별 전월 사용액 추정치와 2번의 스냅샷으로 `RollingPlanInput`을 만든다. 이미 사용한 뒤 분석 대상에서 빠진 카드도 과거 금액을 줄이기 위해 포함하고 `eligibleForFuture=false`로 둔다. 새로 배분할 수 있는 카드만 `true`다. 실제 승인 거래가 들어온 경우 **그 거래까지 포함한 전체 스냅샷**을 넘긴다. 이벤트 금액을 이전 스냅샷에 한 번 더 더하면 중복된다.
4. `RollingPlanReplanner.replan(input)`을 호출한다. `remainingAllocations`만 새 미래 목표액으로 저장하고, 기존 승인 거래는 변경하지 않는다. `approvedByCategory`와 `remainingByCategory`의 합을 화면에 사용한다. 결과의 `estimatedApprovedBenefit`도 카드사 실제 적립액이 아닌 검증 가능한 규칙에 따른 추정치다.
5. 플랜을 버전으로 저장하고 최신 버전을 조회하도록 연결한다. 현재 `POST /api/plans`는 해당 월의 플랜이 이미 있으면 `PLAN-004`를 반환하므로 재계산을 위해 이 API를 반복 호출하면 안 된다. 재계산용 별도 서비스·저장 경로가 필요하다. 동일 동기화의 중복 실행과 동시 수정은 사용자 단위 잠금 또는 작업 키로 방지한다.
6. 계산 실패 시 이전 플랜을 보존하고 재시도 상태를 남긴다. `FEASIBLE`은 그대로 표시하거나 정책상 재시도하되 `OPTIMAL`로 바꾸어 표시하지 않는다.

## 챗봇 수정과의 연결

`POST /api/plans/chat/preview`는 GPT-5 nano로 사용자의 문장을 해석하여 `proposedPlan`(확정 후 업종별 **최종 금액**)과 `proposedPreference`를 제안한다. 응답의 `requiresConfirmation=true`는 저장 완료가 아니다. 화면에서 사용자 확인을 받은 뒤 백엔드가 이 구조화된 값만 검증·저장하고, 그 시점의 승인 거래 스냅샷으로 `RollingPlanReplanner`를 호출한다. 금액이나 업종이 모호해 `needsClarification=true`이면 추가 질문을 표시하고 계산하지 않는다.

원문 대화 전체를 CP-SAT에 보내지 않는다. API 호출은 채팅 메시지 해석에만 쓰고, 승인 거래가 들어올 때의 재계산에는 쓰지 않는다. `proposedPreference=IMMEDIATE_DISCOUNT`라면 앞으로 결제하기 **전** 카드 선택에는 `ImmediateDiscountSelector`를 쓰고, 이미 승인된 거래의 카드는 변경하지 않는다. 현재 선택 모드의 영속화와 결제 전 추천 API는 백엔드 연결 작업에 포함된다.

## 검증 범위

`RollingPlanReplannerTest`에서 이미 쓴 카드 고정, 두 번째 승인 후 재계산, 중간에 제외한 카드, 예산 초과, 지원되지 않는 횟수 한도 제외, 환불 뒤 스냅샷 교체, 카드 3장·승인 200건을 합산한 사례를 확인한다. 이 테스트는 규칙을 메모리에 둔 알고리즘 테스트이며 실제 카드 3종의 카드사 적립 결과를 검증한 것은 아니다. 실제 카드 검증에는 카드사 근거와 DB 규칙, 거래 원장을 대조해야 한다.

로컬 실행: `./gradlew test --tests com.richreach.plan.optimizer.RollingPlanReplannerTest --tests com.richreach.plan.optimizer.cpsat.CpSatPlanStrategyRunnerTest --tests com.richreach.plan.optimizer.ImmediateDiscountSelectorTest`
