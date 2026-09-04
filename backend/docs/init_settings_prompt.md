# 도담 백엔드 초기 설정 구현 프롬프트

Java 21과 Spring Boot 기반으로 도담 백엔드 프로젝트의 초기 설정을 구성해줘.

이 작업의 목적은 비즈니스 API 구현이 아니라, 이후 도메인 기능을 안전하게 추가할 수 있는 실행 가능한 백엔드 기반을 만드는 것이다.

## 1. 작업 전 확인

코드를 생성하기 전에 반드시 다음을 먼저 확인해줘.

1. 프로젝트 루트와 backend 디렉터리의 기존 파일
2. docs/API명세서의 API 명세
3. docs/ERD의 ERD Markdown과 DODAM_ERDCLOUD_IMPORT.sql
4. infra 문서에 정리된 DB와 배포 환경
5. Git 변경 상태

API 명세의 일반 CSV와 이름이 같은 _all.csv는 동일 데이터를 다른 열 순서로 내보낸 중복 파일이다. API명세서의 인덱스에서 연결하는 일반 CSV를 기준 문서로 사용하고, 두 파일을 서로 다른 API 목록으로 계산하지 말아줘.

문서 간 내용이 충돌하면 임의로 한쪽을 선택하거나 추측하여 구현하지 말아줘. 초기 설정과 직접 관계없는 충돌은 구현하지 않고 최종 결과의 향후 작업에 기록해줘. 초기 설정을 진행할 수 없을 정도의 충돌이면 해당 부분의 작업을 중단하고 충돌한 문서와 항목을 명확히 보고해줘.

현재 확인된 문서 간 미결 사항은 다음과 같다.

- API의 회원가입은 SSAFY 금융망 사용자 생성을 포함하지만, 기획안과 Finance Data API는 회원가입 후 별도 금융망 연결 흐름을 전제로 함
- 기획안은 실제 대출 실행을 제외하지만, API에는 마이너스통장 원금 상환과 이자 납부 Endpoint가 존재함
- 관리자 API는 ADMIN 권한을 요구하지만 현재 ERD에는 사용자 역할 또는 권한 모델이 없음
- 기획안은 민감정보 암호화를 요구하지만 현재 ERD DDL은 계좌번호와 카드번호를 평문 VARCHAR와 UNIQUE 인덱스로 정의함
- ERD Markdown의 군집 분석 추가 컬럼이 DODAM_ERDCLOUD_IMPORT.sql에는 아직 반영되지 않음
- 마이너스통장 API 일부 Endpoint 문자열에 줄바꿈이 포함되어 있음
- 인프라는 Kakao/Google OAuth와 FCM을 계획하고 있지만 현재 API와 ERD에는 OAuth 사용자 식별 모델, OAuth Endpoint, FCM Device Token 모델 및 등록 API가 없음
- 인프라는 Redis를 P0으로 계획하고 있지만 캐시, 세션, 분산 락 중 실제 적용 범위는 아직 확정되지 않음

이번 작업에서는 위 미결 사항을 임의로 해결하거나 관련 비즈니스 코드와 DB Schema를 만들지 말아줘.

문서별 책임은 다음 기준으로 구분해줘.

- MVP 기능, 사용자 흐름 및 실제 금융 실행 제외 범위: 기획안
- Endpoint, HTTP Method, Parameter 및 인증 여부: API 명세
- 엔티티, 관계 및 논리 데이터 모델: ERD Markdown
- MySQL 물리 Schema와 데이터 타입: DODAM_ERDCLOUD_IMPORT.sql
- MySQL, Redis, OAuth, FCM, Health Check 및 배포 환경: infra 문서

같은 책임 범위의 문서끼리 충돌하거나 상위 기획과 하위 명세가 모순되면 자동으로 우선순위를 판단하지 말고 앞의 충돌 처리 원칙을 적용해줘.

