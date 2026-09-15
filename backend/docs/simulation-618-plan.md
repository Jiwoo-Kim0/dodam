# 618 추천 효과·복합 순서 교환 일관성 테스트 고정 계획

작성일: 2026-09-16. 브랜치 `S15P21D105-618-587`(Jira Create branch, `origin/develop` `cf5eb3c` 기준).
587 후속 4건(616~619) 중 세 번째. 결과는 [simulation.md](simulation.md) §11에 기록하고 [remaining-work.md](remaining-work.md) 587 절 "계산 일관성 검증(계획 §4) 중 미고정 항목"을 닫는다.

## 1. 범위와 착수 근거

- 대상: S15P21D105-618. 로컬 내보내기([587-remaining-work.csv](../../SPRINT/587-remaining-work.csv)) Issue ID 160 "[587 후속] 추천 효과·복합 순서 교환 일관성 테스트 고정"(2026-09-16, 3pt). Jira 원문이 다르면 이 절을 먼저 고친다.
- 이슈 설명: [simulation-587-plan.md](simulation-587-plan.md) §4 중 미고정 2건을 테스트로 고정한다.
  1. 소비 추천 `effect.monthlyCostSaving` = 프리셋 그대로 실행한 `POST /simulations/spending`의 `after.monthlyAvailableFunds − before.monthlyAvailableFunds`. 진단 `SPENDING_GOAL_RISK`가 나오도록 목표 예산 초과 거래 fixture가 필요하다(587은 프리셋 값만 단위 테스트).
  2. 복합 순서 교환 불변을 자식 9종 전체 조합(최대 10개, 추가상환·대환은 서로 다른 `loanId`)에서 확인. 현재는 2개 조합(소비 감소·할부)만 확인했다.
- 완료 기준: 두 대조가 테스트로 고정되고 simulation.md §11에 결과를 기록한다.
- 변경 파일: `RecommendationApiTest`(1건 추가), `SimulationIntegrationApiTest`(fixture에 대출 1개 추가 + 1건 추가), `simulation.md`, `remaining-work.md`. **운영 코드는 바꾸지 않는다.** 대조가 어긋나면 어느 쪽 식이 맞는지 판단하지 않고 기록·별도 이슈로 올린다.
- 모두 H2다. Docker·MySQL 불필요. 두 클래스 모두 기존 컨텍스트(URL·MockitoBean 구성)를 그대로 쓰므로 Spring 컨텍스트 수는 늘지 않는다.

## 2. 대조 1: 소비 추천 효과 vs 소비 시뮬레이션

### 2.1 식의 출처 (코드로 확인)

- 진단: `GoalCalculationService.spendingRisk` — 이번 달 소비 `spent`(월초~기준일, `EXPENSE`·`hidden = FALSE`), `projected = round(spent × daysInMonth / dayOfMonth)`. `spent > budget`이면 `OVER_BUDGET`, 아니면 `projected > budget`이면 `PACE_EXCEEDS_BUDGET`. 둘 중 하나면 `SPENDING_GOAL_RISK` 이슈에 `metrics {monthlyBudget, spent, projectedMonthEnd}`.
- 추천: `SpendingGoalRiskGenerator` — `over = max(0, max(spent, projected) − budget)`, `effect.monthlyCostSaving = over`, `annualCostSaving = over × 12`.
- 프리셋(587): `RecommendationSimulationPresetAssembler` — SPENDING → `POST /simulations/spending {category: null, monthlyReduction: monthlyCostSaving}`.
- 시뮬레이션: `SimulationBaselineReader` — 월 지출 = 기준월 **직전 3개월** 합 / 3, 월 가용자금 = `income − spending − saving`(소득 없으면 null). `SpendingReductionCalculator` — `after.monthlySpending = before − reduction`, 감소액이 평균 지출을 넘으면 400 `SIMULATION_REDUCTION_EXCEEDS_SPENDING`.

따라서 "동일 금액"은 `reduction ≤ 평균 지출`이고 소득이 있을 때만 성립한다. 진단은 **이번 달**, 시뮬레이션 기준선은 **직전 3개월**을 보므로 두 창이 다른 거래를 읽는다는 점을 fixture와 문서에 명시한다.

### 2.2 fixture (`RecommendationApiTest`, NOW = 2026-09-13)

