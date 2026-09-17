# 예금 상세·금리비교·중도해지·만기예상 구현 (S15P21D105-536)

선행 이슈 535의 예·적금 공통 조회는 [deposit-savings.md](deposit-savings.md)에 있다. 이 문서는 **구현된 계약만** 기록한다.

## 구현 현황

| API | 상태 |
| --- | --- |
| `GET /api/v1/deposits/{accountNo}` | 구현 완료 |
| `GET /api/v1/deposits/{accountNo}/rate-comparison` | 구현 완료 |
| `GET /api/v1/deposits/{accountNo}/early-termination` | 구현 완료 |
| `GET /api/v1/deposits/{accountNo}/maturity-forecast` | 구현 완료 |

2026-09-10, `S15P21D105-536-api-11-1-deposit-api`에서 구현했다. 기준 자료는 `docs/API명세서/11-1 Deposit 3cdbb6ea356480329f97db643f1f1997.csv`, `docs/ERD/`, 기획안 7-1, 그리고 선례로 읽은 대출 금리비교([loan-rate-priority.md](loan-rate-priority.md))·이자분석([loan-analysis.md](loan-analysis.md))이다. 최신 Jira는 확인하지 못했고 로컬 이슈 내보내기와 위 문서를 근거로 했다.

패키지는 `domain/deposit/`이다. 535의 `domain/depositsaving/`은 읽기만 했고 수정하지 않았다. 적금 4종은 537 범위다.

## 데이터 출처

**상세·중도해지·만기예상 3개는 로컬 시연 DB 조회로 구현했다.** 명세의 출처는 SSAFY지만 535와 같은 판단으로 외부 금융망 호출과 호출별 거래고유번호 생성을 하지 않는다. 근거는 세 가지다.

- 시연 데이터가 이미 `account`·`deposit_saving`에 적재돼 있어 금융망 왕복이 같은 값을 다시 가져오는 일이 된다.
- 금융망 연결이 없는 사용자도 본인 데이터를 볼 수 있어야 한다. 535가 이미 그 계약으로 목록을 제공한다.
- 금융망에는 중도해지·만기 예상금액을 돌려주는 조회가 없다. 어차피 계약 조건을 받아 서버에서 계산해야 하므로 계산식은 이 도메인이 소유한다.

금리비교는 명세대로 상품DB(`financial_product`, `financial_product_option`)를 읽는다. 저장된 동기화 스냅샷이며 실시간 공시가 아니다.

## 인증·소유권·공통 계약

- 4개 모두 GET이며 인증이 필요하다. 사용자 식별은 `AuthenticatedUserIdResolver`를 쓴다. 사용자 ID를 요청으로 받지 않는다.
- 경로의 `accountNo`는 `SensitiveNumberHasher`(HMAC-SHA-256)로 해시해 `account.account_no_hash`와 맞춘다. 새 해시 방식이나 평문 조회를 추가하지 않았고 카드·대출과 같은 주입 방식을 따른다.
- 소유권은 **조회 조건**에 넣는다. `user_id`, `account_no_hash`, `account_type = 'DEPOSIT'`을 함께 걸어 조회 후 소유자를 비교하지 않는다. 단일 지점은 `DepositAccountLookup`이다.
- **없는 계좌·타인 계좌·예금이 아닌 계좌는 모두 같은 `RESOURCE_NOT_FOUND` 404다.** 셋을 구분하면 응답만으로 타인 계좌번호의 존재를 확인할 수 있게 된다.
- 계좌번호는 `accountNoMasked`만 반환한다. 원문·해시·암호문·외부 `userKey`를 응답, 로그, 오류 메시지에 남기지 않는다.
- 성공 응답은 `ApiResponse`다. 4개 모두 단건이라 `PageResponse`를 쓰지 않는다. 새 응답 형식과 새 `ErrorCode`를 만들지 않았다.
- 무인증은 401, 입력 검증 실패는 400 공통 오류다. Controller 수치·형식 검증은 `VALIDATION_ERROR`, 빈 문자열 거부는 `INVALID_REQUEST`다.
- 금액은 원 단위 `long`, 금리·비율은 `BigDecimal`이다. 부동소수점 계산을 하지 않는다.
- 기준일은 요청마다 `DepositClock`(Asia/Seoul)에서 한 번 얻어 응답 `asOfDate`와 계산에 같은 값을 쓴다. 서비스가 `LocalDate.now()`를 직접 부르지 않는다. 535의 `DepositSavingClock`과 같은 패턴이며, 병렬 작업 중 공용 파일을 건드리지 않기 위해 도메인마다 따로 둔다.
- **계산할 수 없을 때 0원을 반환하지 않는다.** 0원은 "이자가 없다"는 뜻이고 계산 불가는 `status = UNAVAILABLE`과 `unavailableReasons`로 구분한다. 대출 분석(532·533)과 같은 어휘다.
- nullable 필드는 응답에서 제거하지 않고 null로 유지한다.

