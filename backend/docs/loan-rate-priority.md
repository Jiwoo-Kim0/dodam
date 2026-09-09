# 대출 금리비교·상환우선순위 구현 (S15P21D105-533)

계획과 결정 배경은 [진행계획](loan-rate-priority-plan.md)에 있다. 이 문서는 **구현된 계약만** 기록한다.

## 구현 현황

| API | 상태 |
| --- | --- |
| `GET /api/v1/loans/repayment-priority` | 구현 완료 (goalId 미지원) |
| `GET /api/v1/loans/{accountNo}/rate-comparison` | 구현 완료 |
| `GET /api/v1/loans/high-interest` | 구현 완료 |

## 상환 우선순위

`GET /api/v1/loans/repayment-priority`

### 입력과 인증

- 인증 필수다. 사용자 식별은 `AuthenticatedUserIdResolver`를 사용한다.
- `goalId`는 **지원하지 않는다.** 파라미터가 있으면 값의 형식과 무관하게 `INVALID_REQUEST` 400이다. 형식 오류, 0, 음수, 빈 값도 같다.
  - `goal` 테이블 마이그레이션과 Goal 도메인이 없어 목표의 존재 여부조차 조회할 수 없다. 존재를 확인한 것처럼 404를 반환하지 않는다.
  - 목표를 반영한 우선순위는 명세 16의 `GET /api/v1/recommendations/priorities`가 소유한다.
- 다른 입력은 없다.

### 대상과 정렬

- 본인 대출 중 `LoanStatus.isOutstanding()`(ACTIVE·OVERDUE)이고 잔액이 양수인 것만 대상이다. 종료·잔액 0 대출은 응답에 넣지 않는다.
- 정렬은 **현재 금리 내림차순 → 잔액 오름차순 → loanId 오름차순**이다. loanId가 유일하므로 조회 순서와 무관하게 결과가 하나로 정해진다.
- `rank`는 1부터 순차 부여하며 금리·잔액이 같아도 공유하지 않는다.
- 잔액 오름차순은 동률을 결정적으로 해소하기 위한 기준이며 이자 절감 최적화가 아니다.
- 무이자(0%) 대출은 무효가 아니라 정상 값으로 순위에 포함한다.

### 응답

| 필드 | 설명 |
| --- | --- |
| `asOfDate` | `LoanAnalysisClock`의 Asia/Seoul 기준일. 요청당 한 번 사용 |
| `assumptions` | 아래 가정 코드. 코드명 오름차순 |
| `complete` | 판정 불가 대출이 없으면 true |
| `unavailableReasons` | 판정 불가 대출이 있으면 `INCOMPLETE_LOAN_DATA` |
| `unavailableLoans` | `{loanId, reasons}`. loanId 오름차순 |
| `strategy` | `HIGHEST_RATE_FIRST` |
| `evaluatedCount` | 순위를 매긴 대출 수 |
| `loans[]` | `{rank, loanId, accountName, accountNoMasked, institutionName, balance, interestRate, requiresAttention, status, reasons}` |

- 항목 `status`는 기존 532 계약과 같은 `COMPLETE`·`PARTIAL`·`UNAVAILABLE` 어휘를 쓴다. 현재 구현에서 순위 항목은 항상 `COMPLETE`다.
- 항목 `reasons`는 `HIGHER_CURRENT_RATE`다.
- `requiresAttention`은 OVERDUE 표시일 뿐이며 연체 해소의 최적 순서를 계산한 결과가 아니다. 미납액과 연체 가산금리 자료가 없다.
- 대상 대출이 없으면 `loans`는 빈 배열, `evaluatedCount`는 0, `complete`는 true다.
- 계좌번호 원문·해시·암호문은 반환하지 않는다. `accountNoMasked`만 제공한다.
- 목표 반영 여부를 나타내는 필드는 두지 않는다. goalId가 400으로 거부되어 값이 항상 같아지므로 의미가 없다.

### 가정

| 코드 | 뜻 |
| --- | --- |
| `EXCLUDES_FEES` | 중도상환수수료 등 수수료를 반영하지 않는다. 기존 이자 분석과 같은 코드 |
| `EXCLUDES_GOAL_BUDGET` | 목표·상환 예산을 반영하지 않는다 |
| `ORDER_BY_CURRENT_RATE_ONLY` | 현재 금리만으로 정렬한다. 시장 비교나 절감액 계산이 아니다 |
| `EXCLUDES_OVERDUE_INTEREST_AND_ARREARS` | 대상에 OVERDUE가 있을 때만 추가. 기존 이자 분석과 같은 코드 |

### 제공하지 않는 값

