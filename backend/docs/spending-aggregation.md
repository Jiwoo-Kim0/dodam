# Spending API — 소비 기간·카테고리 집계

JIRA `S15P21D105-454` (D5 Transaction·Spending API 구현)의 하위 티켓 `S15P21D105-506`(API 6 소비 기간·카테고리 집계) 구현 기준이다.
API 명세 6 Transaction / Spending 중 `/api/v1/spending/**` 11개 가운데 **집계 성격 6개**를 다룬다. 분석 성격
(소비 및 저축 비율, 소비 급증, 소비목표 비교, 고정지출, 변동지출)은 다음 티켓 범위다.
거래 원장 조회·편집·수동 거래는 [transaction-query.md](transaction-query.md)를 본다.

## "소비"의 정의

모든 API가 같은 정의를 쓴다. 바꿀 때는 `JdbcSpendingReader.SPENDING_WHERE` 한 곳만 고친다.

| 조건 | 이유 |
| --- | --- |
| `transaction_type = 'EXPENSE'` | 수입(INCOME)과 계좌 간 이체(TRANSFER)는 소비가 아니다 |
| `spending_type IS NULL OR spending_type <> 'NON_SPENDING'` | 저축·투자, 이체 성격 카테고리(SAVING, TRANSFER, INCOME)의 지출은 소비에서 뺀다. 미분류(NULL)는 소비로 본다 |
| `hidden = 0` | 숨긴 거래 제외. 카드(8)·계좌(5) 집계와 같은 규칙 |
| 사용자 원장 전체 | 계좌·카드·수동 거래를 가리지 않는다 |

기획안 9-1의 "고정지출·변동지출"은 `spending_type`으로 나누며 다음 티켓(고정·변동지출 API)에서 다룬다.

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

### 오류 코드

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | 연월 파라미터가 7자를 넘거나 숫자·하이픈 외 문자, `category` 형식 위반 |
| `INVALID_REQUEST` | 400 | 연월 형식(YYYY-MM) 위반, 미래 월, 시작월 > 종료월, 시작일 > 종료일, 기간 12개월 초과, 없는 카테고리 코드 |
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

## 구현 메모

- 집계는 `JdbcSpendingReader`가 `transaction`을 월·일·카테고리·결제수단으로 GROUP BY해 읽고, 빈 달·빈 날 채우기와 비율 계산은
  `SpendingQueryService`가 한다. `YEAR()`/`MONTH()`는 MySQL과 H2(MODE=MySQL) 모두 지원한다.
- 기준일은 요청마다 `TransactionClock`에서 한 번 읽어 자정을 넘는 요청에서도 일관된다.
- 반올림은 모두 `RoundingMode.HALF_UP`이다.

## 테스트

`SpendingApiIntegrationTest`: 소비 정의(수입·이체·NON_SPENDING·숨김·타인 제외)가 6개 API에 같이 적용되는지, 전월 비교와 증감률(전월 0이면 null),
누적·예상 월말(지난 달은 합계와 일치), 빈 날·빈 달 채우기, 미분류·UNKNOWN 묶음, 비중 반올림, 카테고리 필터(대소문자·UNCATEGORIZED·없는 코드),
기본 기간·기간 검증을 확인한다. 날짜 의존을 피하려고 "이번 달" 거래는 1일에만 둔다.

## 남은 작업

[remaining-work.md](remaining-work.md)의 "S15P21D105-506" 절에 기록한다.
