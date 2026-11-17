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

`.env`의 `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`를 원하는 값으로 바꾸고, `DATA_ENCRYPTION_KEY`를 채운다.
`DATA_ENCRYPTION_KEY`는 비어 있으면 앱이 시작되지 않는다. 로컬 DB 전용이라 각자 달라도 되고, `openssl rand -base64 32`로 만든다.
`JWT_SECRET`은 비워도 실행된다. (임시 키로 시작하고 경고가 나오며, 앱을 다시 켜면 기존 로그인이 풀린다.)

> ⚠️ **Windows에서는 방법 B(전체를 Docker로 실행)를 쓴다.**
> Windows에서 `./gradlew bootRun`(방법 A, IntelliJ 실행 포함)으로 띄우면 플랜을 생성하는 순간(OR-tools CP-SAT 계산) JVM이 `EXCEPTION_ACCESS_VIOLATION`(`msvcp140.dll`)으로 종료된다.
> JDK에 들어 있는 오래된 `msvcp140.dll`이 OR-tools와 맞지 않아서 생기는 문제로, 앱 코드와는 상관없다. Docker(리눅스)에서는 정상 동작한다.
> 서버가 꺼지면 진행 중이던 플랜 작업은 5분(`plan.generation.timeout`)이 지나야 실패로 처리되어 다시 만들 수 있다.

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

- 코드를 바꾼 뒤에는 `--build`를 붙여 다시 실행해야 컨테이너에 반영된다.
- 로그 확인: `docker logs -f richreach-backend`
- 확인 전 혜택 규칙까지 써서 플랜을 확인하려면 `.env`에 `BENEFIT_RULE_SELECTION=ALL`을 넣는다.

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
- 처음 실행하면 Flyway가 마이그레이션을 자동으로 적용해서 1~3분 걸린다. (카드 데이터 시드가 커서 처음에만 오래 걸린다.) 로컬 DB에서는 Flyway를 켠 기본값으로 실행한다.
- **공용 DB**에 접속해서 실행할 때는 `SPRING_FLYWAY_ENABLED=false`로 실행한다. DB 구조 변경은 DB 담당이 한 번만 적용한다.

### 문제 해결

**MySQL은 `healthy`인데 앱이 `Access denied`로 DB에 접속하지 못할 때**

MySQL의 계정과 비밀번호는 데이터 볼륨(`mysql-data`)을 **처음 만들 때 한 번만** 적용된다. 이후 `.env`의 `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`를 바꿔도 기존 볼륨에는 이전 값이 남아 있어서, MySQL은 정상으로 보이지만(healthcheck는 로그인 없이 서버 실행 여부만 확인한다) 앱의 로그인은 실패한다.

기존 볼륨을 삭제하고 다시 만들면 해결된다.

```bash
docker compose down -v      # 컨테이너와 볼륨 삭제
docker compose up -d mysql  # .env의 새 값으로 다시 생성
```

> ⚠️ `-v`는 **DB 데이터를 모두 삭제**한다. 지워도 되는 개발용 데이터일 때만 실행한다.

## 개발 서버(AWS) 배포

공용 개발 서버는 **EC2(Docker) + RDS MySQL 8.4**로 띄운다. 앱은 `dev` 프로파일([application-dev.yml](src/main/resources/application-dev.yml))로 실행하고, 모든 설정은 환경변수로만 넣는다. 로컬 개발(`local` 프로파일)과 달리 이 프로파일에서는 시연 데이터 생성과 비밀번호 재설정 인증번호 노출이 **아예 꺼져 있고**, 필수 값이 빠지면 앱이 시작되지 않는다.

### 환경변수

