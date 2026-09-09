# 마이너스통장 API 진행계획 (S15P21D105-534)

## 1. 문서 성격과 근거

**계획 문서이며 구현 완료 문서가 아니다.** 작성일 2026-09-09.

- 사용자 지정 작업 브랜치는 `S15P21D105-534-api-10-1-minus-account-api`다. 작성 시점에 `develop`(HEAD `cf854be`)에서 이 브랜치를 로컬 생성했고 커밋·push는 수행하지 않았다. Jira `Create branch`가 아닌 로컬 생성 방식이므로 Jira 연결 확인이 남는다.
- 선행 이슈는 모두 해소됐다. 531·532·533이 `develop`에 병합되어 있어 연쇄 분기 의존이 없다.
- 연결된 Jira 사이트에서 534를 조회하지 못했다. 로컬 `SPRINT/Jira (4).csv`의 제목은 `[API 10-1] Minus Account API`, 설명은 `목록·상세·이자·거래·알림·상환·이자납부 구현`, 라벨은 `sprint4,day4,be-b`, 상위 작업은 `S15P21D105-462 [D4] Loan·Minus Account API 구현`이다. **최신 Jira 완료 조건을 대신하지 않는다.**

기준 자료는 다음과 같다.

| 자료 | 사용 범위 |
| --- | --- |
| `docs/API명세서/10-1 Minus Account ...csv` | 7개 API의 경로·입력·제한사항 |
| `docs/API명세서/22 Notification ...csv` | 7절 알림 계약의 상위 소유 API |
| `docs/API명세서`의 `5 Account`, `6 Transaction Spending`, `10 Loan`, `13 Dashboards` | 4절 소유 경계 |
| `docs/ERD/ERD 작성 ...md` | **주요 코드값·소유권 무결성·계산 기준 정책.** 6·8절 정책의 상당수가 여기서 확정된다 |
| `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql` | `minus_account`, `account`, `transaction`, `notification` DDL |
| `docs/dev_plan.md` | Day별 담당 배분. 4절 경계와 7절 알림 일정 근거 |
| `docs/기획안/기획안 작성 ...md` | 11-3 활용 API 표(2절 SSAFY 해석) |
| `backend/database/seeds/DODAM_LOAN_MINUS_ACCOUNT_SEED.sql` | 3절 데이터 확인 |
| `backend/docs/loan-query.md`, `card-payment-query.md`, 기존 Loan·Card 코드 | 재사용할 계약과 응답 어휘 |
| `backend/src/main/resources/db/migration/README.md` | 5절 마이그레이션 번호 규칙 |

`dev_plan.md`의 Day 4는 BE2 정현진에게 `10 Loan + 10-1 Minus Account`를 배정한다. 같은 Day 4에 BE1 구동재가 `5 Account`를, Day 9~10에 `22 Notification`을 맡는다. 이 배분이 4절 경계와 7절 알림 일정의 근거다.

**기획안에는 마이너스통장이 없다.** `docs/기획안`에서 "마이너스"·"마통"·"한도대출"·"minus" 검색 결과가 0건이고, 11-3 활용 API 표에도 계좌·이체내역·카드·대출·예적금만 있고 마이너스통장 항목이 없다. 따라서 계산·표시 정책의 기획 근거가 없다. 다만 `ERD 작성 ...md`가 `MINUS` 관련 정책을 여러 건 명시하고 있어(6·8절에 근거를 표기했다) 이전 초안에서 제안으로 두었던 항목 일부는 확정 사항으로 정정했다. 그래도 남는 미확정 항목은 11절에 모았다. 루트 `docs/`의 명세와 ERD는 수정하지 않는다.

## 2. 작업 범위

명세상 7개 API이며 모두 인증이 필요하다. 앞의 5개는 조회, 뒤의 2개는 쓰기다.

| # | Method | 경로 | 명세 입력 |
| --- | --- | --- | --- |
| 1 | GET | `/api/v1/loans/minus-accounts` | `status?` |
| 2 | GET | `/api/v1/loans/minus-accounts/{accountNo}` | `accountNo` |
| 3 | GET | `/api/v1/loans/minus-accounts/{accountNo}/interest` | `accountNo` |
| 4 | GET | `/api/v1/loans/minus-accounts/{accountNo}/transactions` | `startDate?`, `endDate?`, `page?`, `size?` |
| 5 | GET | `/api/v1/loans/minus-accounts/{accountNo}/alerts` | `unreadOnly?`, `page?`, `size?` |
| 6 | POST | `/api/v1/loans/minus-accounts/{accountNo}/repayments` | Body `amount`, `repayAccountNo?` |
| 7 | POST | `/api/v1/loans/minus-accounts/{accountNo}/interest-payments` | Body `amount?`, `paymentAccountNo?` |

경로가 `/api/v1/loans` 아래지만 저장 기준은 `loan`이 아니라 `account` + `minus_account`다. 기존 Loan 도메인 코드와 테이블을 공유하지 않는다.

명세의 데이터/연동 칸은 7개 모두 `SSAFY`이지만, 현재 `SsafyFinanceClient`는 `createMember`·`searchMember`만 제공한다. 531~533과 동일하게 **로컬 DB 기준으로 구현**하고, 외부 호출 계약이 확정되면 `infrastructure/external/ssafy` 경계에서 교체한다. 명세의 "호출별 거래고유번호 신규 생성"은 외부 연동 시점의 요구이므로 이번 범위에서 구현하지 않고 제한사항으로 기록한다.

기획안 11-3 표는 SSAFY 금융 API의 활용 목적을 "계좌 및 잔액 조회", "이체내역", "대출 보유정보" 같은 **조회·분석**으로만 기재하고 연동 계획은 "연동 예정", 대체 방법은 "더미데이터"다. 외부 상환 실행 계약은 어느 문서에도 없다. 이것이 8절에서 쓰기 API를 로컬 상태 전이로 확정한 근거 중 하나다.

