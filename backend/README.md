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

.env.example에는 키만 제공하며 Spring Boot가 .env 파일을 자동으로 읽지는 않습니다. 실행 환경이나 IDE에서 환경변수를 주입하세요.

OAuth Client 정보, Firebase 서비스 계정, JWT Secret과 외부 API Key는 아직 필요하지 않습니다.

## Profile

### 공통

application.yml에는 애플리케이션 이름, Open Session in View 비활성화 및 Actuator Health 노출 정책만 있습니다.

### local

application-local.yml은 환경변수로 MySQL과 Redis에 연결합니다. JPA ddl-auto는 validate이며 Flyway가 활성화됩니다.

### prod

application-prod.yml도 환경변수만 사용합니다. 데이터 손실 위험이 있는 create, create-drop, update 설정은 사용하지 않습니다.

### test

application-test.yml은 H2를 사용하는 초기 Context 및 Health Smoke Test 전용 Profile입니다. 외부 MySQL과 Redis에 연결하지 않으며 Redis Health Indicator를 비활성화합니다.

MySQL Entity와 Flyway Migration 구현을 시작하면 MySQL 전용 JSON, TINYINT 및 제약조건 검증을 위해 Testcontainers MySQL 기반 통합 테스트로 전환해야 합니다.

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

/api/v1/admin/**는 향후 ADMIN 권한이 필요하고, 나머지 /api/v1/**는 인증이 필요합니다. 현재 인증 기능은 없으므로 보호 경로는 JSON 형식의 401을 반환합니다.

formLogin, httpBasic, OAuth2 Login은 비활성화되어 있습니다. JWT Filter, 임시 사용자 및 하드코딩 계정은 없습니다. CSRF와 SessionCreationPolicy는 Refresh Token 전달 방식이 결정되기 전까지 임의로 고정하지 않습니다.

향후 인증 방향은 JWT Access Token과 해시된 Refresh Token을 AuthSession에 보관하는 구조입니다. JWT Claim, 만료시간, 서명키, Token Rotation, Refresh Token의 Body/HttpOnly Cookie 전달 방식은 아직 결정되지 않았습니다.

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

## 아직 결정되지 않은 문서 항목

- 회원가입 시 SSAFY 금융망 사용자를 함께 만들지, 가입 후 별도로 연결할지
- 실제 대출 실행 제외 원칙과 마이너스통장 상환·이자 납부 API의 충돌
- ADMIN 역할 및 권한 데이터 모델
- 계좌번호와 카드번호의 저장 암호화 및 검색용 해시 전략
- ERD Markdown의 군집 분석 컬럼과 ERDCloud Import DDL 동기화
- 마이너스통장 API Endpoint의 줄바꿈 제거
- OAuth 사용자 식별 모델과 인증 Endpoint
- FCM Device Token 모델과 등록 API
- Redis의 캐시, 세션, Refresh Token 저장소 및 분산 락 적용 범위

## 향후 작업

- Auth와 User 도메인
- JWT Access Token 및 Refresh Token 구현
- Kakao/Google OAuth
- API 명세의 금융 도메인
- SSAFY 금융망 및 금융상품한눈에 연동
- Redis 적용 범위 확정 및 구현
- FCM 알림
- 생성형 AI 연동
- ERD와 Flyway Migration
- Testcontainers MySQL 통합 테스트
- Prometheus Metrics 연동
- Docker와 CI/CD는 infra 작업 범위에서 진행
