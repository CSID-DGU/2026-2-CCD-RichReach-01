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

### 사전 준비

- Docker Desktop 실행
- JDK 21 (없어도 Gradle이 자동으로 내려받는다)
- 저장소 **루트**에서 `.env` 만들기 (`.env`는 커밋되지 않는다)

```bash
cp .env.example .env          # PowerShell: Copy-Item .env.example .env
```

`.env`의 `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`를 원하는 값으로 바꾼다.

### 방법 A: DB만 Docker, 앱은 로컬에서 실행 (평소 개발)

```bash
# 저장소 루트에서 DB 실행
docker compose up -d mysql

# backend 폴더에서 .env를 환경변수로 불러온 뒤 앱 실행 (Git Bash, Mac, Linux)
cd backend
set -a; . ../.env; set +a
./gradlew bootRun --args='--spring.profiles.active=local'
```

IntelliJ에서는 Run Configuration의 **Active profiles**에 `local`, **Environment variables**에 `.env` 값(`MYSQL_PASSWORD` 등)을 넣는다.

### 방법 B: 전체를 Docker로 실행

```bash
docker compose up -d --build   # MySQL이 healthy가 된 뒤 backend가 기동된다
```

### 확인

- 헬스체크: http://localhost:8080/actuator/health → `{"status":"UP"}`
- Swagger UI: http://localhost:8080/swagger-ui.html

### 종료

```bash
docker compose down        # 컨테이너 종료 (DB 데이터는 유지)
docker compose down -v     # DB 데이터까지 삭제
```

### 참고

- MySQL 호스트 포트 기본값은 **3307**이다. 로컬에 MySQL이 이미 설치되어 3306을 쓰고 있어도 겹치지 않는다. `.env`의 `DB_PORT`로 바꿀 수 있다.
- 웹 클라이언트를 붙일 때는 `.env`의 `CORS_ALLOWED_ORIGINS`에 허용할 origin을 쉼표로 넣는다. 비워두면 CORS는 닫혀 있다.
- 아직 Flyway 마이그레이션이 없어서 기동 시 `No migrations found` 경고가 나오는 것은 정상이다.

## 테스트

```bash
./gradlew test
```

CI: [backend-ci.yml](../.github/workflows/backend-ci.yml)
