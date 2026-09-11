# Account API — 계좌 목록·상세·잔액·거래내역·집계·휴면 탐지

JIRA `S15P21D105-453` (D4 Account API 구현)의 하위 티켓 `S15P21D105-501`(API 5 계좌 목록·상세·잔액),
`S15P21D105-502`(API 5 계좌 거래내역 Adapter), `S15P21D105-503`(API 5 계좌 집계·휴면 탐지) 구현 기준이다.
API 명세 5 Account의 10개 엔드포인트를 모두 다룬다.

## API

모든 API는 인증이 필요하고 본인 계좌만 다룬다. 타인 계좌·없는 계좌는 구분하지 않고 404다.

| 티켓 | 기능 | Method | Endpoint | 상태 코드 |
| --- | --- | --- | --- | --- |
| 501 | 계좌 전체 조회 | GET | `/api/v1/accounts?bankCode&accountType` | 200 |
| 503 | 계좌 요약 조회 | GET | `/api/v1/accounts/summary` | 200 |
| 503 | 현금성 자산 합계 | GET | `/api/v1/accounts/balance-summary` | 200 |
| 503 | 휴면성 계좌 | GET | `/api/v1/accounts/inactive?inactiveMonths&maxBalance` | 200 |
| 501 | 계좌 상세 조회 | GET | `/api/v1/accounts/{accountNo}` | 200 |
| 501 | 계좌 잔액 조회 | GET | `/api/v1/accounts/{accountNo}/balance` | 200 |
| 502 | 계좌 거래내역 조회 | GET | `/api/v1/accounts/{accountNo}/transactions?startDate&endDate&page&size` | 200 |
| 502 | 거래 단건 조회 | GET | `/api/v1/accounts/{accountNo}/transactions/{transactionId}` | 200 |
| 503 | 월별 입출금 요약 | GET | `/api/v1/accounts/{accountNo}/monthly-summary?yearMonth` | 200 |
| 503 | 입출금 추이 | GET | `/api/v1/accounts/{accountNo}/trend?startMonth&endMonth` | 200 |

`/summary`, `/balance-summary`, `/inactive`는 `/{accountNo}`보다 구체적인 리터럴 경로라 Spring MVC가 먼저 매칭한다.
계좌번호 패턴이 숫자만 허용하므로 실제로 겹치지도 않는다.

### 오류 코드

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | `bankCode`·`accountType` 형식 위반(영숫자·밑줄 외 문자), `accountNo`가 숫자 10~20자리가 아님, `transactionId` 형식 위반, page < 0, size가 1~100 밖, `inactiveMonths`가 1~120 밖, `maxBalance` < 0 |
| `INVALID_REQUEST` | 400 | `accountType`이 DEMAND_DEPOSIT / MINUS / DEPOSIT / SAVINGS 밖의 값, 시작일 > 종료일, 종료일이 오늘 이후, 조회 기간 12개월 초과, 연월 형식(YYYY-MM) 위반, 미래 월, 시작월 > 종료월 |
| `RESOURCE_NOT_FOUND` | 404 | 본인 소유가 아니거나 존재하지 않는 계좌("계좌를 찾을 수 없습니다."), 그 계좌에 없는 거래("거래를 찾을 수 없습니다.") |
| `UNAUTHORIZED` | 401 | 토큰 없음·만료 |

## 계좌 전체 조회

| 파라미터 | 기본값 | 규칙 |
| --- | --- | --- |
| `bankCode` | 전체 | `account.bank_code`와 대문자 기준 정확히 일치(`004`, `088` 등) |
| `accountType` | 전체 | `AccountType` 값. 대소문자 무시, 정의되지 않은 값은 400 |

- 상태(ACTIVE, INACTIVE, CLOSED)로는 걸러내지 않고 **모두 돌려준다**. 해지 계좌를 숨길지는 FE가 `status`로 정한다.
  Finance Data 통합 요약(500)은 CLOSED를 제외하고 집계하므로 목록 개수와 요약 개수는 다를 수 있다.
