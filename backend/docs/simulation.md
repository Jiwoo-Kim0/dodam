# Simulation 공통 모델·소비/저축/예적금/대출/카드/복합 시뮬레이션 (S15P21D105-550 · 551 · 585 · 586 · 587)

작성일: 2026-09-13, 갱신 2026-09-14(585), 2026-09-15(586·587). API 17 Simulation의 저장 구조·계산 계약·공통 조회 API(550), 소비 감소·저축액 변경 실행 API(551),
예적금 가입·신규 대출·추가상환·대환 실행 API(585, §9), 할부·리볼빙·현금서비스·복합 실행 API(586, §10), 전체 통합·추천 연결·MySQL 검증(587, §11)이다. 계획과 결정 배경은
[simulation-plan.md](simulation-plan.md), [simulation-585-plan.md](simulation-585-plan.md), [simulation-586-plan.md](simulation-586-plan.md), [simulation-587-plan.md](simulation-587-plan.md)에 있다.

기준 자료: [API 17](../../docs/API명세서/17%20Simulation%203cdbb6ea356480a38af4c6f56a5280f2.csv),
[ERD SQL](../../docs/ERD/DODAM_ERDCLOUD_IMPORT.sql) 1089~1150행, [기획안](../../docs/기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md) 4-5·7-5·9-6.

## 1. 범위

| 포함 | 제외(후속) |
| --- | --- |
| V19 마이그레이션 3테이블, 엔티티 3종, `SimulationType`·`SimulationStatus` | 실제 결제·약정 변경·대출 실행, 금융망 동기화 |
| 계산 계약 `SimulationCalculator` + 기준선 Reader + Runner/Store | 세후 계산, 중도상환 수수료·대환 부대비용, 카드 연체료·기관별 최소결제액 |
| 실행: `POST /simulations/spending`, `/saving`(551), `/saving-product`, `/loan/new`, `/loan/prepayment`, `/loan/refinance`(585), `/installment`, `/revolving`, `/cash-advance`, `/combined`(586) | 현금흐름 예측(cashflow forecast)과의 연동 — 월 delta 표만 만든다 |
| 조회: 목록·상세·comparison·cashflow·goal-impact·assets·debts·costs, 삭제 | Recommendation → Simulation 연결, `improvement_action`, 실제 가입·대출·상환 실행 |

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
  dto/         SimulationInput(+SpendingReductionInput, SavingChangeInput, SavingProductInput, LoanNewInput, LoanPrepaymentInput,
               LoanRefinanceInput, InstallmentInput, RevolvingInput, CashAdvanceInput, CombinedInput), SimulationBaseline, SimulationResult,
               ScenarioComputation·ScenarioLedger(586 내부 계산 자료), ProductSnapshot, LoanSnapshot, CardRates, request/, response/
  service/     SimulationCalculator, SimulationCalculatorRegistry(586 dispatch), SimulationBaselineReader, SimulationSnapshotReader(585 상품·대출 스냅샷),
               SimulationInputResolver, CombinedScenarioParser·CombinedScenarioValidator(586), SimulationRunner, SimulationStore,
               SimulationQueryService, SimulationGoalImpactResponseAssembler, SimulationRequestHash, SimulationCalculationVersion,
               GoalAchievementProjection, GoalDateProjection(585), ProductOptionSelector(585), SimulationAssumptions(585·586 코드), SimulationClock
  service/calculator/  SpendingReductionCalculator, SavingChangeCalculator, SavingProductCalculator, LoanNewCalculator,
               LoanPrepaymentCalculator, LoanRefinanceCalculator, InstallmentCalculator, RevolvingCalculator, CashAdvanceCalculator,
               CombinedCalculator·CombinedGoalProjection(586), SimulationScenarios·LoanScenarios·CardScenarios·ScenarioCashflow(공통 조립)