기존 파일이 있으면 무조건 덮어쓰지 말고 내용을 확인한 뒤 현재 구조와 충돌하지 않는 방식으로 수정해줘. 기존 frontend 코드는 읽기 확인만 허용하며 절대 수정하지 말아줘.

## 2. 목표와 범위

프론트엔드와 분리된 백엔드 서버를 backend 디렉터리 안에 구축한다.

초기 단계이므로 과도한 DDD나 Clean Architecture는 적용하지 않는다. 도메인 중심 패키지 구조와 일반적인 Spring 계층 구조를 사용하되, 여러 도메인이 공유하는 외부 시스템 연동 코드는 별도 infrastructure 영역에 둘 수 있도록 구성한다.

API 명세에는 Auth, User, Account, Transaction, Card, Loan, Goal, Diagnosis, Recommendation 등 다수의 도메인 API가 정의되어 있다. 이번 작업에서는 해당 API의 Controller, Service, Entity 및 비즈니스 로직을 구현하지 않는다.

이번 작업에서 직접 Controller로 구현할 런타임 API는 서버 확인용 GET /api/health 하나뿐이다. 인프라 Health Check를 위한 GET /actuator/health는 Actuator 표준 Endpoint로 별도 활성화한다.

## 3. 기술 스택

- Java 21
- Spring Boot 3.x
- Gradle
- Spring Web
- Spring Data JPA
- Spring Data Redis
- Spring Security
- Spring Validation
- Spring Boot Actuator
- OAuth2 Client
- Lombok
- MySQL 8.x
- Flyway
- JUnit 5
- Spring Boot Test
- Spring Security Test
- H2 Database: 테스트 전용

Spring Boot와 모든 의존성은 서로 호환되는 안정 버전으로 고정해줘. 동적 버전이나 임의의 snapshot 버전은 사용하지 말아줘.

인프라 문서가 MySQL을 전제로 하므로 PostgreSQL 드라이버는 추가하지 말고 MySQL 드라이버만 사용해줘.

인프라 문서 기준으로 Kakao/Google OAuth와 Firebase FCM은 계획된 후속 기능이다. OAuth2 Client 의존성은 향후 확장을 위해 포함하되, 현재 API와 ERD에 계약이 없으므로 이번 작업에서 OAuth 로그인을 활성화하거나 Client Registration을 구성하지 말아줘. FCM 의존성과 환경변수는 실제 API와 Device Token 모델이 확정될 때 추가해줘.

Redis는 인프라 문서의 P0 구성요소이므로 연결 설정까지 준비한다. 단, 이번 작업에서는 Redis를 사용하는 캐시, 세션, Refresh Token 저장소, 분산 락 또는 비즈니스 로직을 구현하지 말아줘.

## 4. 프로젝트 위치

프로젝트 구조는 다음과 같다.

    project-root/
    ├── frontend/
    └── backend/

Spring Boot 프로젝트와 Gradle Wrapper는 모두 backend 안에 구성해줘.

## 5. 기본 패키지와 애플리케이션 이름

기본 패키지는 다음을 사용해줘.

    com.ssafy.dodam

메인 애플리케이션 클래스는 다음 위치와 이름을 사용해줘.

    src/main/java/com/ssafy/dodam/DodamApplication.java

## 6. 패키지 구조

다음 구조를 기본 방향으로 사용해줘.

    src/main/java/com/ssafy/dodam/
    ├── DodamApplication.java
    ├── global/
    │   ├── config/
    │   ├── exception/
    │   ├── health/
    │   ├── response/
    │   └── security/
    ├── domain/
    │   ├── auth/
    │   │   ├── controller/
    │   │   ├── service/
    │   │   └── dto/
    │   │       ├── request/
    │   │       └── response/
    │   └── user/
    │       ├── controller/
    │       ├── service/
    │       ├── repository/
    │       ├── entity/
    │       └── dto/
    │           ├── request/
    │           └── response/
    └── infrastructure/
        └── external/
            ├── ssafy/
            ├── finlife/
            └── ai/

    src/main/resources/
    ├── application.yml
    ├── application-local.yml
    ├── application-prod.yml
    └── db/
        └── migration/

    src/test/java/com/ssafy/dodam/
    src/test/resources/
    └── application-test.yml