외부 응답을 도입할 때는 `ERD 작성 ...md`의 "SSAFY 금융망·금융감독원·카드사 응답의 상태값은 원문을 그대로 저장하지 않고 Adapter에서 위 내부 코드로 정규화한다" 규칙을 따른다.

## 3. 데이터·스키마 확인 결과

seed SQL과 ERD, 기존 코드를 직접 확인한 결과다. **MySQL에 적재해 실행한 값이 아니라 정적 분석이며 구현 후 실제 조회로 재확인한다.**

### 테이블 보유 현황

| 테이블 | 마이그레이션 | 소관 | 534에 준 영향 |
| --- | --- | --- | --- |
| `account` | `V3` 있음 | BE-A(501). 스키마만 선반영됨 | 그대로 사용. 재생성하지 않는다 |
| `minus_account` | **없음** | 534 | `V14`로 신규 작성 (5절) |
| `transaction` | **없음** | BE-A | 읽기만 한다. 카드 선례대로 JDBC 조회 (6절) |
| `notification` | **없음** | S15P21D105-458 `[D9] Notification 기본 API 및 BE-A 통합`(담당 구동재, 미착수) | 읽기만 한다. 테이블을 만들지 않는다 (7절) |

`minus_account`는 `account`를 복합 외래키 `(user_id, account_id, account_type)`로 참조하고 `V3`가 `uk_account_user_id_type`을 제공하므로 `V14`의 선행 조건은 충족된다. 마이그레이션 README의 무결성 규칙대로 마이너스통장은 `account`에 `MINUS`로 만든 뒤 같은 `account_id`로 상세행을 만든다.

### ERD 제약 (계산·검증에 직접 영향)

- `chk_minus_account_available_amount`: `available_amount = limit_amount - used_amount`. 파생 값이 저장되어 있으므로 상환 시 세 컬럼을 한 UPDATE에서 함께 갱신해야 한다. **이는 도출이 아니라 `ERD 작성 ...md`의 명시 정책이다:** "`available_amount`처럼 파생값을 저장하는 경우 원천값과 같은 트랜잭션에서 갱신하고 CHECK 제약을 만족해야 한다".
- `chk_minus_account_used_amount`: `0 <= used_amount <= limit_amount`.
- `chk_account_balance_by_type`: `account_type = 'MINUS'`이면 `balance <= 0`. seed도 `account.balance = -used_amount`로 맞춰 두었다. **`ERD 작성 ...md` 확정 정책이다:** "`MINUS`의 `account.balance`와 `minus_account.used_amount`는 같은 트랜잭션에서 `balance = -used_amount`가 되도록 갱신한다".
- `account_type`은 하위행 생성 후 직접 변경하지 않는다(ERD 문서 주요 코드값 절). 계좌 유형 전환은 534 범위 밖이다.
- `chk_minus_account_accrued_interest`: `accrued_interest >= 0`. 이자 납부액이 잔여 이자를 넘을 수 없다.
- `chk_minus_account_interest_rate`: `interest_rate >= 0`이고 NOT NULL. 금리 무효는 MySQL에서 발생하지 않는다.
- `next_interest_date`, `maturity_date`는 NULL 허용. 응답에서 nullable로 다룬다.
- `minus_account`에는 상태 컬럼이 없고, `ERD 작성 ...md` 주요 코드값 표가 "`account.status` | `ACTIVE`, `INACTIVE`, `CLOSED`; MinusAccount·DepositSaving도 Account 상태를 사용"으로 확정한다. 명세의 `status?`는 이 값으로 해석한다 (6절).
- `minus_account`에는 출금계좌 컬럼이 없다. `card`/`loan`/`deposit_saving`과 달리 `withdrawal_account_id`가 없으므로 상환 출금계좌의 저장된 기본값이 존재하지 않는다 (8절).
- `transaction.amount`는 `chk_transaction_amount`로 항상 양수이고 방향은 `transaction_type`(`INCOME`/`EXPENSE`/`TRANSFER`)이 결정한다. 마이너스통장 인출·상환의 부호는 응답에서 따로 표현해야 한다.
- `notification`은 `source_type`, `source_id`, `is_read`, `deleted_at`을 가지며 `uk_notification_user_dedupe`로 중복을 막는다.

### 시연 데이터

`DODAM_LOAN_MINUS_ACCOUNT_SEED.sql`은 4명 중 1명(`MOD(sequence_no, 4) = 0`)에게 마이너스통장 1개씩 총 **1,000행**을 만든다. 변형은 4종이며 계좌명은 모두 `도담 마이너스통장`이다.

| 변형 | 한도 | 사용액 | 가용액 | 금리 | 미수이자 | 연체일 | 검증 용도 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A | 5,000,000 | 0 | 5,000,000 | 6.5000% | 0 | 0 | 미사용 한도, 이자 0원 경계 |
| B | 10,000,000 | 3,500,000 | 6,500,000 | 7.2000% | 21,000 | 0 | 일반 사용 |
| C | 20,000,000 | 19,400,000 | 600,000 | 8.1000% | 148,000 | 0 | 한도 소진 임박 |
| D | 30,000,000 | 12,000,000 | 18,000,000 | 9.4000% | 96,000 | 17 | 연체 |

`next_interest_date`는 전부 `2026-09-25`, `maturity_date`는 전부 `2027-06-30`이다. 이자 계산은 기준일 상수를 코드에 고정하지 않고 `LoanAnalysisClock`과 같은 방식의 주입 가능한 기준일을 쓴다.

**2026-09-25 이후의 시연 리스크.** 그날이 지나면 1,000개 계좌의 `next_interest_date`가 전부 과거가 되어 6절 정의대로라면 `daysToNextInterestDate`와 `estimatedInterestUntilNextDate`가 **모두 `null`이 된다.** 시연 시점이 그 이후면 이자 API가 사실상 빈 예측만 반환한다. seed 날짜 갱신이나 응답 정의 보완이 필요하며, 이는 534 코드가 아니라 seed(523) 쪽 문제이므로 발견 사항으로 보고한다.

