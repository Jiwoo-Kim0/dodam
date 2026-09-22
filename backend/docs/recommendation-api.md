# Recommendation API — 생성·조회 (S15P21D105-548) · 상태변경·이력 (S15P21D105-549) · 시뮬레이션 프리셋 (S15P21D105-587)

작성일: 2026-09-13, 갱신 2026-09-15(587 §5.1)·2026-09-16(627 생성기 2종, 628 `sourceIssueId`). 547 저장 기반([recommendation.md](recommendation.md)) 위에 API 16의 HTTP 경로 13개를 구현했다.
계획과 결정 배경은 [recommendation-api-plan.md](recommendation-api-plan.md), [simulation-587-plan.md](simulation-587-plan.md) §3에 있다.

기준 자료: [API 16](../../docs/API명세서/16%20Recommendation%203cdbb6ea3564801eb1bbc55201aee86a.csv),
[기획안](../../docs/기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md) 4-5·5-3·5-4·5-6, [진단 API](diagnosis-api.md) §규칙·근거.

## 1. 범위

| 포함 | 제외(후속) |
| --- | --- |
| 운영 생성기 6종(진단이 만드는 문제 유형 대응. 627: 카테고리 소비 증가·고금리 대출, 640: 단독 군집 이탈) | 카드·계좌·예적금 추천 — 대응 진단 규칙 미구현 |
| 효과 계산, 우선순위 점수·순위, 군집 가중치 α = 1(640, §3) | |
| POST generate, GET 목록·summary·priorities·상세·reasons·effect·goal-impact | ACCEPT 시 `improvement_action` 생성 (Improvement 이슈) |
| POST accept·reject·complete·defer, GET history | 대출·카드 등 추가 유형의 시뮬레이션 대응 |
| 추천 응답의 시뮬레이션 프리셋 `simulation`(587, §5.1) | 시뮬레이션 쪽 추천 id 저장(결과 추적은 Improvement/Report) |

## 2. 생성기와 효과 계산

생성기는 순수 함수이며 **진단이 저장한 값만** 읽는다(현재 데이터로 재계산하지 않는다). 버전 `548.1`. 규칙 의미가 바뀌면 올린다 — generation_key 공간이 갈려 같은 진단에서 새 추천이 만들어진다.
기준일은 진단 스냅샷 `asOf`(접수 시각, 위험 판정에 쓴 날), 없으면 완료일이다.

