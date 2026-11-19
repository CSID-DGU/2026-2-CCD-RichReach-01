# 카드 추천 대화와 월간 플랜 연동: 백엔드 전달 명세

## 현재 연결 상태

| 기능 | 상태 | 연결 지점 |
|---|---|---|
| 금액·업종을 직접 고른 결제 전 추천 | PR #300에 구현. 병합·배포 전 | `POST /api/recommendations/manual` |
| 카드 추천 대화 화면 | 가짜 응답 사용 중 | `frontend/lib/features/recommendation/application/recommendation_providers.dart`의 `FakeRecommendationRepository` 교체 필요 |
| 월 예산 대화 수정안 | 구현됨. 저장하지 않는 미리보기 | `POST /api/plans/chat/preview` |
| 대화 수정안 확정과 재계산 | 미구현. #277에서 연결 예정 | 아래 `챗봇 수정안 확정` 참고 |
| 자연어 결제 질문·가맹점 확인·근거 검색 | 미구현 | 아래 단계별 계약 참고 |
| 카드 연결 해제 뒤 재계산 | PR #299 검토 중 | #299의 CI 시계 오류는 별도 PR #301에서 수정 제안 |

이 문서는 **구현 상태와 후속 작업을 구분**한다. 이전 `recommendation-api.md`의 `POST /api/recommendations`, 구간별 추천, 가맹점별 1만 원 할인 등의 JSON은 화면 설계를 위한 예시이며 현재 서버 응답이 아니다. 현재 동작을 확인할 때는 `manual-card-recommendation.md`를 기준으로 한다.

## 공통 데이터 기준: 카드 331종과 업종 11개

- 331종은 수집한 **카드 상품** 수다. 입력 업종은 `SpendingCategory`의 `DINING`, `CAFE`, `CONVENIENCE_STORE`, `SHOPPING`, `TRANSPORT`, `FUEL_VEHICLE`, `LIFE_HEALTH`, `TELECOM_SUBSCRIPTION`, `CULTURE_TRAVEL`, `EDUCATION`, `ETC` 11개다.
- 추천·플랜의 카드 후보는 `user_cards`에서 해당 사용자의 `active=true`, `selected=true`, `card_product_id`가 존재하는 카드다. 331종 전체를 사용자가 가진 카드처럼 비교하지 않는다.
- 331종이 `stg_card_catalog`에 있다는 사실만으로 API가 쓸 수 없다. 실제 상품은 `card_products`, 계산 규칙은 `benefit_rules`, 실제 보유·상품 매칭은 `user_cards`에서 확인한다. 현재 승격 도구는 MVP로 선택한 상품만 최종 테이블에 옮길 수 있다.
- `rule_engine_ready=true`는 원문 검토 표시다. 이것만으로 계산 가능하지 않다. 현재 `SimpleBenefitCalculator`는 비율 혜택 중 가맹점 지정, 결제수단 지정, 변동 요율, 월 한도 미확정, 일별·횟수·할인대상금액 한도, 다른 혜택과의 대체 관계, 마일리지 등 정확히 처리할 수 없는 규칙을 제외한다. 추천 수치는 **계산에 포함된 규칙의 추가 혜택**이다. 카드의 모든 실제 혜택을 뜻하지 않는다. 자유 문장 `conditions`와 실적 제외 원문이 실제 규칙에 반영됐는지는 카드별 원문 검토가 필요하다.
- 전월실적은 카드사의 확정 실적이 아니라 수집 거래로 계산한 추정값이다. 카드사 실적 제외 조건이 완전히 구조화되지 않은 상품에는 차이가 날 수 있다.

### 배포 DB에서 먼저 확인할 값

아래 SQL은 MySQL 최종 테이블을 읽기만 한다. 개인정보가 포함될 수 있는 행 전체를 출력하지 말고, 시연 사용자 ID를 서버 내부에서 지정해 집계값만 확인한다.

