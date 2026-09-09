# 마이너스통장 API 구현 기준 (S15P21D105-534)

## 상태와 근거

2026-09-09 기준 목록·상세·이자·거래내역·알림·원금상환·이자납부 API 7개를 구현했다.
계획과 결정 배경은 [minus-account-plan.md](minus-account-plan.md)에 있고, 이 문서는 실제 구현 계약을 기록한다.

- 로컬 Jira 내보내기의 534 범위: `[API 10-1] Minus Account API`, 설명 `목록·상세·이자·거래·알림·상환·이자납부 구현`.
- API 기준: `docs/API명세서/10-1 Minus Account 3cdbb6ea356480d4b597e67c0674c55c.csv` (일반 CSV).
- 저장 기준: `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`의 `minus_account`, `account`와 `docs/ERD/ERD 작성 ...md`의 계산 기준·코드값 정책.
- 기획 기준: **없다.** 기획안에 마이너스통장 항목이 없어 이자 표시 정책 등은 이 문서의 계약으로 확정했다.
- 착수 시점: `develop` `cf854be`(533 병합 완료) 기준으로 브랜치를 생성했다.

경로가 `/api/v1/loans` 아래지만 `loan` 테이블과 무관하며 Loan 도메인 코드를 공유하지 않는다.
패키지는 `domain/minusaccount/`다.

## 인증과 공통 응답

모든 API는 인증이 필요하다. `AuthenticatedUserIdResolver`로 본인 ID를 얻고 조회·변경 조건에
`user_id`와 `account_type = 'MINUS'`를 함께 포함한다.

`{accountNo}`는 숫자 10~20자리의 평문 계좌번호를 받아 `SensitiveNumberHasher`로 HMAC 처리한 뒤
`account.account_no_hash`로 조회한다. 응답에는 `accountNoMasked`만 포함하며 원문·암호문·해시는
노출하지 않는다.

타인 계좌, 존재하지 않는 계좌, `MINUS`가 아닌 계좌(예: 입출금계좌 번호로 조회)는 모두
`RESOURCE_NOT_FOUND`로 처리해 리소스 존재 여부를 노출하지 않는다. 인증 실패는 기존 401 흐름을 따른다.

공통 `ApiResponse`와 페이징 응답의 `PageResponse`를 사용한다.

## 엔드포인트와 입력

| Method | Endpoint | 입력 및 기본값 |
| --- | --- | --- |
| GET | `/api/v1/loans/minus-accounts` | `status?` |
| GET | `/api/v1/loans/minus-accounts/{accountNo}` | 숫자 10~20자리 |
| GET | `.../{accountNo}/interest` | 없음 |
| GET | `.../{accountNo}/transactions` | `startDate?`, `endDate?`, `page=0`, `size=20` (1~100) |
| GET | `.../{accountNo}/alerts` | `unreadOnly=false`, `page=0`, `size=20` (1~100) |
| POST | `.../{accountNo}/repayments` | Body `amount`(필수, 양수), `repayAccountNo?` |
| POST | `.../{accountNo}/interest-payments` | Body `amount?`(양수), `paymentAccountNo?` |

`status`는 `ACTIVE`, `INACTIVE`, `CLOSED`만 허용하며 대소문자를 가리지 않는다. 생략하면 전체를
조회한다. `minus_account`에 상태 컬럼이 없고 ERD 문서가 "MinusAccount·DepositSaving도 Account
상태를 사용"으로 정하고 있어 `account.status`로 해석한 것이다. 허용값 밖이면 400이다.

## 조회 API

### 목록

`used_amount` 내림차순, 같으면 `account_id` 오름차순으로 정렬한다. 명세에 페이징이 없어 전체를
반환하며 사용자당 보유 건수가 소수라 메모리 정렬로 처리한다.

`usageRate`는 **백분율이 아니라 비율**이다. `usedAmount / limitAmount`를 소수점 4자리 반올림한
값으로 97% 사용은 `0.9700`이다. `interestRate`가 `7.2000`처럼 퍼센트 값이라 단위가 다르니 주의한다.
`chk_minus_account_limit_amount`가 한도를 양수로 보장해 0 나눗셈은 발생하지 않는다.

`overdue`는 `overdueDays > 0`이다. 별도 연체 상태 컬럼이 없어 이 파생값이 유일한 판정 근거다.

### 상세

목록 필드에 `balance`, `currency`, `lastTransactionDate`, `createdAt`, `updatedAt`을 더한다.
`balance`는 상위 `account`의 잔액이며 ERD 계산 기준에 따라 `-usedAmount`인 0 이하 값이다.

### 이자

**저장값과 계산값을 분리해서 반환하며 서로 검증하지 않는다.**

