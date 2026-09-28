# 도담 Backend

도담은 사용자의 자산과 소비 흐름을 분석하고, 목표 달성을 방해하는 금융 비효율을 발견해 다음 행동을 제안하는 청년 금융 코치 앱입니다.

백엔드는 **Java 21 · Spring Boot 3.5 기반 REST API 서버**입니다. SSAFY 금융망에서 받은 금융 데이터를 저장·분석해 목표 관리, 소비 패턴·예측, 금융 진단과 추천, AI 코치 "토리"의 답변 근거를 제공합니다.

## 주요 기능

### 인증과 사용자

- 이메일 회원가입·로그인, JWT Access Token과 Refresh Token 회전
- Google·Kakao 소셜 로그인 (앱이 받은 토큰을 서버가 공급자에 검증)
- 회원 정보·설정·재무 프로필 관리와 회원 탈퇴

### 금융 데이터

- SSAFY 금융망 사용자 연결, 동기화, 신용등급 조회
- 계좌·거래·카드·청구서·할부·대출·마이너스통장·예적금 조회
- 계좌 이체 (금융망 실제 이체)
- 마이너스통장 상환·이자 납부 (`Idempotency-Key`로 재전송 방지)
- 구독(정기결제) 목록과 결제 이력
- 금융회사·예금·적금·대출·카드 상품 조회

### 목표

- 모으기·줄이기 목표 생성, 수정, 삭제와 진행 이력
- 목표별 연결 계좌와 예상 달성일 계산
- **세이프박스**: 사용자당 도담 전용 계좌 1개에 목표별 금액을 나눠 담고 꺼냄

### 분석과 리포트

- 카테고리별 소비 집계와 현금흐름(일별 잔액, 예정 이벤트, 부족 위험)
- 소비 패턴 분석: K-Means 군집과 같은 성별·나이대 또래 비교
- 소비 예측: 24개월 거래 기반 시계열 예측과 근거 분해(수준·추세·계절)
- 월간 리포트와 금융 효율 점수

### 진단, 추천, 시뮬레이션

- 소비 증가, 고금리 대출, 목표 지연 등 금융 비효율 진단
- 대환대출·예적금·카드 상품 추천과 예상 절약액
- 저축·소비·대출 상환 시뮬레이션과 개선 행동 제안

### AI 코치 "토리"

- 서버가 계산한 진단·추천·예측 수치를 도구로 조회해 문장으로 설명
- 답변 속 숫자를 서버 계산값과 대조하는 가드(`CoachNumberGuard`)
- Redis 응답 캐시와 사용자별 호출 상한

### 알림

- FCM 푸시와 알림함
- 결제·상환 예정, 현금흐름 위험, 목표, 추천, 리포트, 데일리 브리핑 이벤트 스케줄

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5.16 (Web, Data JPA, Data Redis, Security, Validation, Actuator) |
| Build | Gradle 8.14.3 (Wrapper) |
| Database | MySQL 8.0, Flyway |
| Cache | Redis (AI 코치 캐시·대화 세션·호출 상한, 재전송 방지) |
| Auth | JJWT 0.12.6 (HS256), Nimbus JOSE (구글 ID token JWK 검증) |
| Push | Firebase Admin SDK 9.4.3 |
| Monitoring | Micrometer Prometheus |
| ML | Python(scikit-learn)으로 학습한 K-Means 모델 아티팩트 (`ml/spending-kmeans-v2.json`) |
| Test | JUnit 5, Spring Security Test, H2 |

## 프로젝트 구조

```text
backend/
├─ database/seeds/           시연용 seed SQL (Flyway가 실행하지 않음)
├─ docs/                     도메인별 구현 기준 문서
├─ ml/pattern/               K-Means 학습 스크립트
├─ scripts/                  seed fixture 생성기, 서버 점검 스크립트
├─ src/main/java/com/ssafy/dodam/
│  ├─ domain/                도메인별 controller·service·repository·entity·dto
│  ├─ global/                공통 설정·보안·암호화·예외·응답·정책
│  └─ infrastructure/
│     ├─ external/           SSAFY 금융망(ssafy), AI(ai), 소셜 로그인(social) 어댑터
│     ├─ cache/              Redis 기반 코치 캐시·대화 세션
│     └─ ratelimit/          코치 호출 상한
├─ src/main/resources/
│  ├─ db/migration/          Flyway 마이그레이션 V1~V46
│  ├─ ml/                    K-Means 모델 아티팩트
│  └─ application*.yml       공통·local·prod 설정
└─ src/test/                 단위·통합 테스트
```

