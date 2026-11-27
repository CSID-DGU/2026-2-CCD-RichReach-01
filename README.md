# RichReach

> 카드 혜택과 거래 내역을 바탕으로 월간 소비 계획을 세우고, 결제 전에 유리한 카드를 추천하는 서비스

<!-- 이미지: 서비스 대표 이미지 -->
> 📷 **[이미지 자리] 서비스 대표 이미지 / 로고 배너**

## 프로젝트 소개

카드마다 혜택 조건과 전월 실적, 월 한도가 달라서 어떤 카드로 결제해야 유리한지 판단하기 어렵습니다.
RichReach는 보유카드와 거래 내역을 연동해 다음을 돕습니다.

- 이번 달 업종별 예산을 어떤 카드에 얼마씩 쓸지 **월간 플랜**으로 정리합니다.
- 결제 직전에 "카페에서 3만 원"처럼 물으면 **혜택이 가장 크게 늘어나는 카드**를 알려 줍니다.
- 카드 순위와 혜택 금액은 서버가 계산하고, AI는 질문 해석과 설명만 담당합니다.

## 주요 기능

| 구분 | 기능 | 설명 |
|---|---|---|
| 계정 | 회원가입·로그인 | 이메일 가입, JWT 로그인과 토큰 재발급, 이메일 찾기, 비밀번호 재설정, 회원 탈퇴 |
| 카드 연동 | 카드사 연결·동기화 | CODEF를 통한 카드사 연결, 보유카드와 승인내역 수집, 분석할 카드 선택 |
| 카드 연동 | 자동 동기화 | 자동 동기화를 켠 사용자의 거래를 매일 수집 (서버 설정 기본값은 꺼짐) |
| 거래 | 거래내역 | 업종 자동 분류와 수정, 기간·카드·업종별 요약 |
| 홈 | 예정지출 | 예정지출 추가·수정·삭제 |
| 플랜 | 월간 플랜 | 업종별 예산을 카드에 배분 (OR-Tools CP-SAT), 카드별·업종별 목표, 변경 안내 |
| 플랜 | 새 달 예산 초안 | 이번 달 플랜이 없으면 지난달 예산으로 채운 초안 제공 |
| 추천 | 결제 전 카드 추천 | 업종과 금액으로 카드별 추가 혜택 비교, 대안 카드, 금액 구간별 추천, 결제 후 플랜 변화 |
| 추천 | 추천 채팅 | 자연어 질문에서 업종·금액·가맹점을 해석해 같은 계산 서비스 호출 |
| 추천 | 혜택 근거 | 계산에 영향을 준 규칙의 카드사 상품 페이지 원문 발췌 제공 |

> 혜택 계산은 현재 **업종과 월간 사용액으로 계산할 수 있는 비율 혜택**만 다룹니다. 가맹점 지정, 정액, 마일리지, 횟수·일 한도 혜택은 계산에서 제외하며, 계산할 수 있는 혜택이 없으면 안내 문구를 보여 줍니다.

## 화면

<!-- 이미지: 주요 화면 -->
| 온보딩 | 홈 | 월간 플랜 | 카드 추천 |
|:---:|:---:|:---:|:---:|
| 📷 이미지 자리 | 📷 이미지 자리 | 📷 이미지 자리 | 📷 이미지 자리 |

## 시스템 구성

<!-- 이미지: 아키텍처 다이어그램 -->
> 📷 **[이미지 자리] 아키텍처 다이어그램**

```
Flutter 앱 ──REST/JWT──▶ Spring Boot API ──▶ MySQL
                              ├──▶ CODEF      (카드사 연동)
                              └──▶ OpenAI API (질문 해석·설명)
```

개발 서버는 AWS EC2(Docker)와 RDS(MySQL 8.4)로 구성합니다.

## 기술 스택

| 파트 | 기술 |
|---|---|
| Frontend | <img src="https://img.shields.io/badge/Flutter-02569B?style=flat-square&logo=flutter&logoColor=white"> <img src="https://img.shields.io/badge/Dart-0175C2?style=flat-square&logo=dart&logoColor=white"><br/>Riverpod, go_router, Dio, flutter_secure_storage |
| Backend | <img src="https://img.shields.io/badge/Java_21-007396?style=flat-square&logo=openjdk&logoColor=white"> <img src="https://img.shields.io/badge/Spring_Boot_4-6DB33F?style=flat-square&logo=springboot&logoColor=white"> <img src="https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white"><br/>Spring Security(JWT), Spring Data JPA, springdoc-openapi, Google OR-Tools |
| Database | <img src="https://img.shields.io/badge/MySQL_8.4-4479A1?style=flat-square&logo=mysql&logoColor=white"> <img src="https://img.shields.io/badge/Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white"> |
| AI | <img src="https://img.shields.io/badge/OpenAI_API-412991?style=flat-square&logo=openai&logoColor=white"><br/>기본 모델 `gpt-5-nano`, 질문 해석(구조화 출력), 결과 설명, 카드사 원문 검색 |
| Infra·CI | <img src="https://img.shields.io/badge/AWS_EC2-FF9900?style=flat-square&logo=amazonec2&logoColor=white"> <img src="https://img.shields.io/badge/AWS_RDS-527FFF?style=flat-square&logo=amazonrds&logoColor=white"> <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white"> <img src="https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white"> |
| 테스트 | JUnit 5, Testcontainers(MySQL), flutter test |