| 자료 | 값 | 이유 |
| --- | --- | --- |
| 줄이기 목표 | `monthlyBudget` 1,000,000, 2개월 전 생성, ACTIVE | 진단 대상 |
| 이번 달 거래 | 09-05 EXPENSE 1,300,000 | `spent` 1,300,000 > 예산 → `OVER_BUDGET`. `projected = 1,300,000 × 30 / 13 = 3,000,000` → **`over = 2,000,000`** |
| 직전 3개월 거래 | 06·07·08월 각 2,400,000 | 시뮬레이션 평균 지출 2,400,000 ≥ 2,000,000(감소 상한 통과) |
| `financial_profile.monthly_income` | 3,000,000 | 가용자금 계산에 필요. 없으면 null이라 대조 불가 |

기대: 추천 `effect.monthlyCostSaving = 2,000,000`, 프리셋 `request.monthlyReduction = 2,000,000`, 실행 201, `before.monthlyAvailableFunds = 600,000`(3,000,000 − 2,400,000 − 0) → `after = 2,600,000`, **차이 2,000,000**. `after.monthlySpending = 400,000`. 거래 INSERT는 `SimulationApiTest.expense`와 같은 SQL을 쓴다(`JdbcTemplate`·`FinancialProfileRepository` 주입 추가 — autowire 추가는 컨텍스트 키에 영향 없음).

### 2.3 단언

- 진단 응답에 `SPENDING_GOAL_RISK` 이슈, 추천 생성 응답에서 **`type = REDUCE_SPENDING`인 항목을 찾아**(진단이 CASHFLOW 이슈도 낼 수 있어 `items[0]`에 묶지 않는다) `effect.monthlyCostSaving = 2,000,000`, `simulation.available = true`, `request.category = null`.
- 프리셋 `endpoint`·`request`를 그대로 POST → 201, `after.monthlyAvailableFunds − before.monthlyAvailableFunds == effect.monthlyCostSaving`, `before.monthlySpending − after.monthlySpending == monthlyCostSaving`.
- 저장 프리셋과 실행 프리셋 사이의 이 등식은 `monthlyReduction`이 그대로 `after`에 반영된다는 계산기 계약(§9와 같은 값)의 API 수준 고정이다. 기준일 차이(진단 asOf ≠ 실행일)가 있어도 금액은 같다 — 저축과 달리 날짜 식이 아니므로. 그 사실을 §11에 적는다.

## 3. 대조 2: 복합 순서 교환 불변 (자식 9종 전체)

### 3.1 설계

- 위치: `SimulationIntegrationApiTest`. 10종 실행 fixture(계좌·카드·예금·신용대출 상품·대출 1개)가 이미 있어 API 수준에서 9종 자식을 모두 넣을 수 있다. `CombinedSimulationCalculatorTest`(DB 없는 계산기)는 대출·상품·카드 스냅샷을 손으로 만들어야 해서 9종 조합을 넣으면 fixture가 계산기 테스트의 두 배가 된다 — 계산기 수준은 기존 2개 조합으로 두고 API 수준으로 전체 조합을 고정한다.
- fixture 변경: 대출 1개 추가(`LOAN_NO_2`, 같은 신용대출 상품, 잔액 1,200,000, 다음 상환 10-15, 만기 11-15). 추가상환은 대출 A, 대환은 대출 B — `CombinedScenarioValidator`가 같은 `loanId`의 추가상환·대환을 거부한다.
- 자식 10개: SPENDING(100,000) · SAVING(500,000) · SAVING_PRODUCT(예금 12개월 1,000,000) · LOAN_NEW(1,200,000/12) · LOAN_PREPAYMENT(A, 200,000) · LOAN_REFINANCE(B → 신용대출) · INSTALLMENT(1,200,000/2/12) · REVOLVING(카드, 1,000,000/50/12) · CASH_ADVANCE(1,000,000/12) · **CASH_ADVANCE(500,000/12)** — 10번째는 충돌 규칙에 걸리지 않는 같은 유형·다른 금액이다(`DUPLICATE_SCENARIO`는 정규화 입력이 같을 때만).
- 순서 3가지: 명세 순서, 역순, 3칸 회전. 지문이 순서를 포함하므로 세 번 모두 **201·서로 다른 id**(586 계약 "순서 교환 신규 201"). 같은 순서 재실행은 200.

### 3.2 단언 (세 실행의 `simulation` 응답 비교)