```

- **`SimulationInput`**: HTTP 요청과 분리된 확정 입력. `SimulationInputResolver`가 카테고리 문자열 → 코드(없으면 400), 계좌번호 → 본인 계좌 id(해시 조회, 없거나 타인이면 404)로 바꾼다. 계좌번호 원문·해시는 입력·지문·저장·응답 어디에도 들어가지 않는다.
- **`SimulationBaseline`**: 실행 시점 현황. `SimulationBaselineReader`가 읽기 전용으로 모은다.
  - 소득: `financial_profile.monthly_income`. 없으면 null + `INCOME_UNKNOWN`.
  - 소비: **기준일이 속한 달을 제외한 직전 3개 달력월**의 월평균(전체·카테고리 코드별, 미분류는 `UNCATEGORIZED`). 거래가 없으면 0 + `SPENDING_NO_TRANSACTIONS`.
  - 모으기 목표(ACTIVE·SAVING): Goal 도메인과 같은 정의 — 모인 금액 = 연결 계좌 잔액 합, 속도 = 모인 금액 / max(1, 생성일~기준일 경과 개월), 남은 금액 = max(0, 목표 − 모인 금액), 예상 달성일 = `GoalAchievementProjection`. 없으면 `NO_ACTIVE_SAVING_GOAL`.
  - 줄이기 목표(ACTIVE·SPENDING): 예산, 이번 달 지출, 예상 월말 지출(Goal과 같은 식 `spent × 월 일수 / 경과 일수`, 반올림).
- **`GoalAchievementProjection.expectedDate(remaining, pace, base)`**: 남은 금액 ≤ 0 → base, 속도 ≤ 0 → null, 그 외 `base + ceil(remaining / pace)`개월. `GoalCalculationService.getForecast`와 같은 식이며 `SimulationApiTest.baselineForecastMatchesGoalForecastApi`가 Goal API 응답과 대조한다.
  Goal 코드를 호출하지 않는 이유: develop의 `getForecast`는 목표 잔액을 갱신하고 이력을 남기는 쓰기 경로이고, 546이 같은 메서드를 수정 중이라 550에서 Goal 도메인을 건드리면 충돌한다. **546 병합 후 Goal 쪽에 순수 함수가 생기면 이 클래스를 그 함수로 교체한다.**
- **`SimulationCalculator<Q>`**: `type()`, `inputType()`, `compute(baseline, input) → ScenarioComputation{result, ledger}`, `calculate(...)`는 `result`만 돌려주는 기본 메서드다. 순수 함수(저장·외부 호출·시각 조회 없음). 기준선이 있어야 판단하는 검증은 여기서 `BusinessException`으로 거부한다. `ledger`(586)는 전후 날짜별 `{date, principal, interest, fee}` 납부 자료·종료일·비용 확정 여부이며 복합 계산기만 읽고 저장하지 않는다.
- **`SimulationCalculatorRegistry`**(586): 유형 → 계산기 조회와 순수 dispatch. Runner와 복합 계산기가 공유하며 복합 자식 계산은 Runner.run(잠금·지문·저장)을 재호출하지 않는다. 계산기 목록은 첫 조회 때 채운다(복합 계산기가 레지스트리를 주입받으므로 생성자에서 모으면 순환). 같은 유형 계산기가 둘이면 첫 실행에서 실패, 계산기가 없는 유형은 `SIMULATION_TYPE_NOT_SUPPORTED`(계산기 빈이 0개여도 기동된다).
- **`SimulationRunner.run(userId, input)`**: 한 트랜잭션 — 사용자 행 잠금(`SimulationUserLock`) → `input.withBaseDate(기준일)`로 생략값 확정(복합은 자식에 전파) → 요청 지문 재사용 확인 → 기준선 → `SimulationSnapshotReader.prepare`(585 유형만 상품·대출 스냅샷을 입력에 채움, 복합은 자식별) → 계산 → 저장. 계산 예외 시 행이 남지 않는다.
- **`SimulationStore.save`**: `simulation` 1행 + `simulation_scenario` N행(`input.scenarios()` — 단독은 자기 자신 1행, 복합은 자식 N행, sequenceNo = 입력 순서) + `simulation_goal_impact`. 복합 부모 자체는 scenario 행이 아니며 부모 파라미터는 `request_snapshot`에만 있다.

### 요청 지문과 중복 방지

ERD에 멱등 컬럼이 없어 `SimulationRequestHash.of(userId, type, input.fingerprint(), 기준일, SimulationCalculationVersion.CURRENT)`(길이 접두 인코딩 SHA-256)를
`request_snapshot.requestHash`에 저장한다. **같은 날 같은 지문의 미삭제 건이 있으면 새로 만들지 않고 그 건을 돌려준다**(`created=false`, HTTP 200).
기준일이 바뀌면 기준선이 달라지므로 새 건이다. 삭제한 건은 재사용하지 않는다. DB UNIQUE가 없으므로 사용자 행 잠금이 실질 방어선이다.
계산식이 바뀌면 `SimulationCalculationVersion.CURRENT`를 올린다. 585에서 `v2-saving-loan-product`, 586에서 `v3-card-combined`로 올렸다 — 배포 후 이전 버전으로 저장된 같은 날 결과는 재사용되지 않고 새로 만든다.

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
- `goalImpacts[]`는 실행 POST·상세 GET·`GET /goal-impact` 모두 `SimulationGoalImpactResponseAssembler`가 만든다(엔티티 날짜 + `after.goalImpactDetails`의 `status`/`reasons`를 goalId로 결합, goalId 오름차순). spending/saving과 확장 필드가 없는 과거 결과는 `status`·`reasons` 키가 아예 나오지 않는다.

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
| `SIMULATION_PRODUCT_NOT_SUPPORTED` | 400 | (585) 상품은 있지만 대상 유형이 아님: 예금이 `FIXED_DEPOSIT`이 아님, 대출 상품이 533 비교군 밖(한도대출·자격 한정), 대환 대상이 기존 대출의 비교군과 다름, 기존 대출에 연결 상품이 없음 |
| `SIMULATION_PRODUCT_OPTION_UNAVAILABLE` | 400 | (585) 기간(대환은 잔여 회차 수)·상환방식·유효 금리 자료를 만족하는 옵션 없음 |
| `SIMULATION_REPAYMENT_TYPE_REQUIRED` | 400 | (585) 신규 대출 `repaymentType` 생략인데 후보 옵션의 방식이 한 종류로 확정되지 않음(AMORTIZING 등) |
| `SIMULATION_PRODUCT_LIMIT_EXCEEDED` | 400 | (585) 유효 옵션이 모두 공시 한도(옵션·상품 한도 중 작은 값) 때문에 탈락 |
| `SIMULATION_LOAN_DATA_UNAVAILABLE` | 400 | (585) 대출이 ACTIVE가 아님(OVERDUE 포함)·잔액 0·금리/만기/다음 상환일 누락·다음 상환일이 기준일 이전 또는 만기 이후·대환에서 다음 상환일이 기준일 당일 |
| `SIMULATION_INVALID_PAYMENT_DATE` | 400 | (585) 추가상환일 < 기준일 또는 > 만기 |
| `SIMULATION_AMOUNT_EXCEEDS_BALANCE` | 400 | (585) 추가상환액 > 실행 시점 예상 잔액(만기 당일 약정상환 후 0 포함), 대환액 > 잔액 |
| `SIMULATION_PERIOD_NOT_SUPPORTED` | 400 | (585) 보유 대출 잔여 회차 > 360 |
| `SIMULATION_AMOUNT_OUT_OF_RANGE` | 400 | (585) 금액 합계·차이가 long 범위 초과 |
| `SIMULATION_COMBINED_DUPLICATE_SCENARIO` | 400 | (586) 복합 자식 중 정규화 결과가 같은 행동이 둘 이상(할부는 수수료율 모드 제외 비교) |
| `SIMULATION_COMBINED_SCENARIO_CONFLICT` | 400 | (586) 전체·카테고리 소비 감소 혼합, 같은 카테고리 반복, SAVING 2개 이상, 같은 대출의 추가상환·대환, 같은 카드 리볼빙, cardId 없는 리볼빙이 포함된 다중 리볼빙 |
| `INVALID_REQUEST` | 400 | (586) 복합 자식 구조 오류: 알 수 없는 type·COMBINED 중첩·type/parameters 외 필드·parameters의 알 수 없는 필드·자식 DTO 검증 위반. 메시지에 `scenarios[i].parameters.<field>` 경로만 담고 입력 값은 넣지 않는다. 신규 4종의 정수 필드에 소수가 오면 같은 코드 |

585 공통: 상품 없음·대출 없음·타인 대출은 모두 404 `RESOURCE_NOT_FOUND`(존재 여부 비노출). 실패 응답은 기존 `ApiResponse` 실패 형식이며 계좌번호·잔액 등 값을 메시지에 넣지 않는다. 계산 핵심 자료가 부족하면 저장하지 않고, 목표 영향만 계산할 수 없으면 나머지를 저장하고 목표별 `NOT_COMPUTABLE`을 돌려준다.

## 7. 검증

H2 (`.\gradlew.bat test --tests 'com.ssafy.dodam.domain.simulation.*'`, 2026-09-13 통과, 19건 = 550 6건 + 551 13건):

- 550 `SimulationModelTest` 3·`SimulationQueryApiTest` 3: 달성일 식·요청 지문·엔티티 검증, Store 원자 저장·재사용·조회 API·소프트 삭제.
- 551 `SimulationCalculatorTest` 7: 소비 감소의 가용자금·비례 배분·달성일 단축·월별 표·줄이기 목표 항목, 범위 상한과 소득 미등록, 저축 증가·감소·0원, 연결 계좌 한정 배분, 소득 상한, 빈 기준선, 입력 범위·지문.
- 551 `SimulationApiTest` 6: 실행·저장(scenario·goal_impact)·같은 날 재사용·다른 파라미터·다음 날 신규, **기준선 예상 달성일이 Goal API와 일치**, 계좌번호 해시 조회·타인 계좌 404·응답/스냅샷에 원문·해시 부재, 검증 400 12종·401·저장 없음, 목록 필터·페이지 경계·소유권 격리·섹션 페이징·삭제 후 404·재삭제 404·삭제 건 미재사용, 빈 기준선 coverage.

585 (2026-09-14, H2 `.\gradlew.bat test --tests 'com.ssafy.dodam.domain.simulation.*'` 45건 통과 = 550·551 19건 + 585 26건):

- `ProductOptionSelectorTest` 3: 예적금 정확 기간·최저금리·0%·금리 누락·미지원 유형, 대출 한도 필터 → 방식 확정 순서(검산표 "한도 충족 후보 선택"·"모든 후보 한도 초과"·"한도와 방식 자동 선택"), AMORTIZING 모호성, 금리 역전·음수·한도 0 자료 오류, 한도대출 제외.
- `GoalDateProjectionTest` 4: 변화 0이면 기준선 날짜 보존, 검산표 "목표 개선"(30,000/25,000), 음수 0 하한, "목표 발생일 보존"(1/14→3/14)·"당월 미래 절감"(1/28→2/14), 말일 복원, 사건 뒤 기존 속도 연장, 남은 금액 비례 배분·달성 목표 제외.
- `ProductLoanSimulationCalculatorTest` 13: 검산표의 예금 36,500·적금 870·윤년 36,600·0% 원금균등·1/31 약정일·360회 계산과 120개월 표 잘림, 추가상환 당일(24,000→20,000)·미래(22,000)·전액·만기 당일 400·과거/만기 초과 400·OVERDUE·오래된 다음 상환일, 원리금균등 재계산·날짜 보존, 부분 대환 18,000·원금 보존·목표 5개월 유지, 대환 목표 검산(3개월 유지/2개월 단축/여러 목표 배분/속도 0), 전액·동일 금리 대환의 일정·목표일 불변, 비교군 불일치·연결 상품 없음·기간 밴드 후보·방식 불일치·당일·잔액 초과·한도, 361회 거부·long 초과, 입력 범위·AUTO/FULL_BALANCE 지문·paymentDate 생략 정규화.
- `SimulationProductLoanApiTest` 6: 예적금 실행·저장·상세/goal-impact/assets/costs/cashflow/목록 연결·타인 404·상품 금리 변경 후 같은 날 재사용·적금 870; 404/NOT_SUPPORTED/OPTION_UNAVAILABLE/검증 400 7종/401·저장 없음; 신규 대출 AUTO 저장 → 옵션 변경 후 생략·null 재요청 200 동일 id·명시 EQUAL_PRINCIPAL 400·명시 BULLET 신규 201·한도 초과·비대출 상품·enum 오류; 추가상환 계좌번호 해시 조회·paymentDate 기본값·응답/스냅샷에 원문·해시 부재·"오늘" 명시 재사용·미래 날짜 신규·타인/없는 대출 404·날짜/잔액/만기 400·OVERDUE 400·원본 `loan` 잔액 불변; 대환 목표 투영(8/1→7/1, −31일)·POST/상세/goal-impact/재사용 POST 배열 동일·엔티티 저장 날짜·부분 대환·비교군/비대출/잔액/404/0원·기간 불일치·당일 거부; spending/saving 회귀(확장 필드·status 없음).

586 (2026-09-15, H2 `.\gradlew.bat test --tests 'com.ssafy.dodam.domain.simulation.*'` 67건 통과 = 550·551·585 45건 + 586 22건. `SimulationQueryApiTest.runnerRejectsTypesWithoutCalculator`는 10종 모두 계산기가 생겨 빈 레지스트리 계약 검증으로 바꿨다):

- `CardSimulationCalculatorTest` 9: 할부 0%(원금 100,000씩·현금 합 0·ASSUMED_ZERO/EXPLICIT 지문·행동 키)·12% 2개월 검산(11,836/6,115)·1/31 말일 복원·원금 5원 12개월·1/120개월 경계·입력 범위; 리볼빙 검산(500,000/250,000/4,932)·완납·순현금 = −누적이자, 결제비율 100%·0%·1원, 120회차 미완납(잔액 999,879·종료일 null·순현금 = 잔액 − 이자); 현금서비스 9,863·생략/1 동일 지문·0%·120개월 3,653일·윤년 29일/365·반올림 0원; long 초과 400.
- `CombinedSimulationCalculatorTest` 6: 소비+할부 현금흐름 합성·누적 재계산·집계 항목·목표 계산 불가 사유·details.children 보존·순서 교환 불변; 기간 제한 목표 투영(9개월, 5개월 아님)·총 감소액 상한; SAVING 유효 구간 절감액 미배분·종료 후 재개(5개월)·속도 0·달성 목표·목표 없음; 360개월 대출+12개월 할부 평가기간(2036-09-30) 비용·전체 비용 별도 보존·표 잘림 전파; 대환 절감 음수 delta(−2,137)·미완납 리볼빙 부분 비용·자료가 평가기간을 덮으면 완전; 중첩·개수·자식 오류 전파·지문 순서.
- `CombinedScenarioValidatorTest` 2: 수수료율 모드 우회 중복·0/0.0000 중복·현금서비스 기본값 중복·같은 카테고리 중복, 전체+카테고리 혼합·SAVING 2개·같은 대출 추가상환+대환·같은 카드·cardId 없는 다중 리볼빙 충돌, 허용 조합.
- `SimulationCardCombinedApiTest` 5: 할부 실행·저장·상세/goal-impact/costs/debts/목록·타인 404·12/12.0000 재사용·생략과 명시적 0 별개 저장·소수 금액 400 INVALID_REQUEST·검증 400 7종·401; 리볼빙 본인 카드 저장·타인/없는 카드 404·카드 미지정·미완납 표시·검증 400; 현금서비스 기본값·생략/null/1 재사용·120개월; 복합(저축 계좌번호+할부+대환 계좌번호) 201·`scenarios` 3행·요청/scenario 파라미터에 계좌번호·해시 부재·평가기간 비용 24,000→29,951·3경로 goalImpacts 동일·재사용 200·순서 교환 신규 201; 개수 0/1/11 400·알 수 없는 유형·중첩·참조 필드·알 수 없는 파라미터·자식 검증 경로(원문 미포함)·소수·비객체 원소·중복/충돌 코드·타인 카드/대출 404·자식 계산 오류(감소액 초과) 후 0행 저장·401.

586 보완(2026-09-15): `CombinedSimulationCalculatorTest`에 자식 비례 배분의 산술 범위 초과와 부모 현금 유입 합계 범위 초과 회귀 테스트 2건을 추가했다. 해당 클래스 8건이 통과했으며 두 경로 모두 `SIMULATION_AMOUNT_OUT_OF_RANGE`를 반환한다.

MySQL 통합 검증은 587에서 수행했다(§11.3).

## 8. 현재 제한사항과 인계

- [x] **MySQL 검증**(587, §11.3): `SimulationMySqlIntegrationTest`(`SIMULATION_MYSQL_TEST_URL` 설정 시만 실행)로 V19 DDL·`ddl-auto: validate`·CHECK 3종·복합 FK·JSON 타입·N행 scenario·동시 실행 1건 저장을 MySQL 8.0.46에서 확인했다. 시연 seed를 적재한 스키마에서의 10종 실행 확인은 남아 있다(remaining-work.md). 빈 DB에서 V6이 V11보다 먼저 실행돼 Flyway가 실패하는 develop 기존 문제(recommendation.md §8)는 그대로다.
- [ ] `GoalAchievementProjection`은 Goal 계산식의 복제다. 546 병합 후 Goal 도메인에 순수 함수가 생기면 교체한다.
- [x] 명세 17의 실행 10종이 모두 열렸다(586). 계산기 추가 시 `SimulationCalculator.compute`가 내부 납부 자료(`ScenarioLedger`)도 만들어야 복합에 참여할 수 있다.
- [ ] 소비 감소분을 목표에 돌리는 가정, 남은 금액 비례 배분, 이자 미반영은 `assumptions`로 노출하는 서비스 결정이다. 팀 합의가 다르면 계산기만 바꾸고 `SimulationCalculationVersion`을 올린다.
- [ ] `cashflow`는 월 delta 표이며 예정 이벤트·현금흐름 예측과 연동하지 않았다.
- `assets/debts/costs`는 저장 JSON의 메모리 페이징이다. 복합 부모는 집계 항목만 두어 항목 수가 작지만, `after.details.children`은 자식 결과 전체를 담아 응답이 크다(자식 10건 × 월 항목 120개). 목록 조회에는 들어가지 않는다.
- 명세 17에 응답 필드가 없으므로 이 문서가 응답 계약이다. 프론트 연동 시 공유 필요.

## 9. 예적금·대출 실행 API (585)

기준: [simulation-585-plan.md](simulation-585-plan.md) §10. 명세 17이 정한 사실(경로·입력)과 서비스가 보완한 계산 정책을 구분해 적었다. 아래 "가정"은 응답 `after.assumptions[]`·`goalImpacts[].reasons[]`의 코드이며 `SimulationAssumptions`에 정의돼 있다.

### 9.1 공통

- 인증 필수, 신규 201 `{ simulation, created: true }`, 같은 날 같은 요청 재사용 200 `created: false`(§3).
- 기준일은 `SimulationClock`(Asia/Seoul)에서 실행당 한 번 얻는다. 금액은 `long`(원), 금리는 `BigDecimal`(%).
- **상품·대출 자료**: 저장된 `financial_product(_option)`과 `loan` 행(로컬 스냅샷)만 쓴다. 요청 중 금융망을 호출하지 않는다(`PRODUCT_DB_SNAPSHOT`, `LOAN_LOCAL_SNAPSHOT`). 명세의 추가상환 "SSAFY + 자체"와 다르다. 계산에 쓴 상품 조건(옵션 id·term·rateType·상환 코드·공시 금리·한도·`syncedAt`)은 `after.details.product`에 저장한다.
- **준비 순서**: Resolver가 상품 존재(404)·본인 대출(계좌번호 HMAC 조회, 없거나 타인이면 같은 404)만 확인한다 → Runner 잠금 → 기준일 기반 생략값 확정 → 지문 조회 → **신규 계산일 때만** 상품·대출 스냅샷을 읽어 유형·옵션·한도·상태를 검증(400)한다. 같은 날 상품이 바뀌어도 같은 요청은 첫 결과를 돌려준다.
- **지문**(§3): `SimulationRequestHash.of(userId, type, fingerprint, 기준일, "v2-saving-loan-product")`. 유형별 `fingerprint`는 아래 표. 스냅샷(금리·잔액)은 지문에 넣지 않는다.
- **세금·수수료**: 이자소득세·중도상환 수수료·대환 부대비용을 계산하지 않고 0원으로 표시하지도 않는다(`EXCLUDES_TAX_AND_FEES`). 예적금은 **세전**(`details.taxBasis = PRE_TAX`)이며 예금 상세(536)의 세후 수령액과 직접 비교하면 안 된다. "이자 절감액"은 수수료 제외 추정치다.
- **원금 이동과 손익**: 예치 원금·차입 원금·상환 원금 이동은 순자산 변화 0이며 이자만 손익이다. `assets/debts/costs`는 사용자 전체 합계가 아니라 **시나리오 영향액**(before 0, after 영향액)이다.
- 소수 금액은 전역 Jackson 기본값(`ACCEPT_FLOAT_AS_INT`)에 따라 정수로 절사돼 들어온다. 계획서의 "소수 금액 400"은 전역 설정 변경이 필요해 적용하지 않았다(모든 API 공통 동작).

| 경로 | Body(명세 17) | 검증 | fingerprint | 기본값 |
| --- | --- | --- | --- | --- |
| `POST /simulations/saving-product` | `productId, monthlyAmount, term` | 모두 필수, 양수, `term` 1~120 | `productId\|monthlyAmount\|term` | 없음 |
| `POST /simulations/loan/new` | `productId, principal, term, repaymentType?` | 양수, `term` 1~360, `repaymentType`은 `EQUAL_PRINCIPAL`/`EQUAL_PAYMENT`/`BULLET`(그 외 문자열 400) | `productId\|principal\|term\|AUTO 또는 enum` | 생략·null = 내부 모드 `AUTO` |
| `POST /simulations/loan/prepayment` | `accountNo, prepaymentAmount, paymentDate?` | `accountNo` `^[0-9]{10,20}$`, 금액 양수, 날짜 ISO | `loanId\|prepaymentAmount\|paymentDate` | `paymentDate` = 기준일. 생략과 "오늘" 명시는 같은 지문 |
| `POST /simulations/loan/refinance` | `accountNo, targetProductId, refinanceAmount?` | `accountNo` 위와 같음, `refinanceAmount` 양수 | `loanId\|targetProductId\|FULL_BALANCE 또는 금액` | 생략 = 기준일 잔액 전액. 잔액과 같은 금액을 명시해도 **별개 지문** |

`monthlyAmount`는 적금이면 월 납입액, 예금이면 일시 예치 원금이다(명세에 해석이 없어 서비스가 정했다). `request`·`scenarios[0].parameters`에는 계좌번호 대신 `loanId`가, 신규 대출에는 `repaymentType`(생략 시 null)과 `repaymentTypeMode`(`AUTO`/`EXPLICIT`), 대환에는 `refinanceAmount`(생략 시 null)와 `refinanceAmountMode`(`FULL_BALANCE`/`EXPLICIT`)가 저장된다. 실제 선택된 상환방식은 `after.details.resolvedRepaymentType`에만 있다.

### 9.2 상품 옵션 선택 (`ProductOptionSelector`)

1. 예금은 `DEPOSIT` + `saving_type = FIXED_DEPOSIT`, 적금은 `SAVINGS` 전부. 다른 예금 유형·null은 `SIMULATION_PRODUCT_NOT_SUPPORTED`. 대출은 `LOAN` + `loan_type`이 533 비교군(`LoanComparisonPolicy`: CREDIT/CREDIT_MID/MORTGAGE/JEONSE/AUTO)에 속해야 한다. 한도대출·마이너스통장·자격 한정 상품은 지원하지 않는다.
2. 예적금: `option.term == term`이고 `option.rate`가 null·음수가 아닌 옵션. 없으면 `OPTION_UNAVAILABLE`. 여럿이면 `rate` 오름차순·`optionId` 오름차순으로 첫 옵션(`MULTIPLE_OPTIONS_LOWEST_RATE_SELECTED`). 우대금리(`preferred_rate`)는 쓰지 않는다(`BASE_RATE_WITHOUT_PREFERENTIAL`). 가입 자격·특별조건은 판정하지 않는다.
3. 대출 처리 순서: 기간(신규 `term`, 대환 **잔여 회차 수**) → 명시 상환방식과 대응하는 옵션 코드(`EQUAL_PAYMENT`↔`EQUAL_PRINCIPAL_INTEREST`/`AMORTIZING`, `EQUAL_PRINCIPAL`↔`EQUAL_PRINCIPAL`/`AMORTIZING`, `BULLET`↔`BULLET`; AUTO면 대응이 있는 코드) → 유효 금리(`minRate` non-null·0 이상, `maxRate`가 있으면 `minRate ≤ maxRate`)·유효 한도 자료(옵션 한도 0 이하는 그 옵션 오류, 상품 `max_limit` 0 이하는 전체 오류) → 여기서 후보가 없으면 `OPTION_UNAVAILABLE` → 요청 금액(신규 `principal`, 대환할 신규분 A) ≤ 유효 한도(양수 옵션 한도·상품 한도 중 작은 값, 둘 다 없으면 검증 생략) → 모두 탈락하면 `LIMIT_EXCEEDED` → AUTO면 남은 후보 코드에 대응하는 Loan 방식의 합집합이 한 종류일 때만 확정, 아니면 `REPAYMENT_TYPE_REQUIRED` → 모호하지 않은 코드 우선, `minRate` 오름차순, `optionId` 오름차순으로 선택.
4. 적용 금리는 예적금 `option.rate`, 대출 `option.minRate`(**공시 최저금리 시나리오**, `LOAN_RATE_PUBLISHED_MINIMUM`; 승인 가능 여부·예상 승인금리가 아니다). 0%는 유효하고 상품 최상위 금리로 누락을 보충하지 않는다. `rate_type`이 `FIXED`가 아니면 `VARIABLE_RATE_HELD_CONSTANT`, 한도 자료가 없으면 `PRODUCT_LIMIT_UNKNOWN`, AMORTIZING을 해석했으면 `REPAYMENT_CODE_AMBIGUOUS_MAPPED`.

### 9.3 계산식

- **예금**: 기준일 일시 예치 `principal = monthlyAmount`, 만기 `기준일 + term개월`, `grossInterest = floor(principal × rate × days / 36500)`, `days = [가입일, 만기일)` 실제 일수(윤년 366일). 예: 2026-01-01, 1,000,000, 12개월, 3.65% → 36,500.
- **적금**: `i = 0..term−1`에 `기준일 + i개월` 납입(만기일 납입 없음), `grossInterest = floor(Σ(monthlyAmount × 예치일수) × rate / 36500)`. 회차별 절사 없이 전체 합산 후 절사(537과 같은 식). 예: 2026-01-01, 100,000 × 2개월, 3.65% → 59 + 28일, 870. 자유적립형도 매월 같은 금액 납입 가정(`FIXED_MONTHLY_INSTALLMENT`). 단리·ACT/365는 서비스 추정 모형이다(`SIMPLE_INTEREST_ACT_365`, ERD에 복리 주기·지급 방식 컬럼이 없다).
- **대출 공통**: 기존 `LoanRepaymentScheduleCalculator` — 월이율 = 연이율/1200, 회차 이자 = 직전 잔액 × 월이율 HALF_UP, 원금균등/원리금균등/만기일시, 마지막 회차 잔액 보정. 일할 계산 없음(`MONTHLY_RATE_NOT_DAILY_PRORATED`). 비교 이자는 기준일부터 만기까지의 회차 이자 합이며 `interestSaving = beforeInterest − afterInterest`(양수가 절감). 신규 대출 계산은 최대 360회, 보유 대출 잔여 회차가 360을 넘으면 `PERIOD_NOT_SUPPORTED`. 합계·차이는 `Math.*Exact`로 검증하며 long을 넘으면 `AMOUNT_OUT_OF_RANGE`.
- **신규 대출**: 첫 회차 `기준일 + 1개월`, 만기 `기준일 + term개월`, 약정일은 기준일의 일자(31일은 말일 보정 뒤 다음 달 복원: 1/31 → 2/28 → 3/31).
- **추가상환**(`MATURITY_KEPT_PAYMENT_RECALCULATED`): 대출은 ACTIVE·잔액 양수·금리·만기·다음 상환일 필수, 다음 상환일이 기준일 이전이거나 만기 이후면 `LOAN_DATA_UNAVAILABLE`. `paymentDate`는 기준일 이상·만기 이하. 원래 일정에서 `paymentDate` 이하 회차는 전후 공통(같은 날짜면 약정상환 먼저), 그 시점 잔액 P에 대해 `0 < A ≤ P`(아니면 `AMOUNT_EXCEEDS_BALANCE`; 만기 당일은 P = 0이라 항상 초과), `P − A`로 이후 회차 날짜·만기를 유지해 재계산(원리금균등은 월 상환액 재계산, 전액이면 이후 0). 월중 경과이자는 정산하지 않는다(`NO_ACCRUED_INTEREST_SETTLEMENT`). 예: 잔액 1,200,000·12%·BULLET·2/1·3/1, 1/1에 200,000 → 이자 24,000 → 20,000; 2/1에 200,000 → 2/1 이자 12,000 먼저, 3/1 10,000 → 22,000.
- **대환**(`REMAINING_SCHEDULE_AS_TERM`): 위 조건에 더해 다음 상환일이 기준일 당일이면 거부. 기존 대출 연결 상품의 `loan_type` 비교군과 대상 상품 비교군이 같아야 한다(연결 상품 없음 → `PRODUCT_NOT_SUPPORTED`). 옵션은 잔여 회차 수 n = `option.term`, 기존 상환방식 대응 코드(533의 기간 밴드 후보는 비교 가능해도 여기서는 실행 불가). A = P면 신규 금리 일정만, A < P면 기존 금리 `P − A`와 신규 금리 `A`를 같은 날짜·만기·방식으로 따로 계산해 회차별 합산(`PARTIAL_REFINANCE_SPLIT_SCHEDULES`, 회차 반올림으로 1원 차이 가능). 총 원금 P 보존, 대환 유입 A와 상환 A는 순현금 0. 예: 600,000을 6%로 → 기존분 12,000 + 신규분 6,000 = 18,000.

### 9.4 현금흐름 (`cashflow`)

- `basis = "SCENARIO_NET_CASH_CHANGE"`, `before`·`after` = null(사용자 전체 가용잔액을 산출하지 않았다는 뜻). spending/saving의 `MONTHLY_AVAILABLE_FUNDS` 계약은 그대로다.
- `currentMonth { yearMonth, delta }`: 기준일부터 당월 말까지의 변화. `months[]`는 다음 달부터 만기 월까지(예적금 term개월, 대출은 만기 월까지) **최대 120개월**이며 `cumulative`는 `currentMonth.delta`부터 누적한다.
- 날짜별 사건을 한 번씩만 월에 합산한다: 예금 −원금/+만기 수령액, 적금 −회차 납입/+만기 수령액, 신규 대출 +차입/−회차 상환, 추가상환 −추가상환액 + (기존 회차 상환 − 변경 회차 상환), 대환 기존 회차 상환 − 변경 회차 상환.
- 120개월에서 잘리면 `truncated = true`, `coveredThrough`(마지막 yearMonth), `fullTermEndDate`(만기)를 표시하고 `CASHFLOW_TABLE_TRUNCATED`를 남긴다. 누적액은 표시 기간 합계이며 만기 합계가 아니다. 계산 자체(이자 총액)는 잘리지 않는다.

### 9.5 비교표 (`comparison`)

항목은 기존 `{ key, label, before, after, delta }`다. 시점은 `after.details.comparisonTiming[] { key, effectiveDate, basis(INITIAL_STATE | ACTION_EVENT | PERIOD_TOTAL), fromDate?, toDate? }`에 있다.

| 섹션 | 예적금 | 신규 대출 | 추가상환 | 대환 |
| --- | --- | --- | --- | --- |
| `items` | `PRODUCT_PRINCIPAL`, `GROSS_INTEREST`, `GROSS_PAYOUT`, `MONTHLY_INSTALLMENT` | `LOAN_PRINCIPAL`, `INTEREST_TOTAL`, `REPAYMENT_TOTAL`, `FIRST_MONTHLY_REPAYMENT`, `MONTHLY_REPAYMENT_yyyy_MM`(≤120) | `PREPAYMENT_AMOUNT`, `BALANCE_AT_PAYMENT_DATE`, `INTEREST_TOTAL`, `INTEREST_SAVING`, `REPAYMENT_TOTAL`, `MONTHLY_REPAYMENT_yyyy_MM` | `REFINANCE_AMOUNT`, `LOAN_BALANCE`, `INTEREST_TOTAL`, `INTEREST_SAVING`, `REPAYMENT_TOTAL`, `NEXT_REPAYMENT`, `MONTHLY_REPAYMENT_yyyy_MM`, `GOAL_<id>_MONTHLY_SAVING`, `GOAL_<id>_EXPECTED_DATE` |
| `assets` | `INITIAL_CASH_CHANGE`(−가입액), `INITIAL_PRODUCT_PRINCIPAL_CHANGE`(+가입액), `INITIAL_NET_ASSET_CHANGE`(0), `MATURITY_INTEREST_GAIN` | `INITIAL_CASH_CHANGE`(+원금), `INITIAL_NET_ASSET_CHANGE`(0) | `INITIAL_CASH_CHANGE`(당일이면 −A, 미래면 0), `INITIAL_NET_ASSET_CHANGE`(0), `PREPAYMENT_CASH_CHANGE`(−A, effectiveDate = paymentDate) | `INITIAL_CASH_CHANGE`(0), `INITIAL_NET_ASSET_CHANGE`(0) |
| `debts` | — | `INITIAL_DEBT_CHANGE`(+원금) | `INITIAL_DEBT_CHANGE`(당일이면 −A, 미래면 0), `PREPAYMENT_DEBT_CHANGE`(−A) | `INITIAL_DEBT_CHANGE`(0, 총 원금 보존) |
| `costs` | — | `INTEREST_TOTAL` | `INTEREST_TOTAL`(before → after) | `INTEREST_TOTAL`(before → after) |

`INITIAL_*`는 기준일 상태, `PREPAYMENT_*`는 행동일 사건이다. 당일 추가상환이면 둘은 같은 사건의 두 표시이므로 합산하지 않는다. 비교 항목은 집계 원장이 아니다.

### 9.6 목표 영향

- `before`/`after` 스냅샷의 `monthlyIncome/monthlySpending/monthlySaving/monthlyAvailableFunds`는 **참고 기준선**으로 전후 동일하다(대출 납입액을 지출에 끼워 넣지 않는다). 목표별 변화는 `after.goals[].expectedAchievementDate`·`goalImpacts[]`에 있다.
- `goalImpacts[]`에 `status`(`COMPUTED` | `NOT_COMPUTABLE`)와 `reasons[]`가 추가된다(`after.goalImpactDetails`에 저장, `SimulationGoalImpactResponseAssembler`가 결합). 목표가 없으면 `[]`.
- 예적금 가입·신규 대출·추가상환: 자금 출처·목표 귀속이 입력에 없어 `afterForecastDate`·`differenceDays` = null, `NOT_COMPUTABLE`, 사유 `FUNDING_SOURCE_UNKNOWN`(예적금·추가상환) / `GOAL_ALLOCATION_UNKNOWN`(신규 대출). 이미 달성한 목표는 전후 기준일·`ALREADY_ACHIEVED`.
- 대환(`REPAYMENT_DELTA_ALLOCATED_TO_GOALS`, `GoalDateProjection`): 각 약정 상환일의 (기존 상환 − 변경 상환)을 기준일 미달성 목표에 남은 금액 비례(goalId 순, 마지막이 나머지, `SPLIT_BY_REMAINING`)로 배분하고, 투영일 `t(k) = 기준일 + k개월`마다 구간 `(t(k−1), t(k)]`에 발생한 배분액을 `max(0, 기존 속도 + 구간 합)`으로 반영한다(`GOAL_EFFECT_AT_NEXT_PROJECTION_DATE`). 남은 금액이 처음 0 이하가 되는 t(k)가 `afterForecastDate`. 마지막 사건 뒤에는 기존 속도로 이어 계산한다. 변화가 모두 0이면 기존 날짜를 그대로 돌려준다(`differenceDays = 0`). 속도 0이고 절감으로도 미달성이면 null + `SAVING_PACE_ZERO`. 예: 1/14 기준 2/28 절감은 3/14에 반영(2/14로 소급하지 않음).
- 목표 엔티티·잔액·이력은 변경하지 않는다.

### 9.7 응답 예시 (H2 테스트 fixture, 2026-01-01 기준)

`POST /simulations/loan/prepayment` `{ "accountNo": "…", "prepaymentAmount": 200000 }` → 201. 테스트 fixture로 실제 실행한 응답에서 `simulationId`·`status`·`createdAt`·`completedAt`·`scenarios`·`summary`만 뺐다. 값은 `SimulationProductLoanApiTest`가 고정한다.

```json
{
  "simulation": {
    "type": "LOAN_PREPAYMENT",
    "title": "신용대출 200,000원 추가상환 (2026-01-01)",
    "request": { "loanId": 1, "prepaymentAmount": 200000, "paymentDate": "2026-01-01", "calculationVersion": "v2-saving-loan-product" },
    "before": { "baseDate": "2026-01-01", "monthlyIncome": 3000000, "monthlySpending": 0, "monthlySaving": 333333, "monthlyAvailableFunds": 2666667,
                "goals": [{ "goalId": 1, "name": "비상금", "remaining": 2000000, "monthlySaving": 333333, "expectedAchievementDate": "2026-08-01" }],
                "coverage": ["SPENDING_NO_TRANSACTIONS"] },
    "after": {
      "goals": [{ "goalId": 1, "name": "비상금", "remaining": 2000000, "monthlySaving": 333333, "expectedAchievementDate": null }],
      "assumptions": ["LOAN_LOCAL_SNAPSHOT", "MATURITY_KEPT_PAYMENT_RECALCULATED", "NO_ACCRUED_INTEREST_SETTLEMENT", "MONTHLY_RATE_NOT_DAILY_PRORATED", "EXCLUDES_TAX_AND_FEES"],
      "details": {
        "dataSource": "LOAN_LOCAL_SNAPSHOT", "calculationAsOfDate": "2026-01-01", "loanId": 1,
        "loan": { "loanId": 1, "accountName": "신용대출", "institutionName": "테스트은행", "loanType": "CREDIT", "repaymentType": "BULLET", "interestRate": 12,
                  "balance": 1200000, "repaymentDay": 1, "nextRepaymentDate": "2026-02-01", "maturityDate": "2026-03-01", "financialProductId": 4, "productLoanType": "PERSONAL_CREDIT" },
        "paymentDate": "2026-01-01", "prepaymentAmount": 200000, "balanceAtPaymentDate": 1200000, "balanceAfterPrepayment": 1000000,
        "installmentsBeforePaymentDate": 0, "installmentsRecalculated": 2, "beforeInterest": 24000, "afterInterest": 20000, "interestSaving": 4000,
        "nextRepaymentBefore": 12000, "nextRepaymentAfter": 10000,
        "comparisonTiming": [
          { "key": "INITIAL_CASH_CHANGE", "effectiveDate": "2026-01-01", "basis": "INITIAL_STATE" },
          { "key": "INITIAL_DEBT_CHANGE", "effectiveDate": "2026-01-01", "basis": "INITIAL_STATE" },
          { "key": "INITIAL_NET_ASSET_CHANGE", "effectiveDate": "2026-01-01", "basis": "INITIAL_STATE" },
          { "key": "PREPAYMENT_CASH_CHANGE", "effectiveDate": "2026-01-01", "basis": "ACTION_EVENT" },
          { "key": "PREPAYMENT_DEBT_CHANGE", "effectiveDate": "2026-01-01", "basis": "ACTION_EVENT" },
          { "key": "INTEREST_TOTAL", "effectiveDate": "2026-01-01", "basis": "PERIOD_TOTAL", "fromDate": "2026-01-01", "toDate": "2026-03-01" }
        ]
      },
      "goalImpactDetails": [{ "goalId": 1, "status": "NOT_COMPUTABLE", "reasons": ["FUNDING_SOURCE_UNKNOWN"] }]
    },
    "comparison": {
      "items": [
        { "key": "PREPAYMENT_AMOUNT", "label": "추가상환액", "before": 0, "after": 200000, "delta": 200000 },
        { "key": "BALANCE_AT_PAYMENT_DATE", "label": "2026-01-01 상환 후 잔액", "before": 1200000, "after": 1000000, "delta": -200000 },
        { "key": "INTEREST_TOTAL", "label": "만기까지 총 이자", "before": 24000, "after": 20000, "delta": -4000 },
        { "key": "INTEREST_SAVING", "label": "이자 절감액(세전, 수수료 제외)", "before": 0, "after": 4000, "delta": 4000 },
        { "key": "REPAYMENT_TOTAL", "label": "만기까지 총 상환액(추가상환 제외)", "before": 1224000, "after": 1020000, "delta": -204000 },
        { "key": "MONTHLY_REPAYMENT_2026_02", "label": "2026-02 월 상환액", "before": 12000, "after": 10000, "delta": -2000 },
        { "key": "MONTHLY_REPAYMENT_2026_03", "label": "2026-03 월 상환액", "before": 1212000, "after": 1010000, "delta": -202000 }
      ],
      "assets": [
        { "key": "INITIAL_CASH_CHANGE", "label": "기준일 현금 변화", "before": 0, "after": -200000, "delta": -200000 },
        { "key": "INITIAL_NET_ASSET_CHANGE", "label": "기준일 순자산 변화", "before": 0, "after": 0, "delta": 0 },
        { "key": "PREPAYMENT_CASH_CHANGE", "label": "추가상환일 현금 변화", "before": 0, "after": -200000, "delta": -200000 }
      ],
      "debts": [
        { "key": "INITIAL_DEBT_CHANGE", "label": "기준일 부채 변화", "before": 0, "after": -200000, "delta": -200000 },
        { "key": "PREPAYMENT_DEBT_CHANGE", "label": "추가상환일 부채 변화", "before": 0, "after": -200000, "delta": -200000 }
      ],
      "costs": [{ "key": "INTEREST_TOTAL", "label": "만기까지 총 이자", "before": 24000, "after": 20000, "delta": -4000 }]
    },
    "cashflow": {
      "basis": "SCENARIO_NET_CASH_CHANGE", "before": null, "after": null,
      "months": [{ "yearMonth": "2026-02", "delta": 2000, "cumulative": -198000 }, { "yearMonth": "2026-03", "delta": 202000, "cumulative": 4000 }],
      "currentMonth": { "yearMonth": "2026-01", "delta": -200000 }, "truncated": false, "coveredThrough": "2026-03", "fullTermEndDate": "2026-03-01"
    },
    "goalImpacts": [{ "goalId": 1, "beforeForecastDate": "2026-08-01", "afterForecastDate": null, "differenceDays": null, "status": "NOT_COMPUTABLE", "reasons": ["FUNDING_SOURCE_UNKNOWN"] }]
  },
  "created": true
}
```

같은 fixture의 다른 유형(테스트가 고정한 값):

- 대환(전액 1,200,000 → 6% BULLET 2회): `details.beforeInterest 24000 → afterInterest 12000`, `cashflow.months` 2/1·3/1 각 +6,000, `goalImpacts[0]` `2026-08-01 → 2026-07-01`, `differenceDays −31`, `status COMPUTED`, `reasons ["REPAYMENT_DELTA_ALLOCATED_TO_GOALS", "GOAL_EFFECT_AT_NEXT_PROJECTION_DATE"]`.
- 예금(1,000,000·12개월·3.65%): `details.grossInterest 36500`, `grossPayout 1036500`, `assets` 4건(`INITIAL_CASH_CHANGE −1000000` …), `cashflow.currentMonth.delta −1000000`, `months[11].delta 1036500`.
- 신규 대출(1,200,000·12개월·0%, AUTO → `EQUAL_PRINCIPAL`): `details.firstMonthlyRepayment 100000`, `afterInterest 0`, `debts[0].after 1200000`, `cashflow.currentMonth.delta 1200000`, `months[11].cumulative 0`, 목표 `GOAL_ALLOCATION_UNKNOWN`.

### 9.8 FE 인계와 제한

- 모든 금액은 세전·수수료 제외 추정치다. "절감액"을 확정 비용 절감으로 표시하지 않는다. 대출 금리는 공시 최저금리 시나리오이며 승인 여부·실제 금리가 아니다.
- 신규 대출 `repaymentType` 생략/null(AUTO)과 명시, 대환 `refinanceAmount` 생략(FULL_BALANCE)과 잔액 금액 명시는 결과가 같아도 별개 요청(별개 저장)이다. AUTO로 실행한 뒤 같은 날 상품 옵션이 바뀌어도 AUTO 재요청은 첫 결과를 돌려준다.
- `goalImpacts[].status`가 `NOT_COMPUTABLE`이면 `afterForecastDate`·`differenceDays`는 null이며 `reasons`로 이유를 표시한다. spending/saving과 과거 결과에는 `status`·`reasons` 키가 없다.
- `cashflow.months`는 최대 120개월이며 `truncated`·`fullTermEndDate`를 확인해야 한다. 대출 `comparison.items`의 월 상환 항목도 120개월까지다.
- 대환 대상은 533 후보라도 잔여 회차 수와 정확히 같은 `term` 옵션이 없으면 `SIMULATION_PRODUCT_OPTION_UNAVAILABLE`이다. FE는 후보 → 대환 실행이 실패할 수 있음을 안내한다. 기간 밴드 금리 차용은 별도 정책 없이는 채택하지 않았다.
- 대출 자료는 로컬 `loan` 스냅샷이다. 다음 상환일이 지난 오래된 스냅샷은 `SIMULATION_LOAN_DATA_UNAVAILABLE`이며 금융 데이터 동기화 후 다시 실행해야 한다. 시연 seed(523)의 ACTIVE 대출은 `next_repayment_date`가 있다.
- `LoanComparisonPolicy`(loan 도메인)를 public으로 열어 비교군·상환 코드 대응표를 재사용했다. 표 자체는 바꾸지 않았다.
- 미검증: MySQL(V19 JSON·복합 FK·실제 seed 조회, `*_MYSQL_TEST_URL` 방식 미추가), 실제 seed 상품 옵션의 데이터 품질(`rate_type`·`repayment_type` 분포는 seed SQL 기준으로만 확인), 동시 요청 직렬화(사용자 행 잠금은 550 계약 재사용).

## 10. 할부·리볼빙·현금서비스·복합 실행 API (586)

기준: [simulation-586-plan.md](simulation-586-plan.md). 명세 17이 정한 경로·입력 외의 계산 정책은 서비스 보완안이며 실제 카드 약정이나 법정 상한을 뜻하지 않는다. 가정 코드는 `SimulationAssumptions`에 있다.

### 10.1 공통

- 인증·201/200 재사용·기준일·지문 규칙은 §3·§9.1과 같다. 계산 버전 `v3-card-combined`.
- **입력 출처**: 금리·수수료율·결제비율·잔액은 모두 사용자 입력이다(`details.inputSource = USER_INPUT`, `CARD_TERMS_USER_INPUT`). 리볼빙 `cardId`는 소유권·식별에만 쓰고 카드 DB 값으로 입력을 덮어쓰지 않는다. 카드번호는 받지도 저장하지도 않는다. 기준선(소득·지출·목표)은 §3과 같은 로컬 DB다.
- **연율·비율**: `BigDecimal`, 소수 4자리까지, 0~100(결제비율은 0 초과). 지문·저장에는 scale 4로 정규화한다 — `12`와 `12.0000`은 같은 요청이다.
- **정수 필드**: 신규 4종의 금액·개월 수 필드는 전용 역직렬화로 소수(`1200000.5`, `2.5`)를 절사하지 않고 400 `INVALID_REQUEST`로 거부한다. 기존 6종의 전역 동작(`ACCEPT_FLOAT_AS_INT`)은 바꾸지 않았다.
- **이자식**: `회차 시작 잔액 × 연율 / 100 × 실제 경과일수 / 365`(ACT/365, 윤년도 분모 365), 회차마다 원 단위 HALF_UP, 합계는 확정 회차 금액의 합(`SIMPLE_INTEREST_ACT_365`). 회차일은 `기준일.plusMonths(k)`로 매번 최초 날짜에서 구한다(1/31 → 2/28 → 3/31). 세금·연체료·추가 이용·금리 변동·기관별 최소결제액은 반영하지 않는다(`NO_LATE_FEES_OR_ADDITIONAL_USAGE`).
- **목표 영향**: 할부·리볼빙·현금서비스는 자금 귀속을 알 수 없어 미달성 목표는 `NOT_COMPUTABLE` + `GOAL_ALLOCATION_UNKNOWN`, 이미 달성한 목표는 기준일·`ALREADY_ACHIEVED`(§9.6과 같은 계약).
- **공개 상환 일정** `after.details.repaymentSchedule[]`: 날짜 오름차순 `{date, openingPrincipal, principalPayment, interest, fee, payment, closingPrincipal}`. 모든 회차에서 `payment = principalPayment + interest + fee`, `closingPrincipal = openingPrincipal − principalPayment`이며 해당 없는 비용은 0이다(할부는 `fee`, 리볼빙·현금서비스는 `interest`).

| 경로 | Body(명세 17) | 검증 | fingerprint | 기본값 |
| --- | --- | --- | --- | --- |
| `POST /simulations/installment` | `purchaseAmount, months, feeRate?` | 금액 양수 정수, `months` 1~120, `feeRate` 0~100 | `purchaseAmount\|months\|feeRate(scale 4)\|feeRateMode` | `feeRate` 생략/null = 0%·`feeRateMode = ASSUMED_ZERO` + `ZERO_FEE_ASSUMED`. 명시(0 포함) = `EXPLICIT`. **생략과 명시적 0은 별개 지문** |
| `POST /simulations/revolving` | `cardId?, balance, paymentRatio, rate` | `cardId` 양수·본인 카드(없거나 타인 404), 잔액 양수, `paymentRatio` 0 초과 100 이하, `rate` 0~100 | `cardId\|balance\|paymentRatio\|rate` | 없음 |
| `POST /simulations/cash-advance` | `amount, rate, repaymentMonths?` | 금액 양수, `rate` 0~100, `repaymentMonths` 1~120 | `amount\|rate\|repaymentMonths` | `repaymentMonths` 생략/null = 1. **생략·null·1은 같은 지문** |
| `POST /simulations/combined` | `scenarios[]` | 2~10개, 각 원소 `{type, parameters}` | 자식 `type:fingerprint`를 순서대로 길이 접두 인코딩 | 자식 기본값은 단독 API와 같다 |

### 10.2 계산식

- **할부**(`EQUAL_PRINCIPAL_FEE_ON_BALANCE_ACT_365`): 전 = 기준일 `purchaseAmount` 전액 결제. 후 = 기준일 결제 없음, 회차 k(1..n)에 원금 `floor(P/n)`(마지막 회차 잔액 전액, 원금 < n이면 앞 회차 0원 허용)과 회차 시작 잔액 × 직전 회차일부터의 일수 수수료. 현금 delta 기준일 +P, 회차 −(원금+수수료), 합 = −총수수료. 기준일 부채 +P, 순자산 0(구매 물품 가치는 전후 공통). 예: 2026-09-15, 1,200,000/2개월/12% → 10/15 원금 600,000·수수료 11,836(30일), 11/15 600,000·6,115(31일), 합 17,951.
- **리볼빙**(`PRINCIPAL_RATIO_WITH_INTEREST_PAID_SEPARATELY`): `balance`는 기준일 결제 대상 카드대금. 전 = 전액 결제. 후 = 기준일 원금 `min(B, max(1, ceil(B × p/100)))` 결제, 이월 잔액에 회차마다 시작 잔액 × 일수 이자(전액 납부, 이자에 이자 없음)와 같은 비율식 원금. 최대 120회차까지 계산하며 남으면 강제 상환하지 않고 `paidOff=false, remainingBalance, evaluatedThrough, terminationReason=HORIZON_LIMIT`, `REVOLVING_HORIZON_LIMIT`, `cashflow.fullTermEndDate` 생략(null). `terminationReason`은 `PAID_OFF | HORIZON_LIMIT | NO_CARRY_OVER`(결제비율 100%). 현금 delta 기준일 +이월액, 회차 −(원금+이자), 계산 기간 합 = 잔액 − 누적이자(미상환 잔액을 이익으로 보지 않는다). 예: 1,000,000/50%/12% → 기준일 500,000, 10/15 원금 250,000·이자 4,932·잔액 250,000.
- **현금서비스**(`BULLET_SIMPLE_INTEREST_ACT_365`): 전 = 미이용. 후 = 기준일 +A, `repaymentMonths`개월 뒤 만기에 원금 + 기준일~만기 단리 이자 일시 상환. 합 = −이자, 순자산 0. 기본 1개월·1개월 초과 모두 사용자 지정 상환기간의 추정이다. 예: 1,000,000/12%/1개월(9/15→10/15, 30일) → 9,863.
- 합·차·누적은 `Math.*Exact`이며 long을 넘으면 `SIMULATION_AMOUNT_OUT_OF_RANGE`.

### 10.3 비교표·현금흐름(단독 3종)

| 섹션 | 할부 | 리볼빙 | 현금서비스 |
| --- | --- | --- | --- |
| `items` | `INITIAL_PAYMENT`(P→0), `INSTALLMENT_FEE_TOTAL`, `PAYMENT_TOTAL`(P→P+수수료), `FIRST_INSTALLMENT_PAYMENT`, `MONTHLY_PAYMENT_yyyy_MM` | `INITIAL_PAYMENT`(B→최초 결제), `CARRIED_BALANCE`, `REVOLVING_INTEREST_TOTAL`, `PAYMENT_TOTAL`, `REMAINING_BALANCE`, `PAYOFF_DATE`(날짜, 미완납 null), `MONTHLY_PAYMENT_yyyy_MM` | `CASH_ADVANCE_AMOUNT`, `INTEREST_TOTAL`, `REPAYMENT_TOTAL`, `REPAYMENT_DATE`(날짜), `MONTHLY_PAYMENT_yyyy_MM` |
| `assets` | `INITIAL_CASH_CHANGE`(+P), `INITIAL_NET_ASSET_CHANGE`(0) | `INITIAL_CASH_CHANGE`(+이월액), `INITIAL_NET_ASSET_CHANGE`(0) | `INITIAL_CASH_CHANGE`(+A), `INITIAL_NET_ASSET_CHANGE`(0) |
| `debts` | `INITIAL_DEBT_CHANGE`(+P) | `INITIAL_DEBT_CHANGE`(+이월액) | `INITIAL_DEBT_CHANGE`(+A) |
| `costs` | `INSTALLMENT_FEE_TOTAL` | `REVOLVING_INTEREST_TOTAL`(미완납이면 계산 기간 합) | `INTEREST_TOTAL` |

`cashflow`는 §9.4와 같은 `SCENARIO_NET_CASH_CHANGE`·`currentMonth`·최대 120개월이다. `details`에는 `calculationPolicy, inputSource, calculationAsOfDate, paidOff, evaluatedThrough, totalPrincipalPaid, repaymentSchedule, comparisonTiming`과 유형별 입력·합계(할부 `feeRate/feeRateMode/totalFee/totalPayment`, 리볼빙 `cardId/initialPayment/carriedBalance/remainingBalance/terminationReason/totalInterest`, 현금서비스 `repaymentDate/days/interest/totalRepayment`)가 있다.

### 10.4 복합 (`POST /simulations/combined`)

요청은 평면 목록이며 `parameters`는 해당 단독 API 본문과 같다(계좌번호는 같은 해시 조회, 저장에는 `accountId`/`loanId`만).

```json
{ "scenarios": [
  { "type": "SAVING", "parameters": { "accountNo": "…", "monthlySavingAmount": 500000, "durationMonths": 2 } },
  { "type": "INSTALLMENT", "parameters": { "purchaseAmount": 1200000, "months": 2, "feeRate": 12 } },
  { "type": "LOAN_REFINANCE", "parameters": { "accountNo": "…", "targetProductId": 3 } } ] }