## 입력 검증과 기본값

| API | 입력 | 기본값 | 허용 값 |
| --- | --- | --- | --- |
| 공통 | `accountNo` (Path) | 없음 | 숫자 10~20자리 |
| 금리비교 | `term` | 보유 계약 기간 | 1~120 (개월) |
| 금리비교 | `industry` | 보유 계좌의 업권 | `^[A-Z][A-Z_]{0,29}$` |
| 금리비교 | `topN` | 5 | 1~20 |

- `accountNo`는 숫자만 받는다. 은행별 자릿수 차이를 흡수하도록 범위를 넓게 두고 실제 소유 여부는 해시 조회로 판별한다. 대출·마이너스통장 컨트롤러와 같은 패턴이다.
- `term`, `industry`, `topN`의 **빈 문자열은 400이다.** 생략과 빈 값은 다른 입력이며, Spring이 빈 값을 생략으로 바꾸는 것을 `@RequestParam Map` 확인으로 막는다.
- `topN`은 **표시만 자른다.** 기준금리와 `comparableProductCount`는 전체 후보로 계산한다.
- 중도해지·만기예상에는 입력이 없다. 명세에 `Path: accountNo`만 있고 임의의 시뮬레이션 파라미터를 추가하지 않았다.
- 명세에 없는 입력을 새로 만들지 않았다. 535가 목록에 `page`/`size`를 보완한 것과 달리 여기서는 보완 입력이 없다.

## 예금 상세 — `GET /api/v1/deposits/{accountNo}`

| 필드 | 의미 |
| --- | --- |
| `asOfDate` / `dataSource` | 기준일 / `LOCAL_DEMO_DB` |
| `accountId`, `accountName`, `bankCode`, `bankName`, `accountNoMasked` | 계좌 식별 정보 |
| `accountType` / `status` | 항상 `DEPOSIT` / `ACTIVE`·`INACTIVE`·`CLOSED` |
| `balance` | 원금 잔액. 이자를 포함하지 않는다 |
| `detailsAvailable` | `deposit_saving` 상세행 존재 여부 |
| `interestRate`, `openingDate`, `expiryDate` | 계약이율(연 %), 개설일, 만기일 |
| `contractMonths` / `contractDays` | 개설일~만기일의 만 개월 수 / 일수 |
| `daysUntilMaturity` / `matured` | 기준일~만기일 남은 일수(지났으면 음수) / 만기 도달 여부 |
| `productId`, `productName`, `institutionName`, `savingType`, `productSyncedAt` | 연결 상품 정보 |

- `account`를 기준으로 `deposit_saving`을 LEFT JOIN한다. **상세행이 없는 계좌도 200으로 반환하며** `detailsAvailable=false`, 금리·기간 필드는 모두 null이다. 404로 감추면 535 목록에는 보이는 계좌가 상세에서 사라진다.
- 만기일이 없으면 `contractMonths`, `contractDays`, `daysUntilMaturity`, `matured`가 모두 null이다. 0이나 false로 대체하지 않는다.
- 상품이 연결되지 않았거나 카탈로그가 적재되지 않았으면 상품 필드가 null이다. 상세 조회 자체는 성공한다.
- **상품 조회에 유형 조건(`DEPOSIT`)을 함께 넣는다.** `deposit_saving`의 FK는 `financial_product`를 가리키기만 하고 유형을 제약하지 않아, 잘못 연결된 대출·카드 상품명이 예금 상세에 실릴 수 있다. 그 경우 상품 필드는 null이다.
- `CLOSED` 계좌도 조회된다. 해지 이력을 감추지 않는다.

## 예금 시장금리 비교 — `GET /api/v1/deposits/{accountNo}/rate-comparison`

### 비교군과 기준금리

