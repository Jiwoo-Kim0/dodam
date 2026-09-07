# 도담 백엔드

도담 백엔드의 초기 Spring Boot 프로젝트입니다. 현재는 비즈니스 API를 구현하지 않고 공통 응답, 예외 처리, Security, CORS, Profile, MySQL, Redis 및 Health Check 기반만 제공합니다.

## 기술 스택

- Java 21
- Spring Boot 3.5.16
- Gradle 8.14.3
- Spring Web
- Spring Data JPA
- Spring Data Redis
- Spring Security
- Spring Validation
- Spring Boot Actuator
- OAuth2 Client
- MySQL 8.x
- Flyway
- H2: 초기 테스트 전용
- JUnit 5

OAuth2 Client는 계획된 Kakao/Google 로그인 확장을 위해 포함했지만 현재 로그인과 Client Registration은 활성화하지 않았습니다.

## 프로젝트 구조

    backend/
    ├── database/
    │   └── finance_products/
    ├── docs/
    ├── gradle/
    │   └── wrapper/
    ├── src/
    │   ├── main/
    │   │   ├── java/com/ssafy/dodam/
    │   │   │   ├── global/
    │   │   │   │   ├── config/
    │   │   │   │   ├── exception/
    │   │   │   │   ├── health/
    │   │   │   │   ├── response/
    │   │   │   │   └── security/
    │   │   │   └── infrastructure/
    │   │   └── resources/
    │   │       ├── db/migration/
    │   │       ├── application.yml
    │   │       ├── application-local.yml
    │   │       └── application-prod.yml
    │   └── test/
    │       ├── java/com/ssafy/dodam/
    │       └── resources/application-test.yml
    ├── build.gradle
    └── settings.gradle

database/finance_products의 기존 SQL은 시연용 금융상품 데이터이며 Flyway Migration으로 자동 실행하지 않습니다.

도메인 기능은 구현 시점에 domain/{domain-name} 아래에서 controller, service, repository, entity, dto 단위로 확장합니다. SSAFY 금융망, 금융상품한눈에, 생성형 AI처럼 여러 도메인이 공유하는 외부 어댑터는 infrastructure/external 아래에 배치합니다.

## 실행 전제조건

- JDK 21
- MySQL 8.x
- Redis

Gradle은 별도 설치하지 않고 Wrapper를 사용합니다.

## 환경변수

local 또는 prod Profile 실행 전 다음 값을 설정해야 합니다.

| 환경변수 | 설명 |
| --- | --- |
| DB_URL | MySQL JDBC URL |
| DB_USERNAME | MySQL 사용자 |
| DB_PASSWORD | MySQL 비밀번호 |
| REDIS_HOST | Redis 호스트 |
| REDIS_PORT | Redis 포트 |
| REDIS_PASSWORD | Redis 비밀번호 |
| CORS_ALLOWED_ORIGINS | 쉼표로 구분한 허용 Origin 목록 |
| JWT_SECRET | Access Token(HS256) 서명키. 32바이트 이상, 비어 있으면 기동 실패 |

.env.example에는 키만 제공하며 Spring Boot가 .env 파일을 자동으로 읽지는 않습니다. 실행 환경이나 IDE에서 환경변수를 주입하세요.

OAuth Client 정보, Firebase 서비스 계정과 외부 API Key는 아직 필요하지 않습니다.

## Profile

### 공통

application.yml에는 애플리케이션 이름, Open Session in View 비활성화 및 Actuator Health 노출 정책만 있습니다.

### local

application-local.yml은 환경변수로 MySQL과 Redis에 연결합니다. JPA ddl-auto는 validate이며 Flyway가 활성화됩니다.

### prod

application-prod.yml도 환경변수만 사용합니다. 데이터 손실 위험이 있는 create, create-drop, update 설정은 사용하지 않습니다.

### test

application-test.yml은 H2를 사용하는 통합 테스트 전용 Profile입니다. 외부 MySQL과 Redis에 연결하지 않으며 Redis Health Indicator를 비활성화합니다. Flyway DDL이 MySQL 전용이므로 테스트에서는 Flyway를 끄고 JPA `ddl-auto: create-drop`으로 엔티티 기준 스키마를 만듭니다.

MySQL 전용 JSON, TINYINT 및 CHECK 제약조건 검증은 Testcontainers MySQL 기반 통합 테스트로 전환해 Flyway 마이그레이션 기준으로 수행해야 합니다.

## 로컬 실행

환경변수를 설정한 다음 backend 디렉터리에서 실행합니다.

Unix 계열:

    ./gradlew bootRun --args='--spring.profiles.active=local'

Windows PowerShell:

    .\gradlew.bat bootRun --args='--spring.profiles.active=local'

## 테스트 및 빌드

Unix 계열:

    ./gradlew test
    ./gradlew clean build

Windows PowerShell:

    .\gradlew.bat test
    .\gradlew.bat clean build

모든 Spring 통합 테스트는 ActiveProfiles("test")를 사용하므로 외부 MySQL, Redis, OAuth Provider 또는 외부 API 없이 실행됩니다.

## Health Check

### 애플리케이션 Health

    GET /api/health

공통 ApiResponse 형식으로 애플리케이션이 HTTP 요청에 응답할 수 있는지만 확인합니다. DB, Redis 및 외부 API를 호출하지 않습니다.

### 인프라 Health

    GET /actuator/health

Docker 또는 프로세스 Health Check 용도입니다. Actuator 표준 응답을 사용하며 ApiResponse로 감싸지 않습니다. 노출되는 Actuator Endpoint는 health뿐이고 상세정보는 외부에 공개하지 않습니다.