권장 상환금액, 확정 절감액, 완납일은 만들지 않는다. 추가 상환예산 입력과 수수료 자료가 없기 때문이다. 532의 남은 전체 이자를 조기상환 절감액으로 재사용하지 않는다. 추가 상환 절감액은 명세 17의 `POST /api/v1/simulations/loan/prepayment`가 소유한다.

### 데이터 출처

저장된 대출만 읽고 계산 결과를 저장하지 않는다. 새 스키마나 외부 SSAFY 호출은 없다. 상품 데이터를 사용하지 않으므로 상품 동기화 시점과 무관하다.

### 검증

`LoanRepaymentPriorityApiIntegrationTest` 12건이 통과한다. 정렬·동률·무이자·연체 가정·본인 격리·빈 결과·goalId 400(6개 값)·401을 확인한다. 전체 결과는 이 문서 마지막 절에 있다.

**금리 무효(`INVALID_TERMS`) 분기는 검증하지 못했다.** `sql/loan-query-test-data.sql`이 ERD의 `interest_rate >= 0` CHECK를 복제해 H2에서도 음수를 넣을 수 없어 API로 도달할 수 없다. 서비스에 방어 코드로만 남아 있다.

## 시장금리 비교

`GET /api/v1/loans/{accountNo}/rate-comparison`

### 입력

| 입력 | 기본값 | 검증 |
| --- | --- | --- |
| `accountNo` | 필수 | 숫자 10~20자리 |
| `term` | 보유 대출의 `loanPeriod` | 1~600, 빈 값 거부 |
| `industry` | 보유 대출의 업권 | 대문자·밑줄 코드, 빈 값 거부. 현재 유효한 값은 `BANK`뿐이다 |
| `topN` | 5 | 1~20, 빈 값 거부 |

- 인증 필수이며 타인·미존재 계좌는 동일하게 404 `RESOURCE_NOT_FOUND`다.
- `topN`은 후보 표시 개수만 제한하고 기준금리 모집단을 줄이지 않는다.

### 비교 조건

- **비교군은 연결 상품의 `loan_type`으로 결정한다.** 보유 대출의 `LoanType` 5종은 일반 신용과 중금리를 구분하지 못한다.

| 비교군 | 상품 `loan_type` |
| --- | --- |
| `CREDIT` | `PERSONAL_CREDIT`, `CREDIT_REFINANCE` |
| `CREDIT_MID` | `POLICY_CREDIT`, `MID_CREDIT`, `MID_CREDIT_EMERGENCY`, `MID_CREDIT_REFINANCE`, `MID_CREDIT_POLICY`, `MID_EMERGENCY_REFINANCE` |
| `MORTGAGE` | `MORTGAGE`, `MORTGAGE_REFINANCE`, `POLICY_MORTGAGE` |
| `JEONSE` | `JEONSE`, `LEASE_HF`, `LEASE_YOUTH`, `LEASE_SGI`, `LEASE_REFI_HF`, `LEASE_REFI_YOUTH`, `LEASE_REFI_SGI` |
| `AUTO` | `AUTO_NEW`, `AUTO_LOAN`, `USED_CAR` |

- 자격 한정 상품(`SOHO_*`, `MILITARY_*`, `PUBLIC_OFFICIAL` 등)과 한도·마이너스 방식(`CREDIT_LINE`, `LINE_REFINANCE`, `EMERGENCY_CREDIT`)은 어느 군에도 넣지 않는다. 표에 없는 `loan_type`은 `UNSUPPORTED_LOAN_TYPE`이다.
- 기간은 `≤24 / 25~60 / 61~120 / >120`개월 밴드로 묶는다. 옵션 min/max가 상품 값의 복사여서 정확 일치는 기준금리를 바꾸지 않고 표본만 줄인다. 채택한 옵션의 실제 `term`을 후보마다 반환하고 요청·보유 기간과 다르면 `TERM_BAND_MATCH`를 표시한다.
- 상환방식 대응은 `EQUAL_PAYMENT → {EQUAL_PRINCIPAL_INTEREST, AMORTIZING}`, `EQUAL_PRINCIPAL → {EQUAL_PRINCIPAL, AMORTIZING}`, `BULLET → {BULLET}`이다. `AMORTIZING` 채택 시 `REPAYMENT_TYPE_AMBIGUOUS`를 표시한다. `CREDIT_LINE`·`OVERDRAFT`·NULL 옵션은 제외한다.
- `rate_type`은 판정에 쓰지 않고 `RATE_TYPE_NOT_MATCHED`로 표시한다.
- **연결된 보유 상품은 모집단에서 제외한다.** 자기 금리와 비교하게 되기 때문이다.
- 업권은 `institution_code = company_code` 조인으로 확인한다. 요청 업권이 보유 업권과 다르면 `OTHER_INDUSTRY_REFERENCE`로 참고 비교임을 알린다.