- 정렬은 상태(ACTIVE → INACTIVE → CLOSED) → 은행명 → 계좌명 → 계좌 ID다. 사용 중인 계좌가 먼저 보인다.
- 응답은 배열이다(페이지 없음). 사용자당 계좌는 수십 건을 넘지 않는다는 전제이며, 필터·정렬은 메모리에서 한다.

### 계좌 응답 필드 (`AccountResponse`)

목록·상세가 같은 형태다. 카드(8) 응답의 출금계좌 필드 이름(`bankCode`, `bankName`, `accountName`, `accountNoMasked`, `accountType`)과 맞췼다.

| 필드 | 설명 |
| --- | --- |
| `accountId` | 거래(6)·카드(8) 응답의 `accountId`와 같은 값 |
| `bankCode`, `bankName` | 은행 코드·이름 |
| `accountNoMasked` | 마스킹된 계좌번호. **원문·암호문·해시는 어떤 응답에도 싣지 않는다** |
| `accountName` | 계좌 이름(상품명) |
| `accountType` | `DEMAND_DEPOSIT` / `MINUS` / `DEPOSIT` / `SAVINGS` |
| `status` | `ACTIVE` / `INACTIVE` / `CLOSED` |
| `balance` | MINUS 계좌는 0 이하(대출 잔액), 나머지는 0 이상. ERD `chk_account_balance_by_type` |
| `currency` | ISO 통화 코드, 현재 `KRW` |
| `lastTransactionDate` | 마지막 거래일. 없으면 null |
| `sourceType` | `SSAFY` / `MANUAL` / `SEED` |
| `createdAt`, `updatedAt` | 적재 시각, 마지막 갱신 시각 |

## 계좌 상세·잔액 조회

- 경로의 `accountNo`는 계좌번호 **원문**이다(숫자 10~20자리, 구분자 없음). 서버는 `SensitiveNumberHasher`로 HMAC 해시를
  만들어 `uk_account_user_account_hash`로 찾는다. 카드(8)의 `cardNo` 처리와 같다.
- 상세는 `AccountResponse` 전체를, 잔액은 `AccountBalanceResponse`(`accountId`, `bankName`, `accountNoMasked`, `accountName`,
  `accountType`, `status`, `balance`, `currency`, `lastTransactionDate`, `balanceAsOf`)를 돌려준다.
- `balanceAsOf`는 `account.updated_at`이다. 동기화·마이너스통장 상환(534) 등 잔액을 바꾸는 모든 경로가 이 컬럼을 갱신하므로
  "이 잔액이 언제 기준인지"를 FE가 표시할 수 있다.
- 잔액은 우리 DB 값이다. 명세의 "SSAFY" 표기와의 관계는 아래 남은 작업 참고.

## 계좌 거래내역·거래 단건 (502)

| 파라미터 | 기본값 | 규칙 |
| --- | --- | --- |
| `startDate`, `endDate` | 종료일 = 오늘, 시작일 = 종료일 − 3개월 + 1일 | `QueryPeriodPolicy`(카드(8)·마이너스통장(10-1) 거래내역과 동일). 최대 12개월 |
| `page`, `size` | 0, 20 | page ≥ 0, size 1~100 (도담 정책) |

- 우리 DB `transaction`에서 `user_id = 본인 AND account_id = 해당 계좌`로 읽는다. 소유권은 SQL 조건으로 강제한다.
- 목록은 **숨긴 거래(`hidden = 1`)를 제외**하고, 단건 조회는 숨긴 거래도 돌려준다(숨김은 목록 노출 정책이지 접근 제한이 아니다).
  같은 원장을 읽는 카드·마이너스통장 거래내역과 같은 규칙이다.
