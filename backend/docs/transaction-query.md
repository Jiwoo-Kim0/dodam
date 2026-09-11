# Transaction API — 거래 조회·메모·카테고리·숨김

JIRA `S15P21D105-453` (D4 Account API 구현)의 하위 티켓 `S15P21D105-504`(API 6 거래 조회·메모·카테고리·숨김) 구현 기준이다.
API 명세 6 Transaction / Spending 중 거래 원장 조회와 사용자 편집 3종을 다룬다. 수동 거래 등록·수정·삭제와
`/api/v1/spending/**` 집계는 다음 티켓 범위이며 이 문서에 포함하지 않는다.

## API

모든 API는 인증이 필요하고 본인 거래만 다룬다. 타인 거래·없는 거래는 구분하지 않고 404다.

| 기능 | Method | Endpoint | 상태 코드 |
| --- | --- | --- | --- |
| 전체 거래 조회 | GET | `/api/v1/transactions` | 200 |
| 소비 카테고리 목록 (명세 외 추가) | GET | `/api/v1/transactions/categories` | 200 |
| 거래 상세 | GET | `/api/v1/transactions/{transactionId}` | 200 |
| 소비 카테고리 수정 | PATCH | `/api/v1/transactions/{transactionId}/category` | 200 |
| 거래 메모 수정 | PATCH | `/api/v1/transactions/{transactionId}/memo` | 200 |
| 거래내역 숨김 상태 변경 | PATCH | `/api/v1/transactions/{transactionId}/visibility` | 200 |

### 오류 코드

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | page < 0, size가 1~100 밖, 거래 ID·코드 형식 위반, Body 필수값(`categoryId`, `hidden`) 누락 |
| `INVALID_REQUEST` | 400 | 시작일 > 종료일, 종료일이 오늘 이후, 조회 기간 12개월 초과, 없는 카테고리 코드/ID, 비활성 카테고리, 메모 500자 초과·금칙문자 |
| `RESOURCE_NOT_FOUND` | 404 | 본인 소유가 아니거나 존재하지 않는 거래 |
| `UNAUTHORIZED` | 401 | 토큰 없음·만료 |

## 전체 거래 조회

`GET /api/v1/transactions?startDate&endDate&category&paymentMethod&hidden&page&size`

| 파라미터 | 기본값 | 규칙 |
| --- | --- | --- |
| `startDate`, `endDate` | 종료일 = 오늘, 시작일 = 종료일 − 3개월 + 1일 | `QueryPeriodPolicy`(카드·마이너스통장 거래내역과 동일). 최대 12개월, 종료일은 오늘까지 |
| `category` | 전체 | `transaction_category.code`. 대소문자 무시. 없는 코드는 400 |
| `paymentMethod` | 전체 | `transaction.payment_method`와 대문자 기준 정확히 일치 (`CARD`, `ACCOUNT` 등) |
| `hidden` | 생략 → 숨기지 않은 거래만 | `true`면 숨긴 거래만, `false`면 숨기지 않은 거래만. **명세에 없는 추가 파라미터**로 숨김 해제 화면용 |
| `page`, `size` | 0, 20 | page ≥ 0, size 1~100 (도담 정책) |

- 정렬은 거래일 → 거래시각 → 거래 ID 내림차순으로 고정한다. 시각이 없는 거래(수동 등록 등)는 같은 날짜 안에서 뒤로 간다.
- 응답은 공통 `PageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`, `last`)다.
- 원장은 우리 DB `transaction`만 읽는다. 명세의 "SSAFY 데이터 통합"은 금융망 거래를 원장에 적재하는 Finance Data 동기화 ACCOUNT scope(S15P21D105-501)가 맡고, 이 API는 그 결과를 조회한다.

### 거래 응답 필드

목록·상세·수정 응답이 모두 같은 `TransactionResponse`다. 카드(8)·마이너스통장(10-1) 거래내역과 공통 필드 이름을 맞췼다.

| 필드 | 설명 |
| --- | --- |
| `transactionId` | 거래 ID. 금융망 `transactionUniqueNo`, seed(`TX-...`), 수동 등록 ID |
| `accountId`, `cardId` | 출처 계좌·카드 ID. 번호·이름은 계좌(5)·카드(8) API에서 조회 |
| `categoryId`, `categoryCode`, `categoryName` | 소비 카테고리. 미분류면 모두 null |
| `transactionDate`, `transactionTime` | 거래일(필수), 거래시각(없을 수 있음) |
| `transactionType` | `INCOME` / `EXPENSE` / `TRANSFER` |
| `amount` | 항상 양수. 방향은 `transactionType` |
| `balanceAfter` | 거래 후 잔액. 카드·수동 거래는 null |
| `merchantName`, `paymentMethod`, `summary`, `memo` | 저장값 그대로 |
| `spendingType` | `FIXED` / `VARIABLE` / `NON_SPENDING` / null. 고정·변동지출 분류 기준 |
| `hidden` | 숨김 여부 |
| `sourceType` | `SSAFY` / `MANUAL` / `SEED`. 수동 거래 수정·삭제 가능 여부의 기준 |
| `createdAt`, `updatedAt` | 적재 시각, 마지막 사용자 편집 시각(편집 전이면 null) |