| 진단 문제 | 추천 domain/type · candidateKey | 읽는 값 | 효과 |
| --- | --- | --- | --- |
| GOAL/SAVING_GOAL_RISK | SAVING / INCREASE_MONTHLY_SAVING · `:<goalId>` | `currentValue.requiredMonthlySaving`(R)·`averageMonthlySaving`(A), `evidence.savingGoalSnapshot.targetAmount`, `evidence.goalSnapshot.currentAmount` | `contributionAmount = R − A`. `remaining = target − current`. `currentForecastDate = GoalForecastFormula.expectedDate(remaining, A, base)`(A ≤ 0 → null), `improvedForecastDate = GoalForecastFormula.expectedDate(remaining, R, base)`(510·시뮬레이션과 같은 일 단위 식, 658), `advancedDays = current − improved`(한쪽 null → null). R − A ≤ 0 또는 remaining = 0 → 후보 없음. 금액 필드 누락 → 날짜 null + 근거 `SNAPSHOT_FIELD_MISSING` |
| GOAL/SPENDING_GOAL_RISK | SPENDING / REDUCE_SPENDING · `:<goalId>` | `currentValue.monthlyBudget·spent·projectedMonthEnd` | `monthlyCostSaving = max(0, max(spent, projected) − budget)`, `annualCostSaving = monthly × 12`(가정 `ANNUAL_IS_MONTHLY_TIMES_12`, 이 생성기만). 0 → 후보 없음 |
| CASHFLOW/BALANCE_SHORTAGE | CASHFLOW / SECURE_SHORTAGE_BUFFER · `:<riskId>` | `currentValue.riskId·riskDate·expectedBalance`, `evidence.forecastId` | 부족액 `max(0, −expectedBalance)`. `riskReduction = BALANCE_SHORTAGE_AVOIDED`, 금액 효과 null |
| SPENDING/CATEGORY_SPENDING_INCREASE (627, 버전 `627.3` — 631) | SPENDING / REDUCE_CATEGORY_SPENDING · `:<categoryId>` | `currentValue.categoryId·categoryCode·categoryName·monthlyAverage·increaseAmount·recentPeriod.months`, `benchmarkValue.previousMonthlyAverage·baselinePeriod.months` | `monthlyCostSaving = increaseAmount`(631부터 지난달 − 직전 3개월 월평균; 직전 3개월 평균 수준으로 되돌리기), `annualCostSaving = monthly × 12`(유지 — 발견 화면 정렬·요약 합산이 의존). 단월 증가를 연간화했다는 근거로 evidence에 `annualizationBasis = SINGLE_MONTH_INCREASE_X12`(v5 형식 진단이면 `RECENT_AVERAGE_INCREASE_X12`)·`recentMonths`·`baselineMonths`를 남긴다. FE는 이 값으로 "지속 시 연간" 표기를 붙인다. 증가액 ≤ 0 → 후보 없음. 근거 `CATEGORY_SPENDING_INCREASE` evidence에 `categoryCode` 보존(프리셋용). 문구: "직전 3개월 월평균 수준으로 되돌리면 월 N원을 아낄 수 있어요." / 근거 "지난달 식비 소비 A원이 직전 3개월 월평균 B원보다 C원 많아요." |
| `*`/CLUSTER_* — 단독 군집 이탈(641 `DetectionSource.CLUSTER`, 640 버전 `640.1`) | `<문제 domain>` / REVIEW_SPENDING_PATTERN · `:<feature>` | `clusterContext.feature·displayName·userValue·p5·p95·deviation·direction·clusterLabel·sampleSize`, `evidence.patternAnalysisId` | **효과 없음**(`NONE`). 군집 이탈은 "비슷한 소비 유형 사용자군 대비 점검 항목"이지 낭비 판정이 아니라 절감액을 만들지 않는다(기획안 5-7). 근거 `CLUSTER_RANGE_DEVIATION`(evidence에 위 값과 `modelVersion`·`window`). 문구: "비슷한 유형과 소비 구조 비교해 보기" / 소득 대비 소비율·재량 소비 비중은 "줄일 금액을 정하면 시뮬레이션할 수 있어요", 나머지는 "낭비로 단정하지 않아요". `clusterContext` 없음 → `NO_CLUSTER_CONTEXT`, 필드 누락 → `INSUFFICIENT_DATA`. 규칙 문제에 근거만 병합된 `RULE_AND_CLUSTER`는 기존 생성기가 그대로 만든다 |
| LOAN/HIGH_INTEREST_RATE (627, 버전 `627.1`) | LOAN / REFINANCE_LOAN · `:<loanId>` | `currentValue.loanId·accountName·balance·interestRate·referenceRate·rateDifference·refinance{available, targetProductId, appliedRate, remainingInstallments, interestSaving…}` | 추정 있음: `interestSaving`(만기까지 총이자 차이), `monthlyCostSaving = interestSaving / 잔여 회차`, `annualCostSaving = monthly × min(12, 잔여 회차)`(잔여 12회 미만에 ×12를 적용하면 부풀려지므로 소비 생성기와 다르다). 근거 `LOAN_RATE_ABOVE_MARKET` + `REFINANCE_INTEREST_SAVING`(evidence `loanId·targetProductId·feesIncluded=false`). 추정 없음(연체·조건 맞는 상품 없음): 제목 "대환 조건 비교하기", 효과 `NONE`, 근거 `LOAN_RATE_ABOVE_MARKET` + `REFINANCE_ESTIMATE_UNAVAILABLE`(evidence `reason`) |

예상 달성일 식은 `GoalCalculationService.forecast()`와 같다. 현재·개선 달성일을 **같은 식·같은 기준일**로 계산하므로 546이 스냅샷에 넣는 `forecast`는 읽지 않는다(v2·v3 진단 동일 결과).
근거 코드: `GOAL_SAVING_SHORTFALL`, `GOAL_FORECAST_DELAY`, `SNAPSHOT_FIELD_MISSING`, `SPENDING_OVER_BUDGET`, `CASHFLOW_SHORTAGE`,
`CATEGORY_SPENDING_INCREASE`, `LOAN_RATE_ABOVE_MARKET`, `REFINANCE_INTEREST_SAVING`, `REFINANCE_ESTIMATE_UNAVAILABLE`(627).
우선순위 점수에서 LOAN 추천은 실행 난이도 +5를 받지 않고, `interestSaving`은 효과 점수에 들어가지 않는다(`monthlyCostSaving`만).