```

- **구조**: `type`은 COMBINED 제외 9종(대소문자 무시). 원소에 `type`·`parameters` 외 필드, `parameters`에 DTO가 모르는 필드, 자식 DTO 검증 위반은 400 `INVALID_REQUEST`이며 메시지는 `scenarios[i].parameters.<field>: <사유>`다(입력 값 미포함). 개수 위반은 `VALIDATION_ERROR`. 중첩·참조·의존 필드는 구조적으로 불가능하다.
- **순서**: 모든 자식의 자원 존재·소유권(404) → 중복·충돌 검사(400) → 잠금·지문 → 신규 계산이면 자식별 스냅샷 준비 → 자식 독립 계산 → 합성 → 부모 1행 + 자식 N행(`sequenceNo` = 입력 순서) 저장. 자식 하나라도 실패하면 아무 행도 남지 않는다. 입력 순서는 지문에 들어가므로 순서를 바꾸면 별개 요청이지만 합계는 같다.
- **중복·충돌**(§6 코드 표): 자식은 같은 기준선에서 독립 계산되므로 같은 대상을 두 자식이 바꾸는 조합은 거부한다. SPENDING+SAVING은 허용하되 SAVING은 변경 후 절대 저축액이라 SPENDING 절감액을 목표에 다시 배분하지 않는다. 카테고리별 감소액 합이 전체 평균 지출을 넘으면 `SIMULATION_REDUCTION_EXCEEDS_SPENDING`.
- **현금흐름**: 자식의 `months[].delta`와 `currentMonth.delta`를 월별로 합한 뒤 누적을 처음부터 재계산한다. 종료 월은 자식 종료 중 최댓값(≤120), `truncated`는 자식 중 하나라도 잘리면 true, `fullTermEndDate`는 자식 종료일 최댓값이며 미완납 리볼빙이 있으면 생략(null)하고 `details.projectionIncomplete=true`·`cashflowEvaluatedThrough`를 둔다.
- **금융비용**(`FINANCE_COST_EVALUATED`): 평가기간 `[기준일, min(자식 계산 종료일 최댓값, 기준일+120개월이 속한 달 말일)]`에 **납부일이 포함된** 이자+수수료를 자식별 전후로 합산한 `before/after/delta`(`details.evaluationFromDate/evaluationThroughDate`, `costBasis=PAYMENT_DATE`). 원금·상품 이자 수익은 넣지 않고 기간 밖 납부액을 안분하지 않는다. 대환·추가상환 절감은 음수 delta. 미완납 자식의 자료가 평가 종료일 전에 끝나면 `costComplete=false`, `FINANCE_COST_PARTIAL`, `details.financeCost.children[].costCoveredThrough/costComplete/reason`. 자식의 만기 전체 비용은 `details.children[].comparison`에 그대로 있다.
- **비교표**: `items/assets/debts/costs`는 단위·기간·의미가 같은 집계만 — `INITIAL_CASH_CHANGE`, `INITIAL_DEBT_CHANGE`, `INITIAL_NET_ASSET_CHANGE`(자식 `INITIAL_*` 합), `FINANCE_COST_EVALUATED`, 목표 항목 `GOAL_<id>_*`. 자식별 항목은 `after.details.children[] { sequenceNo, type, title, summary, fullTermEndDate, costCoveredThrough, costComplete, assumptions, comparison, cashflow, goalImpacts, details }`.
- **목표 영향**(`details.goalEffectPolicy`):
  - `DURATION_LIMITED_PROJECTION` — 자식이 SPENDING/SAVING뿐일 때. `t(k) = 기준일 + k개월`마다 유효한 자식만 반영한다: SAVING 유효 구간은 그 자식의 대상 목표 선택·절대액 배분으로 목표별 속도를 **교체**하고 SPENDING 절감액은 배분하지 않는다(`SAVING_AMOUNT_IS_FINAL_TARGET`); 그 외 구간은 유효한 SPENDING 절감액 합을 기준일 미달성 목표의 remaining 비중(goalId 순, 마지막 나머지)으로 배분해 기준선 속도에 더한다. 마지막 자식 종료 후 기준선 속도로 복귀하며 미달성이면 필요한 개월 수를 올림해 잇는다(`GOAL_EFFECT_LIMITED_TO_SCENARIO_DURATION`). 기준선 속도 0이면 null + `SAVING_PACE_ZERO`. 예: 잔여 100만원·속도 10만원, 절감 10만원 1개월 → 9개월째(551 단독의 5개월과 다르다). `after.goals[].monthlySaving`은 첫 투영 구간(k=1)의 속도다.
  - `NOT_COMPUTABLE` — 다른 유형이 하나라도 있으면 미달성 목표는 `COMBINED_GOAL_EFFECT_NOT_COMPUTABLE` + 자식의 계산 불가 사유(`FUNDING_SOURCE_UNKNOWN`, `GOAL_ALLOCATION_UNKNOWN` 등, 중복 제거). 대환처럼 단독으로는 날짜를 내는 자식의 결과는 `details.children[].goalImpacts`에만 있다.
  - 두 경우 모두 부모 `after.goals[].expectedAchievementDate`·목표 비교 항목·`goalImpacts[]`는 같은 투영 결과에서 조립하며 자식 날짜를 섞지 않는다. `before/after`의 소득·지출·저축·가용자금 수준값은 참고 기준선으로 전후 동일하다.
- **가정**: 부모 고유 코드(`CHILD_SCENARIOS_INDEPENDENT`, `FINANCE_COST_EVALUATED`, `FINANCE_COST_PARTIAL`?, `CASHFLOW_TABLE_TRUNCATED`?, 목표 코드) 뒤에 자식 가정의 합집합을 알파벳순으로 붙인다 — 순서를 바꾼 요청도 같은 집합이다.
- **저장**: `request_snapshot = { scenarios: [{type, parameters}], requestHash, calculationVersion }`, `simulation_scenario`는 자식별 `(sequenceNo, scenarioType, parameters)`. 정규화된 자식 입력만 저장하며 원문 계좌번호·해시는 어느 깊이에도 없다.

### 10.5 응답 예시 (H2 fixture, 2026-09-15 기준, 저축 500,000×2개월 + 할부 1,200,000/2개월/12% + 대환 1,200,000→6%)

```json
{ "type": "COMBINED", "title": "복합 시나리오 3건: 월 저축액 … + 할부 1,200,000원 · 2개월 12% + 신용대출 1,200,000원 대환 → 신용대출",
  "scenarios": [ { "sequenceNo": 1, "scenarioType": "SAVING", "parameters": { "accountId": 1, "monthlySavingAmount": 500000, "durationMonths": 2 } },
                 { "sequenceNo": 2, "scenarioType": "INSTALLMENT", "parameters": { "purchaseAmount": 1200000, "months": 2, "feeRate": 12.0000, "feeRateMode": "EXPLICIT" } },
                 { "sequenceNo": 3, "scenarioType": "LOAN_REFINANCE", "parameters": { "loanId": 1, "targetProductId": 1, "refinanceAmount": null, "refinanceAmountMode": "FULL_BALANCE" } } ],
  "comparison": { "costs": [ { "key": "FINANCE_COST_EVALUATED", "before": 24000, "after": 29951, "delta": 5951 } ],
                  "debts": [ { "key": "INITIAL_DEBT_CHANGE", "before": 0, "after": 1200000, "delta": 1200000 } ] },
  "cashflow": { "basis": "SCENARIO_NET_CASH_CHANGE", "currentMonth": { "yearMonth": "2026-09", "delta": 1200000 },
                "months": [ { "yearMonth": "2026-10", "delta": -772503, "cumulative": 427497 }, { "yearMonth": "2026-11", "delta": -766782, "cumulative": -339285 } ],
                "truncated": false, "coveredThrough": "2026-11", "fullTermEndDate": "2026-11-15" },
  "after": { "details": { "calculationPolicy": "INDEPENDENT_CHILDREN_NET_CASH_SUM", "scenarioCount": 3, "evaluationFromDate": "2026-09-15",
                          "evaluationThroughDate": "2026-11-15", "costBasis": "PAYMENT_DATE", "costComplete": true, "projectionIncomplete": false,
                          "goalEffectPolicy": "NOT_COMPUTABLE", "financeCost": { "before": 24000, "after": 29951, "delta": 5951, "children": [ "…" ] },
                          "children": [ "…" ] } },
  "goalImpacts": [ { "goalId": 1, "beforeForecastDate": "2027-04-15", "afterForecastDate": null, "differenceDays": null,
                     "status": "NOT_COMPUTABLE", "reasons": ["COMBINED_GOAL_EFFECT_NOT_COMPUTABLE", "GOAL_ALLOCATION_UNKNOWN"] } ] }
