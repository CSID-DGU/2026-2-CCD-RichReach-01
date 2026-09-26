# RichReach

> 프로젝트 한 줄 소개..

카드 혜택과 거래 내역을 바탕으로 월간 소비 계획을 세워주는 서비스입니다.

## 구성

| 디렉터리 | 설명 | 기술 |
|---|---|---|
| [frontend/](frontend/) | 클라이언트 | Flutter |
| [backend/](backend/) | API 서버 (도메인별 controller/service/repository) | Spring Boot, MySQL, Flyway |
| [ai/](ai/) | AI 파트 | 미정 |
| [docs/](docs/) | API 명세, ERD, 회의록, 협업 규칙 | - |

## 협업 규칙

- [브랜치 규칙](docs/convention/branch.md)
- [커밋 메시지 규칙](docs/convention/commit.md)

## 브랜치 전략

```
main      운영/배포 기준 브랜치 (develop에서 PR로만 반영)
└ develop 개발 통합 브랜치 (기본 브랜치)
   └ back/feature/*, front/fix/*, ai/* ... 작업 브랜치 (파트 접두어 필수)
```

## 시작하기

- 백엔드: [backend/README.md](backend/README.md)
- 프론트엔드: [frontend/README.md](frontend/README.md)

## 팀

| [어수빈](https://github.com/EoSubeen) | [강서현](https://github.com/seohyunk09) | [박지현](https://github.com/jihyeonpark5) | [이영민](https://github.com/0min22) |
|:---:|:---:|:---:|:---:|
| [<img src="https://avatars.githubusercontent.com/u/165251165?v=4" width="150"><br/>](https://github.com/EoSubeen) | [<img src="https://avatars.githubusercontent.com/u/180945392?v=4" width="150"><br/>](https://github.com/seohyunk09) | [<img src="https://avatars.githubusercontent.com/u/144185782?v=4" width="150"><br/>](https://github.com/jihyeonpark5) | [<img src="https://avatars.githubusercontent.com/u/190470391?v=4" width="150"><br/>](https://github.com/0min22) |
| 팀장 | 팀원 | 팀원  | 팀원  |