### 도메인 패키지

| 패키지 | 내용 | 문서 |
| --- | --- | --- |
| `auth`, `user` | 인증, 소셜 로그인, 회원·설정·재무 프로필 | [auth-social](docs/auth-social.md), [user-profile](docs/user-profile.md) |
| `finance` | 금융망 연결·동기화·요약·신용등급 | [finance-data](docs/finance-data.md) |
| `account`, `transaction` | 계좌 조회·이체, 거래 조회, 소비 집계 | [account-query](docs/account-query.md), [account-transfer](docs/account-transfer.md), [transaction-query](docs/transaction-query.md), [spending-aggregation](docs/spending-aggregation.md) |
| `card`, `installment` | 카드·청구서·결제 설정, 할부 | [card-basic-query](docs/card-basic-query.md), [card-payment-query](docs/card-payment-query.md), [installment-query](docs/installment-query.md) |
| `loan`, `minusaccount` | 대출 조회·분석·금리 우선순위, 마이너스통장 | [loan-query](docs/loan-query.md), [loan-analysis](docs/loan-analysis.md), [minus-account](docs/minus-account.md) |
| `deposit`, `savings`, `depositsaving` | 예금·적금 상세, 예상금액, 공통 조회 | [deposit-detail](docs/deposit-detail.md), [savings](docs/savings.md), [deposit-savings](docs/deposit-savings.md) |
| `goal` | 목표, 세이프박스 | [goal](docs/goal.md), [safebox](docs/safebox.md) |
| `cashflow` | 현금흐름, 일별 잔액, 이벤트, 위험 | [cashflow](docs/cashflow.md), [cashflow-risk](docs/cashflow-risk.md) |
| `pattern` | 소비 패턴, 또래 비교 | [pattern](docs/pattern.md) |
| `forecast` | 소비 예측 | [spending-forecast](docs/spending-forecast.md) |
| `report`, `improvement` | 월간 리포트, 효율 점수, 개선 행동 | [improvement-report-contract](docs/improvement-report-contract.md) |
| `diagnosis`, `recommendation`, `simulation` | 진단, 상품 추천, 시뮬레이션 | [diagnosis](docs/diagnosis.md), [recommendation](docs/recommendation.md), [simulation](docs/simulation.md) |
| `coach` | AI 코치 토리 | [ai-coach](docs/ai-coach.md) |
| `notification` | 푸시, 알림함, 이벤트 스케줄 | [notification](docs/notification.md) |
| `subscription` | 구독 | [subscription](docs/subscription.md) |
| `product` | 금융회사·상품 조회 | [product-comparison-card-recommendation](docs/product-comparison-card-recommendation.md) |

## 공통 규약

### 응답 형식

모든 API는 `ApiResponse`로 감싸 응답합니다. 목록은 `PageResponse`를 사용합니다.

```json
{ "success": true, "data": {}, "message": null }
```

```json
{ "success": false, "data": null, "message": "요청 값이 올바르지 않습니다.", "code": "VALIDATION_ERROR" }
```

오류는 `ErrorCode`와 전역 예외 처리기로 변환합니다. 페이지 번호는 0부터 시작하고 기본 크기 20, 최대 100입니다.

### 보안