```

10월 delta = 저축 −166,667(500,000 − 기준선 333,333) + 할부 −611,836 + 대환 +6,000(12,000 → 6,000). `SimulationCardCombinedApiTest`가 고정한다.

### 10.6 FE 인계와 제한

- 카드 3종은 사용자 입력 조건의 추정치다. 실제 카드사 약정(할부 수수료 산식, 리볼빙 최소결제액, 현금서비스 취급수수료)과 다를 수 있음을 안내한다. `ZERO_FEE_ASSUMED`는 수수료율을 몰라 0으로 계산했다는 뜻이지 무이자 확인이 아니다.
- 리볼빙 `paidOff=false`면 `cashflow.fullTermEndDate`가 없고 이자 합계는 계산 기간(120회차) 합이다. `REMAINING_BALANCE`를 반드시 함께 보여준다.
- 복합의 `goalImpacts`는 소비·저축만 있는 조합에서만 날짜가 나온다. 그 외 조합은 `NOT_COMPUTABLE`이며 자식별 단독 날짜는 `after.details.children[].goalImpacts`에 있다 — 부모 날짜와 다를 수 있고 참고용이다.
- 복합의 `FINANCE_COST_EVALUATED`는 평가기간 비용이며 평생 총비용이 아니다. `costComplete=false`면 부분합이다. 자식별 만기 총이자는 `details.children[].comparison.costs`에서 읽는다.
- 복합 요청은 자식 하나의 400/404가 전체 실패다. `scenarios[i]…` 경로는 자식 구조·파싱·DTO 검증 오류에만 제공한다. 자원 존재·소유권 및 계산 오류는 기존 오류 코드와 메시지를 반환하며 실패한 자식 위치는 제공하지 않는다.
- 복합 계산은 자식 계산·감소액 합산·부모 합성에서 발생한 산술 범위 초과를 모두 `SIMULATION_AMOUNT_OUT_OF_RANGE`로 반환한다.
- 미검증: MySQL(V19 JSON·N행 scenario·CHECK, `*_MYSQL_TEST_URL` 방식 미추가), 동시 요청 직렬화(사용자 행 잠금은 550 계약 재사용), 실제 seed(491·492·493·522) 계정으로의 앱 통합.

## 11. 전체 통합·추천 연결·MySQL 검증 (587)

2026-09-15. 계산식·응답 계약·지문 규칙·`SimulationCalculationVersion`은 바꾸지 않았다. 이 절은 10종 × 공통 조회의 통합 검증 결과와 추천에서
시뮬레이션으로 이어지는 흐름을 기록한다.

### 11.1 추천 → 시뮬레이션 프리셋

명세 16·17에 연결 필드가 없고 ERD에 `simulation`↔`recommendation` FK가 없어 **추천 응답에 실행 제안(`simulation`)을 싣는 방식**으로 연결했다.
DDL·새 엔드포인트 없음. 계약은 [recommendation-api.md](recommendation-api.md) §5.1이 기준이며 요점은 다음과 같다.

| 추천 domain/type | `simulation.type` · `endpoint` | `request` |
| --- | --- | --- |
| SPENDING/REDUCE_SPENDING | SPENDING · `POST /api/v1/simulations/spending` | `{category: null, monthlyReduction: effect.monthlyCostSaving, durationMonths: null}` |
| SAVING/INCREASE_MONTHLY_SAVING | SAVING · `POST /api/v1/simulations/saving` | `{accountNo: null, monthlySavingAmount: R, durationMonths: null}` — R은 근거 `GOAL_SAVING_SHORTFALL` evidence의 `requiredMonthlySaving`(변경 후 절대값이며 `contributionAmount = R − A`가 아니다) |
| CASHFLOW/SECURE_SHORTAGE_BUFFER | `available=false`, `NO_MATCHING_SIMULATION_TYPE` | — |

- FE는 `request`를 그대로(또는 사용자가 고쳐서) `endpoint`에 POST한다. 응답·저장은 기존 계약 그대로이며 시뮬레이션은 추천 id를 저장하지 않는다(재사용 지문과 충돌하므로 결과 추적은 Improvement/Report 이슈의 계약을 따른다).
- 계좌 미지정 저축은 모든 모으기 목표에 남은 금액 비례 배분된다(`TARGET_GOAL_NOT_SELECTABLE_WITHOUT_ACCOUNT`). 특정 목표만 겨냥하려면 명세 17 `saving` 본문에 `goalId?`를 더해야 하며 이는 루트 명세 변경이라 보류했다(simulation-587-plan.md §3.2).
- **일관성**: 진단 기준일 = 시뮬레이션 기준일이고 목표가 하나면 추천 `effect.improvedForecastDate`와 프리셋 실행의 `goalImpacts[0].afterForecastDate`가 같다(같은 `base + ceil(remaining / R)` 식). 기준일이 다르면 그 차이만큼 달라진다 — 추천은 진단 스냅샷 `asOf`, 시뮬레이션은 실행일 기준이다. `RecommendationApiTest.savingRecommendationCarriesPresetEverywhereAndPresetRunMatchesRecommendedForecast`가 고정한다.
  소비 추천 `effect.monthlyCostSaving` vs 프리셋 실행의 `after.monthlyAvailableFunds − before.monthlyAvailableFunds` 대조는 예산 초과 거래 fixture가 필요해 API 테스트로 고정하지 않았다(프리셋 값만 단위 테스트, remaining-work.md).

### 11.2 통합 회귀 (`SimulationIntegrationApiTest` 3건, H2)

10종을 한 fixture(2026-09-15 기준, 소득 3,000,000·직전 3개월 소비 600,000·모으기 목표 1건·카드 1장·신용대출 1건·정기예금/신용대출 상품)에서 실행했다.

- 10종 모두 201·`COMPLETED`·`requestHash` 미노출·계좌/대출번호·해시 부재. 상세·comparison·cashflow·goal-impact가 실행 응답과 JSON 동일, assets/debts/costs 페이지 content가 `comparison.<section>`과 동일하고 `totalElements`가 항목 수와 같다. 같은 날 재요청은 10종 모두 200·같은 id, 다음 날은 10종 모두 신규 201. 목록은 10종 type을 모두 담는다.
- 인증·소유권: 실행 10종 401, 목록·조회 7경로·삭제 401, 타인의 조회 7경로·삭제 404 `RESOURCE_NOT_FOUND`(존재 미노출), 타인 목록 0건. 삭제 후 7경로 404·재삭제 404·목록 제외·같은 요청은 새 id.
- 빈 결과·경계값: 목표·소득·거래·계좌가 없는 사용자는 `cash-advance`가 실행되고 `goalImpacts=[]`·`monthlyIncome=null`·`coverage` 배열, goal-impact `[]`, 범위를 넘긴 섹션 페이지는 빈 content. 목록·섹션 `page=-1`·`size=0`·`size=101` 400, `size=100` 200. `simulationId` 0·음수·비숫자 400, 없는 id 404, `type=UNKNOWN` 400. 자원 참조 유형(대출·카드)은 자료가 없으면 404, 소비 감소는 소비 0이면 400이며 저장하지 않는다.

복합의 순서 교환 불변은 2개 조합(`CombinedSimulationCalculatorTest`)까지만 확인했고 자식 9종 전체 조합은 확인하지 않았다(remaining-work.md).

기존 검증(§7)과 합쳐 Simulation 72건·Recommendation 58건이 H2에서 통과했고 `gradlew clean build` 전체 1,051건 통과(MySQL 조건부 19건 skip).

### 11.3 MySQL 검증 (`SimulationMySqlIntegrationTest` 587 4건 + 617 3건)

MySQL 8.0.46(Docker `mysql:8.0`), 빈 `dodam_587_test` 스키마에 V1~V17을 V6을 V11 뒤로 돌려 수동 적용한 뒤 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true SPRING_FLYWAY_BASELINE_VERSION=17`로
Flyway가 V18·V19·V30~V34를 적용하게 했다(recommendation.md §8과 같은 방식). `ddl-auto: validate` 통과. 2026-09-15 4건 통과.

