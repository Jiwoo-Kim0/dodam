# Subscription API — 정기결제 목록·상세·이력·요약·후보 탐지·서비스·카테고리·수정·정지/재개·취소

D7 "7 Subscription"의 하위 티켓 `S15P21D105-512`(API 7 정기결제 목록·상세·요약), `S15P21D105-513`(API 7 반복결제 후보·서비스),
`S15P21D105-514`(API 7 정기결제 변경·정지·취소) 구현 기준이다. API 명세 7 Subscription 10개를 모두 다룬다.

## 모델

| 테이블 | 엔티티 | 내용 |
| --- | --- | --- |
| `subscription` (V30) | `Subscription` | 정기결제 한 건. PK는 문자열 `subscription_id`(금융망 식별자 또는 seed `SUB-U0001-01`). 결제 카드(`card_id`, 같은 사용자의 카드 복합 외래키), 금액, 주기(WEEKLY/MONTHLY/YEARLY), 결제일, 다음 결제일, 종료일, 상태, 카테고리 |
| `subscription_payment_history` (V30) | `SubscriptionPaymentHistory` | 결제 시도 기록. (`subscription_id`, `payment_date`, `payment_amount`) 유일. `result_code`가 null이거나 `SUCCESS`면 성공 |

- **등록 API는 없다.** 명세 7에 POST가 없고 데이터는 금융망 동기화(미구현)와 시연 seed(`database/seeds/DODAM_SUBSCRIPTION_SEED.sql`)가 적재한다.
  이 API는 조회·수정·정지/재개·취소만 한다.
- `category`는 VARCHAR(50) 문자열로 저장한다. 금융망·seed가 넣는 값이 우리 enum에 없을 수 있어서다. `SubscriptionCategory` enum(VIDEO, MUSIC, BOOK,
  CLOUD, AI, SHOPPING, DELIVERY, GAME, EDUCATION, FITNESS, TELECOM, NEWS, ETC)은 카테고리 목록·서비스 카탈로그·필터 정규화·표시 이름에 쓴다.
- 마이그레이션 번호는 **V30**이다. BE-A 구간(V1~V9)이 소진돼 V18~V29를 BE-B·Infra 예비로 남기고 V30부터 BE-A 2차 구간으로 잡았다
  (`db/migration/README.md` "버전 번호 할당").

### 상태 전이

```
ACTIVE ──▶ PAUSED ──▶ ACTIVE
  │           │
  └──▶ CANCELED (종료) ◀──┘
EXPIRED (종료) : 종료일이 지난 정기결제. 사용자가 지정할 수 없다(동기화·배치가 넣는 값)
```

CANCELED·EXPIRED는 종료 상태다. 어디로도 옮길 수 없고 다른 필드도 바꿀 수 없다(409 `SUBSCRIPTION_NOT_EDITABLE`).
허용되지 않은 전이는 409 `SUBSCRIPTION_STATUS_TRANSITION_NOT_ALLOWED`. PAUSED가 되면 `next_payment_date`를 비우고(seed 규약과 같음),
ACTIVE로 돌아오면 아래 "다음 결제일 재계산" 규칙으로 다시 채운다.

## API

모든 API는 인증이 필요하고 본인 정기결제만 다룬다. 타인 것·없는 것은 구분하지 않고 404다.

| 기능 | Method | Endpoint | 상태 코드 | 티켓 |
| --- | --- | --- | --- | --- |
| 정기결제 목록 | GET | `/api/v1/subscriptions?status&category&page&size` | 200 | 512 |
| 정기결제 요약 | GET | `/api/v1/subscriptions/summary` | 200 | 512 |
| 정기결제 상세 (+집계) | GET | `/api/v1/subscriptions/{subscriptionId}` | 200 | 512 |
| 정기결제 이력 | GET | `/api/v1/subscriptions/{subscriptionId}/history?startDate&endDate&page&size` | 200 | 512 |
| 미등록 반복결제 탐지 | GET | `/api/v1/subscriptions/candidates?minOccurrences&lookbackMonths` | 200 | 513 |
| 정기결제 서비스 목록 | GET | `/api/v1/subscriptions/services?category&page&size` | 200 | 513 |
| 정기결제 카테고리 | GET | `/api/v1/subscriptions/categories` | 200 | 513 |
| 정기결제 수정 | PATCH | `/api/v1/subscriptions/{subscriptionId}` | 200 | 514 |
| 정지/재개 | PATCH | `/api/v1/subscriptions/{subscriptionId}/status` | 200 | 514 |
| 정기결제 취소 | DELETE | `/api/v1/subscriptions/{subscriptionId}` | 200 (`data: null`) | 514 |

