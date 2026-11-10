# 이슈·PR 등록 규칙 (제목·담당자·라벨)

## 형식

```
[종류] 제목
[종류] 제목 #이슈번호      ← PR은 끝에 이슈 번호를 붙여도 된다 (선택)
```

- 이슈와 PR 모두 같은 형식을 쓴다. PR 제목은 보통 이슈 제목을 그대로 쓴다.
- `[종류]` 뒤에 한 칸 띄운다. (`[Chore]CODEF ...`처럼 붙이지 않는다)
- 제목은 무엇을 하는지 한 줄로 적고, 마침표를 붙이지 않는다.
- 화면 작업이면 화면 ID를 앞에 적어 찾기 쉽게 한다. (`ONB-06`, `PLAN-01`)
- PR 제목 끝에 이슈 번호를 붙일 수 있다. 붙일 때는 `#이슈번호` 한 가지로 쓴다. (`... #186` ○, `... (#186)` ✕)
  - 제목에 번호를 붙여도 PR 본문의 `Close #이슈번호`는 빼지 않는다. 이슈를 자동으로 닫는 것은 본문이다.
- 커밋 메시지 형식(`feat: ...`)을 PR 제목에 쓰지 않는다.

## 종류

| 종류 | 용도 | 브랜치 종류 | 커밋 type |
|---|---|---|---|
| `[Feature]` | 기능 추가, API 구현·연동, 화면 개발 | `feature` | `feat` |
| `[Fix]` | 버그 수정 | `fix` | `fix` |
| `[Docs]` | 문서 작업 (API 문서, 규칙, README) | `docs` | `docs` |
| `[Refactor]` | 동작 변경 없는 코드 개선 | `refactor` | `refactor` |
| `[Chore]` | 빌드, 설정, 의존성, 에셋 등 기타 작업 | `chore` | `chore` |
| `[Bug]` | **이슈 전용.** 발견한 버그 보고 (고치는 PR은 `[Fix]`) | - | - |

- 종류는 [브랜치 규칙](branch.md)의 종류와 맞춘다. (예: `[Docs]` 이슈 → `common/docs/221-issue-pr-convention` 브랜치)

## 담당자 (Assignees)

이슈와 PR을 등록할 때 **Assignees에 작업자를 반드시 등록한다.**

- 보통 작업하는 본인을 등록한다. 여러 명이 함께 작업하면 모두 등록한다.
- 웹에서는 오른쪽 "Assignees"에서 고르고, `gh` CLI는 `--assignee @me`(본인)를 붙인다.

```
gh issue create --title "[Feature] ONB-06 소비 분석 API 연동" --label FRONTEND --label FEATURE --assignee @me
gh pr create --base develop --title "[Feature] ONB-06 소비 분석 API 연동" --label FRONTEND --label FEATURE --assignee @me
```

## 라벨

이슈와 PR에는 **파트 라벨 1개 + 종류 라벨 1개**를 단다.

| 파트 라벨 | 대상 |
|---|---|
| `FRONTEND` | `frontend/` 작업 |
| `BACKEND` | `backend/` 작업 |
| `AI` | `ai/` 작업 |

| 종류 라벨 | 제목 종류 |
|---|---|
| `FEATURE` | `[Feature]` |
| `BUG` | `[Bug]`, `[Fix]` |
| `Chore` | `[Chore]`, `[Docs]`, `[Refactor]` |

- 특정 파트에 속하지 않는 작업(`docs/`, `.github/`, 루트 설정)은 파트 라벨을 생략할 수 있다.

## 작업 흐름

1. 작업 전에 이슈를 먼저 만들고, 라벨과 Assignees를 등록한다. (템플릿: 기능 개발 `[Feature] `, 버그 리포트 `[Bug] `)
2. 이슈 번호로 브랜치를 만든다. ([브랜치 규칙](branch.md))
3. PR은 [PR 템플릿](../../.github/PULL_REQUEST_TEMPLATE.md)을 쓰고, 본문의 "관련 이슈 링크"에 `Close #이슈번호`를 적는다. 머지되면 이슈가 자동으로 닫힌다.
   - PR에도 이슈와 같은 라벨과 Assignees를 등록한다.
   - 이슈를 닫지 않고 관련만 있으면 `Related #이슈번호`로 적는다.

## 예시

```
[Feature] ONB-06 소비 분석 API 연동
[Feature] 월간 플랜 조회 API와 업종별 사용계획 조회 API 구현
[Feature] 소비 패턴 분석 및 월 예산 추천 API 구현 #184      ← PR 제목 끝 이슈 번호 (선택)
[Fix] 월간 혜택 계산 시 타임존 오류 수정
[Docs] 온보딩 API 문서 정리와 1차 연동 현황 정리
[Chore] 소비 카테고리 아이콘 5종 AppAssets 등록
[Bug] 로그인 후 홈 화면이 비어 보임
```

피해야 할 예:

```
[Chore] Riverpod 자동 재시도 끄기 (#177)                 → 괄호 없이 #177
[Chore]CODEF 기관 정책 기준정보 채우기                     → [Chore] 뒤에 한 칸
feat: add bounded CP-SAT card allocation                → [Feature] CP-SAT 카드 배분 추가
```
