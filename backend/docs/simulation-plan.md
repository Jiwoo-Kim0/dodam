# Simulation 공통 모델·소비/저축 착수 계획 — S15P21D105-550 · 551

작성일: 2026-09-13. 구현 전 계획이다. 550·551의 기능 코드는 아직 추가하지 않았다.

## 1. 착수 판단과 근거

**550과 551은 한 흐름으로 진행하며, Recommendation(547~549)과 완전히 독립이다.**
ERD상 `simulation`은 `user`만, `simulation_goal_impact`는 `simulation`·`goal`(V5)만 참조한다. recommendation 테이블과 FK가 없고
코드 의존도 없으므로 `origin/develop` 기준으로 즉시 시작할 수 있고 548·549와 병렬 worktree 진행이 가능하다.

550(모델·계산 인터페이스)은 단독으로는 검증할 HTTP 경로가 없다. 551이 그 인터페이스의 첫 구현체 2개(spending·saving)를 넣으면서
공통 조회 API로 결과를 확인하게 되므로 550 → 551을 한 worktree에서 이어서 한다.

| 이슈 | 로컬 이슈 범위 (`SPRINT/Jira (4).csv`, 2026-09-07) | 이번 계획의 산출물 |
| --- | --- | --- |
| 550 | Simulation·Scenario·GoalImpact와 계산 인터페이스 | V19 마이그레이션, 엔티티 3종, Repository, 계산 인터페이스·실행기·저장, 공통 조회·삭제 API |
| 551 | spending·saving 입력 검증과 대표 계산 | `POST /simulations/spending`, `POST /simulations/saving`, 기준선 Reader, 목표 달성일 재계산 |

최신 Jira 설명·완료 조건과 대조는 구현 시작 전에 다시 한다(이전 연결 시 403).

### 선행 조건

| 선행 | 상태 (2026-09-13) | 영향 |
| --- | --- | --- |
| V5 goal, `uk_goal_user_goal_id` | `origin/develop` | `simulation_goal_impact` 복합 FK 대상 |
| `GoalCalculationService.forecast()` (readOnly `getForecast(userId, goalId, today)`) | `origin/develop` | 목표 달성일 before 계산·after 계산식의 기준 |
| `SpendingQueryService.getCategoryTrend`·`getMonthlySummary` | `origin/develop` | 최근 3개월 평균 소비 기준선 |
| `FinancialProfile.monthlyIncome` | `origin/develop` | 저축액 상한 검증(선택) |
| 546·547 | 미병합 | **의존 없음** |

기준 자료: [API 17](../../docs/API명세서/17%20Simulation%203cdbb6ea356480a38af4c6f56a5280f2.csv),
[ERD SQL](../../docs/ERD/DODAM_ERDCLOUD_IMPORT.sql) 1089~1150행, [기획안](../../docs/기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md) 4-5·7-5·9-6,
[마이그레이션 규칙](../src/main/resources/db/migration/README.md), [Cashflow 요청 지문](../src/main/java/com/ssafy/dodam/domain/cashflow/service/CashflowRequestHash.java).

## 2. 범위

### 포함

1. `V19__create_simulation.sql`: ERD의 `simulation`·`simulation_scenario`·`simulation_goal_impact` 그대로. V18은 547이 선점했고 V19는 BE-B 구간(V18~V29) 다음 번호다. 시작 전 원격 전 브랜치에서 V19 미사용을 재확인한다(2026-09-13 확인: V18, V30만 존재).
2. 엔티티 `Simulation`, `SimulationScenario`, `SimulationGoalImpact`, enum `SimulationType`(10종)·`SimulationStatus`(4종). CHECK와 같은 검증을 생성자에서.
3. 소유권 조건이 든 Repository(`deleted_at IS NULL` 포함), `SimulationUserLock`.
4. 계산 계약: `SimulationCalculator<Q>` 인터페이스, `SimulationBaseline`(기준선), `SimulationResult`, `SimulationRunner`(검증→기준선→계산→저장), `SimulationStore`.
5. 공통 API: `GET /simulations`, `GET /{id}`, `/comparison`, `/cashflow`, `/goal-impact`, `/assets`, `/debts`, `/costs`, `DELETE /{id}`.
6. 551 API: `POST /simulations/spending`, `POST /simulations/saving`와 계산기 2종.
7. 테스트·문서 `simulation.md`.