| 필드 | 산출 |
| --- | --- |
| `accruedInterest` | `minus_account.accrued_interest` 저장값. 서버가 다시 계산하지 않는다 |
| `daysToNextInterestDate` | 기준일부터 `next_interest_date`까지 일수. 같은 날이면 0, 과거이거나 NULL이면 `null` |
| `estimatedDailyInterest` | `usedAmount × interestRate ÷ 100 ÷ 365`를 원 단위 절사 |
| `estimatedInterestUntilNextDate` | `estimatedDailyInterest × daysToNextInterestDate`. 일수가 `null`이면 함께 `null` |

- 일할 계산 기준 `actual/365`는 명세·기획안에 정의가 없어 관행값으로 확정한 것이다.
- **저장된 미수이자를 재현하지 않는다.** seed 값을 역산하면 30~34일치로 흩어져 특정 기산 기준이
  나오지 않았다. 두 값을 같은 것으로 취급하면 안 된다.
- 연체 가산금리 계약이 없어 연체 상태에서도 같은 금리로 계산한다. **연체이자는 반영되지 않는다.**
- `estimated*`는 예상치이며 실제 청구액이 아니다.
- 금액은 `BigDecimal`로 계산한 뒤 마지막에 절사한다. 부동소수점을 쓰지 않는다.

### 거래내역

공통 `QueryPeriodPolicy`를 사용한다. 기본 3개월, 최대 12개월이며 시작일 ≤ 종료일, 종료일 ≤ 오늘을
검증한다. 위반은 400이다.

`transaction`을 `user_id`, `account_id`, `hidden = FALSE`, 기간 조건으로 조회하고
`transaction_date DESC, transaction_time DESC, transaction_id DESC`로 정렬한다.

`amount`는 `chk_transaction_amount`에 따라 항상 양수이고 방향은 `transactionType`
(`INCOME`/`EXPENSE`/`TRANSFER`)이 정한다. `balanceAfter`는 마이너스통장에서 음수이거나 NULL일 수
있으며 **부호를 뒤집지 않고 저장값 그대로 전달한다.**

`transaction`은 BE-A 소관이라 JPA 엔티티로 선점하지 않고 `JdbcMinusAccountTransactionReader`로
읽기만 한다. 카드 도메인의 선례와 같다.

### 알림

`notification`을 `source_type = 'MINUS_ACCOUNT'`, `source_id = account_id`(문자열),
`deleted_at IS NULL`로 조회한다. `unreadOnly=true`면 `is_read = FALSE`를 더한다.
정렬은 `created_at DESC, notification_id DESC`다.

소프트 삭제 제외는 ERD 문서의 "소프트 삭제된 데이터는 일반 조회에서 제외한다"에 따른다.

**읽기 전용 필터 뷰다.** 알림 생성·읽음 처리·삭제는 `22 Notification`(S15P21D105-458)이 소유하며
534는 테이블도 알림 생성 배치도 만들지 않는다.

**연결 규약은 458과 합의되지 않았다.** `source_type` 값과 `source_id`에 `account_id`를 넣는 방식은
`JdbcMinusAccountAlertReader`의 상수로 두었고, 합의 결과에 따라 그 상수만 바꾸면 된다. 합의 전까지
실제 데이터가 없어 항상 빈 페이지를 반환한다.

## 쓰기 API

### 성격: 시연용 로컬 상태 전이

**SSAFY 금융망에 실행을 요청하지 않는다.** 우리 DB의 `minus_account`와 `account`만 바꾼다.
시연 계좌가 `source_type = 'SEED'`, 은행 `SEED_BANK_A`~`D`라 금융망에 존재하지 않기 때문이며,
2026-09-09 사용자 결정 사항이다. 판단 근거는 계획 문서 8절에 있다.

따라서 명세의 데이터/연동 `SSAFY`와 "호출별 거래고유번호 신규 생성"은 **충족하지 않는다.**

### 공통 처리

1. `accountNo`로 본인의 마이너스통장을 찾는다. 없으면 404.
2. `account.status`가 `ACTIVE`가 아니면 `MINUS_ACCOUNT_NOT_ACTIVE`(409).
3. 출금계좌를 특정하고 잠근다.
4. 대상 마이너스통장 행을 `PESSIMISTIC_WRITE`로 잠근다. `available_amount`가 파생 저장값이라
   읽고-쓰는 사이의 경쟁 조건을 막아야 한다.
5. 하나의 쓰기 트랜잭션으로 처리한다.

### 출금계좌 결정

`minus_account`에는 `card`/`loan`/`deposit_saving`과 달리 출금계좌 컬럼이 없어 저장된 기본값이
없다. 명세에도 기본 규칙이 없어 다음과 같이 확정했다.