- 비교군은 **연결 상품의 `saving_type`**으로 정한다. `deposit_saving`에 예금 유형 컬럼이 없어 연결 상품 없이는 확정금리와 지수연동을 구분할 수 없다. 대출 비교가 `loan_type`을 쓰는 것과 같은 구조다.

  | 비교군 | 포함 `saving_type` |
  | --- | --- |
  | `FIXED` | `FIXED_DEPOSIT` |
  | `MARKET_LINKED` | `INDEX_LINKED_DEPOSIT`, `MARKET_LINKED_DEPOSIT` |
  | `PENSION` | `PENSION_DEPOSIT` |
  | `ROTATING` | `ROTATING_DEPOSIT` |

  확정금리와 연동형을 같은 모집단에 넣지 않는다. 연동형의 공시 `rate`는 최저 보장 수준이라 나란히 두면 기준금리를 끌어내린다. 연금예금은 자격·수령 구조가, 회전식은 회전주기마다 금리가 갱신되는 점이 다르다.

- 기간은 밴드로 묶는다: `<=3`, `4-6`, `7-12`, `13-24`, `>24`. 정확 일치를 요구하지 않는 이유는 데이터 분포다. 시연 예금 옵션 56건이 1·3·6·9·12·24·36·48·60개월에만 몰려 있어 9개월 옵션은 1건뿐이고, 정확 일치를 요구하면 표준 개월이 아닌 계약은 항상 비교 불가가 된다. 채택한 옵션의 실제 `term`을 후보마다 반환해 근거를 남긴다.
- 업권은 `financial_company.sector`이며 `account.bank_code`를 `company_code`와 같은 값으로 본다. 별도 대응표가 없다.
- 기준금리 산정은 `DISCLOSED_BASE_RATE_MEDIAN`이다.
  1. 상품별로 밴드에 드는 옵션의 **기본금리(`option.rate`) 중앙값**을 대표값으로 만든다. 옵션 수가 많은 상품이 기준금리를 더 끌어당기지 않게 상품 단위로 한 번 접는다.
  2. 상품 대표값들의 중앙값이 기준금리다. 짝수 개면 가운데 두 값의 산술평균이며 소수 넷째 자리 HALF_UP이다.
  3. **보유 상품 자신은 모집단에서 제외한다.** 넣으면 자기 금리와 비교하게 된다.
- 대출 비교가 옵션 `min_rate`를 쓰는 것과 달리 예금은 `rate`를 쓴다. 시연 데이터의 예금 옵션 56건은 **전부 `rate`가 채워져 있고 옵션 단위 `min_rate`·`max_rate`는 전부 NULL**이다. 예금 공시는 기간별 단일 금리라 범위를 쓰지 않는다.
- `preferred_rate`는 판정에 쓰지 않는다. 값이 있는 옵션이 소수이고 우대 조건 충족 여부를 보유 예금 쪽에서 확인할 수 없다. 섞으면 조건을 채우지 못한 사용자의 금리를 우대금리와 비교하게 된다. 근거 옵션의 우대금리는 후보에 그대로 실어 상한만 보여 준다.
- 서로 다른 상품이 2개 미만이면 기준금리를 만들지 않는다(`confidence`가 `LOW`인 하한). 후보 목록은 그대로 유지한다. 표본 부족과 후보 없음은 다른 상태다.

### 응답

| 필드 | 의미 |
| --- | --- |
| `asOfDate` / `assumptions` | 기준일 / 가정 코드(오름차순) |
| `accountId`, `accountName`, `accountNoMasked`, `bankName`, `balance`, `interestRate` | 보유 예금 |
| `status` | `COMPLETE`·`PARTIAL`·`UNAVAILABLE` |
| `unavailableReasons` | 아래 사유 코드. 오름차순 |
| `comparisonGroup` / `termBand` / `industry` | 확정된 비교 조건 |
| `referenceMethod` / `referenceRate` / `confidence` | `DISCLOSED_BASE_RATE_MEDIAN` / 기준금리 / `NORMAL`·`LOW` |
| `rateDifference` | **보유 금리 − 기준금리** |
| `comparisonDirection` | 항상 `HIGHER_IS_BETTER` |
| `comparableProductCount` | 조건에 맞은 상품 수. `topN`과 무관 |
| `unmatchedConditions` | 판정에 쓰지 않은 조건. **전체 후보의 합집합**이며 오름차순 |
| `oldestSyncedAt` / `latestSyncedAt` | 후보 상품 자료의 동기화 시점 범위 |
| `candidates[]` | `{productId, productName, institutionName, sourceType, representativeRate, disclosedRate, disclosedPreferredRate, term, rateType, syncedAt, unmatchedConditions}` |