- 같아야 하는 것: `after`에서 `details.children`을 뺀 전부(월 가용자금·지출·저축, `assumptions`, `details`의 집계·평가기간·비용), `comparison.items`, `cashflow.months`, `goalImpacts`, `summary`, `costs/debts/assets` 섹션(`GET /{id}/costs` 등 3경로).
- 순서만 달라야 하는 것: `details.children`(자식 순서 보존 계약) — `type + parameters`로 키를 만들어 집합으로 비교, `scenarios[].sequenceNo`.
- 달라도 되는 것: `simulationId`, `createdAt`, `request.scenarios` 순서.
- `assumptions`는 복합이 자식 가정을 합쳐 정렬해 내므로(586 §10.4) 그대로 `isEqualTo`. 정렬돼 있지 않다면 그것이 발견 사항이다.

### 3.3 비교 구현

응답 JSON을 `JsonNode`로 받아 `details.children`을 `remove`한 뒤 `after`끼리 `isEqualTo`. children은 `List<Map>`으로 바꿔 `containsExactlyInAnyOrderElementsOf`. 계산 자체를 다시 하지 않는다.

## 4. 진행 순서

1. 대조 1 → `RecommendationApiTest` 단독 실행.
2. 대조 2 → `SimulationIntegrationApiTest` 단독 실행. 기존 3건이 fixture 변경(대출 2개)에 영향받지 않는지 함께 본다(대출 목록·debts 섹션 건수 단언이 있으면 조정).
3. `.\gradlew.bat test --tests 'com.ssafy.dodam.domain.simulation.*' --tests 'com.ssafy.dodam.domain.recommendation.*'`, 이어서 `clean build` 1회.
4. simulation.md §11.1(대조 1)·§11.2(대조 2) 갱신, "고정하지 않았다" 문구 제거. remaining-work.md 587 절 항목 완료.
5. `git diff --check`, CRLF 확인, 커밋.

## 5. 위험과 미결

- 대조 1의 등식은 진단 창(이번 달)과 시뮬레이션 창(직전 3개월)이 달라도 성립하지만, **감소액이 평균 지출을 넘으면 400**이다. 실제 사용자에게는 프리셋 실행이 400이 될 수 있다는 뜻이며 이는 587 프리셋 설계의 알려진 제한이다. 테스트에서 재현되면 §11에 기록하고 FE 146(프리셋을 사용자가 고쳐서 실행)에 전달한다.
- 대조 2에서 세 실행의 `after`가 다르면 복합 계산기의 순서 의존 결함이다. 운영 코드를 고치지 않고 차이 항목을 기록·별도 이슈.
- `SimulationIntegrationApiTest` fixture에 대출을 더하면 기존 `everyTypeRuns…`의 대출 관련 단언(있다면)이 바뀔 수 있다. 확인 후 조정하되, 기존 검증 의미는 유지한다.
- Jira 618 원문 미확인(§1).

## 6. 실행 결과 (2026-09-16)

- 대조 1: 계획 §2.2 fixture 그대로 통과. `over` 2,000,000, 가용자금 600,000 → 2,600,000. `RecommendationApiTest` 8건 통과.
- 대조 2: 세 순서 모두 201·다른 id, `before`·`after`·`comparison`·`cashflow`·`goalImpacts`·`summary` 동일. `SimulationIntegrationApiTest` 4건 통과(fixture 대출 2개로 바꿔도 기존 3건 영향 없음).
- 설계에서 달라진 점: (1) `after.details.financeCost.children[]`도 자식 순서를 따르는 목록이라 `details.children`과 함께 제외하고 집합으로 비교했다. (2) `title`은 자식 제목을 입력 순서로 이어 붙이므로 동일성 대신 건수 접두만 확인했다. (3) `goalImpacts[].reasons`·`goalImpactDetails[].reasons`의 원소 순서가 자식 순서를 따라 달랐다 — `CombinedCalculator.childReasons` 주석대로("중복 없이 자식 순서대로") 설계된 동작이고 집합은 같아 결함이 아니다. 테스트는 정렬해 비교하고 remaining-work.md에 기록했다.
- 운영 코드 변경 없음. simulation·recommendation 129건(H2) 통과, MySQL 7건 skip. `gradlew clean build` 전체 1,056건 통과(MySQL 조건부 22건 skip. 616·617 병합 후 `develop` `0d7e9f4` 위로 rebase해 재실행).
