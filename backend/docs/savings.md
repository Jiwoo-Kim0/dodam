# 적금 상세·납입·예상금액·현금흐름 구현 (S15P21D105-537)

선행 이슈 535의 예·적금 공통 조회는 [deposit-savings.md](deposit-savings.md), 같은 구조의 예금 API는 [deposit-detail.md](deposit-detail.md)에 있다. 이 문서는 **구현된 계약만** 기록한다.

## 구현 현황

| API | 상태 |
| --- | --- |
| `GET /api/v1/savings/{accountNo}` | 구현 완료 |
| `GET /api/v1/savings/{accountNo}/payments` | 구현 완료 |
| `GET /api/v1/savings/{accountNo}/rate-comparison` | 구현 완료 |
| `GET /api/v1/savings/{accountNo}/early-termination` | 구현 완료 |
| `GET /api/v1/savings/{accountNo}/maturity-forecast` | 구현 완료 |
| `GET /api/v1/savings/payment-schedules` | 구현 완료 |
| `GET /api/v1/savings/monthly-amount` | 구현 완료 |
| `GET /api/v1/savings/cashflow-burden` | 구현 완료 |
| `GET /api/v1/savings/disposable-balance` | 구현 완료 |

2026-09-10, `S15P21D105-537-api-11-2-savings-api`에서 구현했다. 기준 자료는 `docs/API명세서/11-2 Savings 3cdbb6ea35648059a2bae55e700de280.csv`, `docs/ERD/`, 기획안 7-1이다.

**작업 인계 기록.** 상세·납입내역·납입 예정·월 저축액·예상금액의 서비스 계층은 다른 에이전트(Codex)가 먼저 작성했고 컨트롤러·테스트·문서가 없는 상태에서 중단됐다. 이어받아 컨트롤러 3종, 금리비교·현금흐름 2개 서비스, 통합 테스트, 이 문서를 추가하고 아래 결함을 고쳤다. 인계 당시 9개 중 HTTP로 도달 가능한 경로는 0개였다.

## 인계 후 고친 결함

| 결함 | 조치 |
| --- | --- |
| **컨트롤러 부재** — `/api/v1/savings` 매핑이 0건이라 9개 전부 도달 불가 | `SavingsQueryController`, `SavingsAnalysisController` 추가 |
| **적금 중도해지 이율을 0%로 고정** — 예상 수령액이 납입 원금과 같아져 "이자가 전혀 없다"가 계산된 답처럼 보였다 | 예금(536)과 같은 경과기간 구간표 적용. 0은 추정을 피한 것이 아니라 더 틀린 추정이었다 |
| **비교군 우선순위가 무작위** — `Map.copyOf`가 `LinkedHashMap` 순서를 버려 `GROUP_FLEXIBLE` 같은 복합 키워드의 분류가 실행마다 달라질 수 있었다 | 순서가 보장되는 `List`로 교체. 통합 테스트가 잡았다 |
| 와일드카드 import, 한 글자 변수, `java.sql` 인라인 정규화, DTO의 복사된 미사용 import | 정리 |
| `bankName` 미조회 | 금리비교 응답에 필요해 조회 컬럼과 행 레코드에 추가 |

## 데이터 출처

**상세·납입내역·예상금액은 로컬 시연 DB 조회로 구현했다.** 명세의 출처는 SSAFY지만 535·536과 같은 판단으로 외부 금융망 호출과 호출별 거래고유번호 생성을 하지 않는다. 근거는 세 가지다.

- 시연 데이터가 이미 `account`·`deposit_saving`·`savings_payment`에 적재돼 있어 금융망 왕복이 같은 값을 다시 가져오는 일이 된다.
- 금융망 연결이 없는 사용자도 본인 데이터를 볼 수 있어야 한다.
- 금융망에는 중도해지·만기 예상금액을 돌려주는 조회가 없다. 어차피 계약 조건을 받아 서버에서 계산해야 한다.

금리비교는 명세대로 상품DB를, 납입 예정·월 저축액·현금흐름은 명세대로 자체 데이터를 읽는다.

## 인증·소유권·공통 계약

