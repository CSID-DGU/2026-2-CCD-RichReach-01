# Backend

Spring Boot + DDD 기반 API 서버입니다.

## 기술 스택

- Java 21, Spring Boot, Gradle
- MySQL, Spring Data JPA, Flyway
- springdoc-openapi (Swagger)

## 패키지 구조

각 도메인(`user`, `card`, `transaction`, `benefit`, `plan`)은 같은 4계층을 가집니다.

```
com.richreach
├── global/          공통 (config, entity, exception, response, security)
└── {domain}/
    ├── presentation/     Controller, 요청/응답 DTO
    ├── application/      유스케이스 (Service)
    ├── domain/           Entity, VO, Repository 인터페이스
    └── infrastructure/   Repository 구현체, 외부 연동
```

## 로컬 실행

TODO: 백엔드 프로젝트 세팅 후 작성

1. 환경변수: `.env.example`을 복사해 `.env` 작성
2. DB 실행: `docker compose up -d mysql`
3. 앱 실행: `./gradlew bootRun --args='--spring.profiles.active=local'`
4. Swagger: `http://localhost:8080/swagger-ui/index.html`

## 테스트

```bash
./gradlew test
```

CI: [backend-ci.yml](../.github/workflows/backend-ci.yml)