| 이름 | 필수 | 설명 |
|---|---|---|
| `DB_HOST` | ✅ | RDS 엔드포인트 (`DB_PORT` 기본 3306, `MYSQL_DATABASE` 기본 `richreach`, `MYSQL_USER` 기본 `richreach`) |
| `MYSQL_PASSWORD` | ✅ | DB 비밀번호 |
| `DATA_ENCRYPTION_KEY` | ✅ | AES-256 키(Base64, 32바이트). `openssl rand -base64 32`. **한 번 정하면 바꾸거나 잃어버리지 않는다.** 바뀌면 저장된 암호화 데이터(CODEF connectedId 등)를 읽을 수 없다. 안전한 곳에 백업한다. |
| `JWT_SECRET` | ✅ | 로그인 토큰 서명 키(32바이트 이상). `openssl rand -base64 48`. 비어 있으면 앱이 시작되지 않는다. 바꾸면 모든 로그인이 풀린다. |
| `CORS_ALLOWED_ORIGINS` | ✅ | 허용할 웹 origin(쉼표로 구분). 모바일 앱만 쓰면 임의의 값을 넣어도 된다. |
| `CODEF_CLIENT_ID`, `CODEF_CLIENT_SECRET`, `CODEF_PUBLIC_KEY` | | 카드사 연동. 비어 있으면 CODEF 호출 때 안내 오류가 난다. |
| `DB_SSL_MODE` | | 기본 `REQUIRED`. RDS 인증서를 검증하려면 `VERIFY_CA` |
| `SWAGGER_ENABLED` | | 기본 `true`. API 문서를 외부에 숨기려면 `false` |
| `BENEFIT_RULE_SELECTION` | | 기본 `READY_ONLY`. 시연용으로만 `ALL` |
| `OPENAI_API_KEY` | | 월 예산 대화 미리보기. 없으면 해당 기능만 쓸 수 없다. |

값은 서버의 `.env.dev` 파일(커밋되지 않는다) 또는 AWS Systems Manager Parameter Store에서 읽어 넣는다. **저장소, 이미지, 채팅, PR에는 값을 남기지 않는다.**

### 실행

```bash
# EC2에서 저장소 루트
docker compose -f docker-compose.dev.yml up -d --build
docker logs -f richreach-backend
curl -fs http://localhost:8080/actuator/health        # {"status":"UP"}
```

- 앱은 서버 안(`127.0.0.1:8080`)에서만 열린다. 바깥에는 앞단(ALB 또는 nginx)에서 HTTPS로 받아 넘긴다.
- 처음 켜면 Flyway가 빈 DB에 V1부터 마이그레이션을 적용해서 1~3분 걸린다. (이미 DB 담당이 구조를 관리하는 공용 DB에 붙일 때는 `SPRING_FLYWAY_ENABLED=false`)
- 코드를 바꾼 뒤에는 `git pull --no-rebase` 후 같은 명령을 다시 실행한다. DB 데이터는 RDS에 있어 컨테이너를 다시 만들어도 유지된다.

### 보안 설정 체크리스트

- RDS 보안 그룹은 **EC2의 보안 그룹에서 오는 3306만** 허용한다. (퍼블릭 액세스 끔)
- EC2 보안 그룹은 80/443(앞단)과 관리용 22(내 IP만)만 연다. 8080은 열지 않는다.
- RDS 자동 백업을 켜고, `DATA_ENCRYPTION_KEY`는 DB 백업과 따로 보관한다.
- 시연 데이터(`DEMO_SEED_*`)는 `dev`에서 동작하지 않고, 공용 DB에는 어떤 경우에도 만들지 않는다.

## 테스트

```bash
./gradlew test
```

CI: [backend-ci.yml](../.github/workflows/backend-ci.yml)

## 월 예산 챗봇 미리보기

로그인한 사용자는 `POST /api/plans/chat/preview`로 현재 월 예산 초안과 메시지를 보낸다. 서버는 OpenAI에서 대화 답변과 업종별 예산·계획 방향 수정 의도를 받아, 기존 예산 규칙으로 검증한 **수정안**을 반환한다. 이 API는 플랜을 저장하거나 카드 혜택을 계산하지 않는다. 화면 연결과 확인 절차는 [월 예산 챗봇 API 문서](../docs/api/plan-chat-openai.md)를 참고한다.

서버 실행 환경에 `OPENAI_API_KEY`를 설정한다. `OPENAI_MODEL`의 기본값은 `gpt-5-nano`다. 키는 프론트엔드 코드나 Git에 넣지 않는다. 키가 없어도 서버는 시작되며, 이 엔드포인트를 호출하면 `PLAN-007`을 반환한다.
