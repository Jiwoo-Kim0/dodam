# Notification API — 알림 목록·상세·미확인 수·읽음·전체 읽음·삭제 · 푸시 기기 · 이벤트 연동

D9 "22 Notification 기본 API 및 BE-A 통합"(`S15P21D105-458`)의 하위 티켓 `S15P21D105-519`(API 22 알림 목록·상세·미확인 수),
`S15P21D105-520`(API 22 알림 읽음·전체읽음·삭제) 구현 기준이다. API 명세 22 Notification 6개를 모두 다룬다.
`S15P21D105-521`(BE-A 담당 도메인 통합 테스트) 결과는 아래 "BE-A 통합 테스트"에 있다.
D10 `S15P21D105-580`(Notification 기본 API: 푸시 기기 토큰·FCM 발송)과 `S15P21D105-581`(Notification 이벤트 연동)은 "푸시(580)"·"이벤트 연동(581)" 절에 있다.

## 모델

| 테이블 | 엔티티 | 내용 |
| --- | --- | --- |
| `notification` (V32) | `Notification` | 알림 1건. 유형, 제목, 내용(TEXT), 원천(`source_type`·`source_id`), 중복 방지 키, 읽음(`is_read`·`read_at`), 소프트 삭제(`deleted_at`) |

- ERD 정의 그대로다. `(user_id, dedupe_key)` 유일 제약이 같은 이벤트의 알림 중복 생성을 막고, `(user_id, is_read, type, created_at)` 인덱스가 목록 필터를 받친다.
- **생성 API는 없다.** 명세 22는 조회·읽음·삭제만 정한다. 알림은 각 도메인이 {@code NotificationPublisher}로 만든다(D10 이벤트 연동). 시연 데이터는
  `database/seeds/DODAM_NOTIFICATION_SEED.sql`(시연 사용자 U0001~U0004)이 적재한다.
- `type`은 VARCHAR(50) 문자열로 저장한다. `NotificationType` enum은 생성 입력과 목록 필터 검증에 쓰고, 다른 도메인이 SQL로 넣은 미지 값도 읽을 수 있다.
- `user_device`(FCM 토큰, V33)와 푸시 발송은 "푸시(580)" 절. `user_setting.notification_enabled`는 푸시 발송에서 확인하며 앱 안의 알림함에는 항상 남긴다.
- 마이그레이션 번호는 **V32**(notification)·**V33**(user_device)(BE-A 2차 구간 V30~V49, `db/migration/README.md`).

| 테이블 | 엔티티 | 내용 |
| --- | --- | --- |
| `user_device` (V33) | `UserDevice` | 푸시 기기. FCM 토큰 암호문(`FieldCryptor` AES-256-GCM)·해시(HMAC-SHA-256, 전체 UNIQUE), 플랫폼(ANDROID/IOS/WEB), `enabled`, 마지막 사용 시각 |

### 알림 유형 (`NotificationType`)

| 코드 | 의미 | 근거 |
| --- | --- | --- |
| `PAYMENT_DUE` | 카드·정기결제 결제 예정 | 기획안 14-6 결제 예정 |
| `REPAYMENT_DUE` | 대출·마이너스통장 상환(이자) 예정 | 14-6 상환 예정 |
| `OVERDUE_RISK` | 연체 위험 | 14-6 연체 위험 |
| `CASHFLOW_RISK` | 현금흐름 예측 잔액 부족 위험 | 7-4 위험 발생 전 사전 알림 |
| `GOAL_PROGRESS` / `GOAL_ACHIEVED` | 목표 진행·달성 | API 12 |
| `RECOMMENDATION` | 개선안 추천 도착 | 14-6 추천 행동 |
| `IMPROVEMENT_RESULT` | 실행한 개선안의 결과 | 14-6 개선 결과 |
| `MONTHLY_REPORT` / `DAILY_BRIEFING` | 월간 리포트·데일리 브리핑 | 14-6, 7-6 |
| `SYSTEM` | 서비스 공지·계정 | — |

### 원천 연결 규약 (`source_type`·`source_id`)