아직 구현할 클래스가 없는 패키지를 유지하려고 불필요한 빈 클래스나 Utility 클래스를 만들지 말아줘. 향후 구조는 README에 설명하고, 실제 패키지는 필요한 클래스가 생길 때 추가해도 된다.

도메인이 추가될 경우 다음 형태로 확장해줘.

    domain/{domain-name}/
    ├── controller/
    ├── service/
    ├── repository/
    ├── entity/
    └── dto/
        ├── request/
        └── response/

controller, service, repository를 프로젝트 최상위에 전역 계층으로 분리하지 말아줘.

SSAFY 금융망처럼 Account, Card, Loan 등 여러 도메인이 함께 사용하는 외부 API 어댑터는 global이나 특정 domain에 억지로 넣지 말고 infrastructure/external 아래에 배치해줘. 단, 이번 작업에서는 실제 외부 API 클라이언트를 구현하지 않는다.

## 7. Gradle 설정

build.gradle에 최소한 다음 의존성을 구성해줘.

- org.springframework.boot:spring-boot-starter-web
- org.springframework.boot:spring-boot-starter-data-jpa
- org.springframework.boot:spring-boot-starter-data-redis
- org.springframework.boot:spring-boot-starter-security
- org.springframework.boot:spring-boot-starter-validation
- org.springframework.boot:spring-boot-starter-actuator
- org.springframework.boot:spring-boot-starter-oauth2-client
- org.flywaydb:flyway-core
- MySQL에서 사용하는 Flyway 모듈
- com.mysql:mysql-connector-j
- org.projectlombok:lombok
- org.springframework.boot:spring-boot-starter-test
- org.springframework.security:spring-security-test
- com.h2database:h2: 테스트 전용

Lombok은 compileOnly 및 annotationProcessor 범위로 구성해줘. 테스트 코드에서도 Lombok이 필요하다면 testCompileOnly와 testAnnotationProcessor를 올바르게 구성해줘.

Java Toolchain은 Java 21을 사용해줘.

Gradle Wrapper를 포함하고 Windows와 Unix 계열 환경 모두에서 실행할 수 있도록 구성해줘.

이번 단계에서는 WebFlux, QueryDSL, JWT 라이브러리, Firebase Admin SDK, Swagger/OpenAPI 라이브러리, Docker 관련 의존성을 추가하지 말아줘.

Actuator는 Health Check 용도로만 사용하고, 이번 단계에서는 micrometer-registry-prometheus를 추가하지 말아줘.

## 8. 환경 및 Profile 설정

application.yml에는 모든 환경에서 공통으로 사용하는 설정만 넣어줘. application.yml에서 특정 profile을 기본으로 강제 활성화하지 말아줘.

환경은 다음처럼 분리해줘.

- application-local.yml: 로컬 개발용 MySQL 설정
- application-prod.yml: 운영용 MySQL 설정
- application-test.yml: 외부 DB 없이 테스트 가능한 H2 설정

DB와 Redis의 환경변수 참조 설정은 application-local.yml과 application-prod.yml에만 배치해줘. 공통 application.yml에는 DB_URL, DB_USERNAME, DB_PASSWORD, REDIS_HOST, REDIS_PORT, REDIS_PASSWORD를 참조하는 설정을 넣지 말아줘.

application-test.yml에는 H2 Datasource를 독립적으로 정의하고 DB 또는 Redis 환경변수를 참조하지 말아줘. 또한 management.health.redis.enabled=false를 명시하여 외부 Redis가 없는 테스트 환경에서도 Actuator Health가 정상적으로 UP을 반환하도록 해줘.