AI 호출 코드는 별도 서버가 아니라 Spring 백엔드 안에 있습니다. 자세한 내용은 [ai/README.md](ai/README.md)를 참고하세요.

## 디렉터리 구조

```
.
├── frontend/                  Flutter 앱 (Android, iOS)
│   └── lib/
│       ├── app/               라우팅, 앱 설정
│       ├── core/              네트워크, 테마, 공통 위젯
│       └── features/          auth, onboarding, home, plan, recommendation
├── backend/                   Spring Boot API 서버
│   └── src/main/java/com/richreach/
│       ├── auth/  user/       계정, 인증, 탈퇴
│       ├── codef/             카드사 연동, 동기화
│       ├── card/  benefit/    카드 상품, 혜택 규칙, 혜택 계산
│       ├── transaction/       거래내역, 업종 분류
│       ├── plan/              월간 플랜, 배분 최적화
│       ├── plannedexpense/    예정지출
│       ├── recommendation/    결제 전 추천, 추천 채팅
│       └── global/            보안, 예외, 공통 응답, 로깅
├── ai/                        AI 연동 안내
├── docs/                      API 명세, 알고리즘, 협업 규칙
├── .github/                   이슈·PR 템플릿, CI
├── docker-compose.yml         로컬 실행 (MySQL, backend)
└── docker-compose.dev.yml     AWS 개발 서버
```

## 실행 방법

### 사전 준비

- Docker Desktop
- Flutter 3.47.6 (stable), Dart 3.13.5
- Android Studio 에뮬레이터 또는 실기기 (iOS는 macOS와 Xcode 필요)

### 1. 백엔드

처음 실행하기 전의 설정은 [backend/README.md](backend/README.md)를 따라 준비합니다.

```bash
docker compose up -d --build
```

- 헬스체크: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- Windows에서는 OR-Tools 문제 때문에 Docker로 실행합니다. 다른 실행 방법은 [backend/README.md](backend/README.md)를 참고하세요.

### 2. 프론트엔드

```bash
cd frontend
flutter pub get
flutter run                                  # 가짜 데이터로 실행 (기본)
flutter run --dart-define=USE_FAKE_API=false # 로컬 백엔드에 연결
```

서버 주소 변경, 실기기 연결은 [frontend/README.md](frontend/README.md)를 참고하세요.

### 3. 테스트

```bash
cd backend && ./gradlew test          # Docker 필요 (Testcontainers)
cd frontend && flutter analyze && flutter test
```

## 문서

| 문서 | 내용 |
|---|---|
| [API 명세 (OpenAPI)](docs/api/openapi.json) | 전체 REST API |
| [결제 전 추천 API](docs/api/recommendation-api.md) | 추천 요청·응답 |
| [추천 AI API](docs/api/recommendation-chat-ai.md) | 추천 채팅, 원문 근거 |
| [월 예산 대화](docs/api/plan-chat-openai.md) | 예산 대화 미리보기 |
| [온보딩 데이터](docs/api/onboarding-data-requirements.md) | 온보딩 화면에 필요한 데이터 |
| [카드 배분 알고리즘](docs/algorithm/card-allocation.md) | 월간 플랜 최적화 |

## 협업 규칙

- [브랜치 규칙](docs/convention/branch.md)
- [커밋 메시지 규칙](docs/convention/commit.md)
- [이슈·PR 등록 규칙](docs/convention/issue-pr.md)

## 브랜치 전략

```
main      운영/배포 기준 브랜치 (develop에서 PR로만 반영)
└ develop 개발 통합 브랜치 (기본 브랜치)
   └ back/feature/*, front/fix/*, ai/* ... 작업 브랜치 (파트 접두어 필수)
```

## 팀

| [어수빈](https://github.com/EoSubeen) | [강서현](https://github.com/seohyunk09) | [박지현](https://github.com/jihyeonpark5) | [이영민](https://github.com/0min22) |
|:---:|:---:|:---:|:---:|
| [<img src="https://avatars.githubusercontent.com/u/165251165?v=4" width="150"><br/>](https://github.com/EoSubeen) | [<img src="https://avatars.githubusercontent.com/u/180945392?v=4" width="150"><br/>](https://github.com/seohyunk09) | [<img src="https://avatars.githubusercontent.com/u/144185782?v=4" width="150"><br/>](https://github.com/jihyeonpark5) | [<img src="https://avatars.githubusercontent.com/u/190470391?v=4" width="150"><br/>](https://github.com/0min22) |
| 팀장 | 팀원 | 팀원  | 팀원  |