`source_type`은 `NotificationSourceType` 이름(ACCOUNT, MINUS_ACCOUNT, CARD, INSTALLMENT, LOAN, DEPOSIT_SAVING, SUBSCRIPTION, GOAL, DIAGNOSIS,
RECOMMENDATION, CASHFLOW_FORECAST, SIMULATION, PATTERN_ANALYSIS, REPORT), `source_id`는 그 도메인 PK의 문자열이다. 둘은 함께 있거나 함께 없다.
534(마이너스통장)의 `JdbcMinusAccountAlertReader`가 먼저 쓴 `MINUS_ACCOUNT` + `account_id` 규약을 그대로 채택했으므로 그 리더는 수정 없이 동작한다.
FE는 (sourceType, sourceId)로 상세 화면에 이동한다.

### 중복 방지 (`dedupe_key`)

`NotificationPublisher.dedupeKey(parts...)`가 식별 조각(유형·원천·기간 등)을 `|`로 이어 SHA-256 hex 64자를 만든다. 같은 사용자·같은 키의 알림이 있으면
새로 만들지 않고 기존 행을 돌려준다. **삭제된 알림도 되살리지 않는다**(사용자가 지운 알림이 다시 나타나면 안 된다). 동시 요청은 유일 제약이 막고 제약
위반 시 기존 행을 다시 읽는다. 키가 null이면 매번 새 알림이다.

## API

모든 API는 인증이 필요하고 본인 알림만 다룬다. 타인 것·없는 것·삭제된 것은 구분하지 않고 404다.

| 기능 | Method | Endpoint | 상태 코드 | 티켓 |
| --- | --- | --- | --- | --- |
| 알림 목록 | GET | `/api/v1/notifications?type&isRead&fromDate&toDate&page&size` | 200 | 519 |
| 읽지 않은 알림 수 | GET | `/api/v1/notifications/unread-count` | 200 | 519 |
| 알림 상세 | GET | `/api/v1/notifications/{notificationId}` | 200 | 519 |
| 전체 읽음 | PATCH | `/api/v1/notifications/read-all` | 200 | 520 |
| 읽음 | PATCH | `/api/v1/notifications/{notificationId}/read` | 200 (멱등) | 520 |
| 삭제 | DELETE | `/api/v1/notifications/{notificationId}` | 200 (`data: null`) | 520 |

조회는 `NotificationQueryService`/`NotificationQueryController`(519), 변경은 `NotificationCommandService`/`NotificationCommandController`(520)로
나뉜다(cashflow의 Query/Command 분리와 같은 구조). 두 컨트롤러는 같은 기본 경로를 쓰며 매핑이 겹치지 않는다.

- 목록 정렬은 생성 시각 내림차순, 같으면 ID 내림차순. `page` 0 이상, `size` 1~100(기본 20).
- `fromDate`·`toDate`(yyyy-MM-dd)는 `created_at`의 날짜 기준으로 **양끝 포함**이다(`toDate` 다음 날 0시 미만). 시작일 > 종료일은 400.
- `isRead`는 true/false. `type`은 `NotificationType` 이름(대소문자 무관). 모르는 값은 400.
- 읽음 처리는 이미 읽은 알림이면 `readAt`을 바꾸지 않고 200을 돌려준다. 전체 읽음은 이번 요청으로 바뀐 건수(`updatedCount`)와 남은 미확인 수(0)를 준다.
- 삭제는 `deleted_at` 소프트 삭제다. 이후 목록·상세·미확인 수·전체 읽음 대상에서 빠지고 다시 조회하면 404다. 읽음 상태는 바꾸지 않는다.

### 응답 (`NotificationResponse`)

`notificationId, type, title, message, sourceType, sourceId, isRead, createdAt, readAt`. 미확인 수는 `{ unreadCount }`, 전체 읽음은 `{ updatedCount, unreadCount }`.

### 오류 코드

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | page < 0, size 1~100 밖, `type` 형식(영숫자·`_`, 50자) 위반, `notificationId` ≤ 0 |
| `INVALID_REQUEST` | 400 | 모르는 `type`, 시작일 > 종료일, `isRead`·날짜 형식 오류 |
| `RESOURCE_NOT_FOUND` | 404 | 본인 소유가 아니거나 없거나 삭제된 알림 |
| `UNAUTHORIZED` | 401 | 토큰 없음·만료 |