### 오류 코드

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | page < 0, size가 1~100 밖, `subscriptionId` 형식(영숫자·`-`·`_`·`:`·`.`, 100자) 위반, `minOccurrences` 2~12·`lookbackMonths` 1~12 밖, Body `paymentAmount` ≤ 0, `paymentDay` 1~31 밖 |
| `INVALID_REQUEST` | 400 | 알 수 없는 상태 필터·상태 값, 시작일 > 종료일, 조회 기간 12개월 초과, 종료일이 오늘 이전, 종료일이 다음 결제일보다 앞, `status: EXPIRED` 지정, 정지/재개 Body에 `status`·`action`이 둘 다 없음·서로 다름·`action`이 pause/resume 외 |
| `NO_FIELDS_TO_UPDATE` | 400 | 수정 Body에 바꿀 필드가 하나도 없음 |
| `RESOURCE_NOT_FOUND` | 404 | 본인 소유가 아니거나 없는 정기결제 |
| `SUBSCRIPTION_NOT_EDITABLE` | 409 | 종료 상태(CANCELED·EXPIRED) 수정·정지·재개·취소 |
| `SUBSCRIPTION_STATUS_TRANSITION_NOT_ALLOWED` | 409 | 허용되지 않은 상태 전이 |
| `UNAUTHORIZED` | 401 | 토큰 없음·만료 |

## 512 목록·상세·이력·요약

### 목록 `GET /api/v1/subscriptions`

| 파라미터 | 기본값 | 규칙 |
| --- | --- | --- |
| `status` | 전체 | `ACTIVE` / `PAUSED` / `EXPIRED` / `CANCELED`, 대소문자 무시. 다른 값은 400 |
| `category` | 전체 | 저장된 `category`와 대문자 기준 일치. 없는 코드는 빈 목록 |
| `page`, `size` | 0, 20 | page ≥ 0, size 1~100 (도담 정책) |

- 정렬은 상태(ACTIVE → PAUSED → EXPIRED → CANCELED) → 다음 결제일 오름차순(없으면 뒤) → 이름 → ID로 고정한다.
- 사용자당 정기결제는 수십 건을 넘지 않는다는 전제로 필터·정렬·페이지는 메모리에서 한다(Goal 목록과 같은 방식).
- 응답은 공통 `PageResponse`. 카드 이름·마스킹 번호는 카드 도메인에서 한 번에 읽어 붙인다(`SubscriptionResponseAssembler`).

### 정기결제 응답 필드 (`SubscriptionResponse`)

목록·상세(`subscription`)·수정·정지/재개 응답이 같다.

| 필드 | 설명 |
| --- | --- |
| `subscriptionId`, `name` | 식별자, 서비스 이름 |
| `category`, `categoryLabel` | 저장 코드와 표시 이름(정의되지 않은 코드는 코드 그대로) |
| `paymentAmount`, `billingCycle`, `monthlyEquivalentAmount` | 결제 금액, 주기, 월 환산 금액(WEEKLY ×52÷12, YEARLY ÷12, 원 단위 반올림) |
| `paymentDay`, `nextPaymentDate`, `daysUntilNextPayment` | 결제일, 다음 결제일, 오늘부터 남은 일수(다음 결제일이 없으면 null) |
| `endDate`, `status` | 종료일, 상태 |
| `cardId`, `cardName`, `cardNoMasked` | 결제 카드. 카드가 없으면 모두 null |
| `createdAt`, `updatedAt`, `canceledAt` | 적재 시각, 마지막 변경 시각(변경 전이면 null), 취소 시각 |

### 상세 `GET /api/v1/subscriptions/{subscriptionId}`

`{ subscription: SubscriptionResponse, payments: PaymentStats }`. 명세의 "SSAFY + 집계"에서 집계는 결제 이력 전체를 대상으로 한다.

| `payments` 필드 | 설명 |
| --- | --- |
| `paymentCount`, `successCount`, `failedCount` | 전체·성공·실패 시도 수 |
| `totalPaidAmount`, `averagePaidAmount` | 성공 결제 금액 합, 성공 1건 평균(성공이 없으면 null) |
| `firstPaymentDate`, `lastPaymentDate`, `lastResultCode` | 첫·마지막 시도일, 가장 최근 시도의 결과 코드 |