| 항목 | 정책 |
| --- | --- |
| 공개 경로 | `GET /api/health`, `GET /actuator/health`, `POST /api/v1/auth/{signup,login,refresh}`, `POST /api/v1/auth/social/*` |
| 관리자 경로 | `/api/v1/admin/**`는 `ADMIN` 권한 필요 |
| 그 외 `/api/v1/**` | 인증 필요. 토큰이 없거나 만료되면 401 (`UNAUTHORIZED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`) |
| 세션 | STATELESS, CSRF 비활성화 (네이티브 앱이 Bearer 토큰만 사용) |
| Access Token | JWT HS256, 30분. Claim: `sub`(userId), `sid`(세션), `role` |
| Refresh Token | 256bit 난수, DB에는 SHA-256 해시만 저장. 14일, 재발급마다 교체 |
| 소유권 | 모든 사용자 리소스는 인증 사용자 조건으로 조회. 타인 리소스는 존재 여부를 숨겨 `RESOURCE_NOT_FOUND` |

### 민감정보

- 계좌·카드번호, 금융망 `userKey`, 이메일 원문은 AES-256-GCM 암호문(`*_encrypted`)으로 저장합니다.
- 조회용 해시(`*_hash`)는 HMAC-SHA-256이고, 응답에는 마스킹 값(`*_masked`)만 담습니다.
- 금액은 원 단위 `long`, 금리·비율은 `BigDecimal`로 계산합니다.

## 외부 연동

| 서비스 | 용도 | 어댑터 |
| --- | --- | --- |
| SSAFY 금융망 (`finopenapi.ssafy.io`) | 사용자·계좌·카드·대출·예적금·이체·신용등급 | `infrastructure/external/ssafy` |
| SSAFY GMS (OpenAI 중계) | 토리 문장 생성 (`gpt-5.4-mini`) | `infrastructure/external/ai` |
| Google, Kakao | 소셜 로그인 토큰 검증 | `infrastructure/external/social` |
| Firebase Cloud Messaging | 푸시 알림 | `domain/notification` |

가입·발급 절차는 [exec/외부서비스.md](../exec/외부서비스.md)에 정리되어 있습니다.

## 실행 환경

- JDK 21
- MySQL 8.0
- Redis
- (선택) Python 3.11 + `cryptography`: seed fixture 생성 시

Gradle은 설치하지 않고 Wrapper(`gradlew`)를 사용합니다. 명령은 `backend/`에서 실행합니다.

## 환경변수

Spring Boot는 `.env`를 자동으로 읽지 않습니다. 실행 환경이나 IDE에서 주입합니다. 운영 서버는 `infra/.env`의 값을 `docker-compose.yml`이 컨테이너에 전달하며, 키 이름과 설명은 `infra/.env.example`에 있습니다.

| 환경변수 | 설명 | 필수 |
| --- | --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL 접속 정보 | 필수 |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis 접속 정보 | 필수 |
| `CORS_ALLOWED_ORIGINS` | 허용 Origin, 쉼표 구분 | 필수 |
| `JWT_SECRET` | Access Token 서명키. 32바이트 미만이면 기동 실패 | 필수 |
| `DATA_ENCRYPTION_KEY` | `*_encrypted` AES-256-GCM 키. Base64 32바이트. **잃으면 복구 불가** | 필수 |
| `DATA_HASH_KEY` | 이메일·`userKey` 등 암호화 컬럼의 조회용 HMAC 키. Base64 32바이트 | 필수 |
| `NUMBER_HASH_KEY` | 계좌·카드번호 `*_hash` HMAC 키. 바뀌면 기존 해시와 매칭이 깨짐 | 필수 |
| `SSAFY_FINANCE_API_KEY` | SSAFY 금융망 API Key | 필수 |
| `OPENAI_API_KEY` | SSAFY GMS 토큰. 비우면 토리가 템플릿 문장으로 응답 | local 선택, prod 필수 |
| `GOOGLE_CLIENT_ID` | 구글 ID token `aud`로 허용할 클라이언트 ID. 비우면 구글 로그인 503 | 선택 |
| `KAKAO_APP_ID` | 카카오 앱 ID. 비우면 앱 ID 대조만 생략 | 선택 |
| `FCM_SERVICE_ACCOUNT_PATH` | Firebase 서비스 계정 JSON 경로 | prod 필수 |

## Profile