- 9개 모두 GET이며 인증이 필요하다. 사용자 식별은 `AuthenticatedUserIdResolver`를 쓴다.
- 경로의 `accountNo`는 `SensitiveNumberHasher`(HMAC-SHA-256)로 해시해 `account.account_no_hash`와 맞춘다. 새 해시 방식이나 평문 조회를 추가하지 않았다.
- 소유권은 **조회 조건**에 넣는다. 모든 질의가 `user_id`와 `account_type = 'SAVINGS'`를 함께 건다.
- **없는 계좌·타인 계좌·적금이 아닌 계좌는 모두 같은 `RESOURCE_NOT_FOUND` 404다.**
- 계좌번호는 `accountNoMasked`만 반환한다. 원문·해시·암호문을 응답, 로그, 오류 메시지에 남기지 않는다.
- 성공 응답은 `ApiResponse`, 납입내역 목록은 `PageResponse`다. 새 응답 형식과 새 `ErrorCode`를 만들지 않았다.
- 금액은 원 단위 `long`, 금리·비율은 `BigDecimal`이다.
- 기준일은 요청마다 `SavingsClock`(Asia/Seoul)에서 한 번 얻는다. 서비스가 `LocalDate.now()`를 직접 부르지 않는다.
- **계산할 수 없을 때 0원을 반환하지 않는다.** 0원은 "금액이 없다"는 뜻이고 계산 불가는 `complete=false`·`reasons` 또는 `status=UNAVAILABLE`로 구분한다.

## 입력 검증과 기본값

| API | 입력 | 기본값 | 허용 값 |
| --- | --- | --- | --- |
| 공통 | `accountNo` (Path) | 없음 | 숫자 10~20자리 |
| 납입내역 | `startDate` / `endDate` | 기준일 기준 최근 3개월 | `QueryPeriodPolicy` (최대 12개월, 미래 종료일 금지) |
| 납입내역 | `page` / `size` | 0 / 20 | page ≥ 0, size 1~100 |
| 납입 예정 | `startDate` / `endDate` | 기준일부터 3개월 | `PaymentSchedulePeriodPolicy` (최대 12개월, 미래 허용) |
| 월 저축액 | `yearMonth` | 현재 월 | YYYY-MM, 현재 월부터 12개월 이내 |
| 금리비교 | `term` | 보유 계약 기간 | 1~120 (개월) |
| 금리비교 | `industry` | 보유 계좌의 업권 | `^[A-Z][A-Z_]{0,29}$` |
| 금리비교 | `topN` | 5 | 1~20 |
| 현금흐름 부담 | `yearMonth` | 현재 월 | YYYY-MM, 현재 월부터 12개월 이내 |
| 가용금액 | `baseDate` | 기준일 | ISO 날짜, 현재 월부터 12개월 이내 |

- 납입 예정은 미래 날짜를 허용하는 `PaymentSchedulePeriodPolicy`를, 납입내역은 과거만 허용하는 `QueryPeriodPolicy`를 쓴다. 두 정책의 기본값 계산에 주입 기준일을 쓰도록 `QueryPeriodPolicy.resolve(start, end, today)`의 접근 범위를 넓혔다. 정책의 검증 규칙 자체는 바꾸지 않았다.
- `page`, `size`, `term`, `industry`, `topN`의 **빈 문자열은 400이다.** 생략과 빈 값은 다른 입력이다.
- `topN`은 **표시만 자른다.** 기준금리와 `comparableProductCount`는 전체 후보로 계산한다.

## 적금 상세 — `GET /api/v1/savings/{accountNo}`

`accountId`, `accountName`, `accountNoMasked`, `bankCode`, `status`, `balance`, `detailsAvailable`, `interestRate`, `monthlyAmount`, `paymentDay`, `nextPaymentDate`, `openingDate`, `expiryDate`, `financialProductId`, `asOfDate`를 반환한다.

- `account`를 기준으로 `deposit_saving`을 LEFT JOIN한다. **상세행이 없는 계좌도 200으로 반환하며** `detailsAvailable=false`, 약정 필드는 모두 null이다.
- `balance`는 납입 원금 합이며 이자를 포함하지 않는다.
- `CLOSED` 계좌도 조회된다.

## 납입내역 — `GET /api/v1/savings/{accountNo}/payments`

- 항목은 `paymentId`, `paymentDate`, `amount`, `installmentNumber`, `status`다. `status`는 `SCHEDULED`·`PAID`·`FAILED`·`SKIPPED`다.
- 정렬은 `payment_date` 내림차순 → `savings_payment_id` 내림차순이다.
- 기본 조회기간이 과거 3개월이므로 **기준일 이후의 예정 회차는 기본 조회에 들어오지 않는다.** 앞으로의 일정은 납입 예정 API가 소유한다.
- 페이지가 끝을 넘으면 빈 `content`를 반환한다.