## 3. 우선순위 (`RecommendationPriorityPolicy`)

| 기준 | 점수 |
| --- | --- |
| 금융 위험도 | 원본 문제 severity CRITICAL 40 / HIGH 30 / MEDIUM 20 / LOW 10 |
| 예상 개선 효과 | max(monthlyCostSaving, contributionAmount)를 10만 원 단위 올림, 최대 30 |
| 목표 기여도 | advancedDays 30일당 5, 최대 20 |
| 시급성 | CASHFLOW `riskDate − 기준일` 14일 이내 20, 30일 이내 10 (지난 날짜도 14일 이내) |
| 실행 난이도 | SPENDING·SAVING +5 |
| 군집 이탈 α (640) | 원본 문제 `clusterContext.deviationScore`가 있으면 `min(10, round(α × deviationScore × 10))`, α = 1. RULE_AND_CLUSTER(병합)·CLUSTER(단독) 모두 같은 식. deviationScore는 특징 5종 이탈의 평균(대개 0~0.5)이라 기여는 최대 5점 안팎 — 심각도 한 단계(10점)를 뒤집지 못하는 동점 해소 수준(기획안 5-4). α는 서비스 초기값, 실측 후 조정 |

`priority` = 한 생성 요청 안의 점수 내림차순 순위(1부터), 동점은 issueId 오름차순 → candidateKey. 점수는 저장 컬럼이 없어 생성 응답 `items[].score`에만 노출한다.
재생성으로 기존 행을 돌려받을 때 priority는 갱신하지 않는다. 다른 진단의 priority는 비교하지 않는다 → priorities API는 최신 완료 진단으로 범위를 좁힌다.

## 4. 생성 API

`POST /api/v1/recommendations/generate` Body `{ diagnosisId, goalId? }` → 신규 1건 이상이면 **201**, 전부 재사용·후보 0건이면 **200**.

응답 `{ diagnosisId, goalId, created, reused, items[]{recommendation, score, created}, skipped[]{issueId, issueType, reason} }`.
`items[].recommendation.sourceIssueId`(628)는 그 추천을 만든 진단 문제 id다. FE는 `GET /diagnoses/{id}/issues`의 `issueId`와 이 값으로 문제별 추천을 고른다 — 627부터 같은 진단에 같은 domain/type 문제가 여러 개(카테고리별·대출별) 생기므로 정렬 순서로 대응시키면 틀린다. 여러 문제를 합친 후보와 628 이전에 만든 추천은 null이다(재생성해도 기존 행을 돌려주므로 채워지지 않는다).
`goalId` 생략 시 진단의 goalId를 물려받는다. 후보 0건이면 `items` 빈 목록이고 `skipped`가 이유를 드러낸다(문제 0건이면 skipped도 빈 목록).
같은 진단·같은 규칙 버전은 같은 generation_key → 재요청은 `reused`로 기존 행(상태·근거·priority 불변). 동시 요청은 사용자 행 잠금으로 직렬화된다.

| 상황 | 결과 |
| --- | --- |
| diagnosisId 없음·0, goalId ≤ 0 | 400 `VALIDATION_ERROR` |
| 타인·없는 진단, 타인·삭제된 목표 | 404 `RESOURCE_NOT_FOUND` |
| 미완료 진단 | 409 `DIAGNOSIS_NOT_COMPLETED` |
| goalId ≠ 진단 goalId | 400 `RECOMMENDATION_GOAL_MISMATCH` |

## 5. 조회 API

공통: 타인·없는 추천은 404, 저장값만 반환, `@Transactional(readOnly = true)`.