| Profile | 용도 | 특징 |
| --- | --- | --- |
| `local` | 로컬 개발 | 환경변수로 MySQL·Redis 연결, `ddl-auto: validate`, Flyway `out-of-order: true` |
| `prod` | 운영 (EC2 Docker) | local과 같은 DB 정책, FCM 발송 켬, 시연 계정 부트스트랩, K-Means는 커밋된 아티팩트만 사용 |
| `test` | 테스트 | H2 인메모리, Flyway 끔, `ddl-auto: create-drop`, 외부 API는 Mock |

## 실행

Unix 계열:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

## 데이터베이스

### 마이그레이션

Flyway 마이그레이션은 `src/main/resources/db/migration`에 있으며 V1~V46입니다. 번호 구간을 담당 영역별로 나눠 써서 `out-of-order: true`로 운영합니다.

> **빈 DB에 번호순으로 적용하면 V6에서 멈춥니다.** V6의 `transaction.card_id`가 V11의 `card`를 참조하기 때문입니다. 빈 DB에는 V1~V5, V7~V11을 먼저 적용한 뒤 `out-of-order`로 V6과 V12 이후를 적용하거나, 아래 DB 덤프로 시작합니다.

### 시연 데이터

- `database/seeds/`의 SQL은 합성 사용자 4,000명과 24개월 거래를 만드는 시연용 데이터입니다. Flyway가 실행하지 않습니다.
- 적재 순서와 선행 조건은 [database/seeds/README.md](database/seeds/README.md)에 있습니다.
- 운영 서버 적재는 `infra/scripts/seed-load.sh`를 사용합니다.
- 계좌·카드번호 fixture는 `scripts/make_seed_fixtures.py` 등이 실제 키로 암호문·해시를 만들어 넣습니다. seed 파일에는 번호 원문이 없습니다.

### DB 덤프

마이그레이션과 seed 적재가 끝난 상태의 덤프가 [exec/dodam_dump.sql.gz](../exec/dodam_dump.sql.gz)에 있습니다.

```bash
mysql -u<user> -p -e "CREATE DATABASE dodam CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
gunzip -c exec/dodam_dump.sql.gz | mysql --default-character-set=utf8mb4 -u<user> -p dodam
```

덤프의 암호화 컬럼은 덤프 전용 키로 만들었습니다. 다른 키로 서버를 띄우면 계좌·카드번호 복호화와 번호 해시 조회만 실패하고 나머지 기능은 정상입니다. 현금흐름 예측, 패턴 분석, 진단 같은 파생 데이터는 덤프에 없으므로 앱에서 한 번씩 생성합니다.

## 테스트와 빌드

```bash
./gradlew test
./gradlew clean build
```

Windows PowerShell에서는 `.\gradlew.bat test`, `.\gradlew.bat clean build`를 사용합니다.

- 테스트는 `test` Profile(H2)로 외부 MySQL·Redis·외부 API 없이 실행됩니다.
- 통합 테스트 컨텍스트가 많아 테스트 JVM 힙을 2GB, 컨텍스트 캐시 상한을 64로 설정했습니다 (`build.gradle`).
- MySQL 전용 SQL을 검증하는 `*MySqlIntegrationTest`는 `*_MYSQL_TEST_URL` 환경변수가 있을 때만 실행됩니다. H2 통과만으로 MySQL 제약조건까지 검증된 것으로 보지 않습니다.

## Health Check와 모니터링

| 경로 | 용도 |
| --- | --- |
| `GET /api/health` | 애플리케이션 응답 여부. DB·Redis·외부 API를 호출하지 않음 |
| `GET /actuator/health` | 컨테이너 Health Check. DataSource·Redis 포함, 상세 비공개 |
| `GET /actuator/prometheus` | Prometheus 지표. 외부에서는 nginx가 차단하고 컨테이너 네트워크 안에서만 수집 |

## 관련 문서

- 도메인별 구현 기준: [docs/](docs/)
- 남은 작업과 알려진 제한: [docs/remaining-work.md](docs/remaining-work.md)
- 시연 흐름: [exec/시연시나리오.md](../exec/시연시나리오.md)
- 배포와 인프라: [infra/README.md](../infra/README.md)
- API 명세·ERD·기획안: [docs/](../docs/)