## 납입 예정 — `GET /api/v1/savings/payment-schedules`

`ACTIVE` 적금만 대상이다. 두 종류의 항목을 만든다.

| `source` | 뜻 |
| --- | --- |
| `STORED_SCHEDULED` | 저장된 `SCHEDULED` 회차. `paymentId`가 있다 |
| `ESTIMATED_MONTHLY` | 월 약정액으로 만든 추정. `paymentId`가 null이다 |

- **같은 달에 저장된 회차가 있으면 약정액을 보충하지 않는다.** 상태와 무관하게 그렇다. `FAILED`·`SKIPPED`를 예정으로 되살리거나 `PAID`와 약정액을 중복 반영하지 않기 위해서다. 명세의 "예정 이벤트 중복 반영 금지"를 이렇게 해석했다.
- 추정 회차의 날짜는 첫 달은 `next_payment_date`, 이후는 `payment_day`를 그 달 말일로 잘라 쓴다. 만기일 당일 이후는 만들지 않는다.
- 정렬은 `paymentDate` → `accountId` → `paymentId`(null 나중) 순이다.
- 기준일 이전의 저장된 예정 회차가 있으면 `OVERDUE_SCHEDULED_PAYMENT`를 남긴다.

| `reasons` 코드 | 뜻 |
| --- | --- |
| `INCOMPLETE_SAVINGS_TERMS` | 상세행·약정액·납입일·다음 납입일·만기일 중 하나라도 없다 |
| `STALE_NEXT_PAYMENT_DATE` | 다음 납입일이 기준일보다 과거다 |
| `INVALID_PAYMENT_DATES` | 다음 납입일이 개설일 이전이거나 만기일 이후다 |
| `OVERDUE_SCHEDULED_PAYMENT` | 기준일이 지난 저장된 예정 회차가 있다 |
| `AMOUNT_OUT_OF_RANGE` | 합계가 `long` 범위를 넘었다 |

사유가 하나라도 있으면 `complete=false`다. 확정하지 못한 계좌가 하나뿐이어도 그 달 전체를 완전하다고 표시하지 않는다.

## 월 저축액 — `GET /api/v1/savings/monthly-amount`

```text
expectedMonthlyAmount = paidAmount + scheduledAmount + estimatedAmount
```

- 과거 월은 **저장된 실적만** 본다(`STORED_PAID_ONLY`). `scheduledAmount`와 `estimatedAmount`는 0이다. 지난 달에 예정·추정을 덧붙이지 않는다.
- 현재·미래 월은 `PAID_PLUS_REMAINING_MONTHLY_SCHEDULE`이며 납입 예정 계산을 그대로 재사용해 중복을 막는다.
- `paidThrough`는 과거 월이면 월말, 현재 월이면 기준일이다.
- **구성 요소를 하나라도 확정하지 못하면 `expectedMonthlyAmount`는 null이고 `complete=false`다.** 한 계좌의 약정을 모르면 그 달의 저축 총액도 모르는 것이다. 0으로 대체하면 저축이 적은 것처럼 보인다.
- 기준일 이후 날짜에 `PAID`가 있으면 `FUTURE_PAID_PAYMENT`를 남긴다.

## 중도해지·만기 예상금액

`GET /api/v1/savings/{accountNo}/early-termination`, `GET /api/v1/savings/{accountNo}/maturity-forecast`

### 계산식

```text
가중원금 = Σ(회차 납입액 × 그 회차부터 계산종료일까지의 일수)
예상이자 = floor(가중원금 × 적용이율 ÷ 36500)
예상수령액 = 예상원금 + 예상이자
```

- **회차마다 실제 예치일수를 따로 적용한다.** 모든 원금이 가입일에 예치된 것으로 계산하지 않는다. 적금은 납입이 나뉘어 들어오므로 이 구분이 금액을 크게 바꾼다.
- 계산종료일은 중도해지가 기준일, 만기 예상이 만기일이다.
- 만기 예상은 남은 회차를 납입 예정 계산으로 채워 `futurePrincipal`에 넣는다. 중도해지는 0이다.
- 단리이며 원 단위 절사다. 윤년에도 365로 나눈다.
- **세금과 수수료를 반영하지 않는다**(`EXCLUDES_TAX_AND_FEES`). 예금(536)이 이자소득세 15.4%를 반영하는 것과 다르다. 두 API의 수령액을 나란히 비교할 때 이 차이를 감안해야 하며, 통일은 remaining-work에 남겼다.