```sql
SELECT COUNT(*) AS final_products FROM card_products;
SELECT COUNT(*) AS final_rules,
       COALESCE(SUM(rule_engine_ready = TRUE), 0) AS reviewed_rules
FROM benefit_rules;
SELECT COUNT(DISTINCT card_product_id) AS products_with_reviewed_rules
FROM benefit_rules WHERE rule_engine_ready = TRUE;
SELECT COUNT(*) AS selected_matched_cards
FROM user_cards
WHERE user_id = :demo_user_id AND active = TRUE AND selected = TRUE
  AND card_product_id IS NOT NULL;
SELECT uc.id AS user_card_id, COUNT(br.id) AS reviewed_rule_count
FROM user_cards uc
LEFT JOIN benefit_rules br ON br.card_product_id = uc.card_product_id
    AND br.rule_engine_ready = TRUE
WHERE uc.user_id = :demo_user_id AND uc.active = TRUE AND uc.selected = TRUE
GROUP BY uc.id;
```

`reviewed_rule_count`가 0이거나 전부 현재 계산기의 제외 조건에 해당하면 추천은 `TEXT`로 돌아온다. 2026-11-10 로컬 점검표에는 331종 혜택 4,147행 중 계산 승인 규칙이 0행이었다. **현재 공용 DB의 상태는 아직 검증하지 않았다.** 백엔드가 시연 대상 보유카드와 연결된 규칙을 원문·조건과 대조해 승인한 뒤, 실제 API 응답을 확인해야 한다. 숫자를 만들기 위해 미검토 규칙을 일괄 승인하거나 `rule-selection=ALL`을 운영에 켜지 않는다.

## 1단계: 수동 입력 추천과 LLM 설명

화면에서 금액과 11개 업종 중 하나를 직접 입력한다. 백엔드는 로그인 사용자의 선택 보유카드, 전월·당월 수집 거래, 계산 가능한 혜택 규칙으로 카드별 **지금 결제 1건의 추가 혜택**을 비교한다. 이 요청은 거래 승인이나 월간 플랜 재계산을 일으키지 않는다.

```http
POST /api/recommendations/manual
Content-Type: application/json

{"category":"CAFE","amount":10000}
```

응답은 공통 `ApiResponse.data`에 `type`, `message`, `parsed`, `card`, `alternatives`, `calculationScope`를 담는다. `type=CARD`이면 서버가 계산한 양수 혜택이 있는 선택 카드가 있고, `type=TEXT`이면 **현재 검증된 규칙으로 추가 혜택을 확인하지 못했다**. `card.cardId`는 사용자 보유카드 ID, `card.cardProductId`는 상품 ID다. 카드 이미지 URL은 아직 `null`일 수 있다. 정확한 필드·예시는 `manual-card-recommendation.md`를 본다.

GPT-5 nano는 서버가 이미 결정한 결과를 짧게 설명하는 선택 단계다. 모델이 카드, 금액, 순위를 정하지 않는다. 모델 응답이 없거나 깨진 문자·숫자·확정 표현을 포함하면 서버의 고정 문구만 돌려준다. API 키는 서버 환경변수에만 둔다. 한글 출력과 정확한 업종 해석은 별개이므로 실제 문구 샘플을 검수한다.

**프론트 연결:** 추천 대화 화면의 `FakeRecommendationRepository`는 아직 이 엔드포인트를 호출하지 않는다. 금액·업종 선택 UI 또는 기존 화면에서 이 두 값을 확보한 뒤 실제 repository로 교체하고, `CARD`/`TEXT`·오류·로딩 상태를 처리해야 한다. 구현 전까지 이 PR만 병합해도 화면의 가짜 추천은 바뀌지 않는다.

## 2단계: 자연어 결제 질문

예: “다이소에서 3만 원 결제할 건데 뭐 써?” 자연어 입력용 API는 아직 없다. 다음 인터페이스를 **후속 구현 계약**으로 제안한다. 기존 수동 API의 카드 계산 로직을 재사용한다.

```http
POST /api/recommendations/chat
Content-Type: application/json

{"question":"다이소에서 3만 원 결제할 건데 뭐 써?","conversationId":null}
```

| 필드 | 처리 |
|---|---|
| `question` | 서버에서 길이·빈 값 검사. 모델에는 카드번호·연락처를 넣지 않는다. |
| `conversationId` | 이어지는 “그럼 5만 원이면?”을 지원할 때 서버가 소유·만료·대화 길이를 검증한다. 첫 단계는 `null`로 시작해도 된다. |
| 모델이 추출한 `amount` | `1~100,000,000` 정수인지 서버가 다시 확인한다. `3만 원`은 `30000`이다. |
| 모델이 추출한 `merchantName`·`category` | 서버의 가맹점 분류·사용자 확인 결과와 대조한다. 모델이 제안한 업종을 그대로 확정하지 않는다. |