- 정렬은 거래일 → 거래시각 → 거래 ID 내림차순. 시각이 없는 거래는 같은 날짜 안에서 뒤로 간다.
- 단건 조회에서 다른 계좌의 거래 ID를 넣으면 계좌·거래가 함께 일치하지 않으므로 404("거래를 찾을 수 없습니다.")다.
- 응답 `AccountTransactionResponse`는 마이너스통장 거래내역 응답과 필드 이름·순서를 맞췼다(534가 제기한 "응답 계약 불일치" 해소).
  `transactionId`, `transactionDate`, `transactionTime`, `transactionType`(INCOME / EXPENSE / TRANSFER), `amount`(양수), `balanceAfter`,
  `merchantName`, `summary`, `memo`, `categoryCode`, `categoryName`에 `hidden`만 추가했다.
- 거래 메모·카테고리·숨김 편집은 API 6(`/api/v1/transactions/**`, S15P21D105-504)이 맡는다. 이 API는 읽기 전용이다.

### 거래내역 Adapter (원장 적재)

"Adapter"는 금융망 2.4 계좌 거래내역 조회(`inquireTransactionHistoryList`)를 우리 원장 `transaction`으로 옮기는 부분이다.
`AccountSyncHandler`가 계좌 목록을 반영한 뒤 **금융망 출처(SSAFY) 계좌마다** 최근 12개월(조회 정책 최대 기간) 거래를 받아
`JdbcAccountTransactionWriter`로 **없는 거래만 INSERT**한다. 아래 "ACCOUNT 동기화" 표 참고.

| 금융망 필드 | `transaction` 컬럼 | 비고 |
| --- | --- | --- |
| `transactionUniqueNo` | `transaction_id` | 문자열 그대로 |
| `transactionDate` yyyyMMdd / `transactionTime` HHmmss | `transaction_date` / `transaction_time` | 시각이 비면 null |
| `transactionType` "1" 입금 / "2" 출금 | `transaction_type` INCOME / EXPENSE | 그 외 값은 계약 위반 → scope FAILED |
| `transactionBalance` | `amount` | 0 이하면 ERD CHECK 위반이라 건너뛴다(warn 로그) |
| `transactionAfterBalance` | `balance_after` | |
| `transactionSummary` / `transactionMemo` | `summary` / `memo` | 빈 문자열은 null |
| (고정) | `payment_method = 'ACCOUNT'`, `source_type = 'SSAFY'`, `hidden = 0`, `category_id = NULL`, `merchant_name = NULL` | 카테고리 분류는 3 Preference(자동 분류)·API 6(수동)에서 |

이미 있는 거래는 어떤 컬럼도 덮어쓰지 않는다. 사용자가 API 6으로 바꾼 메모·카테고리·숨김이 동기화로 되돌아가지 않게 하기 위해서다.
SEED·MANUAL 계좌의 거래는 받지도 넣지도 않는다.

## 계좌 집계·휴면 탐지 (503)

모두 우리 DB 기준 자체 계산이다. 계좌 단위(요약·현금성 자산·휴면)는 사용자 계좌를 한 번 읽어 메모리에서 계산하고,
거래 단위(월별 요약·추이)는 `transaction`을 월별로 묶어 읽는다(`JdbcAccountFlowReader`). 거래 집계는 **숨긴 거래를 제외**한다.

### 계좌 요약 `GET /accounts/summary`

Finance Data 통합 요약(500)의 `accounts` 절과 같은 합산 규칙이다. CLOSED 계좌는 개수·잔액에서 빼고(`closedCount`로만 보고),
마이너스통장 `balance`(0 이하)는 부호를 뒤집어 `minusUsedAmount`(부채)로 본다.

