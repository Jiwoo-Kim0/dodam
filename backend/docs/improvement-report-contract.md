# Day 11 Improvement / Report 응답 계약 (구현 확정본)

작성일: 2026-09-16. **구현 완료본이다.** 2026-09-16 선공유본을 API 19(Improvement)·20(Report) 구현에 맞춰 갱신했다.
FE 협의안([docs/report-backend-handoff.md](../../docs/report-backend-handoff.md))의 요청 항목은 §4에 항목별로 회신했다.

기준 자료: [API 19](../../docs/API명세서/19%20Improvement%203cdbb6ea35648084a55bf4cec46b357d.csv),
[API 20](../../docs/API명세서/20%20Report%203cdbb6ea3564807e98cfddff069700c1.csv),
[ERD](../../docs/ERD/ERD%20작성%203c9bb6ea356480898bd9eddac2e6840d.md)(ImprovementAction / Report·DailyReport·MonthlyReport),
[기획안](../../docs/기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md) 5-6(개선 결과 추적)·데일리 브리핑.

> ## 선공유본에서 바뀐 것 (FE 확인 필요)
>
> 필드는 하나도 빠지지 않았다. 아래는 **추가**되거나 **계산 기준이 확정**된 항목이다.
>
> | # | 항목 | 선공유본 | 확정본 | 이유 |
> | --- | --- | --- | --- | --- |
> | 1 | `MonthlyReport.totalExpense` | "소비 집계와 같은 규칙"이면서 동시에 "`totalSaving`을 포함" | 숨기지 않은 EXPENSE **전부**(저축성 지출 포함) | 두 문장이 동시에 성립하지 않았다. 소비 집계(506)는 `NON_SPENDING`을 빼므로 `totalSaving`을 포함할 수 없다 |
> | 2 | `totalSpending` (신규) | 없음 | 506 소비 정의(= `totalExpense − totalSaving`) | 화면마다 필요한 "지출"이 달라 둘 다 내려 준다. FE가 빼기로 추정하지 않게 |
> | 3 | `MonthlySpendingPattern.ratioBase` (신규) | 없음 | `"TOTAL_SPENDING"` | `byCategory[].ratio`의 분모를 응답이 직접 밝힌다. 선공유 예시는 `bySpendingType` 합(1,850,000)과 카테고리 분모가 어긋나 있었다 |
> | 4 | `dataStatus` (신규, Daily·Monthly) | 404 하나 | `READY` / `NO_TRANSACTIONS` / `INSUFFICIENT_DATA` | 협의안 3번. 미생성·거래 0건·계산 불가를 404 하나로 표현하지 않는다 |
> | 5 | `/daily`·`/monthly`의 404 | 리포트 없으면 404 | **404 없음**. 없으면 생성해 200 | 위와 같은 이유. 404는 리포트 상세(#5)에만 남는다 |
> | 6 | 기간 검증 | 없음 | 끝나지 않은 기간(오늘·이번 달)과 상한 초과는 **400** | 진행 중인 기간을 리포트로 굳히면 볼 때마다 숫자가 달라진다 |
> | 7 | `UpcomingExpenses` | `yearMonth` 의미 불명확 | `yearMonth` = **집계할 월 그 자체**, 생략 시 오늘의 다음 달 | 선공유 예시(2026-08 리포트 → 2026-10)가 "생성 시점의 다음 달"이었다 |
> | 8 | `UpcomingExpenses.partial`·`warnings` (신규) | `unavailableReason` 하나 | "못 만든 것"과 "덜 모은 것"을 분리 | 원본 하나가 비어도 카드 전체를 숨기면 쓸 수 있는 금액까지 사라진다 |
> | 9 | `ActionComparison.unavailableReason` | `ACTION_NOT_COMPLETED` | `ACTION_NOT_MEASURABLE` | 완료 전(IN_PROGRESS)에도 측정한다. 측정하지 않는 것은 **취소된** 행동뿐이다 |
> | 10 | `MonthlyGoalImpact.unavailableReason` (신규) | 없음 | `NO_HISTORY` / `NO_HISTORY_BEFORE_PERIOD` | 기록이 없어 못 낸 값과 0원을 구분한다 |
> | 11 | `MonthlyReport.transactionCount` (신규) | 없음 | 그 달 INCOME·EXPENSE 거래 수 | 거래 0건과 "집계 실패"를 FE가 직접 확인할 수 있게 |
> | 12 | `DailyReport`에 `totalSpending`·`totalSaving` (신규) | 없음 | 월간과 같은 정의 | 두 화면의 "지출"이 다른 뜻이 되지 않게 |

---

## 0. 공통 규약 (기존 API와 동일)

```json
{ "success": true, "data": { ... }, "message": null, "code": null }
```

| 항목 | 규칙 |
| --- | --- |
| 에러 | `{ "success": false, "data": null, "message": "...", "code": "RESOURCE_NOT_FOUND" }` |
| 인증 | 전부 필수. 미인증 **401**, 남의 리소스·없는 리소스 모두 **404 `RESOURCE_NOT_FOUND`**(구분하지 않는다) |
| 페이지 | `{ content, page, size, totalElements, totalPages, last }`. `page ≥ 0`, `size 1~100`, 위반 시 **400 `VALIDATION_ERROR`** |
| 기간 | `startDate > endDate` → **400 `INVALID_REQUEST`**. 기본 3개월·최대 12개월(`QueryPeriodPolicy`) |
| 날짜 | `yyyy-MM-dd`, 시각 `yyyy-MM-ddTHH:mm:ss`(Asia/Seoul), 월 `yyyy-MM` |
| 금액 | **원 단위 정수**(JSON number). 소수·문자열 아님. 음수 가능 필드는 아래 표에 명시 |
| 미계산 | 계산할 수 없으면 **`null`**이며 0으로 채우지 않는다. 0원과 "모름"은 다르다 |

> **`null`을 쓰는 곳이 세 종류다.** 셋을 같은 빈 상태로 그리면 안 된다.
> 1. 값 자체가 `null` — 아직 측정하지 못했다(예: 측정 전 `actualMonthlySaving`).
> 2. `unavailableReason != null` — 그 응답의 수치를 만들 수 없다. 수치 대신 안내 문구를 띄운다.
> 3. `dataStatus != "READY"` — 리포트는 만들어졌지만 안이 비었거나 일부가 없다.

---

## 1. API 19 — Improvement (`/api/v1/improvements`)

`ImprovementAction` = 사용자가 **실제로 수행한 개선 행동** 1건. `recommendation_id`로 개선안과 1:0..1로 이어진다.
상태: `PLANNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELED`.

### 1-0. 행동은 추천 수락으로만 만들어진다

API 19에는 행동을 만들거나 상태를 바꾸는 엔드포인트가 없다(명세 11개 중 쓰기는 재진단 하나뿐). 생명주기는
추천 상태 변경(API 16)과 **같은 트랜잭션**에서 움직인다.

| 추천 전이 | 행동 |
| --- | --- |
| `PENDING`/`DEFERRED` → `ACCEPTED` | 행동 생성(`PLANNED`, `plannedAt` = 수락 시각). 이미 있으면 **재사용**하고 기준선만 새로 잡는다 |
| 측정 기준 기간 시작(수락 다음 달 1일)이 지남 | `IN_PROGRESS`, `startedAt` = 그 날 00:00 |
| `ACCEPTED` → `COMPLETED` | `COMPLETED`, `completedAt` |
| `ACCEPTED` → `REJECTED` / `DEFERRED` | `CANCELED`, `canceledAt` |

**중복 없이 연결한다.** `uk_improvement_action_recommendation_id`(UNIQUE)가 추천 1건당 행동 1건을 DB에서
보장한다. DEFERRED를 거쳐 다시 수락해도 새 행이 생기지 않는다 — 생기면 같은 개선안의 효과가 두 번 집계된다.

### 1-0-1. 예상 효과와 실제 관측값

| | 출처 | 언제 |
| --- | --- | --- |
| `expectedMonthlySaving` | `recommendation.monthly_cost_saving`(추천이 계산해 둔 예상치) | 추천 생성 시점 |
| `actualMonthlySaving` | `improvement_action.monthly_cost_saving`(실행 후 실제 데이터로 측정) | 실행 후 1개월 이상 |

**둘을 합치지 않는다.** 행동 테이블에는 예상치를 복사해 두지 않고 추천 행에서 조인해 읽는다(두 행이 어긋날
여지를 없앤다). 측정 전이면 `actual*`과 `measuredAt`이 `null`이다 — 0원이 아니다.

### 1-0-2. 측정 기준 (계산 기준 확정)

```
 ...  6월   7월   8월  │   9월   │  10월  11월  12월  ...
     └──── before ────┘ 수락한 달 └──── after ─────┘
```

- **before** = 수락 월 직전 3개 **완료** 월. 수락 시점에 스냅샷으로 고정한다.
- **after** = 수락 다음 달부터 최대 3개 **완료** 월. 완료 월이 0개면 아직 측정할 수 없다.
- 수락한 달과 진행 중인 달은 **양쪽 모두에서 뺀다.** 20일치 소비를 한 달치와 비교하면 반드시 "개선"으로 보인다.
- 개월 수가 다를 수 있으므로 금액은 합계가 아니라 **월평균**으로 비교한다(잔액성 값인 부채는 시점 값).
- 완료 월이 1 → 2 → 3으로 늘면 **다시 측정**해 창을 넓힌다. 3개월이 차면 더 재지 않는다 — 그 뒤의 변화는
  이 행동의 결과라고 말할 수 없다. `measuredAt`은 마지막 측정 시각이다.
- 배치 스케줄러가 없으므로 **조회 시점이 측정 시점**이다. 밀린 측정은 API 19 조회와 월간 리포트 생성이 따라잡는다.

| 실측 항목 | 계산식 | 단위 |
| --- | --- | --- |
| `spendingReduction` | `max(0, before.월평균소비 − after.월평균소비)` | 월 |
| `savingIncrease` | `max(0, after.월평균저축성지출 − before.월평균저축성지출)` | 월 |
| `debtReduction` | `max(0, before.부채잔액 − after.부채잔액)` | 잔액 차 |
| `interestSaving` | `max(0, before.월이자 − after.월이자)` | 월 |
| `monthlyCostSaving` | `spendingReduction + interestSaving` | 월 |
| `annualCostSaving` | `monthlyCostSaving × 12` | 연 |
| `goalAdvancedDays` | `before.예상달성일 − after.예상달성일`(일). 한쪽이 없으면 `null` | 일 |

값은 모두 **0 이상**이다. 악화된 경우는 0으로 기록하고, 방향과 실제 수치는 전후 비교(#4)의 `delta`·`improved`가
보여 준다. 여기서 음수를 "절감"으로 더하면 합계가 상쇄된다.

소비·저축의 정의는 506 소비 집계를 그대로 쓴다(숨긴 거래 제외, `NON_SPENDING`은 저축성 지출로 분리).
월 이자는 `상환 중 대출 잔액 × 연이율 ÷ 1200`(원 단위 반올림)의 합이며, 상환일정 계산기의 월이율 정의와 같다.

### 공통 객체 `ImprovementAction`

```json
{
  "actionId": 12,
  "recommendationId": 34,
  "goalId": 5,
  "domain": "SPENDING",
  "type": "CANCEL_SUBSCRIPTION",
  "title": "쓰지 않는 구독을 정리하세요",
  "status": "COMPLETED",
  "plannedAt": "2026-09-16T12:00:00",
  "startedAt": "2026-10-01T00:00:00",
  "completedAt": "2026-10-10T18:00:00",
  "canceledAt": null,
  "expectedMonthlySaving": 12000,
  "actualMonthlySaving": 11000,
  "measuredAt": "2026-11-05T09:00:00"
}
```

`goalId`·`domain`·`type`은 연결된 추천에서 읽는다(행동 행에 복사하지 않는다). `title`만 수락 당시 문구를
보존하기 위해 복사한다.

### 엔드포인트

| # | API | Method · Endpoint | Query/Path | 응답 `data` |
| --- | --- | --- | --- | --- |
| 1 | 개선 결과 요약 | GET `/summary` | — | `ImprovementSummary` |
| 2 | 개선 행동 이력 | GET `/actions` | `status?`, `startDate?`, `endDate?`, `page?`, `size?` | `PageResponse<ImprovementAction>` |
| 3 | 개선 행동 상세 | GET `/actions/{actionId}` | path | `ImprovementAction` |
| 4 | 실행 전후 비교 | GET `/actions/{actionId}/comparison` | path | `ActionComparison` |
| 5 | 절감금액 조회 | GET `/savings` | — | `AmountTotal` |
| 6 | 부채감소액 조회 | GET `/debt-reduction` | — | `AmountTotal` |
| 7 | 소비감소액 조회 | GET `/spending-reduction` | — | `AmountTotal` |
| 8 | 저축증가액 조회 | GET `/saving-increase` | — | `AmountTotal` |
| 9 | 목표달성일 개선 조회 | GET `/goal-impact` | — | `GoalImpactSummary` |
| 10 | 월별 개선추이 | GET `/monthly-trend` | `startMonth?`, `endMonth?` | `MonthlyTrend` |
| 11 | 재진단 요청 | POST `/rediagnose` | body `{ goalId? }` | `RediagnoseResult` |

#2의 기간은 **수락 시각(`plannedAt`) 기준**이다. 완료·측정 시각으로 거르면 아직 실행 중인 행동이 사라진다.

### 1-1. `ImprovementSummary` (GET /summary)

```json
{
  "totalActions": 7,
  "byStatus": { "PLANNED": 1, "IN_PROGRESS": 2, "COMPLETED": 3, "CANCELED": 1 },
  "measuredActions": 3,
  "totalMonthlySaving": 31000,
  "totalAnnualSaving": 372000,
  "totalDebtReduction": 1200000,
  "firstActionAt": "2026-07-02T09:30:00",
  "lastMeasuredAt": "2026-11-05T09:00:00"
}
```

`byStatus`는 **4개 상태 키를 항상 모두** 포함한다(0이어도). 합계는 **측정된 실측값만** 더한다.
합계가 0이면서 `measuredActions: 0`이면 "효과가 0원"이 아니라 "아직 측정된 게 없다"는 뜻이다.

### 1-2. `ActionComparison` (GET /actions/{actionId}/comparison)

```json
{
  "actionId": 12,
  "status": "IN_PROGRESS",
  "beforePeriod": { "startDate": "2026-06-01", "endDate": "2026-08-31" },
  "afterPeriod":  { "startDate": "2026-10-01", "endDate": "2026-10-31" },
  "metrics": [
    { "metric": "MONTHLY_SPENDING", "displayName": "월 소비금액",
      "before": 1500000, "after": 1000000, "delta": -500000, "improved": true },
    { "metric": "MONTHLY_SAVING", "displayName": "월 저축금액",
      "before": 400000, "after": 511000, "delta": 111000, "improved": true },
    { "metric": "DEBT_BALANCE", "displayName": "부채 잔액",
      "before": 12000000, "after": 10800000, "delta": -1200000, "improved": true },
    { "metric": "MONTHLY_INTEREST", "displayName": "월 이자비용",
      "before": 42000, "after": 37800, "delta": -4200, "improved": true }
  ],
  "unavailableReason": null
}
```

- `delta = after − before`이며 **부호를 그대로** 내려준다. "좋아졌는지"는 `improved`가 알려준다
  (소비는 줄어야 개선, 저축은 늘어야 개선이라 부호만으로 판단할 수 없다). 변화가 없으면 `improved: false`다.
- 비교할 수 없으면 `metrics: []` + `unavailableReason`:
  `ACTION_NOT_MEASURABLE`(취소된 행동), `BEFORE_SNAPSHOT_MISSING`(수락 시점 기준선을 못 만들었다),
  `AFTER_PERIOD_NOT_READY`(실행 후 완료된 달이 아직 없다), `INSUFFICIENT_DATA`(관측된 지표가 하나도 없다).
- `metric` 코드는 **확정**이며 늘어날 수 있다. FE는 모르는 코드를 만나면 `displayName`으로 렌더링한다.
- `beforePeriod`·`afterPeriod`는 **저장된 스냅샷의 기간**이다. 조회 시점에 다시 계산하지 않는다
  (`unavailableReason`이 있을 때만 정책이 계산한 예정 구간을 보여 준다).

### 1-3. `AmountTotal` (GET /savings, /debt-reduction, /spending-reduction, /saving-increase)

네 API가 **같은 형식**을 쓴다. 화면이 4개 타일을 같은 컴포넌트로 그릴 수 있게 한 것이다.

```json
{
  "metric": "SAVINGS",
  "totalAmount": 6000000,
  "monthlyAmount": 500000,
  "currency": "KRW",
  "measuredFrom": "2026-11-05",
  "measuredTo": "2026-11-05",
  "contributingActions": 1,
  "unavailableReason": null
}
```

| `metric` | `monthlyAmount` | `totalAmount` |
| --- | --- | --- |
| `SAVINGS` | Σ `monthlyCostSaving` | 월 금액 × 12 |
| `SPENDING_REDUCTION` | Σ `spendingReduction` | 월 금액 × 12 |
| `SAVING_INCREASE` | Σ `savingIncrease` | 월 금액 × 12 |
| `DEBT_REDUCTION` | **`null`**(잔액 차라 월 개념이 없다) | Σ `debtReduction` |

측정된 행동이 없으면 `totalAmount: 0`, `contributingActions: 0`, `measuredFrom/To: null`,
`unavailableReason: null`. 데이터가 없어서 0인 것이지 계산 불가가 아니다.

### 1-4. `GoalImpactSummary` (GET /goal-impact)

```json
{
  "goals": [
    {
      "goalId": 5,
      "goalName": "비상금 1,000만원 만들기",
      "baselineForecastDate": "2028-03-31",
      "currentForecastDate": "2027-12-31",
      "advancedDays": 91,
      "contributingActions": 2,
      "unavailableReason": null
    }
  ]
}
```

- `baselineForecastDate` = 그 목표에 연결된 **가장 이른** 개선 행동의 수락 시점 예상 달성일.
  나중 행동의 기준선을 쓰면 먼저 실행한 행동이 앞당긴 만큼이 기준선에 이미 반영돼 효과가 사라진다.
- `currentForecastDate` = 조회 시점 예상 달성일(목표 도메인의 계산을 그대로 부른다).
- `advancedDays` 양수 = 앞당겨짐, 음수 = 밀림. `null`이면 `unavailableReason`을 본다:
  `NO_CONTRIBUTING_ACTION`, `BASELINE_FORECAST_UNAVAILABLE`, `CURRENT_FORECAST_UNAVAILABLE`.
  (저축속도가 0이면 예상 달성일이 없다. 0일 앞당겨진 것으로 꾸미지 않는다.)
- ACTIVE 모으기 목표만 담는다. 목표가 없으면 `goals: []`.

### 1-5. `MonthlyTrend` (GET /monthly-trend)

```json
{
  "startMonth": "2026-07",
  "endMonth": "2026-09",
  "months": [
    { "yearMonth": "2026-07", "completedActions": 0, "monthlySaving": 0,
      "spendingReduction": 0, "savingIncrease": 0, "debtReduction": 0 }
  ]
}
```

- `months`는 빈 달을 0으로 채워 **연속**으로 내려준다. 차트가 구멍을 메우지 않아도 되도록.
- 월 배정 기준이 다르다. `completedActions`는 **완료된**(`completedAt`) 달, 금액 4종은 **측정된**(`measuredAt`) 달이다.
  하나로 합치면 같은 행동이 두 달에 중복 집계된다.
- 기본 구간은 **양 끝을 모두 생략했을 때만** 6개월이다(차트가 6개 점은 있어야 추세로 보인다).
  한쪽만 주면 공용 정책(기본 3개월·최대 12개월)을 따른다. 월 범위 검증은 공용 `YearMonthPeriodPolicy`가
  **실제 현재 월** 기준으로 한다(미래 월은 400).

### 1-6. `RediagnoseResult` (POST /rediagnose)

```json
{ "diagnosisId": 77, "goalId": 5, "created": true, "requestedAt": "2026-09-16T12:00:00" }
```

- 신규 진단이면 **201 + `created: true`**, 중복 요청이 막혀 직전 진단을 돌려주면 **200 + `created: false`**.
- 진행 중이면 **409 `DIAGNOSIS_IN_PROGRESS`**. 없는 목표·남의 목표는 **404**.
- 중복 방지는 진단 실행 API(`POST /api/v1/diagnoses`)의 정책을 그대로 쓴다 — 같은 날 같은 규칙 버전의 완료
  진단이 있으면 재사용한다. `forceRefresh`는 열지 않는다(연타로 같은 날 진단이 쌓이지 않게).

---

## 2. API 20 — Report (`/api/v1/reports`)

`Report`(종합) + `DailyReport` / `MonthlyReport`(하위). `(user_id, type, period)`당 1건.

### 2-0. 생성·캐시·상태 (계산 기준 확정)

**리포트는 조회 시점에 만들어진다**(배치 스케줄러가 없다).

- 끝난 기간을 조회하면 저장본을 돌려주고, 없으면 만들어 저장한 뒤 돌려준다. **200이다.**
- `READY`로 저장된 리포트는 **고정**이다. 같은 달을 다시 봐도 숫자와 `generatedAt`이 그대로다.
- `NO_TRANSACTIONS`·`INSUFFICIENT_DATA`는 **다음 조회 때 다시 계산**한다. 금융망 동기화가 늦게 도착해
  거래가 나중에 생기는 경우가 있다.
- 하위 응답(#4 소비패턴, #6 목표변화, #8 개선행동 요약)은 **저장된 월간 리포트의 JSON을 읽는다.**
  같은 달을 두 경로로 다시 집계하면 합계가 어긋난다. #7 예정비용만 조회 시점 계산이다(미래 스케줄).

| 상태 | 뜻 | `unavailableReason` |
| --- | --- | --- |
| `READY` | 정상 생성. 수치를 보여 준다 | `null` |
| `NO_TRANSACTIONS` | 기간은 끝났지만 거래가 0건. 합계는 0원이며 계산 불가가 아니다 | `"NO_TRANSACTIONS"` |
| `INSUFFICIENT_DATA` | 핵심 집계를 만들지 못했다. 수치 대신 안내 문구 | `"INSUFFICIENT_DATA"` |

### 2-0-1. 조회 가능 범위

| 대상 | 기본값 | 허용 | 위반 |
| --- | --- | --- | --- |
| `/monthly` `yearMonth` | 직전 완료 월 | 직전 완료 월부터 **24개월 전**까지 | 이번 달·미래·상한 초과·형식 오류 → **400 `INVALID_REQUEST`** |
| `/daily` `date` | 어제 | 어제부터 **400일 전**까지 | 오늘·미래·상한 초과 → **400** |
| `/monthly/upcoming-expenses` `yearMonth` | 오늘의 **다음 달** | 12개월 뒤까지. 과거도 형식만 맞으면 받는다 | 12개월 초과 → **400** |
| `/` (목록) `fromDate`·`toDate` | 최근 3개월 | 최대 12개월 | 시작 > 종료 → **400** |

목록(#3)에는 **이미 생성된 리포트만** 나온다(조회한 적 없는 달은 아직 존재하지 않는다).
정렬은 `periodStart DESC, reportId DESC`다 — `period`는 DAILY(`yyyy-MM-dd`)와 MONTHLY(`yyyy-MM`)가 섞인
문자열이라 문자열 정렬로는 두 유형을 함께 늘어놓을 수 없다.

### 엔드포인트

| # | API | Method · Endpoint | Query/Path | 응답 `data` |
| --- | --- | --- | --- | --- |
| 1 | 데일리 브리핑 | GET `/daily` | `date?` | `DailyReport` |
| 2 | 월간 리포트 | GET `/monthly` | `yearMonth?` | `MonthlyReport` |
| 3 | 리포트 목록 | GET `` | `type?`, `fromDate?`, `toDate?`, `page?`, `size?` | `PageResponse<ReportSummary>` |
| 4 | 월간 소비패턴 | GET `/monthly/spending-pattern` | `yearMonth?` | `MonthlySpendingPattern` |
| 5 | 리포트 상세 | GET `/{reportId}` | path | `DailyReport` \| `MonthlyReport` |
| 6 | 월간 목표변화 | GET `/monthly/goal-impact` | `yearMonth?`, `goalId?` | `MonthlyGoalImpact` |
| 7 | 다음달 예정비용 | GET `/monthly/upcoming-expenses` | `yearMonth?` | `UpcomingExpenses` |
| 8 | 월간 개선행동 요약 | GET `/monthly/improvements` | `yearMonth?` | `MonthlyImprovements` |

### 2-1. `ReportSummary` (목록 항목)

```json
{ "reportId": 91, "type": "MONTHLY", "period": "2026-08", "generatedAt": "2026-09-01T05:00:00" }
```

상세(#5)는 `type`에 따라 아래 두 형식 중 하나를 돌려주며, 공통 필드 `reportId`·`type`·`period`·`generatedAt`로
분기한다. **#5만 404가 있다**(없는 리포트·타인 리포트를 구분하지 않는다).

### 2-2. `DailyReport` (GET /daily)

```json
{
  "reportId": 120,
  "type": "DAILY",
  "period": "2026-09-15",
  "generatedAt": "2026-09-16T05:00:00",
  "dataStatus": "READY",
  "totalIncome": 0,
  "totalExpense": 47800,
  "totalSpending": 47800,
  "totalSaving": 0,
  "transactionCount": 2,
  "topCategories": [
    { "categoryCode": "DINING", "categoryName": "외식", "amount": 32000, "count": 1 },
    { "categoryCode": "UNCATEGORIZED", "categoryName": "미분류", "amount": 15800, "count": 1 }
  ],
  "balanceChange": -47800,
  "notableEvents": [
    { "code": "SUBSCRIPTION_CHARGED", "message": "정기결제 1건이 결제되었습니다.", "amount": 12000 }
  ],
  "coachMessage": null,
  "unavailableReason": null
}
```

- 합계 정의는 월간과 같다. `totalExpense = totalSpending + totalSaving`,
  `balanceChange = totalIncome − totalExpense`(음수 가능).
- `topCategories`는 **소비만** 담고 상위 5개다. 그 합은 `totalSpending`과 같고 `totalExpense`와는 다르다.
  미분류는 `UNCATEGORIZED`/`미분류`로 내려간다.
- `transactionCount`는 숨기지 않은 **INCOME·EXPENSE** 거래 수다(TRANSFER 제외 — 내 계좌 간 이동은 세지 않는다).
- `notableEvents`는 **그날 실제로 관측된 사실**만 담는다. 코드는 4종이며 각각 SQL 한 줄로 되짚을 수 있다.

| 코드 | 조건 | `amount` |
| --- | --- | --- |
| `NO_SPENDING` | 거래는 있는데 소비가 0원 | `0` |
| `INCOME_RECEIVED` | 수입 > 0 | 그날 수입 합 |
| `LARGE_EXPENSE` | 그날 최대 단일 소비 > 직전 30일 일평균 소비(일평균이 0이면 판정하지 않는다) | 그 지출 금액 |
| `SUBSCRIPTION_CHARGED` | 그날 정기결제 결제 이력이 있다 | 그날 결제 합 |

- **`coachMessage`는 API 21의 응답이 아니다.** 브리핑 전용 문구가 생길 자리이며 당분간 `null`이다.
  "어제 소비가 목표 달성일을 며칠 바꿨다" 같은 인과 문장은 근거 계산이 따로 필요해 만들지 않는다.

### 2-3. `MonthlyReport` (GET /monthly)

```json
{
  "reportId": 91,
  "type": "MONTHLY",
  "period": "2026-08",
  "generatedAt": "2026-09-01T05:00:00",
  "dataStatus": "READY",
  "totalIncome": 3200000,
  "totalExpense": 1850000,
  "totalSpending": 1450000,
  "totalSaving": 400000,
  "netCashflow": 1350000,
  "transactionCount": 4,
  "previousMonth": { "totalIncome": 3200000, "totalExpense": 1910000, "totalSpending": 1560000, "totalSaving": 350000 },
  "changeFromPreviousMonth": { "totalIncome": 0, "totalExpense": -60000, "totalSpending": -110000, "totalSaving": 50000 },
  "cardSpending": 1450000,
  "cardSpendingPreviousMonth": 1560000,
  "cardSpendingDifferenceFromPreviousMonth": -110000,
  "efficiency": { "score": 74, "maxScore": 100, "previousMonthDifference": -10, "summary": "…", "calculationVersion": "efficiency-v4",
                  "availableDimensionCount": 5, "dimensions": [ "…§6-1" ] },
  "diagnosisId": 77,
  "overallStatus": "CAUTION",
  "unavailableReason": null
}
```

| 필드 | 정의 |
| --- | --- |
| `totalExpense` | 숨기지 않은 EXPENSE **전부**(저축성 지출 포함) |
| `totalSpending` | 506 소비 정의(EXPENSE에서 `NON_SPENDING` 제외). 소비 화면·패턴 API와 같은 숫자 |
| `totalSaving` | 저축성 지출(`NON_SPENDING`). `totalExpense`에 **포함**되며 이중 차감하지 않는다 |
| `netCashflow` | `totalIncome − totalExpense` |
| `diagnosisId`·`overallStatus` | 그 달 **말일까지 완료된 가장 최근 진단**, 없으면 **리포트 생성 시점의 최신 완료 진단**(694, 이전엔 "그 달 안"). 진단이 하나도 없으면 둘 다 `null`. §6-4 |
| `cardSpending`·`cardSpendingPreviousMonth`·`cardSpendingDifferenceFromPreviousMonth` (635) | 카드 사용액과 전월 대비 증감. 정의는 §6-2 |
| `efficiency` (635) | 금융 효율 점수(전체 + 5개 항목, v3 이하 저장본은 6개). 정의는 §6-1. 635 이전 리포트는 첫 월간 조회 때 채워진다 |
| `metrics` (697) | 주요 지표 4개 + 금융 생활 패턴 3장. 정의는 §7. 697 이전 리포트는 첫 월간 조회 때 채워진다 |

`totalExpense = totalSpending + totalSaving`이 항상 성립한다.

**전월 비교**: 사용자의 첫 거래일보다 앞선 달은 비교 대상이 아예 없으므로 `previousMonth`와
`changeFromPreviousMonth`가 **둘 다 `null`**이다. 이전 달이 존재하는데 거래가 0건이면 `null`이 아니라 0원이다.
`changeFromPreviousMonth`는 부호를 그대로 둔다.

### 2-4. `MonthlySpendingPattern` (GET /monthly/spending-pattern)

```json
{
  "yearMonth": "2026-08",
  "totalExpense": 1850000,
  "totalSpending": 1450000,
  "ratioBase": "TOTAL_SPENDING",
  "byCategory": [
    { "categoryCode": "UNCATEGORIZED", "categoryName": "미분류", "amount": 900000, "ratio": 0.6207, "count": 1 },
    { "categoryCode": "DINING", "categoryName": "외식", "amount": 550000, "ratio": 0.3793, "count": 1 }
  ],
  "bySpendingType": { "FIXED": 900000, "VARIABLE": 550000, "NON_SPENDING": 400000 },
  "weekdayWeekend": { "weekday": 1450000, "weekend": 0 },
  "comparedToPrevious": { "amountDelta": -110000, "ratioDelta": -0.0705 }
}
```

- `ratio`는 **0~1 소수 4자리**다. 퍼센트 변환은 FE가 한다(패턴 분석 API 3과 같은 규칙).
- **분모가 항목마다 다르다.** 섞으면 비율의 합이 1을 넘는다.
  - `byCategory[].ratio` 분모 = `totalSpending`(`ratioBase`가 명시한다). 카테고리 집계는 소비만 담는다.
  - `bySpendingType`은 3개 키를 항상 포함하고 합 = `totalExpense`.
  - `weekdayWeekend`는 소비 기준이며 합 = `totalSpending`. 토·일이 주말이다.
- `comparedToPrevious.amountDelta` = 그 달 소비 − 전월 소비(부호 그대로).
  `ratioDelta` = `amountDelta ÷ 전월 소비`(증감률, 0~1 소수 4자리). 전월 소비가 0이면 `null`.

### 2-5. `MonthlyGoalImpact` (GET /monthly/goal-impact)

```json
{
  "yearMonth": "2026-08",
  "goals": [
    { "goalId": 5, "goalName": "비상금 1,000만원 만들기",
      "startAmount": 3000000, "endAmount": 3400000, "contributed": 400000,
      "progressRatio": 0.34,
      "forecastDateAtStart": "2028-03-31", "forecastDateAtEnd": "2027-12-31", "advancedDays": 91,
      "unavailableReason": null }
  ]
}
```

- 값은 **`goal_history`에서만** 읽는다. 목표 잔액·예상 달성일은 기록된 시점 값이라야 과거 달을 재현할 수 있다.
  오늘의 잔액으로 지난달 리포트를 만들면 그 달의 변화가 아니라 오늘 상태가 나온다.
- `startAmount` = 그 달 시작 **직전** 마지막 기록, `endAmount` = 그 달 마지막 기록.
- `contributed = endAmount − startAmount`. `progressRatio = endAmount ÷ 목표 금액`(0~1 소수 4자리).
- `advancedDays` 양수 = 그 달 동안 달성일이 앞당겨졌다.
- `unavailableReason`: `NO_HISTORY`(그 목표의 기록이 아예 없다),
  `NO_HISTORY_BEFORE_PERIOD`(그 달 안에 목표가 만들어져 시작값이 없다 — 끝값과 예상일은 그대로 준다).
- 모으기 목표만 담는다(줄이기 목표에는 "모인 금액"·"예상 달성일" 개념이 없다). 삭제된 목표는 빼고, 그 달에
  달성·취소된 목표는 남긴다.
- `goalId`를 주면 해당 목표 1건만. 남의 목표·삭제된 목표·줄이기 목표는 모두 **404**.

### 2-6. `UpcomingExpenses` (GET /monthly/upcoming-expenses)

다음 달 **예정** 비용이다. 이미 확정된 스케줄만 담고 추정하지 않는다.

```json
{
  "yearMonth": "2026-10",
  "collectedFrom": "2026-10-01",
  "collectedTo": "2026-10-31",
  "totalAmount": 1240000,
  "items": [
    { "sourceType": "CARD", "label": "카드 결제예정금액", "amount": 780000, "dueDate": "2026-10-14" },
    { "sourceType": "LOAN", "label": "대출 상환", "amount": 320000, "dueDate": "2026-10-25" },
    { "sourceType": "SUBSCRIPTION", "label": "정기결제", "amount": 140000, "dueDate": null }
  ],
  "partial": false,
  "warnings": [],
  "unavailableReason": null
}
```

- `yearMonth`는 **집계할 월 그 자체**이며 생략하면 오늘의 다음 달이다.
- 원본은 현금흐름 예측과 **같은 수집기**(`CashflowEventCollector`)다. 카드 청구·대출 상환·정기결제·적금 납입을
  각자 다시 읽으면 같은 출금이 두 번 잡히거나(구독이 카드 청구서에 이미 들어 있는 경우) 한쪽만 잡힌다.
  수집기가 dedupe 키로 중복을 막고, 확정할 수 없는 원본은 금액에 넣지 않고 사유로 남긴다.
- `sourceType`: `CARD` | `LOAN` | `SUBSCRIPTION` | `SAVINGS` | `MINUS_ACCOUNT`. 수입(INFLOW)은 제외한다.
- `dueDate`가 `null`인 것은 그 달 안에 여러 건이 흩어져 있다는 뜻이다(합계만 의미 있다).
- **"못 만든 것"과 "덜 모은 것"을 구분한다.**
  - `unavailableReason != null` → `totalAmount`가 `null`이고 수치 대신 안내 문구.
    `PERIOD_ELAPSED`(이미 지난 월), `SOURCE_CONFLICT`(같은 예정이 원본마다 금액·날짜가 다르다),
    `SOURCE_UNAVAILABLE`(원본 조회 자체가 실패했다).
  - `partial: true` → 합계는 있지만 **과소집계**다. 수치를 보여 주되 "일부 빠졌을 수 있음"을 함께 표시한다.
    사유는 `warnings`에 원본 코드 그대로 담는다(`PARTIALLY_ELAPSED`, `ACCOUNT_SCHEDULE_SOURCE_UNAVAILABLE`,
    `CARD_SUBSCRIPTION_REQUIRES_BILLING_RECONCILIATION` 등).

### 2-7. `MonthlyImprovements` (GET /monthly/improvements)

```json
{
  "yearMonth": "2026-08",
  "completedActions": 2,
  "startedActions": 1,
  "canceledActions": 0,
  "monthlySaving": 23000,
  "actions": [ { "actionId": 12, "title": "쓰지 않는 구독을 정리하세요", "status": "COMPLETED",
                 "completedAt": "2026-08-10T18:00:00", "actualMonthlySaving": 11000 } ]
}
```

§1의 `ImprovementAction`을 **줄인 형태**다(리포트 화면에 필요한 필드만). 상세는 API 19 #3으로 간다.
월 배정 기준은 API 19 월별 추이와 같다 — 건수는 각 시각(`completedAt`/`startedAt`/`canceledAt`)이 찍힌 달,
`monthlySaving`은 그 달에 **측정된** 실측값 합이다. `actions`에는 그 달에 하나라도 해당한 행동이 들어간다.

---

## 3. FE가 먼저 확인할 것

1. **`null`과 `0`을 구분해 렌더링**한다. `null` = 아직 측정 못 함(빈 상태 UI), `0` = 측정했는데 0원.
2. **`unavailableReason`이 있는 응답**(`ActionComparison`, `AmountTotal`, `UpcomingExpenses`,
   `GoalImpactSummary.goals[]`, `MonthlyGoalImpact.goals[]`)은 그 값이 `null`이 아니면 수치 대신 안내 문구를 띄운다.
3. **`dataStatus`를 먼저 본다.** `READY`가 아니면 그 리포트의 수치는 "비었다"는 뜻이지 "실패"가 아니다.
4. **`UpcomingExpenses.partial`은 숨김이 아니라 경고다.** 수치를 그대로 보여 주되 표시를 덧붙인다.
5. **`byStatus`·`bySpendingType` 같은 맵은 키가 항상 전부 있다.** 반대로 `topCategories`·`items`·`goals`는
   빈 배열일 수 있다.
6. **`delta`류는 부호가 그대로다.** 좋아졌는지는 `improved` 플래그로 판단한다.
7. **비율의 분모를 확인한다.** `byCategory[].ratio`는 `ratioBase`가 가리키는 값이 분모다.
8. **AI 코치 문장은 리포트 응답에 들어 있지 않다.** 필요하면 API 21을 별도로 호출한다
   ([ai-coach.md](ai-coach.md)). 리포트의 `coachMessage`는 당분간 `null`이다.

---

## 4. FE 협의안 회신 (docs/report-backend-handoff.md)

| 협의안 | 회신 |
| --- | --- |
| 1. 기준 월·`generatedAt`·데이터 부족 사유, 0원과 null 구분 | 기준 월은 직전 완료 월 유지. `generatedAt`은 `Report.createdAt`이며 `READY` 리포트는 고정된다. 사유는 `dataStatus` + `unavailableReason`(§2-0)으로 나눴다 |
| 2. 금융 효율 점수 (`score`·`percentile` 등) | ~~제공하지 않는다~~ → **2026-09-18(635) §6-1로 제공**(항목 6개·산식 버전·저장 정책 확정). `percentile`(상대 순위)은 여전히 없다. 아래 원문: 점수 산식과 비교 모집단이 확정되지 않았고, 근거 없는 점수를 API로 굳히면 되돌리기 어렵다. 그 자리에는 `MonthlyReport.overallStatus`(그 달 완료 진단의 종합 상태 `HEALTHY`/`CAUTION`/`RISK`)와 `diagnosisId`를 쓴다. 상세 근거는 진단 API로 간다. **현재 위험을 과거 점수에 적용하거나 점수에서 순위를 역산하지 않는다**는 FE 판단에 동의한다 |
| 2. 금융 생활 패턴 (`status`·`title`·`description`·`evidencePeriod`) | **문구 목록은 제공하지 않는다.** 진단 문제(API 14 `/issues`)가 이미 `domain`·`type`·`severity`·`evidence`를 갖고 있어 같은 것을 두 번 정의하게 된다. 리포트에서는 `MonthlySpendingPattern`(관측치)만 주고, 문장이 필요하면 진단 문제를 쓴다 |
| 2. 다음 달 예상 지출 | `UpcomingExpenses`로 제공(§2-6). **예측이 아니라 확정 스케줄**이다. 중복 제거는 현금흐름 수집기의 dedupe 키가 맡는다 — 카드 사용액을 다시 합산하지 않는다. `basis`에 해당하는 정보는 `partial`·`warnings`로 내려간다 |
| 2. 주요 지표 (저축률·고정비 비중·카드 사용액·대출 잔액) | 카드 사용액만 **635에서 `cardSpending` 3개 필드로 추가**(§6-2). 나머지는 원문대로: 리포트에 새로 넣지 않는다. `MonthlySpendingPattern.bySpendingType`(고정비 비중)과 `MonthlyReport`의 소득·소비·저축으로 저축률을 만들 수 있다. 카드 사용액·대출 잔액은 기존 개별 API가 정답을 갖고 있고, 리포트가 같은 숫자를 다시 정의하면 두 화면이 어긋난다 |
| 2. 지난 소비 6개월 추이 | 리포트에 넣지 않는다. 기존 카테고리 추이 API를 그대로 쓴다. 필요하면 `/reports/monthly`를 6번 부르는 대신 개선 추이(API 19 #10)처럼 별도 엔드포인트를 다음 티켓에서 연다 |
| 3. 상태 구분 | `dataStatus` 3종 + `unavailableReason`으로 나눴다(§2-0). 일시적 서버 오류는 기존대로 5xx이며 리포트 상태로 표현하지 않는다 |
| 4. 월 선택 UI | 월간 24개월, 데일리 400일 전까지. 끝나지 않은 기간은 400. 미생성 월은 조회하면 생성된다(§2-0-1). 재생성은 `READY`가 아닌 리포트에만 일어난다 |
| 데일리 브리핑 | `coachMessage`는 계속 `null`이다. 대신 `notableEvents` 4종을 **관측 사실**로 내려 준다(§2-2). 인과 문장은 만들지 않는다 — FE 판단에 동의한다 |

---

## 5. 구현 위치

| 영역 | 경로 |
| --- | --- |
| 스키마 | `backend/src/main/resources/db/migration/V37__create_improvement_action.sql`, `V38__create_report.sql`, `V41__monthly_report_efficiency_summary.sql`(635) |
| Improvement | `backend/src/main/java/com/ssafy/dodam/domain/improvement/` |
| Report | `backend/src/main/java/com/ssafy/dodam/domain/report/` |
| 추천 수락 연결 | `RecommendationStatusService` → `ImprovementActionRecorder` |
| 테스트 | `ImprovementApiTest`(10), `ReportApiTest`(15), `ImprovementMeasurementPolicyTest`, `ReportPeriodPolicyTest`, `EfficiencyScorePolicyTest`(12, 635 산식) |

---

## 6. 635 회신 — 금융 효율 점수·카드 사용액·또래 비교 (2026-09-18)

FE 요청 "[FE→BE] 리포트 금융 효율 점수 및 K-Means 또래 비교 연동 요청"(Jira S15P21D105-635) 회신. §4의 "금융 효율 점수는 제공하지 않는다"를
**뒤집어** 제공한다 — 이번에는 항목·산식·저장 정책을 함께 확정했고, 산식은 `calculationVersion`으로 버전을 남겨 되돌릴 수 있다.
구현: `EfficiencyScorePolicy`(산식)·`EfficiencyScoreCalculator`(입력 수집)·`MonthlyReportGenerator`, V41 `monthly_report.efficiency_summary`.

### 6-1. `MonthlyReport.efficiency` (요청 1)

```json
{
  "score": 74,
  "maxScore": 100,
  "previousMonthDifference": -10,
  "summary": "지출 관리는 안정적이에요. 저축을 조금 더 챙겨 보세요.",
  "calculationVersion": "efficiency-v4",
  "availableDimensionCount": 5,
  "dimensions": [
    { "key": "SPENDING", "label": "지출 관리", "score": 100, "maxScore": 100, "status": "GOOD",
      "description": "소득 대비 소비율이 45%예요.", "value": 0.4531, "valueType": "RATIO" },
    { "key": "SAVING", "label": "저축", "score": 42, "maxScore": 100, "status": "NORMAL",
      "description": "소득의 13%를 저축했어요.", "value": 0.125, "valueType": "RATIO" },
    { "key": "FIXED_EXPENSE", "label": "고정지출", "score": 45, "maxScore": 100, "status": "NORMAL",
      "description": "전체 소비 중 고정지출이 62%예요.", "value": 0.6207, "valueType": "RATIO" },
    { "key": "CREDIT", "label": "신용 관리", "score": 85, "maxScore": 100, "status": "GOOD",
      "description": "기존 금융망 기준 신용 상태가 안정적이에요(B등급).", "valueType": "GRADE", "grade": "B" },
    { "key": "DEBT_REPAYMENT", "label": "부채 상환", "score": 100, "maxScore": 100, "status": "GOOD",
      "description": "대출 상환액이 없어요.", "value": 0.0, "valueType": "RATIO" }
  ]
}
```

FE 확인 요청에 대한 답:

| 확인 요청 | 회신 |
| --- | --- |
| 항목 구성 | 키·순서는 SPENDING, SAVING, FIXED_EXPENSE, CREDIT, DEBT_REPAYMENT로 고정(v4, 712). v3 이하로 굳힌 리포트는 CREDIT 뒤에 CASHFLOW, INEFFICIENCY가 오는 6개다. 모두 0~100 정수. 계산 불가 항목의 `score`는 **키를 빼지 않고 `null`로** 내려간다(694 수정 — 이전엔 키가 빠져 FE의 `=== null` 판정이 `undefined`로 깨졌다). `value`·`valueType`·`grade`·`unavailableReason`만 없을 때 생략된다 |
| 신용점수 없을 때 | 680부터 `user.credit_score / 10`을 반올림해 사용한다. 점수가 없고 금융망 A~E 등급만 있으면 종전 등급표를 호환 입력으로 쓴다. 둘 다 없으면 `CREDIT_SCORE_MISSING`. 현재 값이라 과거 달 리포트에도 조회 시점 값이 들어간다 |
| 비효율 탐지 기준 기간·포함 상태 | **v4에서 항목 제거**(§6-5). 아래는 v3 저장본의 기준이다. 리포트 달 **말일까지 완료된 가장 최근 진단**, 없으면 **생성 시점 최신 진단**(694, `MonthlyReport.diagnosisId`와 같은 진단) 하나의 문제 전부. 폴백이면 `description`이 "가장 최근 진단 기준으로 …"로 시작한다. **GOAL 도메인은 제외**(607에서 목표 위험을 발견 대상에서 뺐다). 추천의 수락·완료 상태는 보지 않는다(진단 시점의 문제 수가 기준). 진단이 없으면 `DIAGNOSIS_NOT_FOUND` |
| 항목별 산식 | 아래 표. **모두 서비스 초기값**이며 임계값을 바꾸면 `calculationVersion`을 올린다 |
| 계산 불가 항목이 있을 때 전체 점수 | 계산된 항목의 **단순 평균**(반올림). 불가 항목은 0으로 넣지 않는다. 계산된 항목이 **3개 미만**이면 전체 `score = null`, `unavailableReason = INSUFFICIENT_DIMENSIONS`. `availableDimensionCount`로 몇 개가 들어갔는지 알 수 있다 |
| 월별 저장·전월 비교 | 리포트 생성 시점에 `monthly_report.efficiency_summary`에 굳힌다(READY 리포트는 고정, 산식이 바뀌어도 과거 점수 유지). `previousMonthDifference` = 이번 달 점수 − 전월 점수. 전월 점수는 **전월 리포트 저장본**(같은 산식 버전)이 있으면 그 값, 없으면 같은 산식으로 즉시 계산(저장 안 함). 전월이 사용자 첫 거래 이전이거나 어느 한쪽 전체 점수가 null이면 `null` |

| 항목 | 근거 수치(`value`) | 100점 | 0점 | 계산 불가 |
| --- | --- | --- | --- | --- |
| SPENDING 지출 관리 | 소비(`totalSpending`) / 소득 | ≤ 0.5 | ≥ 1.0 (선형) | 소득 0 → `INCOME_MISSING` |
| SAVING 저축 | 저축성 지출(`totalSaving`) / 소득 | ≥ 0.3 | 0 (선형) | 소득 0 → `INCOME_MISSING` |
| FIXED_EXPENSE 고정지출 | 고정지출(`bySpendingType.FIXED`) / 소비 | ≤ 0.4 | ≥ 0.8 (선형) — v3, v1·v2는 0.3/0.7 | 소비 0 → `NO_SPENDING` |
| CREDIT 신용 관리 | 예상 신용점수(1~1,000) | 1,000점 → 100점(10으로 나눠 반올림) | 1점 → 0점 | 점수 없음 → 금융망 등급 호환, 둘 다 없음 → `CREDIT_SCORE_MISSING` |
| DEBT_REPAYMENT 부채 상환 (v4) | 그 달 `loan_repayment` 중 `status = PAID` 상환액 합(사용자의 모든 대출) / 소득 | ≤ 0.1 | ≥ 0.4 (선형) | 소득 0 → `INCOME_MISSING`. 대출·상환이 없으면 0 → 100점 |
| ~~CASHFLOW 현금흐름~~ (v3까지) | 기준일이 그 달 **말일 이전**인 **마지막 완료 예측**, 없으면 생성 시점 최신 완료 예측의 위험 수(694) | 100 − Σ(CRITICAL 40, HIGH 30, MEDIUM 15, LOW 5), 하한 0 | | 완료 예측이 하나도 없음 → `FORECAST_NOT_FOUND` |
| ~~INEFFICIENCY 비효율 관리~~ (v3까지) | 그 달 **말일까지** 완료된 최신 진단, 없으면 생성 시점 최신 완료 진단의 문제 수(GOAL 제외)(694) | 100 − Σ(CRITICAL 30, HIGH 25, MEDIUM 15, LOW 10), 하한 0 | | 완료 진단이 하나도 없음 → `DIAGNOSIS_NOT_FOUND` |

`status`: GOOD ≥ 70, NORMAL 40~69, CHECK < 40, 계산 불가 UNAVAILABLE. `summary`는 서버 템플릿(최고·최저 항목으로 만든 한 줄).
비율 `value`는 0~1 소수 4자리(리포트 소비패턴과 같은 규칙), `description`의 퍼센트는 반올림 정수다.

> **680 변경(`efficiency-v2`):** CREDIT 입력을 A~E 등급에서 예상 신용점수 우선으로 바꿨다. **694 변경(`efficiency-v3`):** 고정지출 임계값 0.4/0.8, 현금흐름·비효율 입력 완화(§6-4).
> **712 변경(`efficiency-v4`):** 현금흐름·비효율을 빼고 부채 상환을 넣었다(§6-5).
> `efficiency-v1`로 저장된 과거 리포트는 그대로 유지한다. 합성 점수의 산식과 한계는
> [credit-score-model.md](credit-score-model.md)를 따른다.

> **635 이전에 만들어진 READY 리포트**는 `efficiency`·카드 사용액이 비어 있다가 다음 `GET /reports/monthly` 조회 때 채워진다
> (`ReportService.supplementIfMissing`). 저장된 합계·`generatedAt`은 바뀌지 않고, 점수의 소비·저축·소득은 저장된 합계를 쓴다.
> 리포트 상세(`GET /reports/{id}`)는 생성하지 않는 조회라 보강도 하지 않는다 — 월간 조회를 한 번 거친 리포트만 채워져 있다.

### 6-2. 카드 사용액 (요청 3)

`MonthlyReport`에 세 필드를 추가했다.

| 필드 | 정의 |
| --- | --- |
| `cardSpending` | 그 달 카드 사용액. **소비 정의(506, 저축성 지출·숨긴 거래 제외)** 중 `card_id`가 있거나 `payment_method = 'CARD'`인 거래의 합. `totalSpending`의 부분집합이다 |
| `cardSpendingPreviousMonth` | 전월 카드 사용액. 전월 비교 규칙은 `previousMonth`와 같다(비교 대상 없으면 null, 전월 거래 0건이면 0) |
| `cardSpendingDifferenceFromPreviousMonth` | `cardSpending − cardSpendingPreviousMonth`(부호 유지). "지난달보다 84,200원 더 썼어요"는 양수, "덜 썼어요"는 음수 |

FE가 지금 쓰는 `/spending/payment-methods`의 `CARD` 묶음과 같은 기준에 카드 원장 거래(`card_id`)를 더한 정의다. 카드 청구서(`card_billing_statement`)
금액이 아니라 **사용일 기준 거래 합**이므로 청구 금액과는 다를 수 있다.

### 6-3. 또래 비교 (요청 2) — [pattern.md](pattern.md) §GET /patterns/me/peer-comparison "635 FE 회신"에 정리했다.

### 6-4. 694 — 계산 불가 항목 `undefined`·현금흐름/비효율 계산 불가·고정지출 0점 (2026-09-22, `efficiency-v3`)

리포트 화면에서 신용·현금흐름·비효율 점수가 `undefined`로 그려지고 고정지출이 0점이던 문제의 원인과 조치.

| 원인 | 조치 |
| --- | --- |
| `Dimension`에 클래스 수준 `@JsonInclude(NON_NULL)`이 있어 계산 불가 항목의 `score: null`이 **키째 빠졌다.** FE는 `score === null`로 계산 불가를 판정하므로 `undefined점`이 됐다 | `score`는 항상 직렬화한다. 생략되는 건 `value`·`valueType`·`grade`·`unavailableReason`뿐. 저장본(`efficiency_summary`)에 키가 없어도 읽을 때 null로 복원되므로 과거 리포트 재생성은 필요 없다 |
| CASHFLOW·INEFFICIENCY가 "**그 달 안**에 실행한" 예측·진단만 봤다. 예측 기준일·진단 완료 시각은 항상 실행 시점이라 지난 달 것을 나중에 만들 수 없고, 한 달이라도 건너뛰면 그 달 리포트의 두 항목은 영원히 계산 불가였다 | 1순위 "그 달 **말일까지** 완료된 가장 최근" 예측·진단, 그것도 없으면 2순위 **리포트를 처음 만드는 시점의 최신** 예측·진단. 2순위를 썼으면 `description`이 "가장 최근 예측/진단 기준으로 …"로 시작한다. `MonthlyReport.diagnosisId`·`overallStatus`도 같은 기준이라 리포트와 비효율 항목이 같은 진단을 본다. 리포트는 최초 조회 때 굳으므로 2순위로 만든 점수도 그 뒤 실행분에 따라 바뀌지 않는다 |
| FIXED_EXPENSE 임계값 0.3/0.7이 가혹했다. 고정지출에 월세·관리비·대출 상환·보험·구독이 모두 들어가 1인 가구 시연 계정도 61%(→ 22점)이고 70%부터 0점이었다 | 임계값을 **0.4/0.8**로 완화(61% → 48점 NORMAL). 산식 변경이라 `efficiency-v3` |
| CREDIT은 `user.credit_rating`이 금융망 동기화 때만 채워져 seed 사용자는 항상 계산 불가였다 | 680이 먼저 해결했다 — `user.credit_score`(V42)를 우선 보고, 680 seed(`DODAM_USER_CREDIT_SCORE_SEED.sql`)가 seed 사용자 전원의 점수를 채운다. 이 seed가 적재되지 않은 DB에서는 여전히 `CREDIT_SCORE_MISSING`이다 |

`previousMonthDifference`의 전월 점수는 전월 저장본이 **같은 버전**일 때만 재사용하므로, v1·v2로 굳힌 전월 리포트 옆에 v3 리포트가 생기면 전월 점수는 v3 산식으로 즉시 계산한다(저장하지 않음).

> **이미 굳힌 리포트는 바뀌지 않는다.** READY 리포트는 최초 조회 때 저장되고 재생성 API가 없다. 시연 계정에 seed·진단·예측을 새로 넣은 뒤
> 리포트에 반영하려면 그 사용자의 `monthly_report` → `report` 행(`type = MONTHLY`, 해당 `period`)을 지우고 다시 조회해야 한다.
> 시연 준비 순서: seed 적재(680 신용점수 seed 포함) → 진단·예측 실행(8월 말까지 것이 없으면 이 실행분이 2순위로 들어간다) →
> 기존 월간 리포트 행 삭제 → `GET /reports/monthly` 최초 조회.

### 6-5. 712 — 현금흐름·비효율 항목을 부채 상환으로 교체 (2026-09-27, `efficiency-v4`)

| 원인 | 조치 |
| --- | --- |
| CASHFLOW·INEFFICIENCY는 사용자가 **먼저 실행한** 현금흐름 예측·발견 분석이 있어야 계산됐다. FE는 `POST /cashflow/forecasts`를 부르는 곳이 없고 진단은 발견 탭에서 분석을 눌러야 생긴다. 694의 폴백이 있어도 실행 전에 처음 연 리포트에는 두 항목이 "계산 불가"로 굳었다(시연 계정 8월 리포트) | 두 항목을 빼고 **DEBT_REPAYMENT 부채 상환**을 넣었다. 지출·저축처럼 그 달 원천 데이터(`loan_repayment` PAID + 리포트 소득)만 읽어 실행 이력과 상관없이 계산된다. 항목 수는 5개 |
| 거래 원장의 대출상환 카테고리로 세면 연결하지 않은 계좌에서 갚는 대출이 0원으로 보여 100점이 나온다 | 대출 도메인의 실제 상환 내역을 쓴다. `SCHEDULED`·`FAILED`·`OVERDUE` 회차는 그 달 소득에서 나간 돈이 아니라 넣지 않는다 |

- 임계값: 소득의 **10% 이하 100점, 40%(은행권 DSR 규제선) 이상 0점**, 사이는 다른 비율 항목과 같이 선형이다. 시연 계정 U0003 8월은 410,331 / 3,012,400 = 13.6% → **88점**.
- `description`: 상환이 있으면 "소득의 N%를 대출 상환에 썼어요.", 없으면 "대출 상환액이 없어요." FE는 SPENDING·SAVING·FIXED_EXPENSE처럼 기준 달을 앞에 붙인다.
- `MonthlyReport.diagnosisId`·`overallStatus`와 §7 패턴(소비 증가·현금흐름 위험)은 그대로 진단·예측을 본다. 효율 점수만 바뀌었다.
- **이미 굳힌 리포트는 v3 그대로다**(6개 항목, 현금흐름·비효율 포함). 새 산식을 보려면 §6-4 끝의 절차대로 해당 월간 리포트 행을 지우고 다시 조회한다. 시연 계정은 `DODAM_DEMO_U0003_DERIVED_RESET_699.sql`로 2026-03~08 리포트를 지울 수 있다. 전월 점수는 같은 버전 저장본만 재사용하므로 v3 전월 옆의 v4 리포트는 전월을 v4로 즉시 계산한다.

## 7. 697 — 주요 지표·금융 생활 패턴 (`MonthlyReport.metrics`, 2026-09-22)

605 협의안의 "금융 생활 패턴"·"주요 지표" 카드를 서버 값으로 내린다. FE `useReportData`가 개별 API 12개(저축률·고정지출·결제수단·대출 요약·현금흐름 위험·소비 급증 ×
이번 달/전월)를 조합해 만들던 것을 `GET /reports/monthly` 한 번으로 대체할 수 있다. 저장 컬럼 `monthly_report.metrics_summary`(**V44**), 조립기
`MonthlyReportMetricsAssembler`(버전 `report-metrics-v1`). 697 이전 READY 리포트는 첫 월간 조회 때 채워진다(§6-1의 보강 경로와 같다).

```json
"metrics": {
  "calculationVersion": "report-metrics-v1",
  "keyMetrics": [
    { "key": "SAVING_RATE", "label": "저축률", "valueType": "RATIO", "value": 0.125, "previousValue": 0.1094, "difference": 0.0156, "asOf": "MONTH" },
    { "key": "FIXED_EXPENSE_SHARE", "label": "고정지출 비중", "valueType": "RATIO", "value": 0.6207, "previousValue": 0.0, "difference": 0.6207, "asOf": "MONTH" },
    { "key": "CARD_SPENDING", "label": "카드 사용액", "valueType": "CURRENCY", "value": 1450000, "previousValue": 1560000, "difference": -110000, "asOf": "MONTH" },
    { "key": "LOAN_BALANCE", "label": "대출 잔액", "valueType": "CURRENCY", "value": 32000000, "previousUnavailableReason": "HISTORY_NOT_STORED", "asOf": "CURRENT" }
  ],
  "patterns": [
    { "key": "SAVING_RATE", "status": "CHECK", "title": "이번 달 저축률은 13%예요", "description": "수입 3,200,000원 중 400,000원을 저축했어요.",
      "evidencePeriod": "2026-08", "evidence": { "income": 3200000, "saving": 400000, "rate": 0.125, "goodThreshold": 0.2 } },
    { "key": "SPENDING_INCREASE", "status": "CHECK", "title": "식비 소비가 늘었어요", "description": "직전 3개월 평균보다 월 40,000원 늘었어요.",
      "evidencePeriod": "2026-07 vs 2026-04~2026-06", "evidence": { "diagnosisId": 77, "issueId": 301, "issueCount": 1, "categoryCode": "FOOD", "categoryName": "식비", "increaseAmount": 40000, "baselineMonths": 3, "increaseRatePercent": 33.3 } },
    { "key": "CASHFLOW_RISK", "status": "UNAVAILABLE", "title": "현금흐름 예측 데이터가 없어요", "description": "완료한 현금흐름 예측이 없어 예정 잔액 위험을 확인하지 못했어요.",
      "evidencePeriod": "2026-08", "unavailableReason": "FORECAST_NOT_FOUND" }
  ]
}
```

### 7-1. 주요 지표 `keyMetrics[]` (순서 고정)

| key | valueType | 이번 달 `value` | 전월 `previousValue` | 계산 불가 |
| --- | --- | --- | --- | --- |
| SAVING_RATE | RATIO(0~1, 4자리) | `totalSaving / totalIncome` — 효율 점수 SAVING 항목의 `value`와 같은 수 | 전월 합계로 같은 식 | 소득 0 → `INCOME_MISSING` |
| FIXED_EXPENSE_SHARE | RATIO | `bySpendingType.FIXED / totalSpending` — FIXED_EXPENSE 항목의 `value`와 같은 수 | 전월 고정지출 / 전월 소비 | 소비 0 → `NO_SPENDING` |
| CARD_SPENDING | CURRENCY(원) | §6-2 `cardSpending` | `cardSpendingPreviousMonth` | — |
| LOAN_BALANCE | CURRENCY | `GET /loans/summary`의 `totalBalance`(ACTIVE·OVERDUE 잔액 합). **생성·보강 시점 현재 값**(`asOf = CURRENT`) | 없음 — `previousUnavailableReason = HISTORY_NOT_STORED` | 대출 없음은 0(모름이 아니다) |

- `difference = value − previousValue`(부호 유지, 비율은 4자리 반올림). 어느 한쪽이 null이면 null.
- 전월 규칙은 본문 `previousMonth`와 같다: 사용자 첫 거래 이전 달이면 `previousUnavailableReason = NO_PREVIOUS_MONTH`, 전월이 있는데 분모가 0이면 그 사유(`INCOME_MISSING`·`NO_SPENDING`).
- 대출 잔액은 자산 총액과 별개의 지표다(605 요청대로 자산에서는 제외, 이 카드에는 유지). 월별 잔액 이력을 저장하지 않으므로 전월 비교는 하지 않는다 — 필요하면 `loan` 스냅샷 테이블이 선행돼야 한다([discovery-report-metrics-675.md](discovery-report-metrics-675.md) §3).

### 7-2. 금융 생활 패턴 `patterns[]` (순서 고정, 3장)

| key | GOOD | CHECK | UNAVAILABLE | evidencePeriod |
| --- | --- | --- | --- | --- |
| SAVING_RATE | 저축률 ≥ 20% | < 20% | 소득 0 → `INCOME_MISSING` | 기준월 |
| SPENDING_INCREASE | 그 달 말일까지 완료된 가장 최근 진단, 없으면 생성 시점 최신 진단(`MonthlyReport.diagnosisId`, 694 v3 기준)에 `CATEGORY_SPENDING_INCREASE` 문제 없음 | 있음 — 증가액 최대 카테고리를 제목·설명에, `evidence.issueCount`에 건수 | 말일까지 진단 없음 → `DIAGNOSIS_NOT_FOUND` | 진단이 비교한 기간(`recentPeriod vs baselinePeriod`) |
| CASHFLOW_RISK | 기준일이 그 달 말일 이전인 마지막 완료 예측, 없으면 생성 시점 최신 완료 예측(효율 점수 CASHFLOW 항목과 같은 예측, 694 v3 기준 — 폴백이면 문구 앞에 "가장 최근 예측 기준으로", `evidence.afterMonthEnd=true`)에 위험 없음 | 위험 있음 — 가장 이른 `riskDate`의 `message`를 설명에, `evidence`에 `riskCount·riskId·riskDate·severity·expectedBalance` | 말일까지 예측 없음 → `FORECAST_NOT_FOUND` | 예측 기준일~기준일+horizon |

- **자료 없음(UNAVAILABLE)과 위험 없음(GOOD)을 구분한다**(605 요청 2). FE 임시 구현은 진단·예측이 없을 때도 "늘어난 소비가 없어요"를 보여 줄 수 있었다.
- 소비 증가 판정은 API 6 `/spending/anomalies`(N개월 평균 대 N개월 평균)가 아니라 **진단 규칙**(지난달 vs 직전 3개월 평균, 631)이다. 발견 화면과 같은 판정을 쓰기 위해서다. 다만 그 진단이 본 "지난달"은 리포트 기준월의 전달일 수 있다(8월 리포트 ← 8월에 완료된 진단은 7월 vs 4~6월). `evidencePeriod`에 실제 비교 기간이 있으니 FE는 이를 그대로 표기한다.
- 문구는 서버 템플릿이고 AI 문장이 아니다. 코치 문구는 API 21 그대로.
- 저축률 20% 경계는 FE 임시 구현 값을 옮긴 **서비스 초기값**이다. 바꾸면 `calculationVersion`을 올린다(과거 리포트 저장본은 유지).

### 7-3. FE 전환

- `ReportKeyMetric`(저축률·고정지출·카드·대출)·`ReportFinancePattern`(3장)을 `metrics.keyMetrics[]`·`metrics.patterns[]`로 매핑하면 `ledgerApi.getSavingRatio×2`, `getFixedSpending×2`, `getPaymentMethodSpending×2`, `assetApi.getLoanSummary`, `cashflowApi.getRisks`, `ledgerApi.getSpendingAnomalies` 호출을 걷어낼 수 있다. 남는 개별 호출은 지난 소비 6개월(`getCategoryTrend`)·또래 비교·코치 문구다.
- `status`는 FE의 `'good' | 'check'`에 `unavailable`을 추가해야 한다. `unavailable`은 "확인 중/자료 없음" 톤으로 그린다.
- 아이콘은 `key`로 고른다(FE 상수). 서버는 아이콘을 내리지 않는다.

- `value`·`previousValue`·`difference` 키는 null이어도 항상 직렬화한다(694와 같은 이유 — FE가 `=== null`로 판정). 사유·`evidence`·`asOf`만 없을 때 생략한다.