| Endpoint | 파라미터 | 응답 | 정렬·빈 결과 |
| --- | --- | --- | --- |
| `GET /recommendations` | `status?`(enum 외 400), `domain?`(`^[A-Z][A-Z0-9_]{0,49}$`), `page≥0`, `size 1~100` 기본 20 | `PageResponse<Recommendation>` | `createdAt DESC, id DESC` |
| `GET /recommendations/summary` | 없음 | `total, byStatus{5종, 0 포함}, byDomain{}, expectedMonthlyCostSaving, expectedContributionAmount`(PENDING+ACCEPTED 합), `discovery{…}`(§5.2, 2026-09-16) | 0으로 채운 객체 |
| `GET /recommendations/priorities` | `goalId?`(타인·삭제 → 404) | `diagnosisId, goalId, recommendations[]` — 최신 완료 진단(goalId 있으면 그 목표의)의 PENDING·DEFERRED를 `priority ASC, id ASC` | 완료 진단 없음 → `diagnosisId=null`, 빈 목록 |
| `GET /recommendations/{id}` | | `recommendationId, diagnosisId, sourceIssueId(628), goalId, domain, type, title, description, priority, status, effect{…}, simulation{…}(§5.1), createdAt, updatedAt` | |
| `GET /{id}/reasons` | | `[{reasonId, code, description, evidence}]` `reasonId ASC` | 빈 배열 |
| `GET /{id}/effect` | | `available, 8개 효과 필드, measuredAt(=createdAt), assumptions[]` | 모두 null → `available=false` |
| `GET /{id}/goal-impact` | | `goalId, currentForecastDate, improvedForecastDate, advancedDays, contributionAmount, unavailableReason`(`NO_GOAL` / `NOT_CALCULATED` / null) | |

### 5.1 시뮬레이션 프리셋 `simulation` (587)

`RecommendationResponse`가 나가는 모든 곳(생성 `items[].recommendation`, 목록 항목, priorities 항목, 상세, 상태변경 응답)에 같은 값으로 실린다. `sourceIssueId`(628)도 같은 다섯 곳에 실린다.
저장된 추천·근거를 명세 17 요청 본문 모양으로 재배열한 **실행 제안**이며 재계산·실행이 아니다. FE는 `request`를 그대로 또는 사용자가 고쳐서 `endpoint`에 POST한다.
조립은 `RecommendationSimulationPresetAssembler`가 하고, 목록·priorities에서는 SAVING 추천의 근거를 `findAllOwnedIn`으로 한 번에 읽는다.

```json
"simulation": {
  "available": true,
  "type": "SAVING",
  "endpoint": "/api/v1/simulations/saving",
  "request": { "accountNo": null, "goalId": 101, "monthlySavingAmount": 1000000, "durationMonths": null },
  "assumptions": []
}
```