`rateDifference`의 **부호 해석은 대출과 반대다.** 예금은 금리가 높을수록 유리하므로 양수가 유리하다. 같은 필드명을 쓰는 대출 API와 혼동하지 않도록 `comparisonDirection`을 함께 반환한다.

후보 정렬은 대표값 오름차순 → `productId` 오름차순이다. `topN`으로 앞에서 자른다.

`unmatchedConditions`는 **두 층에 있다.** 후보 항목의 값은 그 후보에만 해당하고, 응답 상위의 값은 전체 후보의 합집합이다. 합집합은 `topN`으로 자르기 전 후보 전체를 기준으로 하므로 `comparableProductCount`와 같은 모집단을 가리킨다. 어떤 상품이 어긋났는지는 후보 항목의 값으로 판단한다.

| 사유 코드 | 뜻 | `status` |
| --- | --- | --- |
| `NOT_COMPARABLE_STATUS` | `CLOSED` 계좌. 견줄 현재 금리가 없다 | `UNAVAILABLE` |
| `MISSING_DEPOSIT_DETAIL` | `deposit_saving` 상세행이 없다 | `UNAVAILABLE` |
| `INVALID_TERMS` | 계약이율이 없거나 음수다 | `UNAVAILABLE` |
| `MISSING_DEPOSIT_TERM` | 만기일이 없어 계약 기간을 모르고 `term`도 없다 | `UNAVAILABLE` |
| `DEPOSIT_TERM_OUT_OF_COMPARABLE_RANGE` | 계약 기간은 알지만 비교 범위(1~120개월) 밖이다 | `UNAVAILABLE` |
| `COMPARISON_GROUP_UNKNOWN` | 연결 상품이 없거나, 예금 상품이 아니거나, 상품의 `saving_type`이 비었다 | `UNAVAILABLE` |
| `UNSUPPORTED_DEPOSIT_TYPE` | 위 4개 비교군에 없는 `saving_type` | `UNAVAILABLE` |
| `INDUSTRY_UNKNOWN` | 보유 업권을 모르고 `industry`도 없다 | `UNAVAILABLE` |
| `INSUFFICIENT_COMPARABLE_PRODUCTS` | 조건에 맞은 상품이 2개 미만 | `PARTIAL` |

`UNAVAILABLE`이면 비교 조건과 금액 필드가 모두 null, `comparableProductCount`는 0, `candidates`는 빈 배열이다. `PARTIAL`이면 `referenceRate`·`rateDifference`만 null이고 후보는 유지한다. 보유 업권을 모를 때 요청 업권을 보유 업권으로 간주하지 않는다.

| `unmatchedConditions` 코드 | 뜻 |
| --- | --- |
| `TERM_BAND_MATCH` | 채택 옵션의 `term`이 보유 계약 기간과 정확히 같지 않다. 후보 단위로 판정한다 |
| `RATE_TYPE_NOT_MATCHED` | 확정·변동 조건을 판정에 쓰지 않았다. 보유 예금에 대응 값이 없다. 후보 단위로 판정한다 |
| `OTHER_INDUSTRY_REFERENCE` | 보유 업권과 다른 업권으로 비교했다. 요청 단위 조건이라 응답 상위에만 붙는다 |

| `assumptions` 코드 | 뜻 |
| --- | --- |
| `DISCLOSED_SNAPSHOT_NOT_REALTIME` | 상품 자료는 저장된 동기화 스냅샷이다 |
| `EXCLUDES_PREFERENTIAL_RATE` | 우대금리·금리쿠폰을 반영하지 않는다 |
| `REFERENCE_IS_NOT_APPLIED_RATE` | 기준금리는 사용자가 받을 적용금리가 아니다 |

**현재 시연 데이터에서는 `FIXED`와 `MARKET_LINKED`만 비교가 성립한다.** 예금 상품 15개 중 `FIXED_DEPOSIT`이 10개, `MARKET_LINKED` 계열이 3개, `PENSION`과 `ROTATING`은 각각 1개다. 표본이 모자란 군은 조용히 다른 군에 섞지 않고 `INSUFFICIENT_COMPARABLE_PRODUCTS`로 남긴다.

### 대출 비교와 의도적으로 다른 점

계층 구조와 판정 어휘는 대출 금리비교(533)를 따랐지만 세 가지는 일부러 다르게 했다.