새 ErrorCode는 추가하지 않았다.

## 푸시 (580)

명세 22에는 없지만 FCM 발송에 필요해 추가한 API와 발송 경로다.

| 기능 | Method | Endpoint | 상태 코드 |
| --- | --- | --- | --- |
| 기기 등록(갱신) | POST | `/api/v1/notifications/devices` Body `{fcmToken, platform}` | 201 |
| 내 기기 목록 | GET | `/api/v1/notifications/devices` | 200 |
| 기기 해제 | DELETE | `/api/v1/notifications/devices/{deviceId}` | 200 (`data: null`), 남의 것·없는 것 404 |

- 앱은 로그인·토큰 갱신 시 등록하고 로그아웃 시 해제한다. 응답에 토큰 원문은 없다(`deviceId, platform, enabled, lastUsedAt, createdAt`).
- 같은 토큰 재등록은 같은 기기(사용 시각 갱신, 해제됐던 기기면 다시 켜짐, 플랫폼 변경 반영). **다른 사용자가 같은 토큰을 등록하면 기기 소유자가 바뀐다**
  (한 기기는 마지막에 로그인한 사용자의 푸시만 받는다).
- `platform`은 ANDROID·IOS·WEB(대소문자 무관), 그 외 400 `INVALID_REQUEST`. 토큰 공백·4096자 초과는 400 `VALIDATION_ERROR`.

### 발송 경로

`NotificationPublisher`가 **새** 알림을 만들면(dedupe로 기존 행을 돌려준 경우 제외) 트랜잭션 커밋 뒤 `NotificationPushDispatcher.dispatch`를 부른다.
디스패처는 사용자 설정을 확인하고(`notification_enabled=false`면 중단, `DAILY_BRIEFING`은 `daily_briefing_enabled`, `MONTHLY_REPORT`는 `monthly_report_enabled`도 필요)
활성 기기마다 토큰을 복호화해 `NotificationPushSender.send`를 부른다. 발송 실패는 알림 저장을 되돌리지 않는다(별도 트랜잭션, 로그만).

| 구현 | 조건 | 동작 |
| --- | --- | --- |
| `FcmNotificationPushSender` | `app.notification.push.enabled=true` + `service-account-path` 읽기 가능 (prod: `FCM_SERVICE_ACCOUNT_PATH=/run/secrets/fcm-service-account.json`) | Firebase Admin SDK. `notification`(제목·내용) + `data`(notificationId, type, sourceType, sourceId). UNREGISTERED·INVALID_ARGUMENT면 기기 `enabled=0` |
| `LoggingNotificationPushSender` | 그 외(로컬·테스트, 키 없음, 초기화 실패) | 보낼 내용을 로그로만 남긴다(토큰 원문 제외) |

FCM 초기화 실패는 서버 기동을 막지 않고 로깅 구현으로 넘어간다(`NotificationPushConfig`). 의존성 `com.google.firebase:firebase-admin:9.4.3`.

## 이벤트 연동 (581)

`NotificationEventService`가 다른 도메인의 **저장된 상태**를 읽어 알림을 만든다(엔티티·서비스 결합 없이 `JdbcNotificationEventReader`가 스키마를 직접 읽는다).
모든 생성은 퍼블리셔를 거치고 dedupe 키로 멱등이라 하루에 몇 번 돌아도 늘지 않는다.