| 추천 domain/type | type · endpoint | request | 값의 출처 | assumptions |
| --- | --- | --- | --- | --- |
| SPENDING / REDUCE_SPENDING | SPENDING · `/api/v1/simulations/spending` | `{category: null, monthlyReduction, durationMonths: null}` | `effect.monthlyCostSaving`. 진단 SPENDING_GOAL_RISK가 목표 예산 전체 기준이라 카테고리 미지정 | `REDUCTION_APPLIES_TO_TOTAL_SPENDING` |
| SAVING / INCREASE_MONTHLY_SAVING | SAVING · `/api/v1/simulations/saving` | `{accountNo: null, goalId, monthlySavingAmount: R, durationMonths: null}` | `goalId`는 추천의 `goalId`(619). R은 근거 `GOAL_SAVING_SHORTFALL` evidence의 `requiredMonthlySaving`. 시뮬레이션은 변경 후 절대값을 받으므로 `contributionAmount`(R − A)가 아니라 R을 준다 | 없음. 추천에 `goalId`가 없을 때만 `TARGET_GOAL_NOT_SELECTABLE_WITHOUT_ACCOUNT` |
| SPENDING / REDUCE_CATEGORY_SPENDING (627, 631) | SPENDING · `/api/v1/simulations/spending` | `{category: <categoryCode>, monthlyReduction, durationMonths: null}` | `category`는 근거 `CATEGORY_SPENDING_INCREASE` evidence의 `categoryCode`, 금액은 `effect.monthlyCostSaving`. **(631) 시뮬레이션 기준선(직전 3개월 카테고리 월평균)을 한 번 읽어 그보다 크면 그 값으로 자른다** — 진단은 지난달 vs 직전 3개월 평균이라 6·7월 소비가 거의 없고 8월만 튄 카테고리에서 증가액이 6~8월 평균을 넘을 수 있고, 그대로 보내면 400 `SIMULATION_REDUCTION_EXCEEDS_SPENDING`이다. 이 유형만 "저장된 값만 읽는다" 원칙의 예외다 | 잘랐으면 `REDUCTION_CAPPED_TO_SIMULATION_BASELINE`. 기준선에 그 카테고리 소비가 없으면 `available=false`, `SIMULATION_BASELINE_UNAVAILABLE`. 근거에 코드가 없으면 `SNAPSHOT_FIELD_MISSING`, 금액 없음 `EFFECT_AMOUNT_MISSING` |
| LOAN / REFINANCE_LOAN (627) | LOAN_REFINANCE · `/api/v1/simulations/loan/refinance` | `{accountNo: null, loanId, targetProductId, refinanceAmount: null}` | `loanId`·`targetProductId`는 근거 `REFINANCE_INTEREST_SAVING` evidence. `refinanceAmount: null`은 잔액 전액 | (2026-09-16) 명세 17 대환·추가상환 본문이 `loanId`를 받도록 확장돼 FE가 그대로 실행할 수 있다. evidence에 `loanId`가 없으면 `SNAPSHOT_FIELD_MISSING`. 이전 저장분의 가정 `ACCOUNT_NO_REQUIRED_BY_CLIENT`는 더 만들어지지 않는다. 절감액을 추정하지 못한 추천은 `available=false`, `REFINANCE_TARGET_MISSING` |
| `*` / REVIEW_SPENDING_PATTERN (640) | SPENDING · `/api/v1/simulations/spending` | `{category: null, monthlyReduction: null, durationMonths: null}` | 근거 `CLUSTER_RANGE_DEVIATION` evidence의 `feature`가 `spendingToIncomeRatio`·`discretionaryShare`일 때만. 절감액을 추정하지 않는 추천이라 **금액은 FE가 사용자 입력으로 채워야 한다**(그대로 POST하면 400) | `REDUCTION_AMOUNT_REQUIRED_FROM_USER`, `REDUCTION_APPLIES_TO_TOTAL_SPENDING`. 저축률·고정지출·변동성은 `available=false`, `NO_MATCHING_SIMULATION_TYPE`. 근거에 `feature` 없으면 `SNAPSHOT_FIELD_MISSING` |
| CASHFLOW / SECURE_SHORTAGE_BUFFER, 그 외 | `available=false` | 없음 | — | `unavailableReason=NO_MATCHING_SIMULATION_TYPE` |

- `available=false`면 `type`·`endpoint`·`request` 키가 없고 `unavailableReason`이 있다. 사유: `NO_MATCHING_SIMULATION_TYPE`(대응 유형 없음), `EFFECT_AMOUNT_MISSING`(소비 추천의 월 절감액 없음·0), `SNAPSHOT_FIELD_MISSING`(저축 추천 근거에 R 없음, 카테고리 소비 추천 근거에 코드 없음), `REFINANCE_TARGET_MISSING`(대환 추천에 대상 상품 없음). `assumptions`는 항상 배열(없으면 `[]`).
- 근거 조회는 SAVING·REDUCE_CATEGORY_SPENDING·REFINANCE_LOAN·REVIEW_SPENDING_PATTERN 추천이 있을 때만 `findAllOwnedIn`으로 한 번 한다(627에서 SAVING 한정에서 확장, 640에서 군집 추천 추가).
- `accountNo`는 항상 null이다. 원문 계좌번호를 응답에 싣지 않고 FE도 마스킹 값만 가진다. 대상 목표는 619(2026-09-16)부터 `goalId`로 넘긴다 — 명세 17 `POST /simulations/saving` 본문 확장(simulation.md §4). SAVING 추천은 생성기가 진단 evidence `targetId`를 `Recommendation.goalId`에 채워야만 만들어지므로 과거 추천에도 `goalId`가 있다. FE는 `request`를 그대로 POST하면 되고, 그 사이 목표가 PAUSED·삭제됐으면 실행이 400/404다(프리셋은 재계산하지 않는다).
- `durationMonths: null`은 시뮬레이션 기본값(12개월)을 뜻한다. 추천 상태(REJECTED 등)와 무관하게 제공하며 실행 여부는 FE가 정한다.
- 일관성: 진단 기준일 = 실행일이면 `effect.improvedForecastDate` = 프리셋 실행의 대상 `goalId`에 해당하는 `goalImpacts[].afterForecastDate`다. 목표가 여러 개여도 대상 목표만 변경한다(simulation.md §11.1).

### 5.2 발견 요약 `discovery` (2026-09-16)