- 스키마: `flyway_schema_history` V19 성공, `chk_simulation_*` 3종, JSON 컬럼 6개(`simulation` 5 + `simulation_scenario.parameters`), FK 3종(`fk_simulation_scenario_simulation`, `fk_simulation_goal_impact_simulation`, `fk_simulation_goal_impact_goal`).
- 실행·저장: `POST /saving` 후 `JSON_EXTRACT(after_snapshot, '$.monthlySaving')`가 숫자로 읽히고 `requestHash` 64자, `accountNo` 키 부재, scenario 1행, goal_impact 1행. 같은 날 재사용 200(DATETIME 범위 비교), 다음 날 신규, 상세 = 실행 응답, 타인 404, 삭제 후 `deleted_at` 기록·404·같은 요청 신규.
- 복합: scenario 2행(sequence 1·2, `JSON_EXTRACT(parameters, '$.purchaseAmount')`), `details.children` 2개. `chk_simulation_type`·`chk_simulation_status`·`chk_simulation_scenario_sequence_no` 위반과 `uk_simulation_scenario_sequence`·`uk_simulation_goal_impact_simulation_goal` 중복, 타인 user_id의 goal_impact 복합 FK 거부.
- 동시 실행: 같은 사용자 `POST /cash-advance` 4건 동시 → 201 1건·200 3건, `simulation` 1행. `SimulationUserLock`의 `SELECT … FOR UPDATE`가 InnoDB에서 직렬화됨을 확인했다.
- 하지 않은 것: ~~시연 seed를 적재한 스키마에서의 10종 실행·금액 확인~~(616, §7 실측). ~~롤백·목록 정렬·본인 조건 조회~~(617, 아래). FE 앱 통합.