### 중도해지이율 (`TIERED_CONTRACT_RATE_RATIO`)

명세·ERD·기획안에 계산식이 없다. `deposit_saving`에는 계약이율만 있고 중도해지이율 컬럼이 없다. 국내 시중은행 관행인 **경과기간 구간별 계약이율 차등**을 기본안으로 삼았고 예금(536)과 같은 표다.

| 경과 개월 (`rateTier`) | 적용 이율 |
| --- | --- |
| 1개월 미만 | 연 0.10% 정액 |
| 1 / 3 / 6 / 9 / 11개월 이상 | 계약이율 × 20% / 30% / 40% / 60% / 80% |

최저 연 0.10%를 보장한다. **서비스가 정한 추정치이며** 응답의 `ratePolicy`·`rateTier`·`elapsedMonths`와 가정 코드 `ESTIMATED_EARLY_TERMINATION_RATE`로 노출한다. 만기 예상에서는 세 필드가 모두 null이다.

### 사유 코드

| 코드 | 뜻 |
| --- | --- |
| `ACCOUNT_CLOSED` | 이미 해지된 계좌 |
| `INCOMPLETE_SAVINGS_TERMS` | 상세행·개설일·만기일·금리 중 하나라도 없다 |
| `PAYMENT_HISTORY_BALANCE_MISMATCH` | 납입 이력 합과 계좌 잔액이 다르다 |
| `FUTURE_PAID_PAYMENT` | 기준일 이후 날짜에 `PAID`가 있다 |
| `INVALID_CONTRACT_DATES` / `INVALID_PAYMENT_DATES` | 개설일·만기일 또는 납입일이 계약 기간과 어긋난다 |
| `ACCOUNT_NOT_ACTIVE` | 만기까지 남은 회차를 채워야 하는데 진행 중 계좌가 아니다 |
| `FORECAST_PERIOD_TOO_LONG` | 만기까지 600개월을 넘는다 |
| `AMOUNT_OUT_OF_RANGE` | 금액이 `long` 범위를 넘었다 |

사유가 하나라도 있으면 `status=UNAVAILABLE`이고 금액 필드가 모두 null이다.

## 적금 시장금리 비교 — `GET /api/v1/savings/{accountNo}/rate-comparison`

**예금 비교(536)와 같은 응답 필드·판정 어휘를 쓴다.** 두 API가 다른 모양이면 화면이 같은 비교를 두 번 구현해야 한다. 필드 설명은 [deposit-detail.md](deposit-detail.md)를 참고한다.

### 적금 고유의 차이

- **비교군을 키워드 우선순위로 정한다.** 예금의 `saving_type`은 5종이라 값 집합으로 나눌 수 있었지만, 적금은 53개 상품에 36종이 흩어져 있어 대부분이 상품 1개짜리다. 값마다 군을 만들면 전부 표본 부족이 된다.

  | 순위 | 비교군 | 키워드 |
  | --- | --- | --- |
  | 1 | `RESTRICTED` | POLICY, YOUTH, MILITARY, CHILD, EMPLOYEE, MICROFINANCE, SOCIAL, FOREIGN, BUSINESS, GROUP, MULTI |
  | 2 | `FLEXIBLE` | FLEXIBLE |
  | 3 | `REGULAR` | REGULAR |
  | 4 | `CHALLENGE` | CHALLENGE, MISSION |
  | 5 | `OTHER` | 위에 걸리지 않은 나머지 |

  **자격 한정을 먼저 거른다.** 청년·군인·아동·정책 상품은 가입 자격이 좁은 대신 금리가 높아, 적립 방식으로 먼저 묶으면 일반 상품의 기준금리를 끌어올린다. `GROUP_FLEXIBLE`처럼 두 축의 키워드를 모두 가진 값이 있어 순서가 결과를 바꾼다. 순서를 보장하려고 `Map`이 아니라 `List`를 쓴다.

  시연 데이터 분포는 RESTRICTED 26 / REGULAR 11 / FLEXIBLE 8 / CHALLENGE 6 / OTHER 2다.