FE 발견 화면의 통합 수치(문제 수·전체 개선 시 연 절감액·목표 단축일)를 `GET /recommendations/summary`에 붙였다.
위의 사용자 전체 합계(`expectedMonthlyCostSaving` 등)는 진단을 거듭할수록 같은 문제의 추천이 쌓여 부풀므로, 이 객체는
**최신 완료 진단 한 건**의 열린 추천(PENDING·ACCEPTED)만 본다. 완료 진단이 없으면 `discovery: null`.

```json
"discovery": {
  "diagnosisId": 12, "issueCount": 3, "openRecommendationCount": 3,
  "expectedAnnualCostSaving": 498000, "expectedGoalAdvanceDays": null,
  "notes": ["ANNUAL_SAVING_BEST_ALTERNATIVE_PER_ISSUE", "GOAL_ADVANCE_UNAVAILABLE"]
}
```

| 필드 | 계산 | 없을 때 |
| --- | --- | --- |
| `issueCount` | (675) 진단 문제 중 **GOAL 도메인을 뺀** 수 = `DiagnosisResponse.discoveryIssueCount`. 목표 위험은 발견 목록에 올리지 않으므로(607) 화면 목록 길이와 같다 | — |
| `openRecommendationCount` | (675) 최신 진단의 PENDING·ACCEPTED 추천 중 GOAL 문제에서 나온 추천(`sourceIssueId`가 GOAL 문제)을 뺀 수 | — |
| `expectedAnnualCostSaving` | 같은 `sourceIssueId`의 대안 추천은 `annualCostSaving`이 큰 하나만 세고 문제별 값을 합산. `sourceIssueId`가 없는 추천(628 이전·여러 문제 합산)은 각각 센다. GOAL 문제의 추천은 제외(675) | 금액 있는 추천이 없으면 null, `ANNUAL_SAVING_UNAVAILABLE` |
| `expectedGoalAdvanceDays` | 열린 추천 `advancedDays`의 **최댓값**. 지금 값을 만드는 생성기는 저축 늘리기(GOAL 위험)뿐이라 GOAL 제외 뒤에는 대체로 null이다 — 소비 증가 추천이 값을 갖게 하는 것은 후속 696 | 값 있는 추천이 없으면 null, `GOAL_ADVANCE_UNAVAILABLE` |

GOAL 문제·추천을 뺐으면 `notes`에 `GOAL_ISSUES_EXCLUDED`가 들어간다(675). 빼는 이유와 GOAL 문제 자체를 계속 만드는 이유는 [discovery-report-metrics-675.md](discovery-report-metrics-675.md) §1-1.

- 목표 단축일을 더하지 않는 이유: 추천끼리 같은 목표에 효과가 겹치고, 정확한 합산은 프리셋들을 복합 시뮬레이션으로
  돌려야 하는데 그 실행은 저장 부수효과가 있어 조회 API에서 하지 않는다. 최댓값이라는 사실을 `GOAL_ADVANCE_IS_MAX_NOT_COMBINED`로
  항상 남긴다. 합산값이 필요하면 FE가 프리셋으로 `POST /simulations/combined`를 실행한다.
- 열린 추천이 없으면 `NO_OPEN_RECOMMENDATION`.

## 6. 상태 변경·이력 (549)

`POST /recommendations/{id}/{accept|reject|complete|defer}` Body `{ memo?, reason? }`(각 ≤500, 공백만이면 null, 본문 생략 가능) → 200 상세.

| 현재 \ 요청 | accept | reject | complete | defer |
| --- | --- | --- | --- | --- |
| PENDING | ACCEPTED | REJECTED | ✗ | DEFERRED |
| DEFERRED | ACCEPTED | REJECTED | ✗ | ✗ |
| ACCEPTED | ✗ | REJECTED | COMPLETED | DEFERRED |
| REJECTED | ✗ | ✗ | ✗ | ✗ |
| COMPLETED | ✗ | ✗ | ✗ | ✗ |

✗ 및 같은 상태 재요청은 409 `RECOMMENDATION_STATUS_TRANSITION_NOT_ALLOWED`(이력 중복 방지). 한 트랜잭션: 사용자 행 잠금 → 소유 조회 → 전이 검사 → `Recommendation.changeStatus`(`updated_at`) → `recommendation_history` INSERT. 효과·근거·priority는 불변.

