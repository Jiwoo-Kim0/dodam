# Day 11 Improvement / Report 응답 계약 (FE 선공유)

작성일: 2026-09-16. **구현 전 선공유본이다.** Day 11에 구현할 API 19(Improvement)·20(Report)의 응답 형식을
FE가 미리 화면을 만들 수 있도록 먼저 고정한다.

기준 자료: [API 19](../../docs/API명세서/19%20Improvement%203cdbb6ea35648084a55bf4cec46b357d.csv),
[API 20](../../docs/API명세서/20%20Report%203cdbb6ea3564807e98cfddff069700c1.csv),
[ERD](../../docs/ERD/ERD%20작성%203c9bb6ea356480898bd9eddac2e6840d.md)(ImprovementAction / Report·DailyReport·MonthlyReport),
[기획안](../../docs/기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md) 5-6(개선 결과 추적)·데일리 브리핑.

> **바뀔 수 있는 것과 아닌 것**
> - 바뀌지 않는다: 공통 봉투, 페이지 형식, 필드 이름, 타입, null 규칙, 에러 코드.
> - 바뀔 수 있다: 계산식 세부(어떤 거래를 "절감"으로 볼지), 일부 필드가 구현 시점에 항상 null로 내려갈 가능성.
>   그 경우에도 **필드는 유지**한다 — FE가 옵셔널 체이닝만 하면 되도록.
> 변경이 생기면 이 문서를 갱신하고 FE에 공유한다.

## 0. 공통 규약 (기존 API와 동일)

```json
{ "success": true, "data": { ... }, "message": null, "code": null }
```

| 항목 | 규칙 |
| --- | --- |
| 에러 | `{ "success": false, "data": null, "message": "...", "code": "RESOURCE_NOT_FOUND" }` |
| 인증 | 전부 필수. 미인증 **401**, 남의 리소스·없는 리소스 모두 **404 `RESOURCE_NOT_FOUND`**(구분하지 않는다) |
| 페이지 | `{ content, page, size, totalElements, totalPages, last }`. `page ≥ 0`, `size 1~100`, 위반 시 **400 `VALIDATION_ERROR`** |
| 기간 | `startDate > endDate` → **400**. 기본 3개월·최대 12개월(`QueryPeriodPolicy`) |
| 날짜 | `yyyy-MM-dd`, 시각 `yyyy-MM-ddTHH:mm:ss`(Asia/Seoul), 월 `yyyy-MM` |
| 금액 | **원 단위 정수**(JSON number). 소수·문자열 아님. 음수 가능 필드는 아래 표에 명시 |
| 미계산 | 계산할 수 없으면 **`null`**이며 0으로 채우지 않는다. 0원과 "모름"은 다르다(추천 효과 8종이 이미 쓰는 규칙) |

> **집계 API의 `null` 처리**: 데이터가 없어서 0인 경우와 계산 불가인 경우를 `total*`은 0으로,
> `*Unavailable`/`unavailableReason`으로 구분한다. FE는 `unavailableReason != null`이면 수치 대신 안내 문구를 띄운다.

---

## 1. API 19 — Improvement (`/api/v1/improvements`)

`ImprovementAction` = 사용자가 **실제로 수행한 개선 행동** 1건. `recommendation_id`로 개선안과 1:0..1로 이어진다
(개선안을 ACCEPT하면 행동이 생긴다). 상태: `PLANNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELED`.

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
  "plannedAt": "2026-09-01T10:00:00",
  "startedAt": "2026-09-02T09:30:00",
  "completedAt": "2026-09-10T18:00:00",
  "canceledAt": null,
  "expectedMonthlySaving": 12000,
  "actualMonthlySaving": 11000,
  "measuredAt": "2026-10-01T04:00:00"
}
```

- `expected*` = 추천이 계산해 둔 예상치, `actual*` = 실행 후 실제 데이터로 측정한 값. **둘을 합치지 않는다.**
- 측정 전이면 `actual*`과 `measuredAt`이 `null`이다. 측정은 실행 후 최소 1개월 데이터가 쌓여야 가능하다.

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
  "lastMeasuredAt": "2026-10-01T04:00:00"
}
```

`byStatus`는 **4개 상태 키를 항상 모두** 포함한다(0이어도). FE가 키 존재 여부를 분기하지 않도록 —
추천 요약(`RecommendationSummaryResponse`)이 이미 쓰는 규칙이다.

### 1-2. `ActionComparison` (GET /actions/{actionId}/comparison)

기획안 5-6 "실행 전후의 금융 데이터를 비교하여 개선 효과를 수치화".