### 이력 `GET /api/v1/subscriptions/{subscriptionId}/history`

기간 정책은 거래내역과 같은 `QueryPeriodPolicy`(종료일 기본 오늘, 시작일 기본 종료일 − 3개월 + 1일, 최대 12개월). 결제일 → ID 내림차순.
행: `paymentHistoryId`, `paymentDate`, `paymentAmount`, `resultCode`, `success`, `retryCount`, `resultMessage`.

### 요약 `GET /api/v1/subscriptions/summary`

| 필드 | 설명 |
| --- | --- |
| `totalCount`, `activeCount`, `pausedCount`, `expiredCount`, `canceledCount` | 상태별 건수(전체) |
| `monthlyTotalAmount`, `yearlyTotalAmount` | **ACTIVE만** 월 환산 금액 합, 그 ×12 |
| `nextPaymentDate` | ACTIVE 중 오늘 이후 가장 가까운 다음 결제일 |
| `upcomingWithin7Days`, `upcomingWithin30Days` | 오늘 포함 7일/30일 안 결제 예정 `{ count, amount }` (실제 결제 금액 기준) |
| `byCategory` | ACTIVE의 카테고리별 `{ category, categoryLabel, count, monthlyAmount }`, 월 금액 내림차순. 카테고리 없음은 `ETC` |

## 513 반복결제 후보·서비스·카테고리

### 미등록 반복결제 탐지 `GET /api/v1/subscriptions/candidates`

거래 원장(`transaction`)에서 정기결제로 등록되지 않은 반복 결제를 찾는다. 명세의 "자체 탐지".

| 파라미터 | 기본값 | 규칙 |
| --- | --- | --- |
| `minOccurrences` | 3 | 2~12. 후보로 볼 최소 결제 횟수(서로 다른 결제일 기준) |
| `lookbackMonths` | 6 | 1~12. 오늘 기준 거슬러 볼 개월 수 |

규칙(`SubscriptionDiscoveryService`):

1. 기간 안의 EXPENSE·숨김 아님·가맹점명 있는 거래를 읽는다(`JdbcRecurringPaymentReader`). INCOME·TRANSFER·숨긴 거래와 `spending_type = NON_SPENDING`(저축 이체 등)은 보지 않는다.
2. 가맹점명(공백·대소문자 무시)과 금액이 같은 거래를 한 묶음으로 본다. 금액이 다르면 다른 묶음이다(요금제 변경은 별도 후보로 보인다).
3. 서로 다른 결제일이 `minOccurrences` 이상이고, 연속 결제 사이 **평균 간격**이 주 5~9일, 월 25~36일, 연 350~380일 중 하나에 들면 후보다.
   그 밖(불규칙)이면 뺀다.
4. 이미 등록된 정기결제(CANCELED 제외)와 이름이 같으면 뺀다. 취소한 뒤에도 결제가 계속되면 다시 후보로 보인다.

응답(`RecurringPaymentCandidateResponse`, 결제 횟수 → 금액 → 가맹점명 순):

| 필드 | 설명 |
| --- | --- |
| `merchantName`, `amount`, `occurrences` | 가맹점명(원문), 금액, 결제 횟수 |
| `firstPaymentDate`, `lastPaymentDate`, `averageIntervalDays` | 첫·마지막 결제일, 평균 간격(소수 1자리) |
| `estimatedBillingCycle`, `suggestedPaymentDay`, `expectedNextPaymentDate` | 추정 주기, 가장 자주 결제된 일(WEEKLY는 null), 마지막 결제일 + 주기를 오늘 이후로 맞춘 날 |
| `cardId`, `categoryCode` | 가장 많이 쓰인 카드(계좌 결제만이면 null), 가장 많이 붙은 거래 카테고리 코드 |
| `suggestedCategory`, `matchedServiceCode` | 가맹점명이 서비스 카탈로그와 맞으면 그 카테고리·서비스 코드 |
| `transactionIds` | 묶음의 거래 ID(최신순). 거래 상세(6)로 이동할 때 쓴다 |

### 서비스 목록 `GET /api/v1/subscriptions/services`