민감한 값은 코드나 설정 파일에 직접 작성하지 말고 환경변수를 사용해줘.

현재 필요한 환경변수는 다음과 같다.

- DB_URL
- DB_USERNAME
- DB_PASSWORD
- REDIS_HOST
- REDIS_PORT
- REDIS_PASSWORD
- CORS_ALLOWED_ORIGINS

DB 설정은 다음 방향으로 작성해줘.

    spring:
      datasource:
        url: ${DB_URL}
        username: ${DB_USERNAME}
        password: ${DB_PASSWORD}
      data:
        redis:
          host: ${REDIS_HOST}
          port: ${REDIS_PORT}
          password: ${REDIS_PASSWORD}

application-prod.yml은 환경변수 참조만 포함하는 안전한 설정 템플릿으로 작성하고 Git에 포함해줘. 실제 비밀번호, 운영 DB 주소, OAuth Secret, API Key는 절대 저장하지 말아줘.

아직 구현하지 않는 외부 연동의 API Key, OAuth Client 정보, Firebase 서비스 계정 및 JWT Secret은 필수 환경변수로 바인딩하지 말아줘. 값이 없는 상태에서도 test profile의 Spring Context가 정상적으로 로딩되어야 한다.

test profile에서는 외부 Redis에 접속하지 않고 Redis Health Indicator도 비활성화해줘. Redis 의존성이 존재하더라도 Context, /api/health 및 /actuator/health 테스트가 로컬 Redis 없이 통과해야 한다.

## 9. JPA와 Flyway 설정

- spring.jpa.open-in-view는 false로 설정해줘.
- prod의 ddl-auto는 validate로 설정해줘.
- prod에서 create, create-drop, update를 사용하지 말아줘.
- local도 가능한 한 Flyway migration을 기준으로 하고 ddl-auto는 validate를 사용해줘.
- test에서는 초기 Context와 Health API의 Smoke Test 용도로만 H2를 사용하고 외부 MySQL이나 환경변수에 의존하지 않도록 해줘.
- 이번 작업에서는 ERD의 비즈니스 테이블을 임의로 생성하지 말아줘.

Flyway 디렉터리는 준비하되, 실제 Entity와 스키마 구현 범위가 확정되지 않은 상태에서 빈 테이블이나 추측한 migration을 만들지 말아줘.

H2는 MySQL 전용 DDL, JSON, TINYINT, 인덱스 및 제약조건 호환성을 검증하는 용도가 아니다. 향후 Entity와 Flyway Migration 구현을 시작할 때는 Testcontainers MySQL 기반 통합 테스트로 전환해야 한다는 점을 README의 향후 작업에 기록해줘.

Entity 구현 시 지켜야 할 원칙은 다음과 같다.

- Lombok의 @Data 사용 금지
- 무분별한 setter 생성 금지
- Entity를 API Response로 직접 반환 금지
- ERD DDL의 BIGINT 금액과 잔액은 Java Long으로 매핑
- ERD DDL의 DECIMAL 금리, 비율 및 정밀 계산 값은 BigDecimal로 매핑
- 금융 수치에 float 또는 double 사용 금지
- 날짜와 시간 타입은 의미에 맞게 LocalDate, YearMonth, LocalDateTime 또는 Instant를 선택

## 10. API 경로 원칙

API 명세의 비즈니스 API 기본 경로는 /api/v1이다.

GET /api/health는 로드밸런서, 배포 및 서버 기동 확인을 위한 비즈니스 외 운영 경로이므로 버전 경로 밖에 두고 공개한다.

이번 작업에서는 현재 API 명세 기준 243개 비즈니스 Endpoint를 생성하지 말아줘. 존재하지 않는 기능을 빈 Controller나 501 응답으로 미리 만들지도 말아줘. API 개수는 명세 변경에 따라 달라질 수 있으므로 구현 전에 일반 CSV 기준으로 다시 확인해줘.