| 규칙 | 유형 | 대상 (기본값) | 원천 | dedupe |
| --- | --- | --- | --- | --- |
| 결제 예정 | `PAYMENT_DUE` | ACTIVE 정기결제 `next_payment_date`, SCHEDULED 카드 청구 `payment_date`가 오늘~D+3(`due-days-ahead`) | SUBSCRIPTION / CARD | 원천 ID + 날짜 |
| 상환 예정 | `REPAYMENT_DUE` | ACTIVE 대출 `next_repayment_date`, 마이너스통장 `next_interest_date`(계좌 ACTIVE)가 오늘~D+3 | LOAN / MINUS_ACCOUNT | 원천 ID + 날짜 |
| 연체 위험 | `OVERDUE_RISK` | 상태 OVERDUE인 대출의 상환일이 오늘~D+3 | LOAN | 대출 ID + 날짜 |
| 잔액 부족 위험 | `CASHFLOW_RISK` | 사용자별 최신 COMPLETED 현금흐름 예측의 HIGH·CRITICAL 위험, 위험일 오늘~D+7(`risk-days-ahead`) 또는 미정 | CASHFLOW_FORECAST | 예측 ID + 위험 ID |
| 목표 진행 | `GOAL_PROGRESS` | 모으기 목표(ACTIVE) 진행률 25·50·75% 도달(각 1회) | GOAL | 목표 ID + 구간 |
| 목표 달성 | `GOAL_ACHIEVED` | 진행률 100% 또는 상태 ACHIEVED(구간 알림은 더 만들지 않음) | GOAL | 목표 ID |
| 추천 | `RECOMMENDATION` | 최근 7일(`recommendation-days-back`) 안 생성된 PENDING 개선안 | RECOMMENDATION | 개선안 ID |
| 월간 리포트 | `MONTHLY_REPORT` | 월간 리포트 설정 사용자, 스케줄은 매월 1~3일(직접 실행은 항상), 지난달 | REPORT / yyyy-MM | 사용자 + 월 |
| 데일리 브리핑 | `DAILY_BRIEFING` | 브리핑 설정 사용자, 설정 시각(`daily_briefing_time`, 기본 08:00, 사용자 `timezone`)의 정시. 내용: 오늘 사용 가능 금액((월 예산 − 이달 소비) / 남은 일수, 줄이기 목표가 있을 때) + 오늘 예정 고정 지출 | 없음 | 사용자 + 날짜 |

- 문구는 "N일 후(9월 16일) 17,000원이 결제될 예정입니다"처럼 상대일·날짜·금액을 함께 쓴다. 위험 알림은 `cashflow_risk.message`가 있으면 그대로 쓴다.
- 리포트·브리핑 대상 조회는 `notification_enabled=1`인 사용자만이다(설정이 꺼진 사용자는 만들지도 않는다). 다른 규칙은 알림함에 남기고 푸시만 설정으로 막는다.
- 리포트 본문(API 20)은 아직 없다. 알림은 `REPORT/yyyy-MM`을 가리키고 FE가 리포트 화면으로 라우팅한다.

### 스케줄과 직접 실행

- `NotificationEventScheduler`(`app.notification.events.enabled=true`, 테스트는 false): 매일 08:00(Asia/Seoul) `runAll`, 매 정시 브리핑. cron은
  `app.notification.events.daily-cron`·`briefing-cron`으로 바꿀 수 있다. 서버가 여러 대여도 dedupe로 중복 생성은 없다.
- `POST /api/v1/notifications/events/run`(명세 외): **본인** 대상으로 모든 규칙을 지금 실행하고 규칙별 생성 건수를 돌려준다(`{date, created:{paymentDue,...}, total}`).
  직접 실행은 리포트·브리핑의 날짜·시각 조건을 무시한다(시연·확인용). 멱등이라 여러 번 눌러도 늘지 않는다.

### 설정 (`app.notification`)

| 키 | 기본 | 의미 |
| --- | --- | --- |
| `push.enabled` | false (prod true) | FCM 발송 |
| `push.service-account-path` | `${FCM_SERVICE_ACCOUNT_PATH:}` | 서비스 계정 키 경로 |
| `events.enabled` | true (test false) | 스케줄 등록 |
| `events.due-days-ahead` | 3 | 결제·상환 예정 창 |
| `events.risk-days-ahead` | 7 | 현금흐름 위험 창 |
| `events.recommendation-days-back` | 7 | 추천 알림 대상 기간 |

## 다른 도메인에서 알림 만들기 (D10 계약)