## 소비 카테고리 목록

`GET /api/v1/transactions/categories` → `[{ categoryId, code, name, defaultSpendingType }]`

명세 6에는 없지만 카테고리 수정 Body의 `categoryId`와 목록 필터 `category`(코드)의 선택지를 FE가 알아야 해서 추가했다.
`active = 1`인 행만 ID 순으로 준다. 기준 행은 V6 마이그레이션이 넣는다(FOOD, CAFE, GROCERY, PUBLIC_TRANSPORT, TAXI,
SHOPPING, BEAUTY, CULTURE, TRAVEL, MEDICAL, EDUCATION, HOUSING, UTILITIES, INSURANCE, SUBSCRIPTION, LOAN_REPAYMENT,
SAVING, TRANSFER, INCOME, ETC).

## 편집 3종의 공통 규칙

- 거래는 `transaction_id + user_id`로 찾는다. 타인 거래는 없는 것과 같게 404다.
- **중복 요청 방지**: 요청 값이 현재 값과 같으면 아무것도 갱신하지 않고(`updated_at` 유지) 현재 상태를 200으로 돌려준다.
  PATCH를 재시도해도 결과가 같도록 한 것이며, 409로 막지 않는다.
- 값이 바뀌면 `updated_at`을 서비스 시각(`Asia/Seoul`)으로 찍는다. `TransactionClock`으로 주입해 테스트에서 고정할 수 있다.
- 수정은 우리 DB에만 반영한다. 아래 "남은 작업" 참고.

### 소비 카테고리 수정

`PATCH /transactions/{id}/category` Body `{ "categoryId": 12 }`

- `categoryId`는 필수, `active = 1`인 카테고리만 허용. 없는 ID·비활성은 400 `INVALID_REQUEST`("존재하지 않는 카테고리입니다.").
- 카테고리에 `default_spending_type`이 있으면 거래의 `spending_type`도 그 값으로 바뀐다(예: HOUSING → FIXED).
  기본 소비유형이 없는 카테고리면 기존 `spending_type`을 유지한다.
- 거래 유형(INCOME/EXPENSE/TRANSFER)에 따른 제한은 두지 않았다. 수입 거래에 INCOME, 이체에 TRANSFER를 붙이는 용도도 같은 API다.

### 거래 메모 수정

`PATCH /transactions/{id}/memo` Body `{ "memo": "팀 회식" }`

- `memo`는 선택이다. null·빈 문자열·공백만이면 메모를 지운다(null 저장). Body 자체는 있어야 한다(`{}` 허용).
- 앞뒤 공백을 제거한 뒤 500자(ERD `VARCHAR(500)`)를 넘으면 400.
- 줄바꿈(`\n`, `\r`)·탭을 제외한 제어문자(NUL, ESC 등)와 유니코드 포맷 문자(제로폭 공백, BOM)는 400. 이모지·기호는 허용.
  규칙은 `TransactionMemoPolicy`에 있다.

### 거래내역 숨김 상태 변경

`PATCH /transactions/{id}/visibility` Body `{ "hidden": true }`

- `hidden`은 필수(boolean). 누락은 400 `VALIDATION_ERROR`.
- 숨긴 거래는 목록 기본 조회에서 빠지고, 상세 조회와 `hidden=true` 목록에서는 보인다. 카드(8)·마이너스통장(10-1) 거래내역·집계도
  `hidden = FALSE`만 읽으므로 숨김은 원장 전체에 걸쳐 일관된다.

## 스키마 (V6)

`transaction_category`, `transaction`을 ERD 정의 그대로 만들고 카테고리 기준 행 20개를 넣는다.
두 테이블은 카드·마이너스통장·상품 추천 도메인이 이미 JDBC로 읽고 있었으나 마이그레이션이 없어 MySQL에서는 조회가 실패하던
상태였다. BE-A 구간(V1~V9)의 V6으로 그 공백을 메운다(V5는 543의 `goal`이 먼저 썼다). 기존 DB에는 V17 이후에 적용되므로 `out-of-order`가 켜져 있어야 한다.

`Transaction` 엔티티는 `account`·`card`·`transaction_category`를 ID 컬럼으로만 들고 연관관계를 걸지 않는다. 다른 티켓이
소유한 테이블을 여러 엔티티가 매핑하면 H2 테스트 스키마가 매핑 순서에 따라 갈리는 문제가 이미 보고돼 있기 때문이다.
복합 외래키는 DDL이 보장한다.

## 테스트

- `TransactionApiIntegrationTest`: 회원가입·로그인으로 실제 토큰을 발급받아 6개 API를 호출한다. fixture
  `sql/transaction-test-data.sql`이 V6 컬럼 전체로 두 테이블을 다시 만든다(다른 도메인 fixture는 `created_at` 등이 빠진 축소
  스키마를 만들기 때문).
- `TransactionMemoPolicyTest`: 메모 정규화·길이·금칙문자 규칙.

## 남은 작업

[remaining-work.md](remaining-work.md)의 "S15P21D105-504" 절에 기록한다. 요약하면 금융망 메모 API 반영, MySQL 실검증,
거래 seed 데이터, `transactionType` 필터 추가 여부다.