- `repayAccountNo`(또는 `paymentAccountNo`)를 주면 본인의 `DEMAND_DEPOSIT`이고 `ACTIVE`인 계좌만
  허용한다. 아니면 404다. 마이너스통장 자기 자신을 지정해도 유형 조건에서 걸러진다.
- 생략하면 **본인의 활성 입출금계좌가 정확히 1개일 때만** 자동 선택한다.
- 0개거나 2개 이상이면 `WITHDRAWAL_ACCOUNT_REQUIRED`(400)로 지정을 요구한다. 임의 선택은 잘못된
  계좌에서 출금될 수 있어 하지 않는다.

### 원금 상환 `POST .../repayments`

- 검증: `amount`는 양수이고 `used_amount` 이하여야 한다. 초과는 `REPAYMENT_AMOUNT_EXCEEDED`(400).
  사용액이 0원인 계좌는 어떤 금액도 초과가 되어 같은 오류다.
- 출금계좌 잔액이 부족하면 `INSUFFICIENT_ACCOUNT_BALANCE`(409).
- 반영: `used_amount -= amount`, `available_amount = limit_amount - used_amount`,
  `account.balance = -used_amount`, 출금계좌 `balance -= amount`, 양쪽 `updated_at` 갱신.
- **미수이자는 건드리지 않는다.** 연체 상태의 이자 우선 충당 규칙이 없어 원금만 줄인다.
- **`overdue_days`를 초기화하지 않는다.** 연체 해소 판정 기준이 없다.

### 이자 납부 `POST .../interest-payments`

- `amount`를 생략하면 **잔여 미수이자 전액**을 납부한다. 명세가 선택 입력으로 둔 것을 전액 납부
  의도로 해석했다.
- 미수이자가 0원이면 `NO_ACCRUED_INTEREST`(400). 초과는 `REPAYMENT_AMOUNT_EXCEEDED`(400).
- 반영: `accrued_interest -= amount`, 출금계좌 `balance -= amount`.
- **`used_amount`와 마이너스통장 잔액은 바뀌지 않는다.**
- **`next_interest_date`를 갱신하지 않는다.** 다음 이자일 산정 규칙이 명세에 없다.

### `account` 잔액 갱신 방식

`account`는 BE-A(S15P21D105-501) 소관이라 쓰기 가능한 JPA 엔티티를 만들지 않았다. 읽기 전용
매핑 `MinusAccountOwner`(`@Immutable`)와 같은 테이블에 쓰기 엔티티를 하나 더 두면 501 구현과
이중 매핑이 되기 때문이다.

대신 `JdbcAccountBalanceWriter`가 UPDATE를 담당한다.

- 조건절에 `user_id`와 `account_type`을 포함해 소유권과 유형을 SQL 단계에서 강제한다.
- 출금 UPDATE에는 `balance >= :amount` 조건을 넣어 서비스 검증과 실제 갱신 사이의 경쟁 조건까지 막는다.
- 갱신 행 수가 1이 아니면 예외로 트랜잭션을 되돌린다.

501이 `Account` 엔티티를 만들면 이 어댑터를 교체한다. **501 담당자에게 알려야 하는 사항이다.**

### 미충족 사항

- **중복 요청 방지가 구현되지 않았다.** 행 잠금으로 동시 요청의 경쟁 조건만 막는다. 재전송 멱등성은
  `Idempotency-Key`와 처리 결과 저장이 필요한데 그 계약이 정해지지 않았다. 명세의 "중복 요청 방지"를
  충족하지 못한 상태다.
- **거래내역(`transaction`)에 기록하지 않는다.** 거래 원장은 BE-A 소관이고, 명세의 유일한 거래 생성
  경로인 `POST /api/v1/transactions`조차 Body에 계좌 지정 필드가 없어 시스템 생성 거래의 규약이
  정의되어 있지 않다. 임의 규칙으로 남의 원장에 쓰지 않았다. **따라서 상환 직후 거래내역 조회에는
  그 상환이 나타나지 않는다.**
- **`POST /api/v1/finance-data/sync`가 로컬 상환분을 덮어쓸 수 있다.** `source_type = 'SEED'` 계좌를
  동기화 대상에서 제외하는 합의가 필요하며 534에서 단독으로 정할 수 없다.

## 오류 코드

기존 `ErrorCode`에 다음을 추가했다. 전역 공유 파일이라 추가만 하고 기존 항목은 건드리지 않았다.
409를 표현해야 해서 `InvalidRequestException`(400 고정)이 아니라 `BusinessException(ErrorCode)`로 던진다.