```json
{
  "actionId": 12,
  "status": "COMPLETED",
  "beforePeriod": { "startDate": "2026-06-01", "endDate": "2026-08-31" },
  "afterPeriod":  { "startDate": "2026-09-01", "endDate": "2026-11-30" },
  "metrics": [
    { "metric": "MONTHLY_SPENDING", "displayName": "월 소비금액",
      "before": 1850000, "after": 1739000, "delta": -111000, "improved": true },
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
  (소비는 줄어야 개선, 저축은 늘어야 개선이라 부호만으로 판단할 수 없다).
- 비교 기간이 아직 안 찼으면 `metrics: []` + `unavailableReason: "AFTER_PERIOD_NOT_READY"`.
  다른 값: `ACTION_NOT_COMPLETED`, `INSUFFICIENT_DATA`.
- `metric` 코드는 **확정**이며 늘어날 수 있다. FE는 모르는 코드를 만나면 `displayName`으로 렌더링한다.

### 1-3. `AmountTotal` (GET /savings, /debt-reduction, /spending-reduction, /saving-increase)

네 API가 **같은 형식**을 쓴다. 화면이 4개 타일을 같은 컴포넌트로 그릴 수 있게 한 것이다.

```json
{
  "metric": "SAVINGS",
  "totalAmount": 372000,
  "monthlyAmount": 31000,
  "currency": "KRW",
  "measuredFrom": "2026-07-01",
  "measuredTo": "2026-11-30",
  "contributingActions": 3,
  "unavailableReason": null
}
```

`metric`: `SAVINGS` | `DEBT_REDUCTION` | `SPENDING_REDUCTION` | `SAVING_INCREASE`.
측정된 행동이 없으면 `totalAmount: 0`, `contributingActions: 0`, `unavailableReason: null`
(데이터가 없어서 0인 것이지 계산 불가가 아니다).

### 1-4. `GoalImpactSummary` (GET /goal-impact)

기획안 5-6 "목표 달성 예상 시점 3개월 단축" 화면.

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

- `advancedDays` 양수 = 앞당겨짐, 음수 = 밀림. `null`이면 한쪽 예상일을 계산할 수 없다.
- ACTIVE 목표만 담는다. 목표가 없으면 `goals: []`.

### 1-5. `MonthlyTrend` (GET /monthly-trend)

```json
{
  "startMonth": "2026-06",
  "endMonth": "2026-11",
  "months": [
    { "yearMonth": "2026-06", "completedActions": 0, "monthlySaving": 0,
      "spendingReduction": 0, "savingIncrease": 0, "debtReduction": 0 }
  ]
}
```

`months`는 **빈 달을 0으로 채워 연속**으로 내려준다. 차트가 구멍을 메우지 않아도 되도록.

### 1-6. `RediagnoseResult` (POST /rediagnose)

```json
{ "diagnosisId": 77, "goalId": 5, "created": true, "requestedAt": "2026-09-16T12:00:00" }
```

- 신규 진단이면 **201 + `created: true`**, 중복 요청이 막혀 직전 진단을 돌려주면 **200 + `created: false`**.
  (진단 실행 API `POST /api/v1/diagnoses`가 이미 쓰는 규약과 같다.)
- 진행 중이면 **409 `DIAGNOSIS_IN_PROGRESS`**.

---

## 2. API 20 — Report (`/api/v1/reports`)

`Report`(종합) + `DailyReport` / `MonthlyReport`(하위). `(user_id, type, period)`당 1건.

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

`yearMonth`는 `yyyy-MM`이며 형식이 틀리면 **400 `VALIDATION_ERROR`**. 생략하면 직전 완료 월.
`date` 생략 시 어제(브리핑은 "전날에 대한" 요약이다 — 기획안).
해당 기간 리포트가 없으면 **404 `RESOURCE_NOT_FOUND`**.

### 2-1. `ReportSummary` (목록 항목)

```json
{ "reportId": 91, "type": "MONTHLY", "period": "2026-08", "generatedAt": "2026-09-01T05:00:00" }
```

`period`는 타입에 따라 `yyyy-MM-dd`(DAILY) 또는 `yyyy-MM`(MONTHLY)인 **문자열**이다.
상세(#5)는 `type`에 따라 아래 두 형식 중 하나를 돌려주며, 공통 필드 `reportId`·`type`·`period`·`generatedAt`로 분기한다.

### 2-2. `DailyReport` (GET /daily)

```json
{
  "reportId": 120,
  "type": "DAILY",
  "period": "2026-09-15",
  "generatedAt": "2026-09-16T05:00:00",
  "totalIncome": 0,
  "totalExpense": 47800,
  "transactionCount": 4,
  "topCategories": [
    { "categoryCode": "DINING", "categoryName": "외식", "amount": 32000, "count": 2 }
  ],
  "balanceChange": -47800,
  "notableEvents": [
    { "code": "SUBSCRIPTION_CHARGED", "message": "정기결제 1건이 결제되었습니다.", "amount": 12000 }
  ],
  "coachMessage": null
}
```

- `coachMessage`는 **API 21의 응답을 여기에 끼워 넣지 않는다.** 별도 호출(`POST /api/v1/coach/...`)이며
  이 필드는 나중에 브리핑 전용 문구가 생길 자리다. 당분간 `null`로 내려간다.
- `balanceChange = totalIncome − totalExpense`. 음수 가능.

### 2-3. `MonthlyReport` (GET /monthly)

```json
{
  "reportId": 91,
  "type": "MONTHLY",
  "period": "2026-08",
  "generatedAt": "2026-09-01T05:00:00",
  "totalIncome": 3200000,
  "totalExpense": 1850000,
  "totalSaving": 400000,
  "netCashflow": 1350000,
  "previousMonth": { "totalIncome": 3200000, "totalExpense": 1960000, "totalSaving": 350000 },
  "changeFromPreviousMonth": { "totalIncome": 0, "totalExpense": -110000, "totalSaving": 50000 },
  "diagnosisId": 77,
  "overallStatus": "CAUTION"
}
```

- `previousMonth`가 없으면(첫 달) 해당 필드와 `changeFromPreviousMonth`가 **둘 다 `null`**이다.
- `netCashflow = totalIncome − totalExpense`. `totalSaving`은 저축성 지출이며 `totalExpense`에 **포함**된다
  (이중 차감하지 말 것 — 소비 집계와 같은 규칙이다).

### 2-4. `MonthlySpendingPattern` (GET /monthly/spending-pattern)

```json
{
  "yearMonth": "2026-08",
  "totalExpense": 1850000,
  "byCategory": [
    { "categoryCode": "DINING", "categoryName": "외식", "amount": 420000, "ratio": 0.2270, "count": 31 }
  ],
  "bySpendingType": { "FIXED": 900000, "VARIABLE": 850000, "NON_SPENDING": 100000 },
  "weekdayWeekend": { "weekday": 1200000, "weekend": 650000 },
  "comparedToPrevious": { "amountDelta": -110000, "ratioDelta": -0.0561 }
}
```

`ratio`는 **0~1 소수 4자리**다. 퍼센트 변환은 FE가 한다(패턴 분석 API 3과 같은 규칙).
`bySpendingType`은 3개 키를 항상 포함한다.

### 2-5. `MonthlyGoalImpact` (GET /monthly/goal-impact)

```json
{
  "yearMonth": "2026-08",
  "goals": [
    { "goalId": 5, "goalName": "비상금 1,000만원 만들기",
      "startAmount": 3000000, "endAmount": 3400000, "contributed": 400000,
      "progressRatio": 0.34,
      "forecastDateAtStart": "2028-03-31", "forecastDateAtEnd": "2027-12-31", "advancedDays": 91 }
  ]
}
```

`goalId`를 주면 해당 목표 1건만, 남의 목표면 **404**.

### 2-6. `UpcomingExpenses` (GET /monthly/upcoming-expenses)

다음 달 **예정** 비용이다. 이미 확정된 스케줄만 담고 추정하지 않는다.

```json
{
  "yearMonth": "2026-10",
  "totalAmount": 1240000,
  "items": [
    { "sourceType": "CARD", "label": "카드 결제예정금액", "amount": 780000, "dueDate": "2026-10-14" },
    { "sourceType": "LOAN", "label": "대출 상환", "amount": 320000, "dueDate": "2026-10-25" },
    { "sourceType": "SUBSCRIPTION", "label": "정기결제", "amount": 140000, "dueDate": null }
  ],
  "unavailableReason": null
}
```

`sourceType`: `CARD` | `LOAN` | `SUBSCRIPTION` | `SAVINGS` | `MINUS_ACCOUNT`.
`dueDate`가 `null`인 것은 그 달 안에 여러 건이 흩어져 있다는 뜻이다(합계만 의미 있다).

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

---

## 3. FE가 먼저 확인할 것

1. **`null`과 `0`을 구분해 렌더링**한다. `null` = 아직 측정 못 함(빈 상태 UI), `0` = 측정했는데 0원.
2. **`unavailableReason`이 있는 응답**(`ActionComparison`, `AmountTotal`, `UpcomingExpenses`,
   `GoalImpactSummary.goals[]`)은 그 값이 `null`이 아니면 수치 대신 안내 문구를 띄운다.
3. **`byStatus`·`bySpendingType` 같은 맵은 키가 항상 전부 있다.** 반대로 `topCategories`·`items`·`goals`는
   빈 배열일 수 있다.
4. **`delta`류는 부호가 그대로다.** 좋아졌는지는 `improved` 플래그로 판단한다.
5. **AI 코치 문장은 리포트 응답에 들어 있지 않다.** 필요하면 API 21을 별도로 호출한다
   ([ai-coach.md](ai-coach.md)). 리포트의 `coachMessage`는 당분간 `null`이다.