`SubscriptionServiceCatalog`에 코드로 고정한 정적 목록(30개, 2026-09-11)이다. 금융망에 카탈로그가 없고 ERD에도 테이블이 없어 정적으로 시작했다.
`category`(대소문자 무시)로 걸러 `PageResponse`로 준다. 행: `serviceCode`, `name`, `category`, `categoryLabel`, `typicalMonthlyAmount`(대표 요금제 월 금액,
안내용), `iconKey`(FE `assets/icons/payments` 아이콘 이름, 없으면 null). 후보 탐지의 `matchedServiceCode`가 이 코드다.

### 카테고리 `GET /api/v1/subscriptions/categories`

`SubscriptionCategory` 전체를 선언 순으로 `[{ code, label, subscriptionCount }]`. `subscriptionCount`는 본인 정기결제 중 CANCELED가 아닌 것의 수다
(필터 칩 표시용). 저장된 카테고리가 enum에 없으면 `ETC`로 센다.

## 514 수정·정지/재개·취소

### 수정 `PATCH /api/v1/subscriptions/{subscriptionId}`

Body `{ paymentAmount?, paymentDay?, endDate?, status? }`. null(생략)은 "변경 없음"이다.

- 모든 필드가 비어 있으면 400 `NO_FIELDS_TO_UPDATE`. 종료 상태 정기결제는 어떤 필드도 바꿀 수 없다(409).
- `endDate`는 오늘 이후여야 하고, 다음 결제일이 있으면 그 날보다 앞설 수 없다(DB `chk_subscription_dates`와 같은 규칙, 400).
- `status`는 ACTIVE / PAUSED / CANCELED만. EXPIRED 지정은 400. 전이 규칙은 위 상태 전이와 같다.
- `paymentDay`가 바뀐 ACTIVE 정기결제는 다음 결제일을 새 결제일 기준으로 다시 잡는다.
- 값이 전부 같으면 `updated_at`을 건드리지 않고 현재 상태를 200으로 돌려준다(중복 요청 방지).
- 금융망에 변경을 전달하는 경로는 없다. 명세의 "SSAFY"는 조회 데이터의 출처이고 금융망에는 정기결제 변경 API가 없다. 우리 DB만 바뀐다.

### 정지/재개 `PATCH /api/v1/subscriptions/{subscriptionId}/status`

Body는 명세대로 `status`(ACTIVE·PAUSED) 또는 `action`(pause·resume, 대소문자 무시) 중 하나. 둘 다 없거나 서로 다르면 400.
취소는 이 API로 하지 않는다(`status: CANCELED`·`action: cancel`은 400, DELETE 사용). 이미 목표 상태면 아무것도 바꾸지 않고 200이다.

### 취소 `DELETE /api/v1/subscriptions/{subscriptionId}`

상태를 CANCELED로 바꾸고 `canceled_at`을 남긴다. 행은 지우지 않아 목록·상세·이력에 계속 보이고, 후보 탐지에서는 "취소했는데 결제가 계속되는" 경우를
잡을 수 있다. 이미 종료 상태면 409.

### 다음 결제일 재계산 (`NextPaymentDateCalculator`)

재개·결제일 변경 시 오늘 이후(오늘 포함) 가장 가까운 결제 예정일을 잡는다.

| 주기 | 규칙 |
| --- | --- |
| MONTHLY | `payment_day`(없으면 기존 다음 결제일 또는 생성일의 일자)를 이번 달에 맞추고, 오늘보다 앞이면 다음 달. 말일은 그 달 길이로 보정 |
| WEEKLY | 기존 다음 결제일(없으면 생성일)에 7일씩 더해 오늘 이후로 맞춘다 |
| YEARLY | 기존 다음 결제일(없으면 생성일)의 월·일을 유지하며 1년씩 더한다 |

## 테스트

- `SubscriptionApiIntegrationTest`(512), `SubscriptionDiscoveryApiIntegrationTest`(513), `SubscriptionManagementApiIntegrationTest`(514):
  H2(MODE=MySQL)에서 실제 회원가입·로그인 토큰으로 호출한다. 정기결제·카드·거래 행은 `SubscriptionApiTestSupport`가 JdbcTemplate로 넣고,
  날짜는 모두 오늘 기준 상대값이다. fixture는 `src/test/resources/sql/subscription-test-data.sql`(V6·V11·V30 컬럼 전체, 외래키 없음).
- MySQL 8.0 검증(2026-09-11): V30 적용 후 `ddl-auto: validate` 통과, 시연 seed 구독 6,000·이력 36,000 적재, seed 사용자로 목록·요약·상세·이력·후보·정지·재개·수정·취소 호출 확인. 상세는 `remaining-work.md` 512·514 절.