617 추가 3건(2026-09-16, 같은 방식의 MySQL 8.0 컨테이너, 7건 통과. `gradlew clean build` 전체 1,054건 통과, MySQL 조건부 22건 skip). 계획·결정은 [simulation-617-plan.md](simulation-617-plan.md).

- **저장 중간 실패 롤백**: 테스트가 만든 `BEFORE INSERT` 트리거(`SIGNAL SQLSTATE '45000'`)로 (a) 복합의 3번째 `simulation_scenario` 행, (b) `simulation_goal_impact` 행 INSERT를 거부했다. 두 경우 모두 5xx 응답이고 `simulation`·`simulation_scenario`·`simulation_goal_impact`에 사용자 행 0. 트리거 제거 후 같은 요청은 201(부분 저장 행이 남았다면 같은 날 재사용 200이 됐을 것)이며 scenario 3행·goal_impact 1행이 저장된다. `SimulationUserLock`의 `FOR UPDATE` 잠금이 롤백으로 풀리는 것도 이 201로 확인된다. 운영 코드·마이그레이션 변경 없음.
- **목록 정렬**: 같은 유형 3건(clock을 하루씩 이동) + 다른 유형 1건 + 타인 1건에서 `?type=` 유무 모두 `created_at DESC, simulation_id DESC`(동률은 `created_at`을 같게 맞춰 확인), 타인 행 미포함. 유형 없는 목록 조회(JPQL `:type is null or …`의 null 바인딩)는 MySQL에서 이번에 처음 실행했고 정상이다. `idx_simulation_user_type_created` 컬럼 순서 `user_id, type, created_at` 확인. EXPLAIN(손으로 쓴 동형 SQL, `possible_keys`까지만 단언) 실측: `type` 지정 시 `key=idx_simulation_user_type_created, Extra=Using where; Backward index scan`(정렬을 인덱스 역순으로 처리, filesort 없음). `type` 미지정 시 `key=uk_simulation_user_simulation_id, Extra=Using where; Using filesort` — `(? IS NULL OR type = ?)`가 인덱스 두 번째 컬럼을 막아 user_id 접두만 쓰고 정렬은 filesort다. 사용자·하루 단위 양이 작아 587 설계대로 두며, 행이 5개뿐인 테스트 테이블의 선택이라 운영 규모의 계획과 다를 수 있다.
- **`uk_simulation_user_simulation_id`**: `NON_UNIQUE = 0`, 컬럼 순서 `user_id, simulation_id`, `fk_simulation_goal_impact_simulation`의 `UNIQUE_CONSTRAINT_NAME`이 이 키. 본인 상세·goal-impact 200, 타인은 상세·goal-impact·삭제 모두 404 `RESOURCE_NOT_FOUND`(행 불변), 없는 id 404. goal_impact가 달린 시뮬레이션의 `user_id`를 타인으로 UPDATE → FK 위반으로 거부(ON UPDATE 기본 RESTRICT, 소유자 변경 시 자식 고아 방지). 본인 조건 조회 EXPLAIN은 `possible_keys`에 `PRIMARY`와 이 키가 함께 오르고 실제 선택은 `PRIMARY`다. 본인 삭제 204 후 goal-impact 404.

### 11.4 FE 인계

- FE 595의 `simulationApi.types.ts`는 `SimulationResponse`·`SimulationRunResponse`·`SimulationSummaryResponse`·`SimulationGoalImpactResponse`(status/reasons optional)·comparison·cashflow 확장 필드와 일치한다. 이번 작업으로 바뀐 응답은 없다.
- FE에 필요한 변경(백엔드에서 수정하지 않음): (1) 발견 상세의 추천 목록을 `mocks/discovery.ts`에서 `GET /recommendations/priorities`로 교체하고 항목의 `simulation` 프리셋으로 시뮬레이션 입력을 채운 뒤 사용자가 수정·실행, (2) 실행 후 `/goal-impact`·`/cashflow` 조회로 전후 비교·목표 영향 화면 연결, (3) 미연동 8종 실행 함수·타입 추가(§9.1·§10.1), (4) `paidOff=false`·`NOT_COMPUTABLE`·`costComplete=false`·`truncated` 표시(§9.8·§10.6).