| 필드 | 값 |
| --- | --- |
| `count` | CLOSED 제외 계좌 수(마이너스통장 포함) |
| `activeCount`, `inactiveCount`, `closedCount` | 상태별 계좌 수 |
| `demandDepositCount/Balance`, `depositCount/Balance`, `savingsCount/Balance` | 유형별 개수·잔액 합(CLOSED 제외) |
| `minusAccountCount`, `minusUsedAmount` | 마이너스통장 수, 사용액 합(양수) |
| `totalAssets` | 입출금 + 예금 + 적금 잔액 |
| `netAssets` | `totalAssets − minusUsedAmount` |

### 현금성 자산 합계 `GET /accounts/balance-summary`

기획안 9-2의 "현금성 자산"을 **즉시 인출 가능한 수시입출금(DEMAND_DEPOSIT) 잔액**으로 정의했다. 예금·적금은 저축성 자산,
마이너스통장은 부채라 넣지 않는다. CLOSED는 제외하고 INACTIVE는 포함한다(잔액은 그대로 인출 가능하기 때문).
응답은 `totalCash`, `accountCount`, `accounts[]`(잔액 내림차순, `sharePercent`는 `totalCash` 대비 비중을 소수 첫째 자리까지)다.

### 휴면성 계좌 `GET /accounts/inactive?inactiveMonths&maxBalance`

기획안 「휴면성 소액계좌」 확정 규칙 **"잔액 > 0 AND 최근 N개월간 입출금 거래 없음"** 을 수시입출금 계좌에 적용한다.

| 파라미터 | 기본값 | 규칙 |
| --- | --- | --- |
| `inactiveMonths` | 6 | 1~120. 이 개월 수 이상 거래가 없으면 휴면 |
| `maxBalance` | 없음 | 0 이상. 지정하면 이 금액 이하만 "소액"으로 잡는다 |

- 판정: `lastActivityDate <= 기준일 − inactiveMonths`. `lastActivityDate`는 `account.last_transaction_date`이고, 거래 이력이 없어
  null이면 **계좌 적재일(`created_at`)**을 쓴다. 방금 연동한 새 계좌가 휴면으로 잡히지 않게 하기 위해서다.
- 잔액 0 이하·CLOSED·예금·적금·마이너스통장은 어떤 기준에서도 잡히지 않는다.
- 응답은 적용한 `criteria`(inactiveMonths, maxBalance, asOfDate)와 `count`, `totalBalance`, `accounts[]`(거래 없는 기간이 긴 순,
  각 항목에 `lastActivityDate`·`inactiveMonths`)다. FE가 "최근 N개월간 거래가 없는 계좌 M개에 … 원" 문구를 만들 수 있다.
- `transaction` 원장이 아니라 `account.last_transaction_date`를 기준으로 한다. 동기화(502)가 이 컬럼을 금융망 값으로 갱신하고,
  ERD가 `idx_account_user_inactive (user_id, last_transaction_date, balance)`를 이 용도로 두었기 때문이다.

### 월별 입출금 요약 `GET /accounts/{accountNo}/monthly-summary?yearMonth`

- `yearMonth`는 YYYY-MM, 생략하면 이번 달. 미래 월은 400(`YearMonthPolicy`, 카드 월 사용액과 동일).
- `incomeTotal/Count`(INCOME), `expenseTotal/Count`(EXPENSE), `transferTotal/Count`(TRANSFER), `netChange = income − expense`,
  `transactionCount = 세 건수 합`. ERD의 TRANSFER는 방향이 없어 `netChange`에 넣지 않고 따로 보고한다.
- 거래가 없는 달은 모두 0이다(404가 아니다).

### 입출금 추이 `GET /accounts/{accountNo}/trend?startMonth&endMonth`

- 기간 정책은 카드 청구서와 같은 `YearMonthPeriodPolicy`(기본 최근 3개월, 최대 12개월, 종료월은 이번 달까지).
- 기간 안의 모든 달을 오래된 순으로 채우고 거래가 없는 달은 0이다. `months[]` 각 항목은 월별 요약과 같은 필드(건수는 합계만)이며,
  `totalIncome`·`totalExpense`·`totalNetChange`로 기간 합을 함께 준다.

