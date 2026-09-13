# Simulation 공통 모델·소비/저축 시뮬레이션 (S15P21D105-550 · 551)

작성일: 2026-09-13. API 17 Simulation의 저장 구조·계산 계약·공통 조회 API(550)와 소비 감소·저축액 변경 실행 API(551)다.
계획과 결정 배경은 [simulation-plan.md](simulation-plan.md)에 있다.

기준 자료: [API 17](../../docs/API명세서/17%20Simulation%203cdbb6ea356480a38af4c6f56a5280f2.csv),
[ERD SQL](../../docs/ERD/DODAM_ERDCLOUD_IMPORT.sql) 1089~1150행, [기획안](../../docs/기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md) 4-5·7-5·9-6.

## 1. 범위

| 포함 | 제외(후속) |
| --- | --- |
| V19 마이그레이션 3테이블, 엔티티 3종, `SimulationType`·`SimulationStatus` | 명세의 나머지 실행 8종(saving-product, loan/*, installment, revolving, cash-advance, combined) — **경로 자체가 없다** |
| 계산 계약 `SimulationCalculator` + 기준선 Reader + Runner/Store | 이자·세후 계산, 상품 DB 연계 |
| 실행: `POST /simulations/spending`, `POST /simulations/saving` | 현금흐름 예측(cashflow forecast)과의 연동 — 월 delta 표만 만든다 |
| 조회: 목록·상세·comparison·cashflow·goal-impact·assets·debts·costs, 삭제 | Recommendation → Simulation 연결, `improvement_action` |

## 2. 스키마

`V19__create_simulation.sql`. ERD의 세 테이블 정의를 그대로 옮겼다(diff로 확인). V18(recommendation, 547 브랜치)과 독립이며 V30이 먼저 적용된 DB에는 out-of-order로 들어간다.

| 테이블 | 핵심 제약 |
| --- | --- |
| `simulation` | `(user_id, simulation_id)` UNIQUE, CHECK type 10종·status 4종, `before_snapshot`·`after_snapshot` NOT NULL JSON, `deleted_at` 소프트 삭제 |
| `simulation_scenario` | `(simulation_id, sequence_no)` UNIQUE, `sequence_no > 0`, `parameters` JSON NOT NULL |
| `simulation_goal_impact` | `(simulation_id, goal_id)` UNIQUE, `(user_id, simulation_id)`→simulation·`(user_id, goal_id)`→goal 복합 FK |

엔티티에도 같은 UNIQUE를 선언했다(H2 테스트는 Flyway 없이 엔티티로 스키마를 만든다). CHECK는 생성자가 먼저 막는다.
JSON 컬럼은 `JsonNode`다(문자열 매핑 시 리터럴로 한 번 더 감싸지는 문제, Diagnosis 주석 참고).

## 3. 계산 계약

```text
domain/simulation/
  entity/      Simulation, SimulationScenario, SimulationGoalImpact, SimulationType, SimulationStatus
  repository/  SimulationRepository, SimulationScenarioRepository, SimulationGoalImpactRepository, SimulationUserLock
  dto/         SimulationInput(+SpendingReductionInput, SavingChangeInput), SimulationBaseline, SimulationResult, request/, response/
  service/     SimulationCalculator, SimulationBaselineReader, SimulationInputResolver, SimulationRunner, SimulationStore,
               SimulationQueryService, SimulationRequestHash, SimulationCalculationVersion, GoalAchievementProjection, SimulationClock
  service/calculator/  SpendingReductionCalculator, SavingChangeCalculator, SimulationScenarios(공통 조립)
```

- **`SimulationInput`**: HTTP 요청과 분리된 확정 입력. `SimulationInputResolver`가 카테고리 문자열 → 코드(없으면 400), 계좌번호 → 본인 계좌 id(해시 조회, 없거나 타인이면 404)로 바꾼다. 계좌번호 원문·해시는 입력·지문·저장·응답 어디에도 들어가지 않는다.
- **`SimulationBaseline`**: 실행 시점 현황. `SimulationBaselineReader`가 읽기 전용으로 모은다.
  - 소득: `financial_profile.monthly_income`. 없으면 null + `INCOME_UNKNOWN`.
  - 소비: **기준일이 속한 달을 제외한 직전 3개 달력월**의 월평균(전체·카테고리 코드별, 미분류는 `UNCATEGORIZED`). 거래가 없으면 0 + `SPENDING_NO_TRANSACTIONS`.
  - 모으기 목표(ACTIVE·SAVING): Goal 도메인과 같은 정의 — 모인 금액 = 연결 계좌 잔액 합, 속도 = 모인 금액 / max(1, 생성일~기준일 경과 개월), 남은 금액 = max(0, 목표 − 모인 금액), 예상 달성일 = `GoalAchievementProjection`. 없으면 `NO_ACTIVE_SAVING_GOAL`.
  - 줄이기 목표(ACTIVE·SPENDING): 예산, 이번 달 지출, 예상 월말 지출(Goal과 같은 식 `spent × 월 일수 / 경과 일수`, 반올림).
- **`GoalAchievementProjection.expectedDate(remaining, pace, base)`**: 남은 금액 ≤ 0 → base, 속도 ≤ 0 → null, 그 외 `base + ceil(remaining / pace)`개월. `GoalCalculationService.getForecast`와 같은 식이며 `SimulationApiTest.baselineForecastMatchesGoalForecastApi`가 Goal API 응답과 대조한다.
  Goal 코드를 호출하지 않는 이유: develop의 `getForecast`는 목표 잔액을 갱신하고 이력을 남기는 쓰기 경로이고, 546이 같은 메서드를 수정 중이라 550에서 Goal 도메인을 건드리면 충돌한다. **546 병합 후 Goal 쪽에 순수 함수가 생기면 이 클래스를 그 함수로 교체한다.**
- **`SimulationCalculator<Q>`**: `type()`, `inputType()`, `calculate(baseline, input) → SimulationResult`. 순수 함수(저장·외부 호출·시각 조회 없음). 기준선이 있어야 판단하는 검증은 여기서 `BusinessException`으로 거부한다.
- **`SimulationRunner.run(userId, input)`**: 한 트랜잭션 — 사용자 행 잠금(`SimulationUserLock`) → 요청 지문 재사용 확인 → 기준선 → 계산 → 저장. 계산 예외 시 행이 남지 않는다. 같은 유형 계산기가 둘이면 기동 실패. 계산기가 없는 유형은 `SIMULATION_TYPE_NOT_SUPPORTED`(계산기 빈이 0개여도 기동된다).

### 요청 지문과 중복 방지

ERD에 멱등 컬럼이 없어 `SimulationRequestHash.of(userId, type, input.fingerprint(), 기준일, SimulationCalculationVersion.CURRENT)`(길이 접두 인코딩 SHA-256)를
`request_snapshot.requestHash`에 저장한다. **같은 날 같은 지문의 미삭제 건이 있으면 새로 만들지 않고 그 건을 돌려준다**(`created=false`, HTTP 200).
기준일이 바뀌면 기준선이 달라지므로 새 건이다. 삭제한 건은 재사용하지 않는다. DB UNIQUE가 없으므로 사용자 행 잠금이 실질 방어선이다.
계산식이 바뀌면 `SimulationCalculationVersion.CURRENT`를 올린다.

### 상태

동기 실행이라 저장되는 행은 항상 `COMPLETED`(`completed_at = created_at`). `PENDING`·`RUNNING`·`FAILED`는 쓰지 않는다.

## 4. 실행 API (551)

공통: 인증 필수(`AuthenticatedUserIdResolver`), 201 + `{ simulation, created: true }`, 재사용이면 200 + `created: false`. `durationMonths`는 기본 12, 1~120.

### `POST /api/v1/simulations/spending` — 소비 감소

Body `{ category?, monthlyReduction, durationMonths? }`

| 검증 | 결과 |
| --- | --- |
| `monthlyReduction` 없음·0·음수, `durationMonths` 범위 밖 | 400 (`VALIDATION_ERROR`) |
| `category`가 존재하지 않는 코드 | 400 (`InvalidRequestException`, Spending API와 같은 메시지). `UNCATEGORIZED` 허용, 대소문자 무시 |
| `monthlyReduction` > 기준 구간 월평균 지출(카테고리 지정 시 그 카테고리) | 400 `SIMULATION_REDUCTION_EXCEEDS_SPENDING`. 거래가 없으면 항상 초과다 |

계산(`SpendingReductionCalculator`):

- 현금흐름: `after.monthlySpending = before − reduction`, 저축 약정(`monthlySaving`)은 그대로 → 월 가용자금 `+reduction`. `cashflow.months[]`는 기준일 다음 달부터 `durationMonths`개, `delta = +reduction`, `cumulative` 누적.
- 목표 영향: 감소분을 모으기 목표에 돌린다고 가정(`REDUCTION_DIRECTED_TO_SAVING_GOALS`). 목표가 여럿이면 **남은 금액 비례**로 얹는다(`SPLIT_BY_REMAINING`, 마지막 목표가 나머지). 남은 금액이 모두 0이면 균등.
- `comparison.items`: 범위 월 지출, 월 지출 합계, 월 가용자금, 줄이기 목표별 예상 월말 지출(`− reduction`, 0 하한), 모으기 목표별 월 저축·예상 달성일. `assets`: `durationMonths`개월 누적 추가 저축 가능액 1건. `debts`·`costs` 빈 목록.

### `POST /api/v1/simulations/saving` — 저축액 변경

Body `{ accountNo?, monthlySavingAmount, durationMonths? }`. `monthlySavingAmount`는 **변경 후 월 저축액(절대값)**, 0 이상.

| 검증 | 결과 |
| --- | --- |
| `monthlySavingAmount` 없음·음수, `accountNo` 형식(`[0-9-]{8,32}`) 위반 | 400 |
| `accountNo`가 본인 계좌가 아님(없음·타인) | 404 `RESOURCE_NOT_FOUND` |
| 소득이 등록돼 있고 `monthlySavingAmount` > 소득 | 400 `SIMULATION_SAVING_EXCEEDS_INCOME`. 소득 미등록이면 상한 없음(`INCOME_UNKNOWN`) |

계산(`SavingChangeCalculator`):

- 대상 목표: 계좌를 지정했고 그 계좌가 모으기 목표에 연결돼 있으면 그 목표만(다른 목표는 기존 속도 유지). 아니면 모든 모으기 목표에 남은 금액 비례 배분. 계좌를 지정했지만 연결된 목표가 없으면 `ACCOUNT_NOT_LINKED_TO_GOAL` 가정을 남기고 전체 배분.
- `after.monthlySaving` = 대상 목표 배분액 + 비대상 목표 기존 속도(목표가 없으면 입력값). 월 가용자금 `−(after − before)`. 저축을 줄이면 달성일이 늦어지는 것도 그대로 보여준다. 0원이면 달성 불가(null).
- 이자는 계산하지 않는다(`INTEREST_NOT_APPLIED`). `assets`: `durationMonths`개월 누적 저축액 전후.

### 응답 모양 (`SimulationResponse`)

`simulationId, type, title, summary, status, createdAt, completedAt, request(파라미터, requestHash 제외), scenarios[]{sequenceNo, scenarioType, parameters}, before, after, comparison, cashflow, goalImpacts[]{goalId, beforeForecastDate, afterForecastDate, differenceDays}`

- `before`/`after`: `{ baseDate, monthlyIncome, monthlySpending, monthlySaving, monthlyAvailableFunds(소득 모르면 null), goals[]{goalId, name, remaining, monthlySaving, expectedAchievementDate} }`. `before.coverage[]`에 입력 누락 사유, `after.assumptions[]`에 적용 가정.
- `comparison`: `{ items[], assets[], debts[], costs[] }`, 각 항목 `{ key, label, before, after, delta }`(금액은 원, 날짜는 ISO 문자열, delta는 일수).
- `cashflow`: `{ basis: "MONTHLY_AVAILABLE_FUNDS", before, after, months[]{yearMonth, delta, cumulative} }`. 예정 이벤트를 다시 더하지 않는다.
- `differenceDays = after − before`(음수면 단축). 한쪽이 null이면 null.

## 5. 조회·삭제 API (550)

공통: 타인·없는·삭제된 건은 404 `RESOURCE_NOT_FOUND`. 저장값만 반환하고 재계산하지 않는다. `@Transactional(readOnly = true)`.

| Endpoint | 파라미터 | 응답 | 정렬·빈 결과 |
| --- | --- | --- | --- |
| `GET /simulations` | `type?`(enum 외 400), `page≥0`, `size 1~100` 기본 20 | `PageResponse<{simulationId, type, title, summary, status, createdAt, completedAt}>` | `createdAt DESC, id DESC`. 삭제 건 제외 |
| `GET /{id}` | | `SimulationResponse` | |
| `GET /{id}/comparison` | | `comparison` JSON | |
| `GET /{id}/cashflow` | | `cashflow` JSON | |
| `GET /{id}/goal-impact` | | `goalImpacts[]` | 목표 없으면 `[]` |
| `GET /{id}/assets`·`/debts`·`/costs` | `page`, `size` | `comparison.<section>[]`을 메모리에서 잘라 `PageResponse` | 유형이 다루지 않으면 `totalElements=0` |
| `DELETE /{id}` | | 204. `deleted_at` 기록 | 이미 삭제·타인 → 404 |

## 6. 오류 코드

| 코드 | HTTP | 발생 |
| --- | --- | --- |
| `SIMULATION_REDUCTION_EXCEEDS_SPENDING` | 400 | 감소액 > 기준 구간 월평균 지출 |
| `SIMULATION_SAVING_EXCEEDS_INCOME` | 400 | 저축액 > 등록된 월 소득 |
| `SIMULATION_TYPE_NOT_SUPPORTED` | 400 | 계산기 없는 유형이 Runner에 들어온 경우(공개 경로에서는 발생하지 않음) |

## 7. 검증

H2 (`.\gradlew.bat test --tests 'com.ssafy.dodam.domain.simulation.*'`, 2026-09-13 통과, 19건 = 550 6건 + 551 13건):

- 550 `SimulationModelTest` 3·`SimulationQueryApiTest` 3: 달성일 식·요청 지문·엔티티 검증, Store 원자 저장·재사용·조회 API·소프트 삭제.
- 551 `SimulationCalculatorTest` 7: 소비 감소의 가용자금·비례 배분·달성일 단축·월별 표·줄이기 목표 항목, 범위 상한과 소득 미등록, 저축 증가·감소·0원, 연결 계좌 한정 배분, 소득 상한, 빈 기준선, 입력 범위·지문.
- 551 `SimulationApiTest` 6: 실행·저장(scenario·goal_impact)·같은 날 재사용·다른 파라미터·다음 날 신규, **기준선 예상 달성일이 Goal API와 일치**, 계좌번호 해시 조회·타인 계좌 404·응답/스냅샷에 원문·해시 부재, 검증 400 12종·401·저장 없음, 목록 필터·페이지 경계·소유권 격리·섹션 페이징·삭제 후 404·재삭제 404·삭제 건 미재사용, 빈 기준선 coverage.

MySQL 통합 검증은 하지 않았다(§8).

## 8. 현재 제한사항과 인계

- [ ] **MySQL 미검증**: V19 DDL·`ddl-auto: validate`·CHECK·복합 FK·JSON 타입은 H2에서 확인되지 않는다. 547의 `RecommendationMySqlIntegrationTest` 방식(`*_MYSQL_TEST_URL` 설정 시만 실행)으로 추가해야 한다. 빈 DB에서 V6이 V11보다 먼저 실행돼 Flyway가 실패하는 develop 기존 문제(recommendation.md §8)도 그대로다.
- [ ] `GoalAchievementProjection`은 Goal 계산식의 복제다. 546 병합 후 Goal 도메인에 순수 함수가 생기면 교체한다.
- [ ] 명세 17의 나머지 실행 8종은 경로가 없다. 계산기를 추가하고 컨트롤러 경로를 열면 된다(`SimulationType`·CHECK는 이미 10종).
- [ ] 소비 감소분을 목표에 돌리는 가정, 남은 금액 비례 배분, 이자 미반영은 `assumptions`로 노출하는 서비스 결정이다. 팀 합의가 다르면 계산기만 바꾸고 `SimulationCalculationVersion`을 올린다.
- [ ] `cashflow`는 월 delta 표이며 예정 이벤트·현금흐름 예측과 연동하지 않았다.
- `assets/debts/costs`는 저장 JSON의 메모리 페이징이다. 항목이 수십 건을 넘는 유형(대출·복합)이 생기면 구조를 다시 본다.
- 명세 17에 응답 필드가 없으므로 이 문서가 응답 계약이다. 프론트 연동 시 공유 필요.
