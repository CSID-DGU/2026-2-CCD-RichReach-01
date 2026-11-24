# AI 연결 코드

현재 AI 호출 코드는 별도 `ai` 서버가 아니라 Spring 백엔드 안에 있다. 카드 추천의 자연어 해석·설명·카드사 원문 검색 계약은 [추천 AI API](../docs/api/recommendation-chat-ai.md)를 참고한다. 모델은 금액이나 카드를 계산하지 않는다.

- `POST /api/recommendations/manual`: 기존 서버의 금액·업종 기반 계산. AI를 호출하지 않는다.
- `POST /api/recommendations/chat`: 자연어 질문을 해석하고 같은 계산 서비스를 호출한다. 설명 생성이 실패하면 서버 고정 문구를 사용한다.
- `POST /api/cards/catalog/evidence`: 331종 스테이징 카드 원문을 카드사·상품 코드로 검색한다. 문서 버전은 확인 전까지 `null`이다.
- 추천 응답의 `card.ruleEvidence`: 결제 전후 계산값이 달라진 규칙의 원문 블록을 같은 카드에서 조회한다. 약관의 현재 효력까지 증명하는 값은 아니다.

운영 서버의 `OPENAI_API_KEY`만 사용한다. 키는 프론트와 Git에 넣지 않는다.