## 데이터 출처와 ACCOUNT 동기화

세 API는 모두 우리 DB `account`(V3)만 읽는다. 명세는 "SSAFY" 연동으로 표기하지만, 금융망 계좌를 원장에 적재·갱신하는 일은
Finance Data 동기화(499)의 ACCOUNT scope 구현 `AccountSyncHandler`가 맡고 이 API는 그 결과를 조회한다.
카드(8)·대출(10)·예적금(11)이 각자 테이블을 읽는 방식과 같다.

### AccountSyncHandler (`POST /api/v1/finance-data/sync`, scope `ACCOUNT` 또는 `ALL`)

`FinanceSyncScopeHandler`의 ACCOUNT 구현으로, 금융망 2.4 수시입출금 계좌 목록 조회(`inquireDemandDepositAccountList`)를
호출해 `account`에 반영한다. 이 티켓에서 `SsafyFinanceClient`에 해당 호출을 추가했다.

| 상황 | 처리 | `syncedCount` |
| --- | --- | --- |
| 금융망에만 있는 계좌 | INSERT. `source_type = SSAFY`, `status = ACTIVE`, `account_type = DEMAND_DEPOSIT`. 계좌번호는 암호문(`FieldCryptor`)·해시(`SensitiveNumberHasher`)·마스킹(`AccountNumberMasker`)으로만 저장 | +1 |
| 양쪽에 있고 `source_type = SSAFY` | 은행·계좌명·잔액·최종거래일·통화를 최신 값으로 갱신, CLOSED였으면 ACTIVE로 복귀. 값이 같으면 `updated_at`도 유지 | 바뀐 경우 +1 |
| 양쪽에 있고 `source_type = SEED / MANUAL` | **덮어쓰지 않는다.** 시연 데이터와 마이너스통장 상환(534) 결과 보존 | 0 |
| 우리 DB의 SSAFY 출처 DEMAND_DEPOSIT 계좌가 금융망 목록에 없음 | `status = CLOSED`. 다음 동기화에 다시 나타나면 ACTIVE | +1 |
| 금융망 계좌 목록이 비어 있음 | COMPLETED, 0건. 위 규칙대로 SSAFY 출처 계좌는 CLOSED 처리 | 해지 건수 |
| SSAFY 출처 계좌의 거래내역(502) | 최근 12개월 거래 중 원장에 없는 것만 INSERT. 기존 거래는 어떤 컬럼도 건드리지 않음 | INSERT 건수 |

- `syncedCount`는 계좌 변경 + 거래 INSERT 합계이고, scope 결과 `message`에 "계좌 N건, 거래 M건 반영"으로 나눠 적는다.
- 금융망 호출(계좌 목록 1회 + 계좌별 거래내역 1회씩)과 응답 해석(날짜 `yyyyMMdd`, 시각 `HHmmss`, 거래 구분, 필수값 검사)은
  트랜잭션 밖에서 끝내고 저장만 한 트랜잭션에서 한다. 응답 한 건이라도 깨져 있으면 그 scope 전체가 FAILED고 계좌·거래 모두
  부분 반영이 없다. 새 계좌의 `account_id`가 확정돼야 거래를 넣을 수 있으므로 계좌 저장을 flush한 뒤 거래를 넣는다.
- 금융망 오류 응답은 502 `FINANCE_API_ERROR`, 네트워크·해석 실패는 503 `FINANCE_API_UNAVAILABLE`의 메시지로 scope 결과
  `message`에 실린다. 다른 scope는 계속 진행한다(499 규칙).
- 예금·적금·마이너스통장 행은 이 핸들러가 건드리지 않는다. 금융망의 해당 API는 각 도메인(11, 10-1)이 맡는다.
- 마스킹 형식: 12자리 이상은 앞 6 + `-**-**` + 뒤 4(`123456-**-**3456`), 그 미만은 앞 3 + `-***-` + 뒤 3.