**미수이자는 일할 계산으로 재현되지 않는다.** seed의 `accrued_interest`를 `used_amount × rate ÷ 365`로 역산하면 B 30.4일, C 34.4일, D 31.1일치(360일 기준으로는 30.0/33.9/30.6일)로 흩어진다. 특정 일수·기산 기준이 나오지 않으므로 **저장된 미수이자와 계산된 예상이자를 같은 값으로 취급하지 않는다.** 6절이 둘을 분리해 반환하는 이유이고, 테스트에서 `accruedInterest == estimatedDailyInterest × 일수`를 단정하지 않는다.

**적재 선행 조건이 있다.** 이 seed는 애플리케이션이 만든 임시 테이블 3개(`tmp_dodam_523_account_keys`, `tmp_dodam_523_loan_crypto`, `tmp_dodam_523_minus_crypto`)를 같은 MySQL 세션에 요구한다. 암호화 픽스처 생성기가 없으면 적재할 수 없고, 이는 `remaining-work.md`에 이미 기록된 530·532의 공통 미해결 항목과 같다.

**마이너스통장 거래·알림 시연 데이터는 없다.** `backend/database/seeds/`에 transaction seed 파일 자체가 없고(492는 미병합), 알림 seed도 없다. 따라서 4·5번 API는 구현하더라도 시연 데이터로는 빈 결과만 확인된다.

### 재사용 가능한 기존 자산

| 자산 | 위치 | 용도 |
| --- | --- | --- |
| `AuthenticatedUserIdResolver` | global/security | 인증 사용자 ID |
| `SensitiveNumberHasher` | global/security | `accountNo` HMAC 조회 |
| `QueryPeriodPolicy` | global/policy | 4번 거래내역 기간 (기본 3개월, 최대 12개월) |
| `ApiResponse`, `PageResponse` | global/response | 공통 응답과 페이징 |
| `InvalidRequestException`, `ResourceNotFoundException` | global/exception | 400·404 오류 |
| `BusinessException(ErrorCode)` | global/exception | **409 등 상태 코드가 붙은 오류.** `InvalidRequestException`은 메시지만 받고 400으로 고정되므로 8절의 새 코드는 이쪽으로 던진다 |
| `CardWithdrawalAccount` | card/entity | `account` **읽기 전용**(`@Immutable`) 매핑의 선례. 필요한 컬럼만 노출하고 원문·암호문은 매핑하지 않는다. 8절 쓰기에는 그대로 쓸 수 없다(9절) |
| `JdbcCardTransactionReader` | card/repository | `transaction` JDBC 조회와 페이징 구성의 선례 |
| `CardPaymentSettingsService` | card/service | 쓰기 API(`@Transactional`)의 컨트롤러·서비스 구성 선례 |
| `LoanQueryController`의 `ACCOUNT_NO_PATTERN` | loan/controller | 경로 변수 검증 형식 |

## 4. 인접 API 경계

534가 만들지 않는 값은 다른 영역이 소유한다.

| 소유 API·이슈 | 534에 주는 의미 |
| --- | --- |
| `5 Account` (S15P21D105-501, BE-A, Day 4) | **명세상 겹친다.** 아래에 따로 정리한다 |
| `22 Notification` (S15P21D105-458, BE-A, Day 9~10) | 알림 생성·읽음 처리·삭제는 이 영역이 소유한다. 534의 `alerts`는 **읽기 전용 필터 뷰**만 제공한다 |
| `6 Transaction Spending` (BE-A, Day 5) | 거래 원장 적재와 카테고리 분류는 이 영역이 소유한다. `POST /api/v1/transactions`(수동 거래 등록)가 유일한 거래 생성 경로다. 534는 읽기만 하고, 상환 시 기록이 필요한지는 8절 미결 사항이다 |
| `4 Finance Data`의 `POST /api/v1/finance-data/sync` (BE-A, Day 3) | SSAFY 기준 갱신이 로컬 상환분을 덮어쓸 수 있다. 8절의 미결 사항이며 534에서 동기화 동작을 바꾸지 않는다 |
| `13 Dashboards`의 `GET /api/v1/dashboard/debts` (부채 요약, 김지우 Day 9) | **마이너스통장의 부채 집계는 이 API가 소유한다.** `ERD 작성 ...md`가 "총자산 계산에서는 `DEMAND_DEPOSIT`, `DEPOSIT`, `SAVINGS` 잔액만 합산하고 `MINUS`는 부채로 집계한다"로 확정했다 |
| `10 Loan` (S15P21D105-531~533) | `loan` 테이블 기반이며 마이너스통장은 `loan`에 없다. 위 항목에 따라 부채 집계 소비자는 대시보드이므로 **534는 기존 Loan 요약·부담률 응답을 바꾸지 않는다** |
| `12 Goal` | `goal_account`는 CHECK로 `MINUS` 연결을 DB 단계에서 차단한다(ERD 소유권 무결성 절). 목표 연계는 534 범위 밖이다 |

### API 5와의 중복

`5 Account`는 `accountType` 필터가 있는 `GET /api/v1/accounts`, `GET /api/v1/accounts/{accountNo}`, `.../balance`, `.../transactions`를 제공하고 `account.account_type`에 `MINUS`가 포함되므로 **같은 계좌를 이미 조회할 수 있다.** 특히 거래내역은 경로만 다르고 입력(`startDate?`, `endDate?`, `page?`, `size?`)과 제한사항 문구까지 동일하다.

두 명세 모두 승인된 문서이므로 임의로 한쪽을 지우지 않고 10-1의 7개를 그대로 구현한다. 대신 다음을 지킨다.

- 거래내역 응답 필드·정렬·기간 정책을 API 5와 어긋나게 두지 않는다. 501 구현이 나오면 대조한다.
- 534의 목록·상세는 계좌 일반 정보를 다시 정의하지 않고 **한도·사용액·가용액·금리·미수이자·연체** 관점을 더하는 데 집중한다.
- 이 중복 자체를 사용자와 BE-A에 보고한다. 응답 스키마를 두 곳에서 따로 관리하면 갈라진다.

## 5. 마이그레이션 (V14)

