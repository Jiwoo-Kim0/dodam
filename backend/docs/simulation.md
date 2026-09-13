# Simulation 공통 모델 (S15P21D105-550)

작성일: 2026-09-13. API 17 Simulation의 저장 구조·계산 계약·공통 조회 API(550)다. 소비 감소·저축액 변경 실행 API는 551.
계획과 결정 배경은 [simulation-plan.md](simulation-plan.md)에 있다.

기준 자료: [API 17](../../docs/API명세서/17%20Simulation%203cdbb6ea356480a38af4c6f56a5280f2.csv),
[ERD SQL](../../docs/ERD/DODAM_ERDCLOUD_IMPORT.sql) 1089~1150행, [기획안](../../docs/기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md) 4-5·7-5·9-6.

## 1. 범위

| 포함 | 제외(후속) |
| --- | --- |
| V19 마이그레이션 3테이블, 엔티티 3종, `SimulationType`·`SimulationStatus` | 명세의 나머지 실행 8종(saving-product, loan/*, installment, revolving, cash-advance, combined) — **경로 자체가 없다** |
| 계산 계약 `SimulationCalculator` + 기준선 Reader + Runner/Store | 이자·세후 계산, 상품 DB 연계 |
| — | 실행 경로 전부(551부터: spending·saving). 현금흐름 예측(cashflow forecast)과의 연동 — 월 delta 표만 만든다 |
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
  dto/         SimulationInput, SimulationBaseline, SimulationResult, response/
  service/     SimulationCalculator, SimulationBaselineReader, SimulationRunner, SimulationStore,
               SimulationQueryService, SimulationRequestHash, SimulationCalculationVersion, GoalAchievementProjection, SimulationClock
  service/calculator/  (551) 유형별 계산기
```

- **`SimulationInput`**: HTTP 요청과 분리된 확정 입력 계약(`type`, `durationMonths` 1~120 기본 12, `fingerprint`, `parameters`). 외부 식별자(카테고리 문자열·계좌번호)를 내부 값으로 바꾸는 검증은 유형별 구현(551)이 계산 전에 끝내며, 계좌번호 원문·해시는 입력·지문·저장·응답 어디에도 들어가지 않는다.
- **`SimulationBaseline`**: 실행 시점 현황. `SimulationBaselineReader`가 읽기 전용으로 모은다.
  - 소득: `financial_profile.monthly_income`. 없으면 null + `INCOME_UNKNOWN`.
  - 소비: **기준일이 속한 달을 제외한 직전 3개 달력월**의 월평균(전체·카테고리 코드별, 미분류는 `UNCATEGORIZED`). 거래가 없으면 0 + `SPENDING_NO_TRANSACTIONS`.
  - 모으기 목표(ACTIVE·SAVING): Goal 도메인과 같은 정의 — 모인 금액 = 연결 계좌 잔액 합, 속도 = 모인 금액 / max(1, 생성일~기준일 경과 개월), 남은 금액 = max(0, 목표 − 모인 금액), 예상 달성일 = `GoalAchievementProjection`. 없으면 `NO_ACTIVE_SAVING_GOAL`.
  - 줄이기 목표(ACTIVE·SPENDING): 예산, 이번 달 지출, 예상 월말 지출(Goal과 같은 식 `spent × 월 일수 / 경과 일수`, 반올림).
- **`GoalAchievementProjection.expectedDate(remaining, pace, base)`**: 남은 금액 ≤ 0 → base, 속도 ≤ 0 → null, 그 외 `base + ceil(remaining / pace)`개월. `GoalCalculationService.getForecast`와 같은 식이며 Goal API 응답과의 대조는 551의 `SimulationApiTest.baselineForecastMatchesGoalForecastApi`가 한다.
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

## 4. 실행 API

551에서 spending·saving부터 구현한다. 입력 검증·계산 규칙·응답 모양(`SimulationResponse`)은 그 문서가 확정한다. 응답 골격:
`simulationId, type, title, summary, status, createdAt, completedAt, request(파라미터, requestHash 제외), scenarios[], before(+coverage), after(+assumptions), comparison{items,assets,debts,costs}, cashflow{basis,before,after,months[]}, goalImpacts[]`.

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
| `SIMULATION_TYPE_NOT_SUPPORTED` | 400 | 계산기 없는 유형이 Runner에 들어온 경우(공개 경로에서는 발생하지 않음) |

## 7. 검증

H2 (`.\gradlew.bat test --tests 'com.ssafy.dodam.domain.simulation.*'`, 2026-09-13 통과, 6건):

- `SimulationModelTest` 3: 달성일 식 경계(남은 금액 0/속도 0/올림), 요청 지문 안정성·입력별 변화, 엔티티 검증(sequence·길이·NOT NULL·삭제 멱등·기간 범위).
- `SimulationQueryApiTest` 3: Store 원자 저장(scenario·goal_impact·coverage·assumptions)·같은 날 같은 지문 재사용·다른 날/유형/사용자/삭제 건 미재사용·잠금 트랜잭션 밖 거부, 계산기 없는 유형 거부, 목록 필터·페이지 경계·401, 상세(requestHash 제거)·섹션 페이징·소유권 404·삭제 후 404·재삭제 404.

MySQL 통합 검증은 하지 않았다(§8).

## 8. 현재 제한사항과 인계

- [ ] **MySQL 미검증**: V19 DDL·`ddl-auto: validate`·CHECK·복합 FK·JSON 타입은 H2에서 확인되지 않는다. 547의 `RecommendationMySqlIntegrationTest` 방식(`*_MYSQL_TEST_URL` 설정 시만 실행)으로 추가해야 한다. 빈 DB에서 V6이 V11보다 먼저 실행돼 Flyway가 실패하는 develop 기존 문제(recommendation.md §8)도 그대로다.
- [ ] `GoalAchievementProjection`은 Goal 계산식의 복제다. 546 병합 후 Goal 도메인에 순수 함수가 생기면 교체한다.
- [ ] 실행 경로는 아직 없다(551: spending·saving). 계산기를 추가하고 컨트롤러 경로를 열면 된다(`SimulationType`·CHECK는 이미 10종).
- [ ] `cashflow`는 월 delta 표이며 예정 이벤트·현금흐름 예측과 연동하지 않았다.
- `assets/debts/costs`는 저장 JSON의 메모리 페이징이다. 항목이 수십 건을 넘는 유형(대출·복합)이 생기면 구조를 다시 본다.
- 명세 17에 응답 필드가 없으므로 이 문서가 응답 계약이다. 프론트 연동 시 공유 필요.