향후 API 구현 시 다음 규칙을 적용할 수 있도록 공통 구조만 준비해줘.

- 명세에서 물음표가 붙은 Parameter는 선택값으로 해석
- page는 0 이상, size는 1 이상 100 이하
- 날짜는 ISO-8601 형식 사용
- yearMonth는 YYYY-MM 형식 사용
- Page 객체를 그대로 응답하지 않고 안정적인 PageResponse DTO 사용
- accountNo나 cardNo와 같은 금융 식별자를 로그에 그대로 남기지 않음
- 본인 리소스 여부는 요청값의 userId가 아니라 인증 주체를 기준으로 검증

PageResponse를 만든다면 최소한 다음 메타데이터를 포함해줘.

- content
- page
- size
- totalElements
- totalPages
- last

## 11. 공통 API 응답

global/response에 제네릭 기반의 최소한의 공통 ApiResponse 구조를 만들어줘.

성공 응답 예시는 다음과 같다.

    {
      "success": true,
      "data": {},
      "message": null
    }

실패 응답은 최소한 클라이언트가 분기할 수 있는 안정적인 code를 포함해줘.

    {
      "success": false,
      "data": null,
      "message": "요청 값이 올바르지 않습니다.",
      "code": "VALIDATION_ERROR"
    }

Validation 오류에서 필드별 정보가 필요하면 errors 필드를 선택적으로 추가할 수 있다. 단, 중첩 Wrapper나 불필요하게 복잡한 응답 계층을 만들지 말아줘.

ApiResponse의 code와 errors가 null일 때 code: null 또는 errors: null이 노출되지 않도록 두 필드에만 @JsonInclude(JsonInclude.Include.NON_NULL) 또는 동일한 필드 단위 Jackson 직렬화 정책을 적용해줘.

data와 message는 null이어도 위 응답 예시와 동일하게 항상 직렬화해야 한다. 따라서 ApiResponse 클래스 전체에 @JsonInclude(JsonInclude.Include.NON_NULL)을 적용하지 말아줘.

성공 응답 생성을 위한 정적 팩터리 메서드는 허용하지만 과도한 메서드 오버로딩은 만들지 말아줘.

Spring의 Page, Entity, Exception 객체를 API 응답으로 직접 직렬화하지 말아줘.

## 12. Exception 처리

global/exception에 @RestControllerAdvice 기반의 GlobalExceptionHandler를 만들어줘.

초기 단계에서 다음 상황을 JSON 공통 응답으로 처리해줘.

- Bean Validation 실패: 400
- ConstraintViolationException: 400
- 읽을 수 없는 JSON Body: 400
- Path 또는 Query Parameter 타입 불일치: 400
- Spring Security 인가를 통과한 뒤 존재하지 않는 경로: 404
- 인증되지 않은 요청: 401
- 권한이 없는 요청: 403
- 예상하지 못한 서버 오류: 500

401과 403은 Spring Security Filter 단계에서도 동일한 JSON 형식을 사용하도록 AuthenticationEntryPoint와 AccessDeniedHandler를 구성해줘.

다음 원칙을 지켜줘.

- Stack trace, 클래스명, SQL, 내부 경로를 응답에 노출하지 않음
- 예상하지 못한 예외는 서버 로그에만 기록
- 사용자 입력값, 비밀번호, 토큰, 계좌번호, 카드번호를 로그에 그대로 남기지 않음
- HTTP 상태와 error code의 의미를 일치시킴

이번 작업에서 존재하지 않는 비즈니스 도메인 예외를 추측하여 대량으로 만들지 말아줘.

## 13. Spring Security

global/security에 SecurityConfig를 만들고 SecurityFilterChain 방식으로 구성해줘.

현재 API 명세 기준 공개 경로는 다음과 같다.

