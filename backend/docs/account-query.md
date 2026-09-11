# Account API — 계좌 목록·상세·잔액

JIRA `S15P21D105-453` (D4 Account API 구현)의 하위 티켓 `S15P21D105-501`(API 5 계좌 목록·상세·잔액) 구현 기준이다.
API 명세 5 Account 중 세 개를 다룬다. 계좌 요약·거래내역·월별 입출금·입출금 추이·휴면성 계좌·현금성 자산 합계는
다음 티켓 범위이며 이 문서에 포함하지 않는다.

## API

모든 API는 인증이 필요하고 본인 계좌만 다룬다. 타인 계좌·없는 계좌는 구분하지 않고 404다.

| 기능 | Method | Endpoint | 상태 코드 |
| --- | --- | --- | --- |
| 계좌 전체 조회 | GET | `/api/v1/accounts?bankCode&accountType` | 200 |
| 계좌 상세 조회 | GET | `/api/v1/accounts/{accountNo}` | 200 |
| 계좌 잔액 조회 | GET | `/api/v1/accounts/{accountNo}/balance` | 200 |

### 오류 코드

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | `bankCode`·`accountType` 형식 위반(영숫자·밑줄 외 문자), `accountNo`가 숫자 10~20자리가 아님 |
| `INVALID_REQUEST` | 400 | `accountType`이 DEMAND_DEPOSIT / MINUS / DEPOSIT / SAVINGS 밖의 값 |
| `RESOURCE_NOT_FOUND` | 404 | 본인 소유가 아니거나 존재하지 않는 계좌 |
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

- 금융망 호출과 응답 해석(날짜 `yyyyMMdd`, 필수값 검사)은 트랜잭션 밖에서 끝내고 저장만 트랜잭션을 연다. 응답 한 건이라도
  깨져 있으면 그 scope 전체가 FAILED고 부분 반영은 없다.
- 금융망 오류 응답은 502 `FINANCE_API_ERROR`, 네트워크·해석 실패는 503 `FINANCE_API_UNAVAILABLE`의 메시지로 scope 결과
  `message`에 실린다. 다른 scope는 계속 진행한다(499 규칙).
- 예금·적금·마이너스통장 행은 이 핸들러가 건드리지 않는다. 금융망의 해당 API는 각 도메인(11, 10-1)이 맡는다.
- 마스킹 형식: 12자리 이상은 앞 6 + `-**-**` + 뒤 4(`123456-**-**3456`), 그 미만은 앞 3 + `-***-` + 뒤 3.

## 엔티티

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
- `RestSsafyFinanceClientTest`: 계좌 목록 호출의 Header·경로와 REC 배열·REC 없음·REC 객체 응답 해석.
- `AccountNumberMaskerTest`: 마스킹 형식.
- `FinanceDataApiIntegrationTest`의 ACCOUNT 스텁은 제거했다. 이제 실제 핸들러가 돌고 Mock 계좌 3건으로 `syncedCount = 3`을 만든다.

## 남은 작업

[remaining-work.md](remaining-work.md)의 "S15P21D105-501" 절에 기록한다. 요약하면 금융망 잔액 실시간 조회 여부,
금융망 실호출 검증(API Key), 부분 매핑 엔티티 교체, MySQL 실검증이다.