- 파일: `backend/src/main/resources/db/migration/V14__create_minus_account.sql`
- 번호 근거: README의 BE-B 구간(V10~)이고 마지막 사용 번호는 `V13`이다.
- 내용: `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`의 `minus_account` 정의를 그대로 옮긴다. CHECK 7개와 `fk_minus_account_account`를 포함하고, 컬럼 타입은 `ddl-auto: validate`가 통과하도록 엔티티와 맞춘다(529에서 `card`의 `CHAR` 불일치로 `validate`가 실패한 선례가 있으므로 `DECIMAL(10,4)`와 `VARCHAR(30)`을 명시한다).
- README 표에 `V14 | minus_account 생성 | S15P21D105-534` 행을 추가한다.
- `transaction`, `notification`은 **만들지 않는다.** 각각 BE-A와 458 소관이며, `V3` 선반영은 develop 기동 불가라는 예외 상황의 조치였고 이번에는 그런 사유가 없다.

## 6. 조회 API 계약 초안

### 공통

- `{accountNo}`는 `^[0-9]{10,20}$`. `SensitiveNumberHasher`로 HMAC 처리한 뒤 `account.account_no_hash`로 조회하며, 조건에 `user_id`와 `account_type = 'MINUS'`를 함께 건다.
- 타인 계좌, 존재하지 않는 계좌, `MINUS`가 아닌 계좌는 모두 `RESOURCE_NOT_FOUND`로 처리해 존재 여부를 노출하지 않는다.
- 응답에는 `accountNoMasked`만 포함한다. 원문·암호문·해시를 노출하거나 로그에 남기지 않는다.
- 금액은 `long`, 금리와 비율은 `BigDecimal`로 다룬다.
- 조회 Service는 `@Transactional(readOnly = true)`를 쓴다.

### 1. 목록 `GET /api/v1/loans/minus-accounts`

- `status?`: `account.status`(`ACTIVE`/`INACTIVE`/`CLOSED`)로 해석한다. 대소문자를 가리지 않고 생략하면 전체를 조회한다. **제안이 아니라 `ERD 작성 ...md` 주요 코드값 표의 확정 사항이다**("MinusAccount·DepositSaving도 Account 상태를 사용").
- 응답: `accountId`, `bankCode`, `bankName`, `accountName`, `accountNoMasked`, `status`, `limitAmount`, `usedAmount`, `availableAmount`, `usageRate`, `interestRate`, `accruedInterest`, `nextInterestDate`, `maturityDate`, `overdueDays`, `overdue`.
- `usageRate`는 **백분율이 아니라 비율**이다. `usedAmount / limitAmount`를 소수점 4자리 반올림한 `BigDecimal`로, 변형 C는 `0.9700`이다. `interest_rate`가 `8.1000`처럼 퍼센트 값으로 저장되어 있어 헷갈리기 쉬우므로 필드 주석과 응답 문서에 단위를 명시한다. `chk_minus_account_limit_amount`가 `limit_amount > 0`을 보장하므로 0 나눗셈은 발생하지 않는다.
- `overdue`는 `overdueDays > 0`이다. 별도 연체 상태 컬럼이 없으므로 이 파생값이 유일한 판정 근거다.
- 페이징 없음(명세에 `page`·`size`가 없다). 정렬은 `used_amount DESC, account_id ASC`를 제안한다.

### 2. 상세 `GET .../{accountNo}`

목록 필드에 `currency`, `lastTransactionDate`, `createdAt`, `updatedAt`을 더한다.

### 3. 이자 `GET .../{accountNo}/interest`

저장값과 계산값을 분리해 반환한다.

| 필드 | 산출 |
| --- | --- |
| `interestRate` | `minus_account.interest_rate` |
| `usedAmount` | `minus_account.used_amount` |
| `accruedInterest` | `minus_account.accrued_interest` (저장값 그대로) |
| `nextInterestDate` | `minus_account.next_interest_date` |
| `daysToNextInterestDate` | 기준일부터 `next_interest_date`까지의 일수. 과거이거나 NULL이면 `null` |
| `estimatedDailyInterest` | `usedAmount × interestRate ÷ 100 ÷ 365`, 원 단위 절사 |
| `estimatedInterestUntilNextDate` | `estimatedDailyInterest × daysToNextInterestDate`. **`daysToNextInterestDate`가 `null`이면 이 값도 `null`이다** |
| `overdueDays`, `overdue` | 연체 표시 |

- **일할 계산 기준은 명세·기획안에 없어 제안이다.** 관행적인 `actual/365`를 쓰고, `BigDecimal`로 계산한 뒤 마지막에 원 단위로 절사한다. 부동소수점은 쓰지 않는다.
- 3절에 적은 대로 seed의 `accrued_interest`는 이 식으로 재현되지 않는다. `accruedInterest`는 저장값, `estimated*`는 계산값으로 **서로 검증하지 않는다.**
- 연체 가산금리 계약이 없으므로 연체 시에도 같은 금리로 계산하고, 연체이자 미반영을 응답 문서와 주석에 명시한다.
- `estimated*`는 예상치이며 실제 청구액이 아님을 문서에 남긴다.

### 4. 거래내역 `GET .../{accountNo}/transactions`

- `QueryPeriodPolicy`를 그대로 쓴다(기본 3개월, 최대 12개월, 시작일 ≤ 종료일, 종료일 ≤ 오늘).
- `page` 기본 0, `size` 기본 20 (1~100).
- `transaction`을 `user_id = :userId AND account_id = :accountId AND hidden = FALSE AND transaction_date BETWEEN :startDate AND :endDate`로 조회한다. `idx_transaction_user_account`가 있다.
- 정렬은 `transaction_date DESC, transaction_time DESC, transaction_id DESC` (카드와 동일).
- 응답: `transactionId`, `transactionDate`, `transactionTime`, `transactionType`, `amount`, `balanceAfter`, `merchantName`, `summary`, `memo`, `categoryCode`, `categoryName`.
- 조회는 `JdbcMinusAccountTransactionReader`로 구현한다. `Transaction` JPA 엔티티를 만들지 않는 것은 BE-A 소관 테이블을 선점하지 않기 위해서이며 카드 도메인의 선례와 같다.
- **`balance_after`는 마이너스통장에서 음수이거나 NULL일 수 있다.** 부호를 임의로 뒤집지 않고 저장값 그대로 전달한다.