```java
publisher.publish(new NotificationCommand(
        userId, NotificationType.PAYMENT_DUE,
        "넷플릭스 결제 예정", "9월 25일 17,000원이 결제됩니다.",
        NotificationSourceType.SUBSCRIPTION, subscriptionId,
        NotificationPublisher.dedupeKey("PAYMENT_DUE", "SUBSCRIPTION", subscriptionId, "2026-09-25")));
```

- `publish`는 `REQUIRES_NEW` 트랜잭션이다. 호출한 도메인 트랜잭션이 롤백돼도 알림은 남고, 알림 저장 실패가 호출 도메인을 깨지 않도록 호출 측이 예외를 처리한다.
- 제목 1~255자, 내용 비어 있지 않음, `sourceId` 100자 이하, `dedupeKey` 64자 이하. 위반은 `IllegalArgumentException`.
- 이벤트별 생성 시점(결제일 D-3, 상환일 D-3, 예측 위험 감지 시, 목표 진행률 25/50/75/100%, 리포트 생성 시 등)은 D10에서 정한다.

## 테스트

- H2 통합(`NotificationApiTest` 7종): 목록 정렬·필터(유형·읽음·날짜 양끝 포함)·페이지, 파라미터 검증, 상세·미확인 수, 읽음 멱등·전체 읽음 건수·타인 무영향,
  소프트 삭제 후 조회 제외·404, 인증·소유권, 퍼블리셔 dedupe(같은 키 재사용·다른 사용자 별개·삭제분 미복구·키 없음·입력 검증).
- H2 통합(`NotificationDeviceApiTest` 3종): 기기 등록·재등록·소유자 이전·해제·검증, 새 알림 푸시(기록용 발송기)·dedupe 시 미발송·토큰 무효 시 기기 해제·재등록 복구,
  사용자 설정(브리핑·전체 off)에 따른 푸시 억제와 알림함 유지.
- H2 통합(`NotificationEventServiceTest` 4종, 리더 mock): 결제·상환·연체·위험·추천 알림 문구·원천·dedupe, 목표 구간·달성, 월간 리포트(월초/직접)·브리핑(설정 시각·내용), 실행 API.
- MySQL(`NotificationMySqlIntegrationTest`, `NotificationEventReaderMySqlTest`): V32·V33 validate, 유일 제약, 벌크 읽음, seed(4,000명)에서 리더 SQL 8종 실행과 U0002 규칙 실행·멱등.
  결과는 remaining-work 519·581 절.

## BE-A 통합 테스트 (521)

2026-09-14, 브랜치 `S15P21D105-519-api-22-notification`(develop `45b2c65` + 519·520) 기준. `./gradlew test`에 MySQL 게이트 테스트(`PATTERN_MYSQL_TEST_URL`,
로컬 Docker MySQL 8.0 `dodam_516_test`, V1~V19·V30~V32 + seed 8종 + 알림 seed 적재 상태)를 함께 켜서 돌렸다.

| 결과 | 값 |
| --- | --- |
| 전체 | 950개 실행, 실패 0, 건너뜀 10(BE-B MySQL 게이트 테스트 4종 등 환경 미설정분) |
| MySQL 게이트(BE-A) | `PatternMySqlIntegrationTest` 1, `NotificationMySqlIntegrationTest` 1 실행·통과 |

BE-A 담당 도메인별(H2 통합 + 단위):

| 도메인 | API | 테스트 수 | 결과 |
| --- | --- | --- | --- |
| auth | 1 Auth | 19 | 통과 |
| user | 2 User | 10 | 통과 |
| finance | 4 Finance Data | 36 | 통과 |
| account | 5 Account | 53 | 통과 |
| transaction | 6 Transaction/Spending | 76 | 통과 |
| goal | 12 Goal | 37 | 통과 |
| subscription | 7 Subscription | 16 | 통과 |
| pattern | 3 Preference | 28 | 통과 (아래 수정 1건 후) |
| notification | 22 Notification | 8 | 통과 |

- 첫 실행에서 `PatternMySqlIntegrationTest`가 `populationSize == 41` 단정으로 실패했다. 같은 스키마에 seed 4,000명이 있으면 모집단이 4,041이 되기 때문이다.
  테스트가 DB에 자기 fixture만 있다고 가정한 문제라 하한(≥ 41) 단정으로 고쳤고 재실행에서 통과했다. 제품 코드 변경은 없다.