`GET /recommendations/history?startDate&endDate&page&size` → `PageResponse<{historyId, recommendationId, title, domain, status, reason, memo, changedAt}>`.
기간은 `QueryPeriodPolicy`(endDate 기본 오늘, 기본 3개월, 최대 12개월, `startDate ≤ endDate`, 위반 시 400), `[start 00:00, end + 1일 00:00)`. 정렬 `changedAt DESC, id DESC`. 생성 시 PENDING 이력도 포함.

## 7. 오류 코드

| 코드 | HTTP |
| --- | --- |
| `DIAGNOSIS_NOT_COMPLETED` | 409 |
| `RECOMMENDATION_GOAL_MISMATCH` | 400 |
| `RECOMMENDATION_STATUS_TRANSITION_NOT_ALLOWED` | 409 |

547의 Reader·Store가 쓰던 `INVALID_REQUEST`는 위 두 코드로 바꿨다.

## 8. 검증

H2 (`.\gradlew.bat test --tests 'com.ssafy.dodam.domain.recommendation.*'`, 2026-09-13 통과, 51건 = 547 38건 + 신규 13건):

- `RecommendationGeneratorTest` 7: 저축 생성기 달성일 계산·속도 0·필드 누락·충분한 속도·데이터 부족, 기준일 fallback, 소비 생성기 초과분·연간 ×12, 현금흐름 생성기 필드·날짜 오류, 우선순위 점수(58/40/16)·동점 tie-break, 전이표 25칸.
- `RecommendationApiTest` 6: **실제 진단 실행**으로 만든 스냅샷에서 생성 → 효과·근거·goal-impact, 재요청 멱등(201→200, reused), goal 불일치 400·타인 404·검증 400·401·문제 0건, 목록 필터·페이지 경계·소유권, summary, priorities 최신 진단 한정·goalId·완료 진단 없음, 전이표·같은 상태 재요청 409·memo 501자 400·이력 4건·효과 불변, 이력 기간 정책·반개구간·정렬·소유권, 줄이기 목표 진단 형식.

587 (2026-09-15, 같은 명령 58건 통과):

- `RecommendationSimulationPresetAssemblerTest` 6: 소비 프리셋(카테고리 없음·근거 미조회), 절감액 없음 `EFFECT_AMOUNT_MISSING`, 저축 프리셋이 근거의 R을 읽음(R − A 아님·accountNo null), R 없음 `SNAPSHOT_FIELD_MISSING`, CASHFLOW·미지원 유형 `NO_MATCHING_SIMULATION_TYPE`, 목록 조립 시 SAVING 추천만 골라 근거 1회 조회.
- `RecommendationApiTest` +1: 실제 진단·생성으로 만든 저축 추천의 프리셋이 생성·상세·목록·priorities·상태변경 응답에 같은 값(R = 1,000,000)으로 실리고, 타인 404, 프리셋 본문 그대로 `POST /simulations/saving` 201 → `goalImpacts[0].afterForecastDate`가 추천 `improvedForecastDate`(2026-10-13)와 같음, 응답에 `requestHash`·`accountId` 원문 없음, 같은 날 재실행 200 재사용.

619 (2026-09-16, 같은 명령 59건 통과. 건수 변화 없이 기존 사례를 `goalId` 계약으로 바꿨다):

- `RecommendationSimulationPresetAssemblerTest`: 저축 프리셋 `request.goalId` = 추천 goalId(단건·목록 일괄), `assumptions=[]`.
- `RecommendationApiTest`: 생성·상세·목록·priorities·상태변경 응답의 프리셋에 `goalId`가 실리고, 목표 2개에서 프리셋 그대로 실행한 결과가 대상 목표만 바꿔 `afterForecastDate` = `effect.improvedForecastDate`, 비대상 목표 불변·`SPLIT_BY_REMAINING` 없음.

627 (2026-09-16, 같은 명령 통과 — 생성기 10건·프리셋 8건·API 9건):