- 기준금리에 옵션 `min_rate` 대신 `rate`를 쓴다. 예금 옵션에는 `min_rate`가 없다.
- `rateDifference`의 부호 해석이 반대이며 `comparisonDirection`으로 명시한다.
- **미일치 조건을 후보 단위로 판정한다.** 대출 비교는 상품 순회 중 하나의 Set에 모아 후보 하나만 어긋나도 응답 전체에 붙는데, 그러면 조건이 정확히 맞은 상품과 어긋난 상품을 구분할 수 없다. 예금은 기간별 금리 차이가 커서 이 구분이 실제로 필요하다. 응답 상위 값은 합집합으로 유지해 기존 대출 계약과 같은 의미를 함께 제공한다.

이 API는 참고 지표만 만든다. 갈아타기 가능 여부, 예상 이자 차액, 절감액을 계산하지 않는다.

## 이자 계산 가정

명세·ERD·기획안에 **계산식이 없다.** 확인한 근거는 다음과 같다.

- 명세 11-1은 Endpoint·Method·인증·Parameter·제한사항만 정하고 계산식을 쓰지 않는다.
- ERD의 `deposit_saving`에는 계약이율(`interest_rate`)만 있고 중도해지이율·복리주기·이자지급방식 컬럼이 없다. `financial_product_option`에도 대응 항목이 없다.
- 기획안 7-1은 "중도해지·추가납입 등 선택 비교"라고만 적혀 있다.

그래서 아래 기본안을 적용했고 응답의 `assumptions`·`ratePolicy`·`rateTier`로 노출한다. **추정한 부분을 숨기지 않는다.**

### 공통 계산식 (`ACT_365_SIMPLE`)

```text
세전이자 = floor(원금 × 적용이율 ÷ 100 × 일수 ÷ 365)
이자소득세 = floor(세전이자 × 15.4 ÷ 100)
세후이자 = 세전이자 − 이자소득세
예상수령액 = 원금 + 세후이자
```

- **단리·만기일시지급**으로 계산한다. 복리 주기나 이자지급 방식을 나타내는 컬럼이 없다.
- 나눗셈을 한 번만 해서 중간 반올림을 없애고 **원 단위 절사는 마지막에 한 번**만 한다. 1원 미만 이자는 0이 되며 반올림으로 1원을 만들지 않는다.
- **윤년에도 365로 나눈다.** 366일 예치는 이자가 365일분보다 많아진다.
- 세액도 절사한다. 세후를 먼저 절사하면 `세전 ≠ 세액 + 세후`가 되어 1원이 어긋난다.
- 세율은 **이자소득세 15.4%**(소득세 14% + 지방소득세 1.4%)다. 정책 코드는 `GENERAL_INTEREST_INCOME_TAX_15_4`다. 비과세·세금우대·분리과세와 금융소득종합과세는 반영하지 않는다. 사용자별 과세 구분 자료가 없다.
- 원금은 `account.balance`다. 524 seed의 규약대로 이자가 포함되지 않은 값이다.
- 우대금리·금리쿠폰은 반영하지 않는다. 충족 여부를 확인할 자료가 없다.
- 이자나 수령액이 `long` 범위를 넘으면 `CALCULATION_AMOUNT_OUT_OF_RANGE`로 계산 불가 처리한다. 0원이나 잘린 값으로 바꾸지 않는다.

### 중도해지이율 (`TIERED_CONTRACT_RATE_RATIO`)

국내 시중은행이 정기예금에 널리 쓰는 **경과기간 구간별 계약이율 차등** 관행을 기본안으로 삼았다. **상품별 공시 중도해지이율이 아니라 서비스가 정한 추정치다.** 적금(537)과 같은 표이며 2026-09-16에 `global/policy/EarlyTerminationRatePolicy`로 합쳤다. 세율·절사 규칙도 `InterestIncomeTaxPolicy`로 공유한다.

| 경과 개월 (`rateTier`) | 적용 중도해지이율 |
| --- | --- |
| 1개월 미만 (`ELAPSED_MONTHS_LT_1`) | 연 0.10% 정액 |
| 1개월 이상 (`ELAPSED_MONTHS_GTE_1`) | 계약이율 × 20% |
| 3개월 이상 (`ELAPSED_MONTHS_GTE_3`) | 계약이율 × 30% |
| 6개월 이상 (`ELAPSED_MONTHS_GTE_6`) | 계약이율 × 40% |
| 9개월 이상 (`ELAPSED_MONTHS_GTE_9`) | 계약이율 × 60% |
| 11개월 이상 (`ELAPSED_MONTHS_GTE_11`) | 계약이율 × 80% |

