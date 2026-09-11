# Spending API — 소비 집계·분석

JIRA `S15P21D105-454` (D5 Transaction·Spending API 구현)의 하위 티켓 `S15P21D105-506`(API 6 소비 기간·카테고리 집계),
`S15P21D105-507`(API 6 목표·이상소비·결제수단 분석) 구현 기준이다. API 명세 6 Transaction / Spending 중 `/api/v1/spending/**`
11개를 모두 다룬다. 거래 원장 조회·편집·수동 거래는 [transaction-query.md](transaction-query.md)를 본다.

| 티켓 | 엔드포인트 |
| --- | --- |
| 506 집계 | monthly-summary, progress, daily, categories, categories/trend, payment-methods |
| 507 분석 | saving-ratio, anomalies, goal-comparison, fixed, variable |

결제수단별 소비는 507 제목("결제수단 분석")에 들어 있지만 집계 성격이라 506에서 먼저 구현했다. 고정·변동지출은 반대로 506 제목에
가깝지만 남아 있던 것을 507에서 마무리했다.

## "소비"의 정의

모든 API가 같은 정의를 쓴다. 바꿀 때는 `JdbcSpendingReader.SPENDING_WHERE` 한 곳만 고친다.

| 조건 | 이유 |
| --- | --- |
| `transaction_type = 'EXPENSE'` | 수입(INCOME)과 계좌 간 이체(TRANSFER)는 소비가 아니다 |
| `spending_type IS NULL OR spending_type <> 'NON_SPENDING'` | 저축·투자, 이체 성격 카테고리(SAVING, TRANSFER, INCOME)의 지출은 소비에서 뺀다. 미분류(NULL)는 소비로 본다 |
| `hidden = 0` | 숨긴 거래 제외. 카드(8)·계좌(5) 집계와 같은 규칙 |
| 사용자 원장 전체 | 계좌·카드·수동 거래를 가리지 않는다 |

기획안 9-1의 "고정지출·변동지출"은 `spending_type`으로 나눈다(아래 507 절).

## API

모든 API는 인증이 필요하고 본인 거래만 집계한다.

| Method | Endpoint | 파라미터 | 정책 |
| --- | --- | --- | --- |
| GET | `/api/v1/spending/monthly-summary` | `yearMonth?` | `YearMonthPolicy`(YYYY-MM, 기본 이번 달, 미래 월 400) |
| GET | `/api/v1/spending/progress` | `yearMonth?` | 〃 |
| GET | `/api/v1/spending/daily` | `startDate?`, `endDate?` | `QueryPeriodPolicy`(기본 최근 3개월, 최대 12개월, 오늘까지) |
| GET | `/api/v1/spending/categories` | `yearMonth?` | `YearMonthPolicy` |
| GET | `/api/v1/spending/categories/trend` | `startMonth?`, `endMonth?`, `category?` | `YearMonthPeriodPolicy`(기본 최근 3개월, 최대 12개월) |
| GET | `/api/v1/spending/payment-methods` | `yearMonth?` | `YearMonthPolicy` |
| GET | `/api/v1/spending/saving-ratio` | `yearMonth?` | `YearMonthPolicy` |
| GET | `/api/v1/spending/anomalies` | `startMonth?`, `endMonth?`, `category?` | `YearMonthPeriodPolicy` |
| GET | `/api/v1/spending/goal-comparison` | `goalId?`, `yearMonth?` | `YearMonthPolicy`; `goalId` ≥ 1 |
| GET | `/api/v1/spending/fixed` | `startMonth?`, `endMonth?` | `YearMonthPeriodPolicy` |
| GET | `/api/v1/spending/variable` | `startMonth?`, `endMonth?` | `YearMonthPeriodPolicy` |

### 오류 코드

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | 연월 파라미터가 7자를 넘거나 숫자·하이픈 외 문자, `category` 형식 위반 |
| `INVALID_REQUEST` | 400 | 연월 형식(YYYY-MM) 위반, 미래 월, 시작월 > 종료월, 시작일 > 종료일, 기간 12개월 초과, 없는 카테고리 코드 |
| `RESOURCE_NOT_FOUND` | 404 | `goalId`가 본인 소비목표가 아니거나 삭제됨 |
| `UNAUTHORIZED` | 401 | 토큰 없음·만료 |

거래가 없어도 404가 아니다. 합계 0, 빈 목록으로 200을 돌려준다.

## 월 소비 요약 `GET /spending/monthly-summary`