- `RecommendationGeneratorTest` +3: 카테고리 생성기(이전 평균 복원액·×12·근거 `categoryCode`·증가 0 → 후보 없음·benchmark 없음 → INSUFFICIENT_DATA), 대환 생성기(추정 있음: 절감 18,000·회차당 6,000·잔여 3회라 연간 18,000, 근거 2종·`feesIncluded=false`; 추정 없음: 효과 NONE·"대환 조건 비교하기"·`REFINANCE_ESTIMATE_UNAVAILABLE`, 기준금리 없음 → INSUFFICIENT_DATA).
- `RecommendationSimulationPresetAssemblerTest` +2: 카테고리 프리셋 `{category: FOOD, monthlyReduction}`·코드 없음 `SNAPSHOT_FIELD_MISSING`·금액 없음 `EFFECT_AMOUNT_MISSING`, 대환 프리셋 `{accountNo: null, targetProductId, refinanceAmount: null}` + `ACCOUNT_NO_REQUIRED_BY_CLIENT`, 대상 상품 없음 `REFINANCE_TARGET_MISSING`.
- `RecommendationApiTest` +1: 실제 진단(식비 +40,000, 대출 12% → 6% 대환 18,000 절감, 연체 대출)으로 추천 3건 생성 — 우선순위 대환(31) > 연체 비교(30) > 식비(26), 효과·프리셋·근거 evidence, 응답에 계좌번호 원문 없음, 상세·목록·재요청 `reused=3`, 카테고리 프리셋 그대로 실행 시 식비 140,000 → 100,000.

628 (2026-09-16, `RecommendationApiTest` 10건·`RecommendationStoreTest` 19건 통과):

- `RecommendationApiTest` +1: 실제 진단의 `issueId`가 생성·목록·priorities·상세·defer 응답의 `sourceIssueId`와 같고 재요청에도 유지. 627 사례에 같은 `LOAN/HIGH_INTEREST_RATE` 문제 2건이 각자의 `issueId`로 구분되는 검증 추가.
- 저장·MySQL 검증은 [recommendation.md](recommendation.md) §10.

MySQL: 628 `V36`(`source_issue_id`)은 MySQL 8.0에서 실측했다(recommendation.md §10). 547 `RecommendationMySqlIntegrationTest` 3건은 `RECOMMENDATION_MYSQL_TEST_URL` 설정 시만 실행된다.

## 9. 현재 제한사항과 인계

- [ ] 카드·계좌·예적금 추천 없음. 진단 규칙(저사용 카드, 휴면 계좌 등)이 먼저다. 생성기를 추가하면 Collector가 자동으로 모은다. 대출은 627에서 대환 추천 1종만 추가했다(금리인하요구권·일부 상환 권고는 없음).
- [x] (628, 2026-09-16) 추천 응답 `sourceIssueId` 추가. 문제별 추천 조회 API(`GET /diagnoses/{id}/issues/{issueId}/recommendations`)는 FE가 응답 필드로 충분하다고 해 두지 않았다.
- [x] (640, 2026-09-18) 군집 α = 1을 `RecommendationPriorityPolicy.clusterPoints`로 연결했다. 641 규칙이 `clusterContext.deviationScore`를 채운다. 값은 초기값(시연 seed 실측 후 조정).
- [ ] ACCEPT 시 `improvement_action` 생성은 Improvement 이슈. 여기서는 훅을 두지 않았다. 시뮬레이션 실행 결과를 추천에 되돌려 연결하는 것(추천 id 저장)도 같은 이슈에서 계약을 정한다 — 587은 추천 → 시뮬레이션 방향만 연결했다.
- [ ] `annualCostSaving = monthly × 12`, 우선순위 가중치, 같은 상태 재요청 409는 서비스 결정이다. 팀 합의가 다르면 생성기 버전(`548.1`)을 올린다.
  (631) 카테고리 소비 생성기는 지난달 단월 증가에 ×12를 적용하므로 "한 달 이벤트를 연간으로 확대"하는 가정이 더 강해졌다. 발견 화면 정렬(604)·요약 합산이 값에 의존해 유지했고 evidence `annualizationBasis`로 표시한다. FE·기획이 "지속 시 연간" 표기 또는 연 절감액 미표시 중 하나를 정하면 `627.4`로 올린다.
- [ ] 생성기의 예상 달성일 식은 Goal·Simulation(550)과 각각 복제돼 있다. 세 곳이 같은 식을 쓰는 것은 테스트로 고정했지만 한 곳으로 모으는 정리는 546 병합 후.
- 진단·목표 삭제 이후 기존 추천의 표시 정책은 정하지 않았다(저장 당시 근거 보존, 조회 가능).
- 명세 16에 응답 필드가 없으므로 이 문서가 응답 계약이다. 프론트 연동 시 공유 필요.