- 어떤 구간에서도 **최저 연 0.10%를 보장한다.** 저금리 계약은 비율을 곱하면 최저이율보다 낮아진다.
- 최고 구간이 80%이므로 만기를 넘겨 유지해도 중도해지이율이 계약이율을 넘지 않는다.
- 경과 개월은 개설일부터 기준일까지의 만 개월 수다.
- 실제 상품의 공시 중도해지이율이 확보되면 이 표 대신 상품별 값을 쓴다.

### 일수 기준

| 계산 | 일수 |
| --- | --- |
| 중도해지 | 개설일 ~ 기준일 (`elapsedDays`) |
| 만기 예상 | 개설일 ~ 만기일 (`contractDays`) |

중도해지의 예치일수는 **개설일부터 해지일 전일까지**다. 개설 당일 해지는 0일이라 이자가 붙지 않고 원금만 돌려준다. 국내 은행의 일수 계산 관행을 따랐다.

## 예금 중도해지 예상금액 — `GET /api/v1/deposits/{accountNo}/early-termination`

| 필드 | 의미 |
| --- | --- |
| `asOfDate` / `assumptions` | 기준일 / 가정 코드(오름차순) |
| `accountId`, `accountName`, `accountNoMasked`, `bankName` | 계좌 식별 정보 |
| `status` / `unavailableReasons` | `COMPLETE`·`UNAVAILABLE` / 사유 코드 |
| `principal` / `contractRate` | 원금 / 계약이율(연 %) |
| `openingDate` / `expiryDate` | 개설일 / 만기일 |
| `elapsedDays` / `elapsedMonths` | 예치일수 / 경과 만 개월 수 |
| `appliedRate` / `ratePolicy` / `rateTier` | 적용 중도해지이율 / 정책 코드 / 적용 구간 |
| `grossInterest` / `tax` / `netInterest` | 세전이자 / 이자소득세 / 세후이자 |
| `estimatedPayoutAmount` | 원금 + 세후이자 |

| 사유 코드 | 뜻 |
| --- | --- |
| `ACCOUNT_CLOSED` | 이미 해지된 계좌 |
| `ZERO_PRINCIPAL` | 원금이 0 이하 |
| `MISSING_DEPOSIT_DETAIL` | `deposit_saving` 상세행이 없다 |
| `INVALID_TERMS` | 계약이율 없음·음수, 개설일 없음, 개설일이 기준일보다 미래, 만기일이 개설일보다 과거 |
| `ALREADY_MATURED` | 기준일에 이미 만기가 지났다. 중도해지 대상이 아니다 |
| `CALCULATION_AMOUNT_OUT_OF_RANGE` | 금액이 `long` 범위를 넘었다 |

- 사유는 여러 개가 함께 나올 수 있으며 코드명 오름차순이다. 예를 들어 해지된 잔액 0 계좌는 `ACCOUNT_CLOSED`, `ALREADY_MATURED`, `ZERO_PRINCIPAL`이 함께 나온다.
- 상세행이 없으면 계약 조건을 검증할 수 없어 그 뒤의 판정을 건너뛴다. 계좌 상태·원금 사유는 그보다 먼저 판정하므로 `MISSING_DEPOSIT_DETAIL`과 함께 나올 수 있다.
- 만기일이 없어도 중도해지는 계산할 수 있다. 만기일은 `ALREADY_MATURED` 판정에만 쓴다.
- `UNAVAILABLE`이면 `elapsedDays`부터 `estimatedPayoutAmount`까지 모두 null이고 `ratePolicy`만 유지한다. 계좌 식별 정보와 계약 조건은 알고 있으므로 그대로 반환한다.
- **실제 해지 금액은 은행이 확정한다.** 이 값은 참고 추정치이며 중도해지 수수료를 반영하지 않는다.

## 예금 만기 예상금액 — `GET /api/v1/deposits/{accountNo}/maturity-forecast`

| 필드 | 의미 |
| --- | --- |
| `asOfDate` / `assumptions` | 기준일 / 가정 코드(오름차순) |
| `accountId`, `accountName`, `accountNoMasked`, `bankName` | 계좌 식별 정보 |
| `status` / `unavailableReasons` | `COMPLETE`·`UNAVAILABLE` / 사유 코드 |
| `principal` / `contractRate` | 원금 / 계약이율(연 %) |
| `openingDate` / `expiryDate` / `contractDays` | 개설일 / 만기일 / 계약일수 |
| `daysUntilMaturity` / `matured` | 남은 일수(지났으면 음수) / 만기 도달 여부 |
| `grossInterest` / `tax` / `netInterest` | 세전이자 / 이자소득세 / 세후이자 |
| `estimatedPayoutAmount` | 원금 + 세후이자 |