- **기간 밴드가 예금보다 넓다**: `<=6`, `7-12`, `13-24`, `>24`. 시연 적금 옵션 81건이 12개월(35건)과 36개월(16건)에 몰려 있고 4·9·15·18·60개월은 각각 1건뿐이다.
- **우대금리를 판정에 쓰지 않는 이유가 예금보다 강하다.** 시연 적금 옵션의 우대금리는 기본금리의 두세 배까지 벌어진다(최대 10.25%). 급여이체·카드실적 같은 조건 충족 여부를 보유 적금 쪽에서 확인할 수 없어, 우대금리로 비교하면 도달 불가능한 기준을 보여 준다. 근거 옵션의 우대금리는 후보에 실어 상한만 알린다.

사유 코드는 예금과 같고 이름만 도메인에 맞춘다: `MISSING_SAVINGS_DETAIL`, `MISSING_SAVINGS_TERM`, `SAVINGS_TERM_OUT_OF_COMPARABLE_RANGE`, `NOT_COMPARABLE_STATUS`, `COMPARISON_GROUP_UNKNOWN`, `INDUSTRY_UNKNOWN`, `INVALID_TERMS`, `INSUFFICIENT_COMPARABLE_PRODUCTS`.

## 현금흐름 부담·저축 후 가용금액

`GET /api/v1/savings/cashflow-burden`, `GET /api/v1/savings/disposable-balance`

**둘은 같은 계산의 두 관점이라 한 서비스가 소유하고 같은 응답을 쓴다.** 부담 API는 비율을, 가용금액 API는 잔액을 앞세울 뿐 구성 요소가 같다. 따로 계산하면 같은 달에 서로 어긋난 숫자가 나온다. 가용금액 API는 `baseDate`가 속한 달로 접는다. 소득·고정지출·저축이 모두 월 단위 항목이라 일 단위로 나눌 근거가 없다.

```text
지출합 = 저축액 + 고정지출 + 상환액(낸 금액) + 상환액(남은 예정)
disposableAmount  = 월 소득 − 지출합
savingsBurdenRate = 저축액 × 100 ÷ 월 소득
totalBurdenRate   = 지출합 × 100 ÷ 월 소득
```

- 저축액은 월 저축액 API의 `expectedMonthlyAmount`를 그대로 쓴다. 중복 반영 방지가 이미 그쪽에서 끝난다.
- 대출 상환은 `loan_repayment`를 상태별로 나눠 **낸 금액과 남은 예정액을 분리**한다. 명세의 "예정 이벤트 중복 반영 금지"다. `FAILED`·`OVERDUE` 회차는 어느 쪽에도 넣지 않는다.
- 소득·고정지출은 `financial_profile`이며 **행이 없거나 NULL이면 0으로 대체하지 않는다.** 0원과 미등록은 다른 상태다.
- **구성 요소를 하나라도 모르면 `disposableAmount`와 `totalBurdenRate`는 null이다.** 빠진 항목을 0으로 보면 부담이 작아 보인다. 고정지출만 없으면 저축 부담률은 그대로 낸다.
- 비율은 소수 둘째 자리 HALF_UP이다. `negativeDisposable`은 가용금액이 음수인지이며 계산하지 못하면 null이다.

| `reasons` 코드 | 뜻 |
| --- | --- |
| `INCOME_NOT_REGISTERED` / `ZERO_INCOME` | 월 소득 미등록 / 0원 |
| `FIXED_EXPENSE_NOT_REGISTERED` | 고정지출 미등록 |
| `AMOUNT_OUT_OF_RANGE` | 금액이 `long` 범위를 넘었다 |
| 월 저축액의 사유 코드 | 저축액을 확정하지 못한 이유가 그대로 올라온다 |

| `assumptions` 코드 | 뜻 |
| --- | --- |
| `EXCLUDES_VARIABLE_SPENDING` | 변동 지출을 반영하지 않는다. 카드·계좌 거래 집계는 명세 15의 Cashflow가 소유하며 여기서 다시 계산하면 두 API가 다른 값을 낸다 |
| `FIXED_EXPENSE_FROM_PROFILE` | 고정지출은 프로필 등록값이다 |
| `FUTURE_MONTHLY_PAYMENTS_AS_SCHEDULED` | 남은 저축은 약정대로 낸다고 본다 |
| `INCOME_ASSUMED_CONSTANT` | 과거 소득 이력이 없어 현재 소득이 조회 월에도 같았다고 본다 |

