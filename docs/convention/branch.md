# 브랜치 규칙

## 브랜치 종류

| 브랜치 | 용도 |
|---|---|
| `main` | 배포 기준 브랜치. `develop`에서 PR로만 반영한다. 직접 push 금지 |
| `develop` | 개발 통합 브랜치 (GitHub 기본 브랜치) |
| `{파트}/{종류}/...` | 작업 브랜치. 아래 이름 규칙을 따른다 |

## 이름 규칙

> ⚠️ **작업 브랜치 이름 맨 앞에는 반드시 담당 파트(`back/`, `front/`, `ai/`)를 붙인다.**

```
{파트}/{종류}/{이슈번호}-{작업-요약}
```

- 이슈번호는 선택이다. 이슈가 있다면 브랜치 이름이 겹치거나 헷갈리는 것을 막기 위해 적어주고, 없으면 생략한다. (`back/feature/card-register`)
- 소문자와 하이픈을 사용한다.

### 파트

| 파트 | 대상 |
|---|---|
| `back/` | `backend/` 작업 |
| `front/` | `frontend/` 작업 |
| `ai/` | `ai/` 작업 |
| `common/` | 특정 파트에 속하지 않는 작업 (`docs/`, `.github/`, 루트 설정 등) |

### 종류

| 종류 | 용도 |
|---|---|
| `feature` | 기능 개발 |
| `fix` | 버그 수정 |
| `docs` | 문서 작업 |
| `refactor` | 리팩터링 |
| `chore` | 빌드, 설정, 환경 작업 |

### 예시

```
back/feature/12-card-register
back/fix/25-benefit-calculation
back/chore/3-gradle-setup
back/feature/plan-optimizer      # 이슈번호 생략
front/feature/8-login-screen
ai/feature/5-plan-optimizer
common/docs/2-branch-convention
```

### 주의

- 파트 접두어 없이 `feature/12-card-register`처럼 만들지 않는다.
- Git은 `back`이라는 이름의 브랜치가 있으면 `back/...` 브랜치를 만들 수 없다. 파트 이름만으로 된 브랜치는 만들지 않는다.

## 작업 흐름

1. `develop`에서 작업 브랜치를 만든다.
2. 작업 후 `develop`으로 PR을 올린다. CI 통과와 리뷰 승인이 필요하다.
3. 머지 방식은 **Merge commit**(일반 Merge)을 사용한다.
4. `develop` → `main` 반영도 PR로 하며 같은 **Merge commit**으로 머지한다.
   (Squash/Rebase로 하면 두 브랜치의 히스토리가 갈라져 다음 PR에서 충돌이 반복되므로 사용하지 않는다.)
5. 머지된 작업 브랜치는 삭제한다. (GitHub 설정으로 자동 삭제)--> 논의 필요!