| 필드 | 값 |
| --- | --- |
| `totalSpending`, `transactionCount` | 그 달 소비 합·건수 |
| `daysElapsed` | 이번 달이면 오늘까지 일수, 지난 달이면 그 달 전체 일수 |
| `daysInMonth` | 그 달 일수 |
| `dailyAverage` | `totalSpending / daysElapsed` 반올림 |
| `averagePerTransaction` | `totalSpending / transactionCount` 반올림, 거래가 없으면 0 |
| `previousMonthSpending` | 전월 전체 소비 |
| `changeFromPreviousMonth` | `totalSpending − previousMonthSpending` |
| `changeRatePercent` | 전월 대비 증감률(%, 소수 첫째 자리). 전월 소비가 0이면 null |

## 월 누적 소비 `GET /spending/progress`

- 월 1일부터 기준일(`asOfDate`: 이번 달이면 오늘, 지난 달이면 말일)까지 **하루씩** `days[]`를 채운다. 거래가 없는 날은 0이다.
- 각 날에 `dailySpending`, `transactionCount`, `cumulativeSpending`(그날까지 누적)을 준다.
- `projectedMonthEnd`는 `totalSpending × daysInMonth / daysElapsed`(반올림)로 한 번에 계산한다. 반올림한 일평균에 일수를
  곱하면 지난 달 합계와 어긋나기 때문이다. 지난 달은 `totalSpending`과 같다.

## 일별 소비 `GET /spending/daily`

기간 안의 모든 날을 오래된 순으로 채운다(거래 없는 날 0). `totalSpending`, `transactionCount`, `dailyAverage`(기간 일수 기준)를 함께 준다.

## 카테고리별 소비 `GET /spending/categories`

- 소비 금액 내림차순. 각 항목은 `categoryId`, `code`, `name`, `totalSpending`, `transactionCount`, `sharePercent`(합계 대비, 소수 첫째 자리).
- 카테고리가 없는 거래는 `categoryId = null`, `code = "UNCATEGORIZED"`, `name = "미분류"`로 한 묶음이다. FE가 "분류하기" 진입점으로 쓸 수 있다.

## 카테고리 소비 추이 `GET /spending/categories/trend`

- 기간 안의 모든 달을 오래된 순으로 채운다. 달마다 `totalSpending`, `transactionCount`, `categories[]`(그 달 카테고리별 소비, 내림차순).
- `category`(코드, 대소문자 무시)를 주면 그 카테고리만 남긴다. `UNCATEGORIZED`도 받는다. 없는 코드는 400.
- `totalSpending`은 기간 합, `monthlyAverage`는 기간 개월 수로 나눈 값(반올림).

## 결제수단별 소비 `GET /spending/payment-methods`

소비 금액 내림차순. `paymentMethod`는 저장값 그대로(CARD, ACCOUNT, CASH 등)이고 값이 없는 거래는 `"UNKNOWN"`으로 한 묶음이다.
`sharePercent`는 합계 대비 비중(소수 첫째 자리).

## 소비 및 저축 비율 `GET /spending/saving-ratio` (507)

기획안 「소비 : 저축 목표 비율 대비 현재 비율」.

| 필드 | 값 |
| --- | --- |
| `income`, `incomeSource` | 분모. 금융 기본정보 `financial_profile.monthly_income`이 있으면 그 값(`PROFILE`), 없으면 그 달 INCOME 거래 합(`TRANSACTIONS`), 둘 다 없으면 0(`NONE`) |
| `spending` | 그 달 소비(506 정의) |
| `savingTransfer` | 소비에서 제외한 저축성 지출(`spending_type = NON_SPENDING`인 EXPENSE) 합. 참고용 |
| `remaining` | `income − spending`. 음수면 소득보다 더 썼다 |
| `spendingRatioPercent`, `savingRatioPercent` | `spending / income`, `remaining / income`(기획안의 저축률). 소득이 0이면 null |
| `targetSpendingRatioPercent`, `targetSavingRatioPercent` | ACTIVE 소비목표의 `monthly_budget` 합 / income, 그리고 100 − 그 값. 목표나 소득이 없으면 null |
| `exceedsTarget` | 소비 비율 > 목표 비율. 목표가 없으면 null |

저축률을 "소득 − 소비" 잔여로 정의한 이유: 저축 거래를 원장에서 식별하는 규약(어느 카테고리·계좌 이체가 저축인지)이 없어서다.
`savingTransfer`를 따로 주어 FE가 "실제 저축 이체" 수치도 보일 수 있게 했다.

## 소비 급증 `GET /spending/anomalies` (507)

기획안 「변동지출 급증」 확정 규칙 **최근 N개월 카테고리 평균지출 > 이전 N개월 카테고리 평균지출 × (1 + 기준 증가율)**.