- GET /api/health
- GET /actuator/health
- POST /api/v1/auth/signup
- POST /api/v1/auth/login
- POST /api/v1/auth/refresh
- CORS preflight 요청

인가 규칙은 다음 방향으로 명시해줘.

- /api/v1/admin/**는 ADMIN 권한 필요
- 위 공개 경로를 제외한 /api/v1/**는 인증 필요
- 그 외 경로는 기본적으로 거부하거나 필요한 경우만 명시적으로 허용
- 모든 요청을 permitAll로 설정하지 않음

현재는 회원가입, 로그인, Access Token, Refresh Token, AuthSession 저장소, OAuth 로그인 및 사용자 권한 조회를 구현하지 않는다.

API, ERD 및 인프라 문서를 종합하면 향후 인증은 JWT Access Token과 원문이 아닌 해시를 AuthSession에 저장하는 상태 기반 Refresh Token 구조를 전제로 한다. 다만 Refresh Token을 Request Body와 HttpOnly Cookie 중 어떤 방식으로 전달할지, JWT Claim과 만료시간, 서명키 및 Token Rotation 정책은 아직 확정되지 않았다. 이번 작업에서는 확정된 방향과 미확정 사항을 구분하고 인증 기능 자체를 구현하지 말아줘.

- JWT 의존성이나 JWT Filter를 추가하지 않음
- 임시 UserDetailsService나 하드코딩 사용자를 만들지 않음
- Spring Boot의 기본 생성 사용자와 임시 비밀번호가 활성화되거나 로그에 출력되지 않도록 구성
- formLogin과 httpBasic을 개발 편의 목적으로 노출하지 않음
- OAuth2 로그인을 활성화하지 않음
- SessionCreationPolicy를 임의로 STATELESS 또는 ALWAYS로 고정하지 않음
- Refresh Token을 HttpOnly Cookie로 전달할 가능성이 있으므로 CSRF를 이유 없이 전역 비활성화하지 않음
- JWT Claim, 만료시간, 서명키, Token Rotation, Refresh Token 전달 방식 및 ADMIN 권한 모델의 결정 필요 사항을 README의 향후 작업에 명확히 기록

보호된 API를 인증 없이 요청하면 HTML이나 redirect가 아니라 공통 JSON 형식의 401 응답을 반환하도록 해줘.

## 14. Health API와 Actuator

비즈니스 도메인 밖의 global/health에 HealthController를 만들고 다음 Endpoint만 구현해줘.

    GET /api/health

응답은 다음과 같아야 한다.

    {
      "success": true,
      "data": {
        "status": "UP"
      },
      "message": null
    }

Health API에서 DB, Redis 또는 외부 API를 호출하지 말아줘. 초기 단계에서는 Spring 애플리케이션이 요청에 응답할 수 있는지만 확인한다.

인프라의 컨테이너 Health Check를 위해 Spring Boot Actuator의 다음 Endpoint도 활성화해줘.

    GET /actuator/health

Actuator 설정은 다음 원칙을 지켜줘.

- management.endpoints.web.exposure.include에는 health만 포함
- 운영환경에서 health 상세정보를 외부에 노출하지 않음
- /actuator/health는 SecurityConfig에서 공개
- 다른 Actuator Endpoint를 전체 공개하지 않음
- /actuator/health 응답은 Actuator 표준 형식을 유지하고 ApiResponse로 감싸지 않음
- local과 prod에서는 기본 DataSource 및 Redis Health 상태를 확인할 수 있도록 구성
- test에서는 외부 Redis가 없어도 UP이 되도록 Redis Health Indicator 비활성화
- Prometheus와 Metrics 수집은 이번 작업 범위에서 제외

## 15. CORS

CORS 설정은 별도의 Config와 @ConfigurationProperties 기반 속성 클래스로 관리해줘.

허용 Origin은 CORS_ALLOWED_ORIGINS 환경변수에서 주입받고 여러 Origin을 설정할 수 있도록 List 형태로 바인딩해줘.

필요한 HTTP Method에는 GET, POST, PUT, PATCH, DELETE, OPTIONS를 포함해줘.

API 명세 구현 시 Authorization과 Content-Type Header를 사용할 수 있도록 구성하되 모든 Header를 불필요하게 노출하지 말아줘.

향후 쿠키나 세션 인증 가능성을 고려해 credentials 설정을 지원해줘. credentials를 true로 사용할 때는 구체적인 Origin만 허용하고 다음과 같은 잘못된 조합을 절대 사용하지 말아줘.

    allowedOrigins("*") + allowCredentials(true)

테스트 환경에서는 별도의 안전한 테스트 Origin을 사용하여 CORS 환경변수가 없어도 Context가 로딩되도록 해줘.

## 16. Git과 비밀정보

backend/.gitignore를 적절하게 구성해줘.

다음 항목은 Git에 포함하지 말아줘.

- build/
- .gradle/
- IDE의 불필요한 설정
- .env 및 실제 환경변수 파일
- Secret이 포함된 설정 파일
- 로그 파일

.env.example을 만든다면 키 이름만 포함하고 실제 값은 넣지 말아줘. .env.example은 Git에 포함할 수 있도록 예외 처리해줘.

application.yml, application-local.yml, application-prod.yml, application-test.yml은 Secret이 없는 설정 템플릿이므로 Git에 포함해줘. application-prod.yml 자체를 무조건 ignore하지 말아줘.

## 17. README

backend/README.md에 다음 내용을 작성해줘.

1. 서비스와 백엔드 초기 설정 설명
2. 기술 스택
3. 프로젝트 구조
4. 실행 전제조건
5. 필요한 환경변수
6. local profile 실행 방법
7. test profile과 테스트 실행 방법
8. Gradle build 방법
9. 공개 및 보호 Endpoint 정책
10. JWT Access Token과 Refresh Token 세션 방향 및 아직 결정되지 않은 세부 정책
11. 향후 외부 연동과 비즈니스 도메인 작업
12. API, ERD, 기획안 사이의 현재 미결 사항과 구현 전 결정할 항목

실행 명령은 Gradle Wrapper 기준으로 안내해줘.

Unix 계열:

    ./gradlew bootRun --args='--spring.profiles.active=local'
    ./gradlew test
    ./gradlew clean build

Windows PowerShell:

    .\gradlew.bat bootRun --args='--spring.profiles.active=local'
    .\gradlew.bat test
    .\gradlew.bat clean build

## 18. 코딩 스타일

다음 원칙을 지켜줘.

- 생성자 주입 사용
- 필드 주입 금지
- Controller에 비즈니스 로직 작성 금지
- Repository를 Controller에서 직접 호출 금지
- Entity를 API Response로 직접 반환 금지
- Request DTO와 Response DTO 분리
- 불필요한 Service Interface와 ServiceImpl 구조 금지
- 실제 구현체가 여러 개 필요한 경우가 아니면 Service 클래스 직접 사용
- Utility 클래스 남발 금지
- global 영역에 특정 도메인 비즈니스 로직 배치 금지
- Lombok은 필요한 범위에서만 사용
- Entity에 @Data 사용 금지
- 설정값은 @ConfigurationProperties 사용을 우선하고 무분별한 @Value 사용 지양
- 테스트를 위해 운영 코드를 왜곡하거나 테스트 전용 Endpoint를 추가하지 않음
- 향후 생성형 AI에는 원본 거래내역이나 불필요한 개인정보를 전달하지 않고 서버가 계산한 최소 데이터만 전달
- 향후 군집 비교에는 다른 사용자의 거래 원문이나 식별정보를 사용하지 않고 익명화된 집계 통계만 사용
- 계좌번호와 카드번호의 저장 암호화 및 검색용 해시 전략이 확정되기 전에는 임의의 가역 암호화나 임시 암호화 코드를 추가하지 않음

## 19. 과도한 구현 금지

현재 작업은 백엔드 초기 설정만을 목적으로 한다.

다음 기능은 구현하지 말아줘.

- API 명세의 비즈니스 Endpoint
- 회원가입과 로그인
- JWT 또는 다른 Access Token 발급
- Refresh Token 저장과 재발급
- Spring Session 또는 Redis Session
- OAuth 로그인
- User CRUD
- SSAFY 금융망 연동
- 금융상품한눈에 연동
- 생성형 AI 연동
- Redis 캐시, 세션, Refresh Token 저장소 및 분산 락의 실제 구현
- 중복 요청 방지 및 Idempotency 실제 구현
- FCM
- Docker
- CI/CD
- Swagger/OpenAPI
- ERD 전체 Entity와 DB Schema

확장 가능한 위치와 공통 기반까지만 준비하고 실제 비즈니스 로직은 구현하지 말아줘.

## 20. 테스트와 완료 검증

테스트는 외부 MySQL, Redis, OAuth Provider 또는 외부 API 없이 통과해야 한다.

최소한 다음을 검증해줘.

1. test profile에서 Spring Context가 정상 로딩됨
2. GET /api/health가 200과 약속된 공통 응답을 반환함
3. GET /actuator/health가 외부 Redis 없이 200과 Actuator 표준 UP 응답을 반환함
4. 노출을 허용하지 않은 Actuator Endpoint가 외부에 공개되지 않음
5. 인증 없이 보호 경로를 요청하면 JSON 형식의 401을 반환함
6. 인증 없이 관리자 경로를 요청하면 JSON 형식의 401을 반환함
7. CORS 설정이 허용된 테스트 Origin에 올바른 Header를 반환함

작업 완료 후 다음 명령을 직접 실행해줘.

    ./gradlew clean build

Windows 환경에서는 다음을 실행해줘.

    .\gradlew.bat clean build

그리고 다음을 확인해줘.

- 모든 테스트 통과
- Spring Context 정상 로딩
- package와 import 오류 없음
- 불필요한 dependency 없음
- Secret이나 실제 운영 값이 저장되지 않음
- frontend 파일 변경 없음
- 기존 사용자 변경사항을 덮어쓰지 않음

오류가 발생하면 원인을 해결한 뒤 clean build를 다시 실행해줘.

## 21. 최종 결과 보고

작업 완료 후 다음 형식으로 결과를 보고해줘.

### 생성하거나 수정한 구조

주요 디렉터리와 파일 트리 및 기존 파일과 병합한 내용

### 추가한 Dependencies

추가한 Spring, Gradle, DB 및 테스트 의존성과 각각의 목적

### 설정 내용

DB, Redis, JPA, Flyway, Actuator, Security, CORS, Profile, 공통 응답 및 예외 처리의 주요 설정

### Security 정책

공개 경로, 보호 경로, 관리자 경로와 JWT 및 Refresh Token 세부 정책 미결정 사항

### 실행 방법

로컬 실행, 테스트 및 build 명령

### 환경변수

현재 필요한 환경변수와 향후 연동 시 추가될 환경변수 구분

### 검증 결과

실행한 명령, 테스트 수, 성공 여부 및 해결한 오류

### 향후 작업

아직 구현하지 않은 Auth, User, 각 금융 도메인과 외부 API 작업, JWT Claim·만료시간·서명키·Token Rotation과 Refresh Token 전달 방식 결정, ADMIN 권한 모델, 금융 식별자 암호화 및 검색 전략, OAuth API와 사용자 식별 모델, Redis 적용 범위, FCM Device Token 모델과 등록 API

코드를 생성하기 전에 기존 프로젝트 파일을 먼저 확인하고, 이미 존재하는 설정이나 사용자 변경사항을 보존하면서 현재 구조와 충돌하지 않도록 작업해줘.
