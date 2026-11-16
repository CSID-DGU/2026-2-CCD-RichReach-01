# OpenAI 결제 질문 해석 연결

## 현재 구현 범위

백엔드의 `POST /api/recommendations/interpret`는 로그인 토큰이 있는 요청의 질문 한 문장만 OpenAI Responses API에 보낸다. 모델은 업종, 가맹점, 금액, 카드 질문 여부를 구조화한다. `store=false`를 설정하고, 응답 형식은 JSON 스키마로 제한한다. 혜택 금액과 카드 순위는 생성하지 않는다.

현재 Flutter 추천 화면은 `FakeRecommendationRepository`를 사용한다. 이 해석 API만으로는 화면의 카드 추천이 실제 데이터로 바뀌지 않는다. [추천 API 초안](recommendation-api.md)의 `POST /api/recommendations`는 별도로 카드 혜택 계산기와 연결해야 한다.

## 서버 설정

`RichReach-dev` 프로젝트의 키를 서버 환경변수 `OPENAI_API_KEY`로 주입한다. 선택한 모델은 `OPENAI_MODEL=gpt-5-nano`다. 저장소의 `.env.example`은 변수 이름만 보여주며 실제 키를 넣지 않는다. 로컬 `.env` 계열 파일은 `.gitignore`로 제외된다. 배포 환경에서는 서버의 비밀값 관리 기능으로 주입한다.

| 설정 | 값 | 설명 |
| --- | --- | --- |
| `OPENAI_API_KEY` | 비밀값 | 서버만 접근 |
| `OPENAI_MODEL` | `gpt-5-nano` | 질문 해석 모델 |

한 요청에는 최대 200자의 질문만 보내고, 모델의 추론 수준을 `minimal`, 출력 상한을 400토큰으로 제한한다. 카드 거래내역, 카드번호, CODEF 연결 정보, 카드 혜택 원본은 전송하지 않는다. 키가 없거나 모델 응답이 불완전하면 계산 결과를 만들어 내지 않고 오류를 반환한다.

## 요청과 응답

```http
POST /api/recommendations/interpret
Authorization: Bearer <로그인 토큰>
Content-Type: application/json

{"question":"편의점에서 1000원 결제할 때 어떤 카드 써?"}
```

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "category": "CONVENIENCE_STORE",
    "merchantName": null,
    "amount": 1000,
    "cardQuestion": true
  }
}
```

가맹점이 명시되지 않으면 `merchantName`은 `null`이다. 업종도 불명확하면 `category`가 `null`일 수 있다. 이 값은 모델이 질문에서 읽은 후보이므로 실제 카드 혜택 적용 전에는 가맹점 매칭과 혜택 규칙을 백엔드에서 검증해야 한다.

## 다음 연결 단계

1. 로그인 사용자 보유카드, 현재 월 사용액, 전월실적, 카드 혜택 규칙을 백엔드에서 조회한다.
2. 해석된 업종·가맹점·금액을 검증하고, 불명확하면 앱에서 다시 묻는다.
3. 검증된 카드 혜택 계산기로 보유카드별 혜택을 계산하고 월간 플랜의 남은 목표를 반영한다.
4. 계산 결과를 [추천 API 초안](recommendation-api.md)의 응답 형식에 맞춰 앱으로 보낸다. 숫자와 순위는 백엔드 계산 결과를 사용한다.
5. Flutter의 `FakeRecommendationRepository`를 실제 API 저장소로 교체한다.

현재 `SimpleBenefitCalculator`는 가맹점 지정 혜택과 일부 횟수·일일 한도를 계산하지 못한다. 이 계산기의 숫자를 완전한 카드 추천 결과로 노출하면 안 된다.

## 로컬 검증

외부 API를 호출하지 않는 `OpenAiQuestionInterpreterTest`가 요청 형식, `store=false`, 불완전 응답 처리, 키 누락 처리를 검사한다. 실제 키로 짧은 질문 1건을 호출한 연결 테스트에서도 응답 해석에 성공했다. 로컬 테스트는 Docker 없이 JDK 21과 Gradle로 실행했다. 전체 앱 연결과 프론트엔드 화면 교체는 위 다음 연결 단계가 완료되어야 검증할 수 있다.