Function Calling을 쓰면 모델이 `recommend_card({amount,category,merchantName})` 같은 구조화된 인자를 제안하고 **백엔드가** 검증 후 기존 추천 서비스를 호출한다. 모델에게 DB 접근이나 직접 결제·플랜 변경 권한을 주지 않는다. 금액 또는 업종이 없거나 불확실하면 `FOLLOW_UP`으로 필요한 값만 되묻고 추천 금액을 반환하지 않는다. 서버 검증이 끝난 입력에만 `ManualRecommendationService`의 카드 비교를 재사용한다. 이 엔드포인트와 `FOLLOW_UP` 응답은 아직 구현되지 않았다.

## 3단계: 가맹점·업종 불확실성과 간편결제

현재 `merchant_category_rules`와 `MerchantCategoryClassifier`는 가맹점명 패턴으로 거래를 11개 업종에 분류하며, 매칭되지 않으면 `ETC`다. 이 분류는 “그 가맹점에서 실제로 무엇을 샀는가”까지 알려주지 않는다. 예를 들어 `동국대학교` 한 가맹점명에 학식과 서점 결제가 모두 있으면 가맹점명만으로 `DINING`과 `EDUCATION`을 확정할 수 없다.

후속 채팅 응답은 `type=FOLLOW_UP`, `question="학식 결제인가요, 서점 결제인가요?"`, `parsed={merchantName,amount,category:null}`, `missing=["category"]`처럼 추가 질문이 필요한 상태를 표현한다. 사용자가 선택한 업종을 새 요청에 명시적으로 보내면 그 값을 사용하고, 실제 승인 거래의 업종을 고칠 때는 기존 거래 업종 수정 API를 사용한다. 가맹점명에 대한 모델의 자신감 숫자만으로 할인액을 확정하지 않는다.

특정 가맹점 혜택, 간편결제 조건은 현재 수동 추천 입력과 `SimpleBenefitCalculator`가 처리하지 않는다. `payment_methods`가 지정된 규칙은 결제 수단 확인 전까지 계산에서 제외한다. 가맹점 DB를 추가할 때는 정규화 상호명, 가맹점 식별자, 후보 업종, 매칭 근거·검토 상태를 저장하고, 여러 업종 후보가 가능하면 사용자에게 확인한다. 결제수단을 알 수 없는 CODEF 승인내역만으로 간편결제 여부를 추정하지 않는다.

## 4단계: 카드 약관 근거 검색

카드사 상품설명서·약관 원문을 카드 상품과 규칙에 연결하고, `source_url`, 원문 버전 또는 해시, 적용 시작·종료일, 조회일, 조항 위치를 함께 보관한 뒤 검색한다. 모델이 검색 문서를 요약할 수는 있지만 추천 금액은 검증된 규칙 계산값으로 유지한다. 응답의 근거에는 **실제로 검색된 조항**만 넣고 상품명·규칙 ID·문서 버전·기준일을 함께 준다. 구버전 약관은 해당 결제일에 유효했는지 확인한다.

현재 원문 추적용 `source_scope_id`·상품 `source_url`은 있지만, 약관 전체의 버전별 검색 인덱스와 응답 근거 계약은 구현되지 않았다. 근거 문서를 수집·검토하기 전에는 RAG가 동작한다고 표시하지 않는다.

## 챗봇 수정안 확정과 월간 플랜 재계산: #277

현재 `POST /api/plans/chat/preview`는 `message`, `currentPlan`, `currentPreference`, `history`를 받아 `needsClarification`, `requiresConfirmation`, `proposedPlan`, `proposedPreference`를 돌려준다. **이 응답은 저장·재계산을 하지 않는다.** `currentPlan`은 클라이언트가 보낸 초안이므로 확정 단계에서 서버의 최신 플랜과 비교해야 한다.

#277에는 다음 흐름을 제안한다.

후속 확정 API의 제안 경로는 `POST /api/plans/chat/confirm`이다. 요청은 `basePlanId`(미리보기를 시작할 때 본 플랜 ID), `proposedPlan`(`PlanCreateRequest`와 같은 월·총액·업종별 예산), `proposedPreference`(`MONTHLY_PLAN` 또는 `IMMEDIATE_DISCOUNT`)로 한다. 서버가 사용자·월·기준 플랜을 검증한 뒤 `ApiResponse.data.jobId`를 반환한다. 아직 구현된 경로가 아니므로 프론트에서 호출하지 않는다.