### 기준금리

`referenceMethod = DISCLOSED_MIN_RATE_MEDIAN`

1. 조건에 맞고 `minRate`가 있는 옵션의 `minRate`를 쓴다.
2. 한 상품의 적합 옵션이 여럿이면 그 중앙값을 상품 대표값으로 삼는다. 옵션 수가 많은 상품이 기준을 더 끌어당기지 않는다.
3. 상품 대표값들의 중앙값이 `referenceRate`다. 짝수면 가운데 두 값의 산술평균이며 소수 넷째 자리 HALF_UP으로 표시한다.
4. 서로 다른 상품이 2개 미만이면 `referenceRate`·`rateDifference`는 null, `status`는 `PARTIAL`, 사유는 `INSUFFICIENT_COMPARABLE_PRODUCTS`다. 확보한 후보는 유지한다.
5. 상품이 정확히 2개면 `referenceConfidence=LOW`, 그 이상은 `NORMAL`이다.

`referenceRate`는 **동일 조건에서 공시된 최저금리 수준**이며 실제 평균 대출금리도, 예상 승인금리도 아니다. 옵션 `rate`는 사용하지 않는다. 대출 옵션 319건 중 308건이 NULL이고, 값이 있는 11건 중 min/max와 다른 6건은 `repayment_type`이 NULL이라 조건에서 제외되며 나머지 5건은 `rate`와 `minRate`가 같다.

`rateDifference = interestRate - referenceRate`이며 단위는 %p다.

### 응답과 상태

- 최상위에 `asOfDate`, `assumptions`, 대출 식별·마스킹 정보, `status`, `reasons`, `comparisonGroup`, `termBand`, `industry`, `referenceMethod`, `referenceRate`, `referenceConfidence`, `rateDifference`, `comparableProductCount`, `unmatchedConditions`, `oldestSyncedAt`, `latestSyncedAt`, `candidates`를 둔다.
- 후보에는 `productId`, `productName`, `institutionName`, `sourceType`, `representativeRate`, `disclosedMinRate`, `disclosedMaxRate`, `term`, `repaymentType`, `rateType`, `syncedAt`을 둔다. 정렬은 대표값 오름차순, `productId` 오름차순이다.
- `productId`는 명세 17의 대환 시뮬레이션이 받는 `targetProductId`로 쓸 수 있게 반환한다. 533은 대환 가능 여부나 절감액을 계산하지 않는다.

| `status` | 조건 |
| --- | --- |
| `COMPLETE` | 기준금리와 편차까지 산출 |
| `PARTIAL` | 후보는 있으나 상품 2개 미만이라 기준금리 null |
| `UNAVAILABLE` | 비교 조건을 만들지 못함 |

`UNAVAILABLE` 사유는 `NOT_OUTSTANDING`(종료·잔액 0), `COMPARISON_GROUP_UNKNOWN`(연결 상품 없음), `UNSUPPORTED_LOAN_TYPE`, `MISSING_LOAN_PERIOD`, `INDUSTRY_UNKNOWN`, `INVALID_TERMS`다. 모두 HTTP 200으로 응답한다.

### 자료 신선도

허용 경과기간 임계값을 두지 않는다. 명세 18·10이 요구하는 것은 "DB 최신 동기화 시점 기준; 실시간 상품정보 아님"이라는 고지이고, 동기화 주기가 문서에 정의되지 않아 "오래됐다"의 기준선이 없다. 신선도 관리는 명세 18-1의 `sync/status`·`sync/history`가 소유한다. 533은 `syncedAt`·`oldestSyncedAt`·`latestSyncedAt`과 `sourceType`을 노출하고 판정을 신선도로 차단하지 않는다.

## 고금리 대출

`GET /api/v1/loans/high-interest`

### 입력

| 입력 | 기본값 | 검증 |
| --- | --- | --- |
| `loanType` | 전체 | `LoanType` 값. 잘못된 값은 400 |
| `industry` | 보유 대출의 업권 | 보유 대출 업권 필터. 빈 값 거부 |
| `thresholdMode` | `MARKET_SPREAD` | `MARKET_SPREAD` 외의 값은 400 |

`loanType=CREDIT` 요청은 일반과 중금리 대출을 모두 포함하며 각 대출은 자기 비교군의 기준금리로 판정된다. 두 군의 기준금리를 합치지 않는다.

### 판정

`interestRate - referenceRate >= rateSpreadThreshold`이면 점검 대상이다.