local과 prod에서는 DataSource와 Redis Health를 포함합니다. test에서는 외부 Redis 없이 검증할 수 있도록 Redis Health Indicator를 비활성화합니다.

## Security 정책

현재 공개 경로:

- GET /api/health
- GET /actuator/health
- POST /api/v1/auth/signup
- POST /api/v1/auth/login
- POST /api/v1/auth/refresh
- CORS Preflight

/api/v1/admin/**는 ADMIN 권한(`user.role`)이 필요하고, 나머지 /api/v1/**는 인증이 필요합니다. 보호 경로에 토큰이 없으면 JSON 형식의 401(`UNAUTHORIZED`)을 반환합니다.

formLogin, httpBasic, OAuth2 Login은 비활성화되어 있습니다. 클라이언트가 네이티브 앱(APK)이고 Bearer 토큰만 사용하므로 CSRF는 비활성화, SessionCreationPolicy는 STATELESS입니다.

### 인증 구조 (Auth API)

| 항목 | 결정 |
| --- | --- |
| Access Token | JWT HS256. Claim: `sub`=userId, `sid`=auth_session.session_id, `role`. 기본 30분(`app.auth.jwt.access-token-validity`) |
| Refresh Token | JWT가 아닌 256bit 난수(Base64URL). DB `auth_session`에는 SHA-256 해시만 저장. 기본 14일 |
| 전달 방식 | 둘 다 응답 Body. 앱은 SecureStore 등 안전한 저장소에 보관 |
| 서명키 | `JWT_SECRET` 환경변수 (`app.auth.jwt.secret`), 32바이트 미만이면 기동 실패 |
| 세션 검증 | Access Token은 만료 전까지 DB를 다시 보지 않음. 세션 폐기는 `GET /api/v1/auth/session`과 재발급 시점에 반영 |

`JwtAuthenticationFilter`는 토큰 오류 시 바로 응답하지 않고 오류 코드만 request attribute에 남깁니다. 공개 경로는 오래된 헤더가 있어도 정상 동작하고, 보호 경로는 `RestAuthenticationEntryPoint`가 `TOKEN_EXPIRED` / `INVALID_TOKEN`으로 구분해 401을 반환합니다.

구현된 엔드포인트 (API 명세 1. Auth):

| Method | Endpoint | 인증 | 응답 |
| --- | --- | --- | --- |
| POST | /api/v1/auth/signup | X | 201, `{ user: { userId, email, name, role }, createdAt }` |
| POST | /api/v1/auth/login | X | 200, `{ tokenType, accessToken, accessTokenExpiresAt, refreshToken, refreshTokenExpiresAt, user }` |
| GET | /api/v1/auth/session | O | 200, `{ user, sessionId, sessionCreatedAt, lastAccessedAt, sessionExpiresAt }` |

주요 오류 코드: `EMAIL_ALREADY_EXISTS`(409), `INVALID_CREDENTIALS`(401, 이메일 존재 여부를 노출하지 않음), `SESSION_EXPIRED`(401, 세션 폐기·만료·탈퇴 계정), `TOKEN_EXPIRED` / `INVALID_TOKEN`(401).

회원가입은 현재 자체 회원만 생성합니다. SSAFY 금융망 사용자 생성은 연동 방식이 확정되면 별도 단계로 추가합니다. 비밀번호는 BCrypt로 저장하며 8~72자, 영문·숫자 포함, 공백 불가입니다. 이메일은 trim + 소문자로 정규화해 저장합니다.

로그아웃, Access Token 재발급, 세션 삭제는 후속 티켓에서 추가합니다.

## 공통 응답과 예외

성공 응답:

    {
      "success": true,
      "data": {},
      "message": null
    }

실패 응답:

    {
      "success": false,
      "data": null,
      "message": "요청 값이 올바르지 않습니다.",
      "code": "VALIDATION_ERROR"
    }

code와 errors는 null일 때만 생략하고 data와 message는 null이어도 항상 직렬화합니다. Validation, 잘못된 요청, 404, 401, 403 및 예상하지 못한 500 오류에 공통 JSON 응답을 사용합니다.

## 아직 결정되거나 구현되어야 할 항목

- 회원가입 시 SSAFY 금융망 사용자를 함께 만들지, 가입 후 별도로 연결할지 (현재는 자체 회원만 생성)
- 실제 대출 실행 제외 원칙과 마이너스통장 상환·이자 납부 API의 충돌
- ERD에서 확정한 AES-256-GCM 암호화와 HMAC-SHA-256 조회 해시의 키 저장·순환 구현
- Refresh Token Rotation 적용 여부 (재발급 API 구현 시 결정)
- ERD의 `UserOAuthAccount`를 사용하는 OAuth 인증 Endpoint와 계정 연결 정책
- ERD의 `UserDevice`를 사용하는 FCM Device Token 등록·폐기 API
- Redis의 캐시, 세션, Refresh Token 저장소 및 분산 락 적용 범위

## 향후 작업

- Auth 나머지 API(로그아웃·재발급·세션 삭제)와 User 도메인
- Kakao/Google OAuth
- API 명세의 금융 도메인
- SSAFY 금융망 및 금융상품한눈에 연동
- Redis 적용 범위 확정 및 구현
- FCM 알림
- 생성형 AI 연동
- ERD 나머지 테이블의 Flyway Migration (V1은 `user`, `auth_session`만 포함)
- Testcontainers MySQL 통합 테스트
- Prometheus Metrics 연동
- Docker와 CI/CD는 infra 작업 범위에서 진행