## 스키마·엔티티

`transaction`·`transaction_category`는 V5 마이그레이션이 만든다. V5는 API 6(S15P21D105-504) 작업 브랜치에서 먼저 작성된 것을
이 브랜치로 가져온 것이며(cherry-pick), 두 브랜치가 모두 병합될 때 한쪽에서 중복 커밋을 정리해야 한다.
`Transaction` 엔티티는 504가 소유하므로 여기서는 JDBC reader/writer만 둔다.

`Account`가 `account`의 정식 매핑이다. 같은 테이블을 카드(`CardWithdrawalAccount`, 읽기 전용)와 마이너스통장(`MinusAccountOwner`)이
부분 매핑하고 있으며, 두 도메인이 Account 도메인 구현 전에 임시로 둔 것이다. 교체는 남은 작업으로 남겼다.

`account_no_encrypted`·`source_type`은 DDL이 NOT NULL이지만 엔티티에서는 nullable이다. local/prod의 `ddl-auto: validate`는
NULL 허용 여부를 검사하지 않고, H2 테스트는 엔티티로 스키마를 만드는데 예·적금·현금흐름 테스트 fixture가 이 두 컬럼 없이
`account`를 적재하기 때문이다. NOT NULL은 V3 DDL이 보장한다.

## 테스트

- `AccountApiIntegrationTest`: 회원가입·로그인으로 실제 토큰을 발급받아 세 API를 호출한다. fixture `sql/account-test-data.sql`이
  V3 컬럼 전체로 `account`를 다시 만든다(다른 도메인 fixture는 컬럼 구성이 다르고, 카드 fixture처럼 `account_no_encrypted`를
  NOT NULL로 두어 읽기 경로가 원문 컬럼에 의존하지 않음을 확인한다).
- `AccountSyncIntegrationTest`: 금융망 Mock으로 `POST /finance-data/sync`(ACCOUNT)를 실행해 추가·갱신·해지·복귀, SEED 보존,
  빈 목록, 금융망 오류·해석 실패 시 부분 반영 없음을 계좌 API로 확인한다. 저장된 암호문을 복호화해 원문과 대조한다.
  502: 거래내역 INSERT와 건수 메시지, 기존 거래(사용자 메모·숨김) 보존, SEED 계좌 거래 미적재, 깨진 거래로 인한 계좌 롤백.
- `AccountTransactionApiIntegrationTest`: 계좌 거래내역 목록(기간·페이지·숨김 제외·소유권)과 거래 단건(숨긴 거래 포함, 다른 계좌 거래 404).
- `AccountAnalysisApiIntegrationTest`: 요약(유형·상태별 합산, CLOSED 제외, 마이너스 부호 반전), 현금성 자산(수시입출금만, 비중),
  휴면 탐지(기본 6개월, 개월·소액 상한 조정, 거래일 없는 계좌의 적재일 기준, 잔액 0·CLOSED·타 유형 제외), 월별 요약(숨김·타 계좌 제외,
  거래 없는 달 0, 미래 월·형식 오류), 추이(빈 달 채움, 기간 정책).
- `RestSsafyFinanceClientTest`: 계좌 목록·거래내역 호출의 Header·Body(계좌번호·기간·구분)·경로와 REC 배열·REC 없음·REC 객체 응답 해석.
- `AccountNumberMaskerTest`: 마스킹 형식.
- `FinanceDataApiIntegrationTest`의 ACCOUNT 스텁은 제거했다. 이제 실제 핸들러가 돌고 Mock 계좌 3건으로 `syncedCount = 3`을 만든다.

## 남은 작업

[remaining-work.md](remaining-work.md)의 "S15P21D105-501" 절에 기록한다. 요약하면 금융망 잔액 실시간 조회 여부,
금융망 실호출 검증(API Key), 거래내역 동기화 기간·증분 전략, 부분 매핑 엔티티 교체, MySQL 실검증이다.
