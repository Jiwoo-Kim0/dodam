# 발견(비효율 진단) 화면 백엔드 요청서

기준 화면: `frontend/src/pages/discovery/` (발견 홈 · 전체 보기 · 발견 상세).
2026-09-17 기준 FE 작업분에서 **서버 값이 없어 화면에서 우회 처리한 것**과 **BE 없이는 못 고치는 것**을 모았다.
FE는 이미 우회 구현을 넣어 배포 가능한 상태이므로, 아래는 전부 "지금 깨져 있다"가 아니라 "제대로 하려면 필요하다"에 해당한다.

우선순위는 시연 영향도 순이다.

> **BE 회신(S15P21D105-675, 2026-09-22)** — 항목별 회신을 각 절 끝에 `BE 회신` 블록으로 붙였다. 계약 상세는
> [backend/docs/discovery-report-metrics-675.md](../backend/docs/discovery-report-metrics-675.md) §1. 서버 변경은 전부 추가 필드라 지금 FE 코드는 그대로 동작한다.

---

## 1. (P1) 목표 위험을 발견 대상에서 제외

### 현상
`DiagnosisEvaluator.evaluate()`가 ACTIVE 목표마다 `GOAL` / `{SAVING|SPENDING}_GOAL_RISK` 문제를 만든다.
제목·설명이 목표와 무관한 고정 문자열이라, 목표가 2개면 발견 목록에 **똑같은 줄이 2개** 쌓인다.

```
1  목표   목표 달성 계획 점검   연 7,882만원
2  소비   문화·여가 소비가 늘었어요   연 239만원
3  목표   목표 달성 계획 점검   산정 전      ← 1번과 제목·설명이 동일
```

### 요청
`DiagnosisEvaluator`가 GOAL 문제를 만들지 않도록 해 달라. 목표 위험 판정 자체는 목표 화면이 이미 보여주고 있어
발견(비효율)으로는 중복이다.

해당 블록은 `DiagnosisEvaluator.java:75-82`의 `if (!risk.factors().isEmpty()) { issues.add(...) }` 하나다.

**단, `inputs.add(input)`(74행)은 반드시 남겨야 한다.** 스냅샷의 `goals[]`는 546 `goal-impact`가
예상 달성일·`daysDifference`를 읽는 곳이고, FE가 아래 3번 배너에서 쓰고 있다.

### 같이 확인할 것
- `coverage`의 `GOAL` 항목 status를 어떻게 둘지. 현재 `EVALUATED`인데, 문제를 안 만들면 이 값이 맞지 않는다.
  `CoachFactAssembler.coverage()`가 `EVALUATED`/`UNAVAILABLE`만 매칭하므로 제3의 값(`EXCLUDED` 등)을 넣어도
  코치 프롬프트는 깨지지 않는다. 다만 "검사 못 한 규칙" 목록에 들어가면 안 된다 — 검사는 했고 노출만 안 하는 것이다.
- `SavingGoalRiskGenerator`(`INCREASE_MONTHLY_SAVING`)와 `SpendingGoalRiskGenerator`(`REDUCE_SPENDING`)가
  입력을 잃고 동면한다. 삭제할지 남길지는 BE 판단에 맡긴다. FE는 이 두 추천을 발견 화면 외에서 쓰지 않는다.
- `diagnosis.issueCount`가 GOAL을 빼고 세어지는지. **FE는 현재 서버 `issueCount`를 믿지 않고 목록 길이를 센다**
  (`DiscoveryHome.tsx`의 `진단에서 N건 발견했어요`). BE에서 제외되면 FE를 서버 값으로 되돌릴 수 있다.

### FE 현재 우회
`discoveryModel.ts`의 `isHiddenFromDiscovery()`가 `domain === 'GOAL'`을 목록에서 걸러낸다.
기존 진단 행에도 즉시 먹히라고 넣은 것이라, BE에서 막히면 이 함수는 제거 대상이다.

발견 개수·요약 수치도 서버 집계(`issueCount`, `discovery.expectedAnnualCostSaving`,
`expectedGoalAdvanceDays`)를 쓰지 않고 화면에 뜬 발견만으로 다시 센다. **BE 대응은 필요 없고**,
목표가 제외되면 FE에서 되돌릴지 그대로 둘지 판단하면 된다.