### 제외 (후속)

- 나머지 8개 POST(saving-product, loan/*, installment, revolving, cash-advance, combined). `SimulationType`·`scenario_type` 값은 미리 두되 계산기 미등록 → 요청 시 404가 아닌 **엔드포인트 자체를 만들지 않는다**(구현되지 않은 경로를 열지 않음).
- 이자 수익·세후 계산(예적금 상품 연계), 현금흐름 예측(cashflow forecast) 재실행. 551의 `cashflow_impact`는 월 단위 가용자금 변화표만 담는다.
- Recommendation → Simulation 연결, `improvement_action`.

## 3. 브랜치와 worktree

- 550: Jira `Create branch` → `S15P21D105-550-api-17-simulation-model`(가칭). 기준 `origin/develop` 최신.
- 551: 550 커밋 위에서 `S15P21D105-551-api-17-spending-saving` 연쇄 분기. 550 MR 병합 후 551 MR.
- worktree `.worktrees/550`. 548 worktree와 동시에 열어도 파일 겹침 없음(도메인 패키지·마이그레이션 번호 분리). 단 `ErrorCode.java`는 양쪽 다 항목을 추가하므로 병합 시 사소한 충돌 예상 — 각자 enum 끝에 도메인 주석 블록으로 추가한다.

## 4. 550 구현 순서

### 4.1 스키마·엔티티

ERD 1089~1150행 DDL을 그대로 옮긴다(README 규칙, diff로 확인). 핵심 제약:

| 테이블 | 보존할 계약 |
| --- | --- |
| `simulation` | `(user_id, simulation_id)` UNIQUE(goal_impact 복합 FK 대상), CHECK type 10종·status 4종, `before_snapshot`·`after_snapshot` NOT NULL JSON, `deleted_at` 소프트 삭제 |
| `simulation_scenario` | `(simulation_id, sequence_no)` UNIQUE, `sequence_no > 0`, `parameters` JSON NOT NULL |
| `simulation_goal_impact` | `(simulation_id, goal_id)` UNIQUE, `(user_id, goal_id)` → goal 복합 FK |

엔티티 `@Table(uniqueConstraints)`로 같은 UNIQUE 선언(H2 테스트는 Flyway 없이 엔티티로 스키마 생성 — 547과 같은 이유). JSON 컬럼은 diagnosis의 `input_snapshot` 매핑 방식을 그대로 따른다.

### 4.2 계산 계약 (`service/`)

```text
domain/simulation/
  entity/      Simulation, SimulationScenario, SimulationGoalImpact, SimulationType, SimulationStatus
  repository/  SimulationRepository, SimulationScenarioRepository, SimulationGoalImpactRepository, SimulationUserLock
  dto/         SimulationBaseline(+GoalBaseline), SimulationResult(+GoalImpact, MonthlyDelta), 요청·응답 DTO
  service/     SimulationCalculator<Q>, SimulationBaselineReader, SimulationRunner, SimulationStore,
               SimulationQueryService, SimulationCalculationVersion, SimulationRequestHash
  service/calculator/  (551) SpendingReductionCalculator, SavingChangeCalculator
```

- `SimulationCalculator<Q>`: `type()`, `calculate(SimulationBaseline, Q, LocalDate base) → SimulationResult`. **순수 함수**(저장·외부 호출·시각 조회 없음). 단위 테스트가 DB 없이 돈다.
- `SimulationBaseline`: 실행 시점 사용자 현황. `monthlyIncome?`, `averageMonthlySpending`(최근 3개월, 카테고리별 포함), `goals[]`(활성 SAVING 목표의 target·current·remaining·averageMonthlySaving·expectedAchievementDate, SPENDING 목표의 budget·projected). `SimulationBaselineReader`가 기존 Goal·Spending 서비스의 readOnly 경로로 채운다. 누락은 `coverage[]`에 남긴다(진단과 같은 방식).
- `SimulationResult`: `beforeSnapshot`, `afterSnapshot`, `comparison`(항목별 before/after/delta), `cashflowImpact`(월별 가용자금 delta, `durationMonths`개), `goalImpacts[]`(goalId, before, after, differenceDays), `assumptions[]`.
- 목표 달성일 재계산은 `GoalCalculationService.forecast()`와 **같은 식**을 쓴다: `remaining ≤ 0 → base`, `pace ≤ 0 → null`, 그 외 `base.plusMonths(ceil(remaining / pace))`.
  **(2026-09-13 구현 시 변경)** 처음 계획은 `GoalCalculationService`에 정적 함수를 뽑아 공유하는 것이었으나, 546이 같은 메서드(`getForecast`)를 수정 중이라 550에서 Goal 코드를 건드리면 병합 충돌이 나고,
  develop의 `getForecast`는 조회 중 목표 잔액을 갱신하고 이력을 남기는 쓰기 경로라 시뮬레이션이 호출할 수도 없다. 그래서 simulation 도메인에 `GoalAchievementProjection`(순수 함수)과
  `SimulationBaselineReader`(읽기 전용 기준선)를 두고, Goal API 응답과의 동일성을 API 테스트로 고정했다. Goal 도메인은 수정하지 않았다. 546 병합 후 통합 여부를 다시 본다.
- `SimulationRunner.run(userId, type, request, now)`: 입력 검증 → `SimulationUserLock`(PESSIMISTIC_WRITE) → 요청 지문 조회(4.3) → 있으면 기존 반환 → 기준선 읽기 → 계산 → `SimulationStore.save` 한 트랜잭션(simulation + scenario 1건 + goal_impact N건, status `COMPLETED`, `completed_at = now`).
  동기 실행이므로 `PENDING`/`RUNNING`은 쓰지 않고, 계산 실패 시 행을 남기지 않는다(`FAILED` 미사용 — 문서에 명시).

### 4.3 중복 요청 방지

ERD에 `generation_key` 같은 멱등 컬럼이 없다. cashflow의 `CashflowRequestHash`(길이 접두 인코딩) 방식을 따라
`SimulationRequestHash.of(userId, type, 정규화된 요청 파라미터, baseDate, SimulationCalculationVersion.CURRENT)`를 만들고 `request_snapshot.requestHash`에 저장한다.
같은 날 같은 사용자·같은 지문의 미삭제 `COMPLETED` 건이 있으면 새로 만들지 않고 그 건을 돌려준다(`created=false`). 기준일이 바뀌면 기준선이 달라지므로 새 건이다.
JSON 안 값이라 DB UNIQUE가 없으므로 사용자 행 잠금이 실질 방어선이다. `accountNo` 같은 민감번호는 지문 입력에서 제외한다(해시라도 응답·로그에 남을 수 있음).

### 4.4 공통 조회·삭제 API

공통: `AuthenticatedUserIdResolver`, 타인·삭제된 건은 `RESOURCE_NOT_FOUND`, `@Transactional(readOnly = true)`, 저장값만 반환(재계산 없음).

| Endpoint | 파라미터·검증 | 응답 | 정렬·빈 결과 |
| --- | --- | --- | --- |
| `GET /simulations` | `type?`(enum 외 400), `page≥0`, `size 1~100` 기본 20 | `PageResponse<SimulationSummaryResponse>{id, type, title, summary, status, createdAt, completedAt}` | `createdAt DESC, id DESC`. 삭제 건 제외 |
| `GET /{id}` | Long | `SimulationResponse`: 헤더 + `request`(민감번호 마스킹) + `scenarios[]` + `comparison` + `goalImpacts[]` + `assumptions` | — |
| `GET /{id}/comparison` | | `comparison` JSON 그대로 (`items[]{key, label, before, after, delta}`) | 빈 items |
| `GET /{id}/cashflow` | | `cashflow_impact`: `months[]{yearMonth, before, after, delta}` — 예정 이벤트를 다시 더하지 않는다(명세 "중복 반영 금지"; 저장된 표만 반환) | 빈 months |
| `GET /{id}/goal-impact` | | `goals[]{goalId, beforeForecastDate, afterForecastDate, differenceDays}` | 목표 없으면 빈 배열 |
| `GET /{id}/assets`·`/debts`·`/costs` | `page`, `size` | `comparison.assets[]`·`debts[]`·`costs[]`를 페이지로 자른 `PageResponse` (메모리 페이징, 항목 수 ≤ 수십 건) | 551은 assets에 저축액 항목만, debts·costs 빈 목록 |
| `DELETE /{id}` | | 204, `deleted_at = now`. 이미 삭제됐으면 404 | — |

리터럴 경로가 없어 `/{simulationId}` 충돌 없음. `type` 필터는 enum 문자열 대소문자 그대로(다른 도메인과 동일).

## 5. 551 구현 순서

### 5.1 `POST /simulations/spending` — 소비 감소

Body `{ category?, monthlyReduction, durationMonths? }`.

| 항목 | 검증·기본값 | 근거 |
| --- | --- | --- |
| `category` | 없으면 전체 소비. 있으면 `transaction_category` 이름(또는 id — 기존 Spending API의 `category` 파라미터 형식을 그대로 따름). 없는 카테고리 400 | 기획안 "배달·외식비 12만 원 증가" 예시 |
| `monthlyReduction` | 1원 이상, 해당 범위의 **최근 3개월 평균 월 소비 이하**(초과 시 `SIMULATION_REDUCTION_EXCEEDS_SPENDING` 400) | 줄일 수 없는 금액을 줄이는 결과를 만들지 않음 |
| `durationMonths` | 기본 12, 1~120 | Goal forecast의 months 범위와 동일 |

계산(`SpendingReductionCalculator`):
- before: `averageMonthlySpending`(전체·카테고리별), 각 SAVING 목표의 `expectedAchievementDate`(기준선의 Goal forecast).
- after: `spending' = spending − reduction`, 가용자금 `+reduction/월`. SAVING 목표 pace' = `averageMonthlySaving + reduction`(감소분 전액을 저축으로 돌린다는 가정 `REDUCTION_FULLY_SAVED`를 `assumptions`에 기록).
  `after = projectAchievementDate(remaining, pace', base)`; duration 안에 못 이루면 그대로 예상일(기간 제한은 cashflow 표 길이만 결정). SPENDING 목표는 `projectedMonthEnd − reduction` vs budget으로 초과 여부 변화.
- `comparison.items`: 월 소비, 월 가용자금, 목표별 달성일. `costs`·`debts` 빈 목록, `assets`에 `누적 추가 저축 = reduction × duration` 1건.
- `cashflowImpact.months`: `base+1..base+duration`, delta = `+reduction`.

### 5.2 `POST /simulations/saving` — 저축액 변경

Body `{ accountNo?, monthlySavingAmount, durationMonths? }`.

| 항목 | 검증·기본값 |
| --- | --- |
| `accountNo` | 있으면 `SensitiveNumberHasher`로 해시 후 `AccountRepository.findByUserIdAndAccountNoHash`로 **본인 계좌** 확인(없거나 타인 → 404, 원문은 저장·로그·응답 금지). 목표에 연결된 계좌면 그 목표만 영향 대상, 아니면 전체 SAVING 목표. 없으면 전체 |
| `monthlySavingAmount` | 0원 이상의 **새 월 저축액(절대값)**. `monthlyIncome`이 있으면 그 이하(초과 시 `SIMULATION_SAVING_EXCEEDS_INCOME` 400), 없으면 상한 없음 + `INCOME_UNKNOWN` 가정 |
| `durationMonths` | 기본 12, 1~120 |

계산(`SavingChangeCalculator`): `delta = amount − averageMonthlySaving`(음수 허용 — 저축 감소 시 달성일이 늦어지는 것도 보여준다).
pace' = `amount`, `after = projectAchievementDate(remaining, amount, base)`; `amount = 0`이면 after null(달성 불가). cashflow delta = `−delta`(저축 증가는 가용자금 감소).
`comparison`: 월 저축액, 월 가용자금, 목표별 달성일. `assets`에 `누적 저축 = amount × duration`. 이자는 계산하지 않고 `INTEREST_NOT_APPLIED` 가정을 남긴다(예적금 상품 연계는 saving-product 이슈).

### 5.3 응답과 오류

두 POST 모두 201 + `SimulationResponse`(4.4 상세와 동일) + `created`(지문 재사용이면 false → 200). ErrorCode 추가:

| 코드 | HTTP |
| --- | --- |
| `SIMULATION_REDUCTION_EXCEEDS_SPENDING` | 400 |
| `SIMULATION_SAVING_EXCEEDS_INCOME` | 400 |
| `SIMULATION_TYPE_NOT_SUPPORTED` | 400 (`GET ?type=` 검증 외에, 등록 계산기 없는 type이 내부 경로로 들어올 때 방어) |

기준선 데이터 부족(목표 없음, 거래 없음)은 오류가 아니라 **빈 goalImpacts·0원 기준선 + coverage 사유**다(진단과 같은 태도). 단 spending의 `monthlyReduction > 평균 소비`는 0원 소비 기준선이면 항상 400이 되므로 응답 메시지에 기준 금액을 포함한다.

## 6. 테스트

- 마이그레이션: `FlywayOutOfOrderTest` 계열에 V19 추가. MySQL 통합(`SIMULATION_MYSQL_TEST_URL` 설정 시)에서 `ddl-auto: validate`, CHECK 4종, 복합 FK 2종, JSON 타입. 547 문서 §8의 "빈 DB에서 V6 실패" 우회 절차 재사용.
- 계산기 단위(DB 없음): 달성일 식 경계(remaining ≤ 0 / pace ≤ 0 / 반올림), 감소 전액 저축 가정, 저축 감소 시 지연, duration 경계 1·120, 카테고리 지정.
- (변경) Goal 도메인 미수정. 대신 기준선 예상 달성일 = Goal forecast API 응답을 API 테스트로 대조.
- Runner·Store: 원자 저장(scenario·goal_impact 포함), 같은 지문 재사용, 다른 날 새 건, 계산 실패 시 행 없음, 잠금 직렬화.
- API: 정상 201/200, 검증 400(음수·상한 초과·duration 0/121·없는 카테고리), 인증 401, 타인 계좌 404, 타인·삭제 simulation 404, 목록 필터·페이지 경계, DELETE 후 목록 제외·재삭제 404, 응답에 accountNo 원문 없음.
- 기준선 없음(목표·거래 0건) 시 빈 결과와 coverage.

## 7. 문서와 인계

- `backend/docs/simulation.md` 신규: 스키마·계산 계약·지문 규칙·엔드포인트·검증 규칙·가정 코드 목록·제한사항.
- 남길 제한사항: 8개 유형 미구현(엔드포인트 없음), 이자 미반영, 현금흐름 예측과 미연동(월 delta 표만), 소비 감소분 전액 저축 가정, `FAILED`·`PENDING`·`RUNNING` 상태 미사용, `assets/debts/costs`는 메모리 페이징.
- (변경) Goal 도메인 변경 없음. `GoalAchievementProjection`이 계산식 복제라는 점을 MR 본문과 simulation.md 인계 항목에 명시.
- 루트 `docs/` API 명세 변경 없음. 응답 필드는 명세에 없어 이 문서가 계약 — 프론트 공유 필요.