### 5. 알림 `GET .../{accountNo}/alerts`

7절 참조.

## 7. 알림 엔드포인트 처리 방안

`notification` 테이블은 458 소관이며 마이그레이션도 도메인 코드도 없다. `dev_plan.md`상 `22 Notification`은 Day 9~10이고 534는 Day 4이므로 **일정상 알림 스키마가 늦게 온다.** 534 완료를 458에 묶으면 5일 이상 대기한다. 세 가지 선택지를 검토했다.

| 안 | 내용 | 평가 |
| --- | --- | --- |
| A | 458 완료까지 미구현으로 남긴다 | 정확하지만 7개 중 1개가 비고, 534 완료가 남의 이슈에 묶인다 |
| B | `minus_account` 상태에서 알림을 즉석 생성한다(한도 임박·연체·이자일 임박) | 저장이 없어 `unreadOnly`가 의미를 잃고, 458이 만드는 실제 알림과 이중 원천이 된다 |
| C | `notification`을 읽기 전용으로 필터 조회한다 | 명세의 `unreadOnly`가 그대로 성립하고 원천이 하나로 유지된다 |

**C를 제안한다.** 구현은 다음과 같다.

- 조회 조건: `user_id = :userId AND source_type = 'MINUS_ACCOUNT' AND source_id = :accountId AND deleted_at IS NULL`. `unreadOnly=true`면 `is_read = FALSE`를 더한다. `deleted_at` 제외는 `ERD 작성 ...md`의 "Notification은 `deleted_at` 기반 소프트 삭제를 사용한다", "소프트 삭제된 데이터는 일반 조회에서 제외한다"에 따른다.
- 정렬 `created_at DESC, notification_id DESC`, `page`/`size`는 4번과 같은 정책.
- 응답: `notificationId`, `type`, `title`, `message`, `isRead`, `createdAt`, `readAt`.
- 읽음 처리·삭제는 제공하지 않는다. 명세상 `PATCH /api/v1/notifications/{notificationId}/read`와 `DELETE /api/v1/notifications/{notificationId}`가 소유한다.
- 테이블도 알림 생성 배치도 만들지 않는다.

**미확정으로 남는 것.** `source_type` 값 `MINUS_ACCOUNT`와 `source_id`에 `account_id`를 넣는 규약은 458과 합의해야 한다. 합의 전까지 이 엔드포인트는 스키마 부재로 MySQL 검증이 불가능하고 항상 빈 페이지를 반환한다. 이 상태를 "구현 완료"로 보고하지 않고 `remaining-work.md`에 미검증 항목으로 기록한다.

## 8. 쓰기 API 계약 초안

**금융 잔액을 바꾸는** 첫 쓰기 경로다. 프로젝트에 쓰기 API 자체가 처음인 것은 아니다. `8 Card`의 `PATCH /api/v1/cards/{cardNo}/payment-settings`가 이미 `CardPaymentSettingsService`로 구현되어 있으므로 컨트롤러·서비스의 쓰기 구성은 그 선례를 따른다. 다만 그것은 결제 설정 변경이고, 여기서는 두 테이블(`minus_account`, `account`)의 금액이 한 트랜잭션에서 정합해야 한다.

### 성격: 시연용 로컬 상태 전이 (2026-09-09 사용자 결정)

두 POST는 **우리 DB의 `minus_account`와 `account` 값을 바꾸는 로컬 상태 전이**로 구현한다. SSAFY 금융망에 상환을 실행 요청하지 않는다. 사용자 결정 사항이며 근거는 다음과 같다.

- **시연 데이터가 SSAFY에 없는 계좌다.** seed는 마이너스통장을 `source_type = 'SEED'`, 은행 `SEED_BANK_A`~`D`(도담테스트은행 A~D)로 만든다. 금융망에 등록된 계좌가 아니므로 외부 실행 방식은 명세 제한사항의 "사전 생성된 시연 데이터 범위 내"와 양립하지 않는다.
- **명세 전체에서 잔액을 바꾸는 엔드포인트가 이 둘뿐이다.** `docs/API명세서` 일반 CSV 22개를 CSV 파서로 읽어 `POST`/`PUT`/`PATCH`/`DELETE` **61건**을 전수 확인했다. 나머지는 인증, 사용자 설정, 목표 CRUD, 진단·추천·시뮬레이션 생성, 상품 비교·동기화, AI 코치, 알림 읽음·삭제, 거래 메모·카테고리·수동 거래 CRUD, 정기결제 상태 변경, 카드 결제 설정 변경이다. 대출 상환조차 `17 Simulation`의 `POST /api/v1/simulations/loan/prepayment`라는 **계산 전용** API로만 있고, 예적금 납입(`11-2`)과 할부(`9`)에는 쓰기 API가 없다.
- 기획안 11-3 표가 SSAFY 연동을 조회 목적으로만 기재하고, 현재 `SsafyFinanceClient`에도 상환 관련 메서드가 없다.
- 외부 실행 방식은 외부 성공·로컬 실패 시 보상 트랜잭션과 멱등키 저장소가 선행 조건이 되어 Day 4 범위를 넘는다.

**대신 다음을 지킨다.** 상환 로직을 Service에 두되 외부 호출 지점이 생길 자리를 인터페이스로 분리해, SSAFY 상환 계약이 확정되면 `infrastructure/external/ssafy` 경계에서 교체할 수 있게 한다.

**충족하지 못하는 명세 항목:** 데이터/연동 칸의 `SSAFY`와 "호출별 거래고유번호 신규 생성". 미충족으로 문서에 남기고 완료로 표시하지 않는다.

### finance-data sync와의 충돌 (미결)

