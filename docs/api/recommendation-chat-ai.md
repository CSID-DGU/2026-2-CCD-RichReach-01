# 결제 전 카드 추천 AI 연결

## 흐름과 범위

로그인 사용자의 자연어 결제 질문을 OpenAI가 **금액·업종·가맹점**으로 구조화한다. 서버는 가맹점 분류 결과와 입력을 검증한 뒤 기존 `ManualRecommendationService`를 호출한다. 추천 카드·순위·예상 혜택은 기존 계산기가 정한다. AI의 결과 설명은 선택 기능이고 실패하면 계산기의 고정 문구가 반환된다. 거래와 월간 플랜은 변경하지 않는다.

`POST /api/recommendations/manual`은 그대로 유지한다. 추천 후보도 기존처럼 **로그인 사용자가 분석 대상으로 선택하고 카드 상품에 연결된 보유카드**다. 수집된 카드 331종을 한 사용자에게 모두 추천하지 않는다.

## 자연어 질문 API

`POST /api/recommendations/chat`, 로그인 필요:

```json
{"message":"다이소에서 3만 원 결제할 건데 뭐 써?","history":[],"confirmedCategory":null}
```

`message`는 1~500자, `history`는 최근 최대 10개 `{ "role": "USER" | "ASSISTANT", "content": "..." }`다. 서버는 이력을 저장하지 않으므로 프론트가 이전 대화를 다시 보내야 한다. 각 이력 내용도 1~500자다. 모델에 보낼 때 카드번호·전화번호·이메일을 마스킹하고, OpenAI 요청에는 `store=false`를 붙인다.

응답 `data`의 모양:

```json
{
  "reply": "이번 결제에는 예시카드가 유리해요. 예상 혜택은 1,000원이에요.",
  "needsClarification": false,
  "answerMode": "BEST_CARD",
  "merchantName": "다이소",
  "recommendation": {
    "type": "CARD",
    "message": "추천 카드는 '예시카드'이고, 이번 결제의 예상 혜택은 1,000원이에요.",
    "parsed": {"category":"SHOPPING","amount":30000},
    "card": {"cardId":1,"cardProductId":2,"cardCompany":"KB","cardName":"예시카드",
             "imageUrl":null,"expectedBenefit":1000,"confirmedOnly":true,"notes":[]},
    "alternatives": [],
    "calculationScope": "계산 범위 안내"
  }
}
```

예시는 응답 모양만 보여주는 합성값이다. 실제 카드·혜택은 서버 계산 결과를 따른다. `recommendation`은 기존 `/manual` API와 같은 구조다. 프론트는 `reply`를 말풍선으로, `recommendation.card`와 `alternatives`를 카드 UI로 보여준다. `answerMode=ALTERNATIVES`이면 `alternatives` 목록을 보여준다.

금액·업종이 없으면 `needsClarification=true`, `recommendation=null`이며 서버 고정 질문을 반환한다. 모델이 가맹점 이름을 만들었거나 가맹점 DB 분류와 업종이 충돌해도 계산하지 않는다. DB에 없는 가맹점의 업종은 사용자가 확인해야 한다. 확인한 뒤 **같은 질문**과 `confirmedCategory`(11개 업종 코드 중 하나)를 다시 보낸다. 가맹점 전용 할인은 현재 수동 계산기에서 제외되므로 `merchantName`을 받았다는 이유만으로 적용했다고 안내하면 안 된다.

모델 호출에 실패하면 금액·카드를 추측하지 않고 직접 입력 안내를 돌려준다. 설명 생성이 실패하거나 확인 전 규칙이 섞인 경우에는 서버의 기존 `message`를 사용한다. `OPENAI_API_KEY`는 서버 환경변수로만 둔다. 기본 모델은 `gpt-5-nano`다.

## 카드사 원문 검색 API

카드 상품으로 승격되지 않은 331종도 조회하려면 `POST /api/cards/catalog/evidence`를 사용한다. 로그인 필요:

```json
{"issuerCode":"bc","sourceCardId":"1000027768","question":"점심 캐시백 조건은?"}
```

이미 승격된 상품 ID가 있으면 `POST /api/cards/{cardProductId}/evidence`에 `{"question":"..."}`를 보낼 수 있다. 두 경로 모두 `stg_source_scope`에서 **해당 카드의 원문만** 찾아 최대 3개 조각을 모델에 제공한다. 응답은 `status`(`ANSWERED`, `SOURCE_FOUND`, `INSUFFICIENT_EVIDENCE`), `answer`, `sourceSnapshotDate`, `documentVersion`, `citations[]`다. 각 citation에는 `citationId`, `sourceUrl`, `excerpt`, `sourceType=PRODUCT_PAGE_EXCERPT`가 있다. 모델 응답의 출처 ID가 검색 결과에 없으면 생성 답변은 버리고 원문 조각만 반환한다.

현재 자료는 **카드사 상품 페이지 수집본**이다. `sourceSnapshotDate`는 수집일이지 약관 시행일이 아니다. 331종의 상품설명서·약관 PDF 및 문서 버전은 모두 확보되지 않았으므로 `documentVersion=null`이다. 이 API는 상품 원문 설명용이며, **이번 추천 금액에 실제 적용된 조항이라는 증명은 아니다.** 그런 표시를 하려면 계산기가 적용 혜택 규칙 ID를 반환하고 그 규칙의 `source_scope_id`와 연결해야 한다.

## 프론트·백엔드 연결 시 확인할 점

- 프론트의 `FakeRecommendationRepository`는 여전히 가짜 응답을 준다. 이를 실제 `/chat` 또는 `/manual` 호출로 교체해야 한다.
- 가짜 화면의 `AMOUNT_RANGE`와 브랜드별 `FOLLOW_UP` 카드 UI는 현재 서버 계산 기능에 없다. `/chat`은 금액·업종이 없으면 텍스트로 되묻는다. UI 매핑을 조정하거나 별도 알고리즘을 설계해야 한다.
- 카드 혜택 `rule_engine_ready`가 0건인 환경에서 기본 `READY_ONLY` 계산은 `TEXT`가 된다. AI는 없는 혜택을 만들어 메울 수 없다.
- 이 코드는 **AI API 실제 호출 및 운영 DB를 이용한 앱 통합 시험은 아직 거치지 않았다.** 단위 테스트와 Java 컴파일 결과는 PR 설명에 기록한다.