> **BE 회신(675)**: GOAL 문제 **생성은 유지**한다 — 546 goal-impact·changes, 코치 요약, 저축 늘리기 추천 → 저축 프리셋(619 goalId), 그리고 678 시연 seed가 U0003 목표를 `SAVING_GOAL_RISK`로 만들어 둔 것이 이 문제를 입력으로 쓴다.
> 대신 **발견 화면이 세는 숫자에서 GOAL을 뺐다**: 진단 응답에 `discoveryIssueCount`(= issueCount − GOAL 문제 수) 추가, `GET /recommendations/summary`의 `discovery.issueCount`는 같은 정의로 바뀌었고
> `openRecommendationCount`·`expectedAnnualCostSaving`·`expectedGoalAdvanceDays`는 GOAL 문제에서 나온 추천을 빼고 계산한다(`notes`에 `GOAL_ISSUES_EXCLUDED`). FE는 목록 길이·재집계 대신 이 두 서버 값을 쓸 수 있다.
> `isHiddenFromDiscovery`는 계속 필요하다(문제 목록 API는 GOAL을 돌려준다). coverage `GOAL`은 실제로 검사하므로 `EVALUATED` 그대로. 목표 위험 추천 2종도 유지(발견 목록에는 안 붙는다).

---

## 2. (P1) 카테고리 소비 증가 추천에 `advancedDays`가 비어 있다

### 현상
`CategorySpendingIncreaseGenerator.generate()`가 효과를 이렇게 만든다.

```java
Recommendation.Effect effect = new Recommendation.Effect(
    increase, increase * MONTHS_PER_YEAR, null, null, null, null, null, null);
//                                                          ↑ advancedDays
```

`currentForecastDate`·`improvedForecastDate`·`advancedDays`·`contributionAmount`가 모두 null이라
`GET /v1/recommendations/{id}/goal-impact`는 `NOT_CALCULATED`를 돌려준다.

### 요청
"이 카테고리 소비를 직전 평균 수준으로 되돌리면 목표가 며칠 앞당겨지는가"를 채워 달라.
기획 원안의 발견 상세 문구가 **"이 소비가 계속되면 '비상금 500만 원' 목표가 약 24일 늦어질 수 있어요"** 인데,
이 값 없이는 인과를 말할 수 없다.

`SpendingReductionCalculator`가 이미 소비 감소 시뮬레이션을 하고 있으니 같은 규칙을 재사용할 수 있을 것으로 보이나,
계산 주체·기준은 BE에서 정해 달라.

### FE 현재 우회
`goal-impact`(진단)의 `goals[].daysDifference`를 대신 쓰고, 문구를 **"지금 속도면 '…' 목표가 약 N일 늦어져요"** 로 바꿨다.
`daysDifference`는 목표 전체의 현재 지연이지 이 카테고리 때문에 생긴 지연이 아니므로, 인과 표현("이 소비가 계속되면")은
의도적으로 쓰지 않았다. 관련 코드는 `frontend/src/pages/discovery/goalDelay.ts`.

> **BE 회신(675 → 후속 696)**: 675에서는 계약만 정했다 — `REDUCE_CATEGORY_SPENDING` 추천이 진단 스냅샷 `goals[].forecast`(남은 금액·현재 저축속도)로 "늘어난 금액을 이 목표 저축에 돌리면"의
> `currentForecastDate`·`improvedForecastDate`·`advancedDays`(양수 = 앞당김)·`contributionAmount`(= 증가액)를 계산하고 대상 목표(요청 goalId, 없으면 **가장 많이 밀린 모으기 목표** = `goalDelay.ts`와 같은 규칙)를
> `Recommendation.goalId`에 싣는다. 근거 `GOAL_FORECAST_ADVANCE`에 `goalName`·계산 입력. 현재 저축속도 0이면 `currentForecastDate`·`advancedDays`는 null, `improvedForecastDate`만 있다.
> 원안 문구의 N("계속되면 N일 늦어짐")은 이 `advancedDays`와 같은 크기. 식은 510·시뮬레이션과 같은 `GoalForecastFormula`(일 단위), 현재 데이터로 재계산하지 않는다.
> **구현은 S15P21D105-696**(코드·테스트 준비 완료). 배포 뒤 `POST /diagnoses {forceRefresh:true}` → `POST /recommendations/generate`를 다시 해야 새 버전 추천이 생긴다.

---

## 3. (P2) 발견 상세의 도메인별 고유 수치가 화면에 안 붙는다

발견 상세는 지금 `isCategorySpending` 하나로만 갈라진다. 소비 진단은 전용 화면을 새로 만들었지만,
**목표·대출·현금흐름은 `issue.title`/`issue.description`만 바꿔 끼운 같은 화면**을 쓴다.