- 계약이율을 만기까지 유지했을 때의 금액이다. **만기 후 이율(경과기간별 하락)은 반영하지 않는다.**
- **이미 만기가 지났어도 계산해서 `COMPLETE`로 반환하고 `matured=true`로 표시한다.** 계약 기준 수령액은 이미 정해져 있으므로 `UNAVAILABLE`로 감추는 것보다 유용하다. 중도해지 API가 같은 상태를 `ALREADY_MATURED`로 거부하는 것과 의도적으로 다르다.
- 사유 코드는 중도해지와 같고 `ALREADY_MATURED`가 없으며 `MISSING_EXPIRY_DATE`가 추가된다. 만기일 없이는 계약일수를 만들 수 없다.
- `assumptions`는 `EXCLUDES_PREFERENTIAL_RATE`, `SIMPLE_INTEREST_AT_MATURITY`, `TAXED_AT_GENERAL_RATE`다. 중도해지에는 `ESTIMATED_EARLY_TERMINATION_RATE`가 하나 더 붙는다. 그 이율만 서비스 추정치이고 만기 계산은 저장된 계약이율을 그대로 쓴다.

## 스키마와 seed

- **새 마이그레이션을 추가하지 않았다.** 535의 `V15__create_deposit_saving_and_savings_payment.sql`과 V3 `account`, V10 `financial_product`로 충분하다. `deposit_saving`을 읽기만 하고 쓰지 않는다.
- 535가 만든 파일(`DepositSaving`, `SavingsPayment`, `DepositSavingQueryRepository`/`Service`/`Controller`, V15 SQL, `deposit-saving-test-data.sql`)을 수정하지 않았다. `global/` 아래에 새 파일을 만들지 않았고 `ErrorCode`에 항목을 추가하지 않았다.
- 535와 같은 이유로 `account`의 쓰기 엔티티를 중복 생성하지 않고 `DepositAccountReader`가 JDBC로 필요한 컬럼만 읽는다. `account`에는 이미 부분 매핑 엔티티가 둘(`CardWithdrawalAccount`, `MinusAccountOwner`) 있어 세 번째를 더하지 않는다.
- 상품 조회는 요청당 예금 상품 전량을 한 번 읽는다(`DepositMarketProductReader`). 시연 데이터의 예금 상품은 15개·옵션 56건이고 `financial_product_option`에는 `product_id` 인덱스만 있어 조건을 DB로 내리는 이점이 없다. 대출의 `LoanMarketProductReader`와 같은 판단이다. 상품 수가 크게 늘면 재검토한다.
- 시연 예금은 `DODAM_DEPOSIT_SAVING_SEED.sql`(524)의 4개 변형이다. 계약이율 3.15/3.05/3.30/3.20%, 기간 12/24/12/36개월, 원금 1,000만/2,000만/500만/3,000만원이며 모두 `FIXED_DEPOSIT` 상품에 연결된다. 생애주기는 진행 2 / 만기 미인출 1 / 해지 1 순환이다. seed는 2026-09-07 스냅샷이다.
- seed 파일과 루트 `docs/`의 명세서·ERD를 수정하지 않았다.

## 검증 방법과 결과

```powershell
$env:JAVA_HOME = 'C:\Users\SSAFY\.jdks\ms-21.0.11'
.\gradlew.bat test --tests '*DepositApiIntegrationTest' --tests '*DepositCalculationTest'
.\gradlew.bat test
```

H2 전용 DB(`deposit536`)에 `deposit-detail-test-data.sql` fixture를 매 테스트 재적재한다. 535의 fixture를 공유하지 않고 새 파일로 분리했다. fixture 계좌번호는 `10000000<계좌ID>`이며 저장 해시는 테스트가 실제 `SensitiveNumberHasher`로 다시 채운다. 서비스가 평문이 아니라 해시로만 소유자를 찾는지 함께 확인하기 위해서다.

기준일은 `DepositClock`을 대체해 2026-09-09로 고정한다. 시연 seed 날짜에 서버 시각이 끌려가지 않게 하고 예치일수·경과 개월 경계를 실제 실행일과 무관하게 단정하기 위해서다.

2026-09-10 검증 결과:

- **관련 테스트 69건 통과**: API 통합 49건(`DepositApiIntegrationTest`), 계산·기준일 단위 20건(`DepositCalculationTest`).
- 포함한 범위: 정상 흐름, `term`/`topN` 경계값(0·1·20·21·121·비숫자·빈 값), `industry` 형식·빈 값, `accountNo` 자릿수 경계(9·10·20·21자리·비숫자), 무인증 401 4경로, 소유권 격리(타인·적금·없는 계좌 × 4경로 = 12건 모두 동일 404), 상세행 없음, 만기일 없음, 계약 기간 0개월·120개월 초과, 해지 계좌, 예금이 아닌 상품 연결, 예치일수 0일, 중도해지이율 구간 경계 11건과 최저이율 하한, 윤년 366일, 1원 미만 절사, `long` 범위 초과.
- 오류 본문 유출 검증: 형식 오류 3종과 없는·타인·적금 계좌 3종 × 4경로 = 24개 요청의 400·404 본문에 계좌번호 원문과 해시가 모두 없음을 단정한다. `@Pattern` 기본 메시지는 정규식만 담지만 메시지를 바꾸거나 핸들러가 rejectedValue를 넣으면 조용히 유출로 바뀌는 지점이라 계약으로 고정했다.
- 후보 단위 미일치 조건 검증: 같은 응답에서 조건이 맞은 후보는 빈 배열, `rate_type`이 있는 후보만 `RATE_TYPE_NOT_MATCHED`를 갖는 것을 확인했다.
- 응답 본문에 계좌번호 원문·해시·암호문이 없음을 문자열 단정으로 확인했다.
- 금액 단정은 계산식으로 손검산한 값이다. 1,000만원·3.15%·365일 → 세전 315,000 / 세금 48,510 / 세후 266,490 / 수령 10,266,490. 경과 8개월 중도해지(1.26%·251일) → 세전 86,646 / 세금 13,343 / 세후 73,303 / 수령 10,073,303.
- 전체 `gradlew.bat test`: 471건 중 438건 통과, 3건 생략(MySQL 전용), 30건 실패. **실패 30건은 모두 536 범위 밖의 기존 결함이다.** 29건은 535의 `DepositSavingApiIntegrationTest`, 1건은 `CardQueryApiIntegrationTest.getsBenefitsAndEstimatedBenefitUsage`이며 후자는 535 문서에 이미 기록된 날짜 의존 실패다.
- 전체 건수에는 같은 작업 트리에서 병행 중인 537(적금)의 진행 중 코드가 포함된다. 536 파일만 격리해 실행한 결과가 위의 61건이다.

### 미검증 범위와 남은 위험

- **MySQL 전용 검증을 하지 않았다.** 새 마이그레이션과 새 테이블이 없어 검증할 DDL 변경이 없고, 조회 SQL은 표준 문법(LEFT JOIN, 파라미터 바인딩)만 쓴다. H2 통과가 MySQL 제약·타입 검증을 대신하지는 않는다. MySQL로 확인하려면 `dodam_536_test` 스키마를 쓴다.
- **금융상품 카탈로그가 적재되지 않은 환경에서의 실제 시연 조회는 확인하지 않았다.** 금리비교 테스트는 fixture 상품으로 검증했다. 카탈로그가 없으면 금리비교가 `COMPARISON_GROUP_UNKNOWN`으로 떨어지고 상세의 상품 필드가 null이 된다.
- **중도해지이율 구간표와 세율 15.4%는 서비스가 정한 기본안이다.** 실제 상품 공시와 사용자별 과세 구분을 반영하지 않으므로 표시 금액이 은행 확정 금액과 다를 수 있다. 화면에서 참고값임을 함께 알려야 한다.
- 기존 `DepositSavingApiIntegrationTest`(535)는 이 브랜치에서 실패하지만 **536과 무관한 기존 결함**이다. `origin/develop`(`aff8056`)을 별도 worktree로 복사해 같은 실패를 재현했다. 원인은 fixture의 `account` INSERT가 `currency`를 채우지 않는데 H2가 `MinusAccountOwner` 매핑으로 `account`를 만들면 `currency`가 NOT NULL이 되는 것이다. `account`를 부분 매핑한 엔티티가 둘이라 어느 쪽으로 DDL이 생성되는지에 따라 결과가 갈린다. 535 범위 밖이라 수정하지 않았다.
- 커밋·push·MR·배포를 수행하지 않았다.