**`rateSpreadThreshold = 1.0%p`.** 기획안의 α는 "0.0%p -> 추후 결정"으로만 적혀 있어 확정값으로 읽지 않았고, 확정 정책으로 계산한 시연 데이터의 편차 분포(+3.03 / +0.17 / -0.83 / -1.35%p)를 근거로 정했다. 이 값이면 1건이 판정된다. 0.2%p까지 낮추면 2건이 되지만 통계적 근거 없이 건수를 늘리는 선택이라 하지 않았다. **서비스 초기 임계값이며 검증된 기준이 아니다.**

전체 대출 평균이나 사용자 보유 대출 평균으로 기준금리를 대체하지 않는다.

### 응답

- `loans`에는 점검 대상만 담고 판정 불가 대출은 `unavailableLoans`로 분리한다. `evaluatedCount`(기준금리를 확보해 판정한 수), `highInterestCount`, `unavailableCount`, `complete`로 빈 결과의 의미를 구분한다. 대상이 없는 것과 전부 판정 불가인 것은 다른 상태다.
- 항목마다 `comparisonGroup`, `referenceMethod`, `referenceRate`, `referenceConfidence`, `comparableProductCount`를 함께 반환한다. 서로 다른 비교군의 편차를 한 목록에서 정렬하므로 편차 숫자만으로 다른 군과 직접 비교되지 않게 한다.
- 정렬은 편차 내림차순 → 현재 금리 내림차순 → `loanId` 오름차순이다.
- 판정 불가 대출이 있으면 집계 `unavailableReasons`에 `INCOMPLETE_LOAN_DATA`를 둔다.
- 수수료·심사조건을 반영한 대환 권유나 대환 가능 여부는 반환하지 않는다.

### 가정

| 코드 | 뜻 |
| --- | --- |
| `EXCLUDES_FEES` | 수수료를 반영하지 않는다. 기존 이자 분석과 같은 코드 |
| `DISCLOSED_SNAPSHOT_NOT_REALTIME` | 상품 자료는 저장된 스냅샷이며 실시간·판매 가능 정보가 아니다 |
| `REFERENCE_IS_NOT_APPROVAL_RATE` | 기준금리는 공시 최저금리 수준이며 예상 승인금리가 아니다 |
| `EXCLUDES_OVERDUE_INTEREST_AND_ARREARS` | 대상에 OVERDUE가 있을 때만 추가 |

## 조회 설계

요청당 대출 상품 전량을 회사·옵션과 함께 한 번 읽고 메모리에서 비교군별로 그룹핑한다(`LoanMarketProductReader`). 대출 건마다, 비교군마다 조회하지 않으므로 고금리 API가 대출 여러 건을 평가해도 상품 조회는 1회다.

근거는 규모와 인덱스다. 시연 데이터의 대출 상품은 76개, 옵션은 319건이다. `financial_product_option`에는 `product_id` 인덱스만 있고 `term`·`repayment_type` 인덱스가 없어 옵션 조건을 DB에서 거르는 이점이 없다. 상품 수가 크게 늘면 재검토한다.

상품 조회 API의 기존 분류(`ProductQueryService.*_LOAN_TYPES`)와 응답은 바꾸지 않았다. 다만 **같은 상품을 두 API가 다르게 분류한다.** 상품 조회의 `MORTGAGE_LOAN_TYPES`는 `POLICY_HOUSING`·`SOHO_MORTGAGE`를 포함한 5종이고 533의 `MORTGAGE` 비교군은 3종이다. 조회용 넓은 분류와 금리 비교용 동질 군의 목적이 다르기 때문이다.

## 검증 (전체)

- `LoanRepaymentPriorityApiIntegrationTest` 12건, `LoanRateComparisonApiIntegrationTest` 34건 통과.
- **전체 284개 통과, 실패·오류 0건, 스킵 1건**(환경변수 없는 MySQL 테스트). `clean build` 성공.
- 검산한 항목: 상품 대표값 4.0/6.0/8.0 → 기준 6.0, 옵션 2개 상품의 대표값 중앙값, topN 1/5에 기준금리 동일, 자기 상품 제외, 기간 밴드 밖 옵션 제외, term NULL 옵션 제외, 상품 2개 시 `LOW`, `AMORTIZING` 모호 표시, 중금리와 일반 신용대출 분리, 비교 불가 5종 사유, 소유권 격리, 입력 경계값, `thresholdMode`·`loanType` 400.
- **MySQL 미검증.** H2로만 확인했다. 기존 `LoanAnalysisMySqlIntegrationTest`와 같은 `@EnabledIfEnvironmentVariable` 조건부 테스트는 아직 추가하지 않았다.