`4 Finance Data`의 `POST /api/v1/finance-data/sync`(scope `ALL`/`ACCOUNT`)가 SSAFY 기준으로 계좌를 갱신하면 **로컬 상환분이 덮어써져 사라질 수 있다.** 로컬 상태 전이를 택한 이상 이 위험은 구조적이다.

- 필요한 합의: `source_type = 'SEED'`인 계좌는 동기화 대상에서 제외한다.
- 담당: 동기화는 `4 Finance Data`(BE-A, Day 3) 소관이므로 534에서 단독으로 정할 수 없다.
- 확인 전까지: 534는 `source_type`을 바꾸지 않고, 상환이 `SEED` 계좌에만 적용되는 시연 동작임을 문서에 명시한다. 동기화 제외 로직을 534에서 구현하지 않는다.

### `account` 쓰기 매핑

상환은 `account.balance`(마이너스통장 본인과 출금계좌 양쪽)를 **UPDATE 해야 한다.** 그런데 `account`는 BE-A 소관이고, 카드가 만든 `CardWithdrawalAccount`는 `@Immutable` 읽기 전용이라 그대로 쓸 수 없다. 같은 테이블에 쓰기 가능한 엔티티를 하나 더 매핑하면 501 구현과 이중 매핑이 되어 위험하다.

**제안:** `account` 잔액 갱신은 JPA 엔티티를 새로 만들지 않고 도메인 전용 JDBC writer(`JdbcAccountBalanceWriter` 성격)로 처리한다. `transaction`을 JDBC로만 읽는 것과 같은 이유이며, 501이 `Account` 엔티티를 만들면 그 시점에 교체한다. 조건절에 `user_id`와 `account_type`을 포함해 소유권과 유형을 SQL 단계에서 강제하고, 갱신 행 수가 1이 아니면 롤백한다.

이 선택은 9절 구조에 반영했다. 501 담당자에게 알려야 하는 사항이다.

### 공통 처리

1. 인증 사용자 확인 후 `accountNo`로 대상 마이너스통장을 조회한다. 없으면 `RESOURCE_NOT_FOUND`.
2. `account.status`가 `ACTIVE`가 아니면 처리하지 않는다.
3. 대상 행을 `SELECT ... FOR UPDATE`로 잠근다. `available_amount`가 파생 저장값이라 읽고-쓰는 사이의 경쟁 조건을 막아야 한다.
4. 출금계좌도 함께 잠그고 잔액을 차감한다. 두 계좌를 잠그므로 **`account_id` 오름차순 고정 순서**로 잠가 교착을 피한다.
5. 쓰기 `@Transactional`로 묶는다.

### 6. 원금 상환 `POST .../repayments`

- Body: `amount`(필수, `@Positive`), `repayAccountNo?`.
- `repayAccountNo` 생략 시 기본 출금계좌 규칙이 명세에 없고, `minus_account`에는 `card`/`loan`/`deposit_saving`과 달리 출금계좌 컬럼도 없어 저장된 기본값이 존재하지 않는다. **본인의 `DEMAND_DEPOSIT` 계좌가 정확히 1개일 때만 그것을 쓰고, 0개거나 2개 이상이면 `repayAccountNo`를 요구**하는 안을 제안한다. 임의 선택은 하지 않는다. 출금계좌를 `DEMAND_DEPOSIT`으로 한정하는 것은 ERD 문서의 "Card·Loan·DepositSaving의 출금계좌는 유형을 포함한 복합 FK로 `DEMAND_DEPOSIT`만 허용한다"와 같은 기준이다.
- 검증: `amount <= used_amount`(초과 상환 불가), 출금계좌가 본인·`ACTIVE`·`DEMAND_DEPOSIT`이고 `balance >= amount`.
- 반영: `used_amount -= amount`, `available_amount += amount`, `account.balance = -used_amount`, 출금계좌 `balance -= amount`, 양쪽 `updated_at` 갱신.
- 원금 상환은 원금만 줄이고 `accrued_interest`는 건드리지 않는다. 연체 상태의 이자 우선 충당이 필요하면 별도 결정 사항이다.
- `overdue_days`는 이 API에서 초기화하지 않는다. 연체 해소 판정 기준이 없다.
- 응답: `accountNoMasked`, `repaidAmount`, `usedAmount`, `availableAmount`, `limitAmount`, `repayAccountNoMasked`, `repayAccountBalance`, `processedAt`.

### 7. 이자 납부 `POST .../interest-payments`

- Body: `amount?`, `paymentAccountNo?`. `amount` 생략 시 **잔여 미수이자 전액**을 납부한다(명세가 `amount`를 선택 입력으로 둔 이유로 해석한다).
- 검증: `amount <= accrued_interest`(`chk_minus_account_accrued_interest` 위반 방지), `accrued_interest = 0`이면 납부할 이자가 없다는 오류.
- 반영: `accrued_interest -= amount`, 출금계좌 `balance -= amount`. **`used_amount`는 바꾸지 않는다.**
- `next_interest_date`는 갱신하지 않는다. 다음 이자일 산정 규칙(월 단위인지 개설일 기준인지)이 명세에 없다.
- 응답: `accountNoMasked`, `paidInterest`, `accruedInterest`, `paymentAccountNoMasked`, `paymentAccountBalance`, `processedAt`.

### 중복 요청 방지

명세의 "중복 요청 방지"를 만족할 저장소가 없다. 검토 결과는 다음과 같다.

- 행 잠금은 **동시성**만 보장하고 재전송 멱등성은 보장하지 않는다.
- `(user, account, amount)` 시간창 중복 차단은 정상적인 연속 상환을 막아 채택하지 않는다.
- 신뢰할 수 있는 방법은 `Idempotency-Key` 헤더와 처리 결과 저장(테이블 또는 Redis)이다. Redis는 이미 프로젝트에 있으나 키 TTL과 응답 재생 계약이 정해지지 않았다.

**제안:** 1차에서는 행 잠금으로 동시성만 보장하고, 멱등키 저장 방식은 사용자 결정 후 추가한다. 미구현 상태를 문서와 `remaining-work.md`에 명시하고 "중복 요청 방지 완료"로 보고하지 않는다.

