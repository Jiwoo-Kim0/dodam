# Notification API — 알림 목록·상세·미확인 수·읽음·전체 읽음·삭제

D9 "22 Notification 기본 API 및 BE-A 통합"(`S15P21D105-458`)의 하위 티켓 `S15P21D105-519`(API 22 알림 목록·상세·미확인 수),
`S15P21D105-520`(API 22 알림 읽음·전체읽음·삭제) 구현 기준이다. API 명세 22 Notification 6개를 모두 다룬다.
`S15P21D105-521`(BE-A 담당 도메인 통합 테스트) 결과는 아래 "BE-A 통합 테스트"에 있다.

## 모델

| 테이블 | 엔티티 | 내용 |
| --- | --- | --- |
| `notification` (V32) | `Notification` | 알림 1건. 유형, 제목, 내용(TEXT), 원천(`source_type`·`source_id`), 중복 방지 키, 읽음(`is_read`·`read_at`), 소프트 삭제(`deleted_at`) |

- ERD 정의 그대로다. `(user_id, dedupe_key)` 유일 제약이 같은 이벤트의 알림 중복 생성을 막고, `(user_id, is_read, type, created_at)` 인덱스가 목록 필터를 받친다.
- **생성 API는 없다.** 명세 22는 조회·읽음·삭제만 정한다. 알림은 각 도메인이 {@code NotificationPublisher}로 만든다(D10 이벤트 연동). 시연 데이터는
  `database/seeds/DODAM_NOTIFICATION_SEED.sql`(시연 사용자 U0001~U0004)이 적재한다.
- `type`은 VARCHAR(50) 문자열로 저장한다. `NotificationType` enum은 생성 입력과 목록 필터 검증에 쓰고, 다른 도메인이 SQL로 넣은 미지 값도 읽을 수 있다.
- `user_device`(FCM 토큰)와 푸시 발송은 D10에서 별도 마이그레이션으로 다룬다. `user_setting.notification_enabled`는 푸시 발송에서 확인하며
  앱 안의 알림함에는 항상 남긴다.
- 마이그레이션 번호는 **V32**(BE-A 2차 구간 V30~V49, `db/migration/README.md`).

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
- MySQL은 remaining-work 519 절 참고.

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
- 서버(시연 DB)에서의 실제 로그인 토큰 기반 스모크는 seed 사용자에 비밀번호가 없어 아직 못 했다(remaining-work 521 절).