## 스키마

- **새 마이그레이션을 추가하지 않았다.** 535의 V15와 V3 `account`, V10 `financial_product`, V13 `loan`·`loan_repayment`를 읽기만 한다.
- 535가 만든 파일을 수정하지 않았다. `global/`에 새 파일을 만들지 않았고 `ErrorCode`에 항목을 추가하지 않았다.
- `global/policy/QueryPeriodPolicy.resolve(start, end, today)`의 접근 범위만 package-private → public으로 넓혔다. 검증 규칙은 바꾸지 않았다.
- `account` 쓰기 엔티티를 중복 생성하지 않고 JDBC로 필요한 컬럼만 읽는다.

## 검증 방법과 결과

```powershell
$env:JAVA_HOME = 'C:\Users\SSAFY\.jdks\ms-21.0.11'
.\gradlew.bat test --tests '*SavingsApiIntegrationTest'
```

H2 전용 DB(`savings537`)에 `savings-test-data.sql` fixture를 매 테스트 재적재한다. 기준일은 `SavingsClock`을 대체해 2026-09-09로 고정한다. fixture 계좌번호는 `10000000<계좌ID>`이며 저장 해시는 실제 `SensitiveNumberHasher`로 다시 채워, 서비스가 평문이 아니라 해시로만 소유자를 찾는지 함께 확인한다.

`loan`·`loan_repayment`는 JPA 엔티티가 없어 H2가 만들어 주지 않으므로 531 대출 fixture와 같은 방식으로 이 테스트가 쓰는 컬럼만 최소로 만든다. 실제 마이그레이션(V13)을 대체하지 않는다.

2026-09-10 검증 결과:

- **관련 테스트 63건 통과**(`SavingsApiIntegrationTest`).
- 포함한 범위: 9개 경로의 정상 흐름, 무인증 401 9경로, 소유권 격리(타인·입출금·예금·없는 계좌 × 경로 = 16건 모두 동일 404), 오류 본문에 계좌번호 원문·해시 없음(20개 요청), 입력 경계값(page/size/term/topN/yearMonth/날짜 형식/빈 값), 상세행 없음, 해지 계좌, 저장 회차와 약정액 중복 방지, 과거 월과 현재 월의 계산 기준 차이, 중도해지 구간 이율, 소득 미등록·0원·고정지출 미등록 구분, 실패 상환 회차 제외.
- 금액 단정은 계산식으로 손검산한 값이다. 중도해지(경과 1개월, 3.6%×20%=0.72%): 300,000×61일 + 300,000×30일 = 27,300,000 → ×0.72÷36500 = 이자 538원, 수령 600,538원. 만기 예상: 납입 600,000 + 남은 회차 3,000,000 = 원금 3,600,000.
- 인계받은 서비스 계층의 일정·월 저축액·만기 예상 로직은 위 손계산과 일치했다.

### 미검증 범위와 남은 위험

- **MySQL 전용 검증을 하지 않았다.** 새 마이그레이션이 없고 조회 SQL은 표준 문법만 쓰지만 H2 통과가 MySQL 제약·타입 검증을 대신하지는 않는다.
- **`loan_repayment`에 예정 회차가 실제로 쌓이는지 확인하지 않았다.** 스키마의 CHECK는 `SCHEDULED`를 허용하지만 현재 seed에 예정 회차가 없다면 `remainingLoanRepaymentAmount`는 항상 0이 된다. 남은 상환 일정을 계산으로 만드는 것은 대출 도메인(532·533)의 `LoanRepaymentScheduleCalculator`가 소유한다.
- **중도해지이율 구간표는 서비스가 정한 기본안이다.** 예금(536)과 같은 표를 두 도메인에 따로 두었다. 두 이슈가 서로 다른 브랜치에서 병합될 수 있어 의존을 만들지 않았고, 둘 다 병합되면 공용 위치로 합쳐야 한다.
- **예금은 세후, 적금은 세전을 반환한다.** 두 예상금액을 나란히 비교하면 적금이 유리해 보인다. 통일이 필요하다.
- **금융상품 카탈로그가 적재되지 않은 환경의 실제 시연 조회는 확인하지 않았다.** 금리비교 테스트는 fixture 상품으로 검증했다.
- 커밋·push·MR·배포를 수행하지 않았다.