### transaction 기록 여부 (미결)

상환·이자납부를 `transaction`에 남기지 않으면 4번 거래내역이 방금 실행한 상환을 보여주지 않는다. 남기려면 `transaction_id`(VARCHAR(100)) 생성 규칙, `category_id`, `source_type` 값을 BE-A와 합의해야 한다.

명세에서 거래를 만드는 유일한 경로는 `6 Transaction Spending`의 `POST /api/v1/transactions`인데, **그 Body(`transactionDate`, `transactionType`, `amount`, `merchantName?`, `categoryId?`, `paymentMethod?`, `memo?`)에는 계좌 지정 필드가 없다.** 즉 거래를 특정 계좌에 붙이는 계약이 명세 어디에도 정의되어 있지 않다. 시스템이 만드는 거래의 규약은 더더욱 없다.

**합의 전에 임의 규칙으로 남의 원장에 쓰지 않는다.** 1차 구현은 기록하지 않고 이 불일치를 응답 문서의 제한사항으로 명시한다. 기록하기로 정하면 `account.last_transaction_date`도 함께 갱신해야 한다(ERD 문서 계산 기준 절).

### 필요한 ErrorCode

현재 `ErrorCode`에는 상태 위반을 표현할 코드가 없다. 다음 추가를 제안한다. `ErrorCode`는 전역 공유 파일이므로 추가만 하고 기존 항목은 건드리지 않는다. 던질 때는 `BusinessException(ErrorCode)`를 쓴다. `InvalidRequestException`은 메시지만 받아 400으로 고정되므로 409를 표현할 수 없다.

| 코드 | 상태 | 용도 |
| --- | --- | --- |
| `MINUS_ACCOUNT_NOT_ACTIVE` | 409 | 해지·휴면 계좌 처리 시도 |
| `REPAYMENT_AMOUNT_EXCEEDED` | 400 | 사용액·미수이자를 초과하는 금액 |
| `INSUFFICIENT_ACCOUNT_BALANCE` | 409 | 출금계좌 잔액 부족 |
| `WITHDRAWAL_ACCOUNT_REQUIRED` | 400 | 기본 출금계좌를 특정할 수 없음 |

## 9. 구조와 구현 순서

패키지는 `domain/minusaccount/`로 새로 만든다. 경로만 `/api/v1/loans` 아래일 뿐 저장 기준이 `loan`과 다르므로 Loan 패키지에 섞지 않는다.

```
domain/minusaccount/
  controller/  MinusAccountQueryController, MinusAccountCommandController
  service/     MinusAccountQueryService, MinusAccountCommandService, MinusAccountInterestCalculator
  repository/  MinusAccountRepository(JPA)
               MinusAccountTransactionReader / JdbcMinusAccountTransactionReader
               MinusAccountAlertReader / JdbcMinusAccountAlertReader
               AccountBalanceWriter / JdbcAccountBalanceWriter   ← 8절 account 쓰기
  entity/      MinusAccount, MinusAccountOwner(account 읽기 전용 매핑)
  dto/         request/, response/
```

`MinusAccountOwner`는 조회 응답용 읽기 전용 매핑이고, 잔액 갱신은 `AccountBalanceWriter`가 맡는다. 하나의 클래스로 겹치지 않게 두는 이유는 8절 `account` 쓰기 매핑 항목에 있다.

| 단계 | 내용 | 완료 조건 |
| --- | --- | --- |
| 1 | `V14` 마이그레이션과 README 갱신 | ERD 정의와 일치. 로컬 MySQL에서 `V1~V3`, `V10~V14` 순차 적용 성공 |
| 2 | `MinusAccount` 엔티티와 `account` 읽기 매핑, `accountNo` 해시 조회 | `ddl-auto: validate` 통과. 소유권·`MINUS` 조건 포함 |
| 3 | 1·2번 API(목록·상세) | 정상·빈 결과·타인 계좌 404 |
| 4 | 3번 API(이자)와 계산기 | 사용액 0원, 이자 0원, `next_interest_date` NULL·과거 경계 |
| 5 | 4번 API(거래내역) | 기간 정책, 페이징 경계, 빈 결과 |
| 6 | 5번 API(알림) | 7절 계약. 스키마 부재 제한 문서화 |
| 7 | 6·7번 API(상환·이자납부) | 8절 검증과 트랜잭션 정합 |
| 8 | 구현 문서 `minus-account.md` 작성, `remaining-work.md`에 534 항목 추가 | 미검증 범위와 미결 사항 기록 |

3~6단계는 서로 독립이라 순서를 바꿔도 된다. 7단계는 2단계에 의존한다.

## 10. 검증 계획

### 테스트

`MinusAccountApiIntegrationTest`를 기존 통합 테스트 방식(H2, `@SpringBootTest` + `MockMvc`)으로 작성한다. `transaction`과 `notification`은 마이그레이션이 없으므로 `card-query-test-data.sql`처럼 테스트 전용 스키마 SQL로 만든다. 이 파일이 MySQL 마이그레이션을 대체하지 않는다는 주석을 단다.

**H2에는 CHECK 제약이 생기지 않는다.** 테스트 프로파일은 `ddl-auto: create-drop`으로 엔티티 기준 스키마를 만들고 Flyway는 꺼져 있다. 엔티티에 CHECK를 선언하지 않는 한 `available_amount = limit_amount - used_amount`나 `balance <= 0`을 DB가 막아주지 않는다. 따라서 아래의 정합성 단정은 **애플리케이션 계산이 맞는지 확인하는 것이지 제약 위반을 잡는 것이 아니다.** 제약 자체의 검증은 MySQL 확인 항목에서 한다. 반대로 말하면 **로컬·운영의 CHECK 위반을 막는 유일한 방어선은 8절의 애플리케이션 검증**이므로 그쪽을 빠짐없이 구현한다.