- 서버(시연 DB)에서의 실제 로그인 토큰 기반 스모크는 seed 사용자에 비밀번호가 없어 2026-09-14에는 못 했다. 2026-09-15에 시연 계정 비밀번호 seed
  (`database/seeds/DODAM_DEMO_PASSWORD_SEED.sql`)와 스모크 스크립트(`scripts/be-a-server-smoke.sh`, 로그인 → 계좌·거래·소비·목표·정기결제·패턴·알림)를
  준비했다. 서버 DB에 seed를 적용한 뒤 실행 결과를 아래에 기록한다(remaining-work 521 절).

### 서버 스모크 (2026-09-15)

시연 서버 `https://j15D105.p.ssafy.io/api`, 담당자가 `DODAM_DEMO_PASSWORD_SEED.sql`을 서버 DB에 적용(검증 SELECT `demo_users_with_password 4 / other_users_with_password 0`)한 뒤
`DEMO_PASSWORD=... bash backend/scripts/be-a-server-smoke.sh --analyze`를 U0001로 실행했다(13:12 KST). 15개 호출 전부 기대 상태 코드.

| 도메인 | 호출 | 상태 | 응답 확인 |
| --- | --- | --- | --- |
| Auth | `POST /v1/auth/login` | 200 | Bearer accessToken·refreshToken 발급 |
| Account | `GET /v1/accounts`, `GET /v1/accounts/summary` | 200, 200 | 계좌 2개(KB국민 주거래 마스킹 `110000-**-**1301`), 요구불 잔액 19,160,000 |
| Transaction | `GET /v1/transactions?page=0&size=5` | 200 | seed 거래 `SEED-U0001-202608-VAR-06` 등 |
| Spending | `GET /v1/spending/monthly-summary?yearMonth=2026-08` | 200 | 총 소비 1,585,800원, 20건 |
| Goal | `GET /v1/goals`, `GET /v1/goals/summary` | 200, 200 | 모으기 목표 1개(여행 자금) ACTIVE |
| Subscription | `GET /v1/subscriptions`, `GET /v1/subscriptions/summary` | 200, 200 | 구독 1개(도담 비디오), 월 12,400원 |
| Pattern | `POST /v1/patterns/analyze`, `GET /v1/patterns/me` | 201, 200 | analysisId 1 COMPLETED, HYBRID, 6개월(서버 DB 첫 분석 이력) |
| Notification | `GET /v1/notifications`, `GET /v1/notifications/unread-count` | 200, 200 | 최신 알림은 이벤트 스케줄이 만든 `GOAL_ACHIEVED`(id 2139), 미확인 3 |
| Auth | `GET /v1/auth/session`, `POST /v1/auth/logout` | 200, 200 | userId 1, 세션 폐기 |

- U0002~U0004도 로그인 200, `GET /v1/accounts/summary` 200, 미확인 알림 5·4·5로 확인했다.
- (2026-09-20) 위 표의 구독 값은 8번 seed 기준이다. 15번 `DODAM_DEMO_SUBSCRIPTION_SEED.sql`을 적용하면 U0001은 구독 5개·월 54,590원이 되고, 9번 알림 seed를 다시 돌려야
  `PAYMENT_DUE` 문구가 `도담 비디오`가 아닌 새 구독 이름으로 바뀐다(`backend/database/seeds/README.md` "시연 계정 구독 목록").
- 패턴 분석은 `--analyze`로 서버 DB에 U0001의 분석 이력 1건을 만들었다(시연 홈에서 패턴 조회가 404가 되지 않도록 의도한 쓰기). U0002~U0004는 아직 분석 이력이 없어 시연 전 같은 방식으로 실행하거나 앱에서 분석을 호출해야 한다.
- 알림함에 seed 외 알림(`GOAL_ACHIEVED` 2139)이 있는 것은 D10 이벤트 스케줄(08:00)이 서버에서 실제로 돌았다는 뜻이다.
