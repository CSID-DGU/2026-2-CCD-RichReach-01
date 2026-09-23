# Backend

Spring Boot 기반 API 서버입니다. 도메인별로 패키지를 나누고, 각 도메인 안에서 기술 계층(controller, service 등)으로 구분합니다.

## 기술 스택

- Java 21, Spring Boot, Gradle
- MySQL, Spring Data JPA, Flyway
- springdoc-openapi (Swagger)

## 패키지 구조

각 도메인(`user`, `card`, `transaction`, `benefit`, `plan`)은 같은 기본 구조를 가집니다.

```
com.richreach
├── global/          공통 (config, entity, exception, response, security)
└── {domain}/
    ├── controller/    REST API (비즈니스 로직 없이 service에 위임)
    ├── service/       비즈니스 로직
    ├── repository/    DB 접근 (Spring Data JPA)
    ├── entity/        JPA Entity
    ├── dto/           요청/응답 DTO (*Request, *Response)
    ├── client/        외부 API 연동 (필요한 도메인만, 예: plan의 LLM 호출)
    └── optimizer/     OR-Tools 최적화 (plan 도메인만)
```

- 다른 도메인의 Entity/Repository는 직접 참조하지 않고 ID 또는 해당 도메인의 service를 통해 호출합니다.
- `optimizer`는 Entity가 아닌 일반 객체를 입출력으로 사용하고, DB 접근은 service가 담당합니다.

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