도메인마다 보여줄 만한 고유 수치가 스냅샷에는 있는데 화면 계약이 없어 안 쓰고 있다.

| 도메인 | 스냅샷에 있는 것 | 화면에 필요한 형태 |
| --- | --- | --- |
| `LOAN` / `HIGH_INTEREST_RATE` | 보유 금리, 비교 공시금리 중앙값, 편차(%p), 대환 절감액, `unavailableReason` | 비교 카드(내 금리 vs 시장) + 대환 시 절감액 |
| `CASHFLOW` / `BALANCE_SHORTAGE` | 부족액, 필요금액, 예정 출금일, 부족 비율 | 며칠에 얼마가 비는지 타임라인 |

FE가 `currentValue`/`benchmarkValue` JSON을 직접 파싱해 만들 수는 있으나, 소비 진단에서 그렇게 했다가
키 이름·기간 의미를 FE가 다시 해석하는 부담이 컸다. **어떤 키를 화면이 읽어도 되는지 확정**해 주면 좋겠다.

지금 당장 막히는 건 아니므로 시연 이후 논의해도 된다.

> **BE 회신(675)**: 도메인별로 읽어도 되는 키를 [discovery-report-metrics-675.md](../backend/docs/discovery-report-metrics-675.md) §1-3 표로 확정했다(진단 `ruleVersion` 안에서 고정, 추가만 허용).
> LOAN: `interestRate`(내 금리) vs `referenceRate`(공시 최저금리 중앙값), `rateDifference`(%p), `refinance.available`일 때 `interestSaving`·`monthlySaving`, `feesIncluded=false`.
> CASHFLOW: `riskDate`(예정 출금일), `expectedBalance`(출금 직전 예상잔액, 음수 = 부족액), `message`. **필요 금액·부족 비율은 저장 위험에 없다** — §3 데이터 요건으로 넘겼다.

---

## 4. (P3) 참고 — FE가 서버 계산을 대신하고 있는 곳

"재계산하지 않는다"는 544 계약과 어긋날 여지가 있어 남긴다. BE가 가져갈지 FE에 둘지 판단 부탁한다.

- **발견 상세 `8월에는 어디에 썼을까요?`**
  진단이 본 기간의 해당 카테고리 거래를 `GET /v1/transactions`로 받아 **FE가 가맹점별로 합산**한다.
  (`frontend/src/pages/discovery/categoryMonthlySpending.ts`)
  진단 스냅샷에는 카테고리 월 합계만 있고 가맹점 분해가 없어서다. 지출처별 분해를 진단이 함께 저장하면
  FE 집계를 걷어낼 수 있다.

> **BE 회신(675)**: 이번에는 FE 집계를 그대로 둔다. 가맹점명 정규화(같은 가맹점의 표기 편차)가 선행돼야 진단 저장값으로 굳힐 수 있다 — §3 데이터 요건(`topMerchants[]`)에 기록.

---

## 부록 — 진단 규칙 현황 (2026-09-17, `v6-last-month-vs-3m`)

확인차 적어 둔다. 인식이 다르면 알려 달라.

**실제로 문제를 만드는 규칙 4종**

| 도메인 / 타입 | 규칙 |
| --- | --- |
| `SPENDING` / `CATEGORY_SPENDING_INCREASE` | 지난 완료 월 vs 직전 3개월 월평균 |
| `LOAN` / `HIGH_INTEREST_RATE` | 비교군 공시 최저금리 중앙값 +1.0%p 초과 |
| `GOAL` / `{SAVING,SPENDING}_GOAL_RISK` | 목표 위험 factor 1개 이상 ← **1번에서 제외 요청** |
| `CASHFLOW` / `BALANCE_SHORTAGE` | 예정 출금일 잔액 부족 |

**`coverage`에 `RULE_POLICY_NOT_DEFINED`로만 남는 미구현 9종**

`DORMANT_ACCOUNT`, `FIXED_SPENDING_INCREASE`, `VARIABLE_SPENDING_INCREASE`, `LOAN_INTEREST_BURDEN`,
`LOAN_REPAYMENT_BURDEN`, `LOW_USAGE_CARD`, `CARD_PAYMENT_BURDEN`, `SAVING_CASHFLOW_BURDEN`,
`CLUSTER`(`CLUSTER_NOT_CONNECTED`)

그리고 `goal-impact`의 `FINANCING_COST_DELAY`도 `RULE_POLICY_NOT_DEFINED` 상태다. 2번 요청과 같은 줄기로 보인다.