- 인증 없음 401, 타인 계좌·비 `MINUS` 계좌 404
- 목록: 정상, 빈 결과, `status` 유효·무효 값, 정렬
- 상세·이자: 3절 변형 A~D에 대응하는 픽스처. `usageRate` 0%·97%, 미수이자 0원, `next_interest_date` NULL·과거
- 거래내역: 기본 3개월, 12개월 초과 400, 시작일 > 종료일 400, `page`·`size` 경계(0, 1, 100, 101), 빈 결과
- 알림: `unreadOnly` true·false, 삭제된 알림 제외, 빈 결과
- 상환: 정상, 전액 상환, 초과 400, 0·음수 400, **사용액 0원 계좌(변형 A) 상환 시도**, 출금계좌 잔액 부족, 출금계좌 미지정·다건, 출금계좌가 `DEMAND_DEPOSIT`이 아닌 경우(마이너스통장 자기 자신 지정 포함), 비 `ACTIVE` 계좌. 상환 후 `used_amount + available_amount = limit_amount`와 `account.balance = -used_amount`, 출금계좌 잔액 차감을 함께 검증한다
- 이자 납부: 정상, `amount` 생략 시 전액, 초과 400, 미수이자 0원, 납부 후 `used_amount`가 그대로인지 확인
- 이자: `accruedInterest`(저장값)와 `estimated*`(계산값)를 서로 단정하지 않는다. 3절 참조

명령은 `backend/`에서 `.\gradlew.bat test`를 관련 테스트 먼저, 이어서 전체로 실행한다.

### MySQL 확인

530에서 쓴 방식대로 일회용 MySQL 8.0 컨테이너에 `V1~V3`, `V10~V14`를 적용해 `minus_account`의 CHECK 7개와 외래키 생성, 앱의 `validate` 통과를 확인한다. **H2 통과를 MySQL 검증으로 대신하지 않는다.** 데이터 기준 동작은 3절의 seed 적재 선행 조건(암호화 픽스처)이 해결되기 전까지 확인할 수 없으며 이를 미검증으로 기록한다.

## 11. 결정 현황과 완료 기준

### 사용자 결정이 필요한 항목

| # | 항목 | 이 문서의 제안 | 상태 |
| --- | --- | --- | --- |
| 1 | 쓰기 API 2개의 성격 | 시연용 로컬 상태 전이 | **확정** (2026-09-09 사용자 결정, 8절) |
| 2 | **sync가 로컬 상환분을 덮어쓰는 문제** | `source_type='SEED'` 계좌를 동기화 대상에서 제외 | **미결.** 1번 결정에서 파생됐고 `4 Finance Data`(BE-A) 합의 필요 |
| 3 | 알림 엔드포인트 처리 | 7절 C안. `source_type='MINUS_ACCOUNT'` 규약은 458과 합의 필요 | 미결 |
| 4 | 상환 시 `transaction` 기록 | 1차에서 기록하지 않음. BE-A 합의 후 추가 | 미결 |
| 5 | 중복 요청 방지 방식 | 1차에서 행 잠금만. 멱등키 저장소는 미결 | 미결 |
| 6 | 기본 출금계좌 규칙 | `DEMAND_DEPOSIT`이 1개일 때만 자동 선택 | 제안 |
| 7 | 이자 일할 계산 기준 | `actual/365`, 원 단위 절사, 연체 가산금리 없음 | 제안 |
| 8 | API 5 거래내역과의 중복 | 10-1 명세대로 구현하되 응답 계약을 501과 맞춘다 | 팀 보고 필요 |
| 9 | `account` 잔액 쓰기 방식 | 도메인 전용 JDBC writer. 501이 `Account` 엔티티를 만들면 교체 | 팀 보고 필요 (8절) |
| 10 | seed의 `next_interest_date`가 2026-09-25 고정 | 그날 이후 이자 예측이 전부 `null`이 된다. seed(523) 쪽 보완 필요 | 발견 사항 보고 |

1번이 확정되어 쓰기 API를 포함한 7개 전부를 착수할 수 있다. 2~5번이 확정되지 않아도 구현은 진행할 수 있으나, 그 상태에서 534를 전부 완료로 보고하지 않는다. 특히 2번은 미해결 상태로 두면 시연 중 상환 결과가 사라질 수 있으므로 MR 전에 BE-A에 전달한다.

### 기준 문서로 확정되어 결정 항목에서 내린 것

초안에서 제안으로 두었으나 `docs/` 검토 결과 이미 확정된 사항이다.

| 항목 | 확정 근거 |
| --- | --- |
| `status`를 `account.status` 3종으로 해석 | `ERD 작성 ...md` 주요 코드값: "MinusAccount·DepositSaving도 Account 상태를 사용" |
| `balance = -used_amount` 동시 갱신 | 같은 문서 계산 기준 절의 명시 정책 |
| `available_amount` 파생값 동시 갱신 | 같은 문서: "파생값을 저장하는 경우 원천값과 같은 트랜잭션에서 갱신" |
| 마이너스통장을 Loan 요약에 넣지 않음 | `MINUS`는 부채로 집계하며 소비자는 `13 Dashboards`의 `GET /api/v1/dashboard/debts` |
| 알림 조회에서 소프트 삭제 제외 | 같은 문서 삭제·이력 보존 절 |
| 목표 연계 없음 | `goal_account`가 CHECK로 `MINUS` 연결을 차단 |

### 완료 기준

1. 7개 엔드포인트가 6·7·8절 계약대로 동작하고 요청 범위 밖 변경이 없다.
2. 인증·소유권·입력 경계값이 검증되고, 계좌번호 원문·암호문·해시가 응답과 로그에 없다.
3. 관련 테스트와 전체 테스트가 통과한다.
4. `V14`가 MySQL에서 적용되고 `validate`가 통과한다.
5. 구현 문서 `backend/docs/minus-account.md`와 `remaining-work.md`가 갱신되고 미검증·미결 항목이 명시된다.
6. `git diff --check`에 공백 오류가 없다.

**이번 범위에서 완료로 보고하지 않을 항목:** 알림 실데이터 검증, 상환의 거래원장 반영, 멱등성 보장, SSAFY 외부 연동과 거래고유번호 생성, sync 덮어쓰기 방지, seed 실데이터 기준 동작 확인.