- 요청 기간(기본 최근 3개월, 최대 12개월)이 "최근", 그 직전 같은 길이가 "이전"이다. 응답에 두 기간을 모두 싣는다.
- 카테고리별로 두 기간의 월평균(반올림)을 비교한다. 미분류는 `UNCATEGORIZED` 한 묶음.
- 급증 판정: 이전 평균 > 0 **AND** 증가액 ≥ `minimumIncrease`(10,000원, 기획안의 "소액 보정") **AND** 증가율 > `thresholdRatePercent`(20%).
  이전 기간 소비가 0인 카테고리(새로 생긴 소비)는 증가율을 정의할 수 없어 급증으로 잡지 않는다.
- `category`(코드)를 주면 그 카테고리만 판정한다. `evaluatedCategories`는 두 기간 중 한 곳이라도 소비가 있어 판정 대상이 된 수다.
- 기준값 두 개는 `SpendingAnalysisService` 상수다. 팀 확정 후 설정으로 뺄 수 있다.

## 소비목표 비교 `GET /spending/goal-comparison` (507)

`goal`(V5) + `spending_goal`(V7, 이 티켓에서 선반영)의 `monthly_budget`과 그 달 소비를 비교한다. 소비목표에 카테고리가 없으므로
예산은 **월 전체 소비**에 대한 것이고, 모든 목표가 같은 `spending`을 본다.

- `goalId` 없음 → ACTIVE 소비목표 전부(목표 ID 순). 목표가 없으면 빈 목록(404 아님).
- `goalId` 있음 → 그 목표 1건(PAUSED 등 상태 무관). 본인 것이 아니거나 삭제(`deleted_at`)됐으면 404.
- 목표마다 `remaining`(예산 − 소비, 음수면 초과액), `usageRatePercent`, `projectedMonthEnd`(총액 × 그 달 일수 / 경과 일수),
  `dailyBudgetRemaining`(남은 예산 / 남은 일수, 이번 달만, 초과면 0), `status`(ON_TRACK < 80% ≤ WARNING ≤ 100% < EXCEEDED).
- 지난 달은 `asOfDate`가 말일이고 `projectedMonthEnd = spending`, `dailyBudgetRemaining = null`.

## 고정지출·변동지출 `GET /spending/fixed`, `/spending/variable` (507)

`spending_type`이 FIXED / VARIABLE인 소비를 기간(기본 최근 3개월, 최대 12개월)별로 본다. 미분류(`spending_type` null)는 어느 쪽에도
들어가지 않는다(505 절 "소비유형 재계산" 참고).

- `months[]`: 기간 안 모든 달(거래 없는 달 0). `totalAmount`, `monthlyAverage`.
- `shareOfSpendingPercent`: 같은 기간 전체 소비 대비 비중.
- `latestMonthAmount`, `previousAverage`(종료월을 뺀 나머지 달 평균, 1개월이면 null), `changeRatePercent`(기획안 「고정지출 증가」 예시의
  "이전 3개월 평균 대비 N% 증가").
- `categories[]`: 기간 합 기준 카테고리 내림차순과 비중.

## 구현 메모

- 집계는 `JdbcSpendingReader`가 `transaction`을 월·일·카테고리·결제수단으로 GROUP BY해 읽고, 빈 달·빈 날 채우기와 비율 계산은
  `SpendingQueryService`가 한다. `YEAR()`/`MONTH()`는 MySQL과 H2(MODE=MySQL) 모두 지원한다.
- 기준일은 요청마다 `TransactionClock`에서 한 번 읽어 자정을 넘는 요청에서도 일관된다.
- 반올림은 모두 `RoundingMode.HALF_UP`이다.

## 테스트

`SpendingAnalysisApiIntegrationTest`(507): 소득 출처 우선순위(프로필 → INCOME 거래 → 없음)와 목표 비율 비교, 급증 판정(기준 증가율·소액 보정·
이전 기간 0 제외·카테고리 필터·기간 변경), 목표 비교(ACTIVE 전체 / 단건은 상태 무관, 타인·삭제 404, 초과·경고 상태, 지난 달 예측값),
고정·변동지출(월별 채움, 전체 소비 대비 비중, 이전 평균 대비 증감률, 1개월 null). 6개월치 거래를 달마다 1일에만 둔다.

`SpendingApiIntegrationTest`(506): 소비 정의(수입·이체·NON_SPENDING·숨김·타인 제외)가 6개 API에 같이 적용되는지, 전월 비교와 증감률(전월 0이면 null),
누적·예상 월말(지난 달은 합계와 일치), 빈 날·빈 달 채우기, 미분류·UNKNOWN 묶음, 비중 반올림, 카테고리 필터(대소문자·UNCATEGORIZED·없는 코드),
기본 기간·기간 검증을 확인한다. 날짜 의존을 피하려고 "이번 달" 거래는 1일에만 둔다.

## 남은 작업

[remaining-work.md](remaining-work.md)의 "S15P21D105-506", "S15P21D105-507" 절에 기록한다.