1. 프론트가 `requiresConfirmation=true`인 수정안을 화면에 보여주고 사용자의 확정을 받는다. 모호한 답변은 확정 버튼을 띄우지 않는다.
2. 확정 요청에 `basePlanId`, `proposedPlan`, `proposedPreference`를 담는다. 백엔드는 로그인 사용자 소유, 대상 월이 이번 달인지, 현재 최신 `planId`와 `basePlanId`가 같은지, 예산 합계·11개 업종·범위·전략 값을 다시 검사한다. 값이 바뀐 상태면 `409`와 최신 플랜 재조회 안내를 준다.
3. 예산 변경은 기존 `PUT /api/plans/budget`의 작업 등록·동시성 제어·계산 경로를 재사용하되 변경 사유는 서버가 `CHAT_CONFIRMED`로 저장한다. `PlanRecalculationWorker`에서 새 사유도 예산 수정 요청을 읽도록 확장해야 한다. 사유 문구는 `PlanChangeReason`의 고정 문자열로 두고 모델 답변을 저장하지 않는다. #299의 `CARD_DISCONNECTED` 방식과 같다.
4. 확정 API는 `jobId`를 돌려준다. 프론트는 `GET /api/plans/jobs/{jobId}`를 1~2초 간격으로 조회하고, `COMPLETED` 후 `GET /api/plans?month=...`와 `GET /api/plans/{planId}/changes`를 새로 읽는다. 실패하면 이전 플랜을 유지한다.
5. `proposedPreference`만 바뀐 경우 현재 코드에는 전략 선택값 저장·적용 경로가 없다. 이 값을 저장할 위치와 플랜 전략 선택 연결을 먼저 구현한 뒤 확정 API에 포함한다. 단순히 변경 문구만 추가하면 실제 계산 방향은 바뀌지 않는다. 같은 요청의 중복 전송은 동일 작업을 재사용하거나 멱등 키로 막는다.

**중요:** 기존 거래 동기화·업종 수정·예정지출 변경은 이미 이벤트로 남은 기간 재계산을 시작한다. 챗봇 수정안의 저장은 이 이벤트들과 다른 **사용자 확정 동작**이다. `preview` 호출마다 재계산하거나 원문 채팅을 최적화 입력으로 넘기지 않는다.

## 백엔드 적용 순서와 완료 기준

1. #301을 #299 작업 브랜치에 반영해 Linux CI를 다시 확인한다. #299가 성공하기 전에는 `develop`에 병합하지 않는다. 로컬 Windows OR-Tools DLL 차단 설명은 이번 CI 실패 원인과 별개다.
2. #300의 코드와 `manual-card-recommendation.md`를 검토한다. DB 집계 SQL과 시연 사용자 보유카드 매칭을 확인한다. 계산 승인 규칙이 없으면 `TEXT`가 정상이다.
3. 프론트와 1단계 수동 입력 화면을 실제 API에 연결하고, 양수 혜택·0원·대상 카드 없음·미검토 규칙·인증 실패를 같은 빌드에서 검증한다.
4. #277 확정 API를 위 검증·동시성 규칙으로 구현하고, 사용자가 확정한 예산이 실제 다음 플랜에 저장되며 변경 사유가 고정 문자열인지 검증한다.
5. 자연어 질문, 가맹점 확인, 간편결제 조건, 약관 근거 검색은 각각 필요한 데이터와 테스트가 준비된 순서대로 추가한다. 구현되지 않은 화면 응답을 완료된 기능처럼 연결하지 않는다.

백엔드 테스트 최소 사례: 카드 331종을 스테이징에 적재해도 미선택 카드가 추천되지 않음; 같은 금액·업종의 후보 카드 동률; 전월실적 경계값; 월 한도 소진; `rule_engine_ready=false`; 가맹점 불명·학식/서점 중복; 간편결제 수단 미확인; GPT-5 nano의 깨진 글자·근거 없는 숫자·응답 실패; 챗봇 수정안 확정 직전 거래로 플랜 버전 변경; 동일 확정 요청 재전송; 재계산 실패 시 이전 플랜 유지.