| 코드 | 상태 | 발생 조건 |
| --- | --- | --- |
| `MINUS_ACCOUNT_NOT_ACTIVE` | 409 | 해지·휴면 계좌에 상환·이자납부 시도 |
| `REPAYMENT_AMOUNT_EXCEEDED` | 400 | 사용액 또는 미수이자를 초과하는 금액 |
| `NO_ACCRUED_INTEREST` | 400 | 미수이자가 0원인데 납부 시도 |
| `INSUFFICIENT_ACCOUNT_BALANCE` | 409 | 출금계좌 잔액 부족 |
| `WITHDRAWAL_ACCOUNT_REQUIRED` | 400 | 출금계좌를 특정할 수 없음 |

## 스키마

`V14__create_minus_account.sql`로 `minus_account`를 생성했다. ERD 정의를 그대로 옮겼고 CHECK 7개와
`account`에 대한 복합 외래키 `(user_id, account_id, account_type)`를 포함한다.

`transaction`과 `notification`은 각각 BE-A와 458 소관이라 만들지 않았다. 읽기만 한다.

## 검증

`.\gradlew.bat test` 기준으로 `MinusAccountApiIntegrationTest` 35건과 전체 378건이 통과했다(2026-09-09, 최신 develop `4d10e73` 리베이스 후).
스킵 2건은 환경변수가 없을 때 건너뛰는 MySQL 조건부 테스트다.

검증 범위는 인증 없음 401, 타인·비 MINUS 계좌 404, 계좌번호 형식 400, 상태 필터와 허용값 밖 400,
정렬, `usageRate` 비율, 이자의 사용액 0원·이자일 과거·이자일 NULL 경계, 거래내역의 기본 기간·기간
정책 위반·페이징 경계·숨김 거래 제외·빈 결과, 알림의 소프트 삭제 제외·`unreadOnly`·빈 결과,
상환의 정상·전액·초과·0·음수·사용액 0원 계좌·출금계좌 부족·다건·0건·유형 위반·해지 계좌·타인 계좌,
이자 납부의 정상·전액·초과·미수이자 0원이다.

상환 후에는 `used_amount + available_amount = limit_amount`와 `account.balance = -used_amount`,
출금계좌 잔액 차감을 DB에서 직접 확인한다.

### MySQL 검증

`MinusAccountMySqlIntegrationTest`로 확인했다(2026-09-09). `MINUS_MYSQL_TEST_URL`이
`jdbc:mysql://127.0.0.1:<port>/dodam_534_test` 형식일 때만 실행되고, 없으면 건너뛴다.
빈 스키마를 가진 일회용 MySQL 8.0 컨테이너에서 실행했으며 운영 DB와 인프라 구성은 건드리지 않았다.

검증한 것은 다음과 같다.

- 실제 Flyway가 `V1`~`V14`를 적용하고 `flyway_schema_history`에 `version = '14'`가 성공으로 남는다.
- Hibernate `ddl-auto: validate`가 통과한다. 즉 `V14`의 DDL과 `MinusAccount`·`MinusAccountOwner`
  매핑이 일치한다. 529에서 `card`의 `CHAR` 컬럼이 이 검증에서 걸린 전례가 있어 확인했다.
- `information_schema.CHECK_CONSTRAINTS`에 `chk_minus_account%` 7개가 생성된다.
- `account`에 대한 복합 외래키가 `(user_id, account_id, account_type)` 3컬럼으로 생성된다.
- **CHECK가 살아 있는 상태에서** 상환·이자납부가 통과한다. 상환 후
  `used_amount + available_amount = limit_amount`와 `account.balance = -used_amount`를 확인했다.
- 타인 계좌 404.

H2 테스트는 `ddl-auto: create-drop`이 엔티티 기준으로 스키마를 만들어 CHECK가 없다. 이 도메인의
fixture(`sql/minus-account-test-data.sql`)에 금액 정합 CHECK를 직접 넣어두긴 했지만, 그것이 MySQL
제약 검증을 대신하지는 않는다. 위 조건부 테스트가 그 역할을 한다.

### 미검증 범위
- **seed 실데이터 기준 동작을 확인하지 못했다.** seed 적재에 암호화 픽스처 임시 테이블 3개가 필요한데
  생성기가 없다. 530·532와 같은 선행 조건이다.
- 알림은 `notification` 테이블이 실제로 없어 빈 결과만 확인했다.

### 시연 시 주의

seed의 `next_interest_date`는 1,000행 모두 `2026-09-25`다. **그날이 지나면 이자 API의
`daysToNextInterestDate`와 `estimatedInterestUntilNextDate`가 전부 `null`이 된다.** 534 코드가 아니라
seed(S15P21D105-523) 쪽 보완이 필요하다.
