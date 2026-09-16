# 587 Simulation 전체 통합·FE 연동 및 계산 검증 계획

작성일: 2026-09-15. 같은 날 브랜치 `S15P21D105-587-d12-simulation-fe`(Jira Create branch, `origin/develop` `d11a5cc` 기준)에서 구현했다.
**구현된 계약은 [simulation.md](simulation.md) §11·[recommendation-api.md](recommendation-api.md) §5.1이 기준**이며 이 문서는 결정 배경 기록이다.
결정 반영(사용자 확인, 2026-09-15): §3.2의 세 항목 모두 기본안 채택 — (1) SAVING 프리셋은 계좌 미지정 + 가정 코드, (2) 프리셋은 상세·목록·priorities 모두, (3) 시뮬레이션에 추천 id 저장 안 함.
구현 중 바뀐 점: SAVING의 R은 진단 이슈를 다시 읽지 않고 **추천 자신의 근거**(`GOAL_SAVING_SHORTFALL` evidence `requiredMonthlySaving`)에서 읽는다(§3.1 표 참고). 추천이 소유한 저장값이라 진단 도메인 재조회가 필요 없다.

## 1. 범위와 착수 근거

- 대상: S15P21D105-587. 로컬 Jira 내보내기(`SPRINT/Jira (4).csv`)는 572까지만 있어 원문을 직접 확인하지 못했다. [스프린트 보조 자료](../../SPRINT/sprint_2026-09-14_2026-09-20.csv) Issue ID 123 "[D12] Simulation 전체 통합·FE 연동 및 계산 검증"(2026-09-16, 8pt)을 585→121, 586→122 대응(커밋 메시지로 확인)에 따라 587로 본다. Jira 원문과 다르면 이 문서를 먼저 고친다.
- 이슈 설명: API 17 실행 10종과 공통 결과 조회를 통합하고 추천→시뮬레이션→전후 비교·목표 영향의 FE 흐름을 연결한다.
- 완료 기준: 동일 기준일·입력에서 계산 결과가 일관되고 소유권·멱등·빈 결과·경계값 및 MySQL 핵심 조회를 검증한다. 기존 소비/저축 구현을 신규 작업으로 중복 계산하지 않는다.
- 기준 자료: [API 17](../../docs/API명세서/17%20Simulation%203cdbb6ea356480a38af4c6f56a5280f2.csv), [API 16](../../docs/API명세서/16%20Recommendation%203cdbb6ea3564801eb1bbc55201aee86a.csv), [기획안](../../docs/기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md) 7-5·10-5·10-6("개선안 추천 → 개선 효과 시뮬레이션 → 사용자 행동 → 결과 추적"), [ERD](../../docs/ERD/DODAM_ERDCLOUD_IMPORT.sql) `simulation`·`recommendation`(FK 없음), [dev_plan](../../docs/dev_plan.md) Day 12 "전체 통합 / 계산 결과 검증".

### 현재 상태 (2026-09-15 점검)

| 항목 | 상태 |
| --- | --- |
| API 17 경로 19개(실행 10 + 목록·상세·comparison·cashflow·goal-impact·assets·debts·costs·삭제) | 550·551·585·586으로 모두 구현, `develop` 병합 |
| H2 테스트 | Simulation 12개 클래스(586 시점 67건 통과) |
| 추천 → 시뮬레이션 연결 | **미구현.** recommendation-api.md §1이 "Simulation 연동"을 후속으로 제외. 명세 16·17 어디에도 연결 필드가 없다 |
| MySQL 검증 | **미수행.** simulation.md §8·§9.8·§10.6에 V19 JSON·복합 FK·N행 scenario·CHECK 미검증으로 기록 |
| FE 연동 | FE 595(`bd811e3`)가 spending/saving 실행, 목록, 상세, comparison 5개 경로만 연결. `simulationApi.types.ts`는 백엔드 응답 모양과 일치(§7). 나머지 8종 실행과 cashflow·goal-impact·assets·debts·costs·삭제는 FE 미연동이며, 발견 상세 화면의 추천 목록은 아직 `mocks/discovery.ts`다 |
| 백엔드 변경 | 586 병합(`3580389`) 이후 `backend/` 변경 없음 |

### 포함·제외

| 포함 | 제외 |
| --- | --- |
| 추천 → 시뮬레이션 연결 계약(§3) | FE 화면 구현·연동(FE 146 범위). 필요한 FE 변경은 §7로 보고한다 |
| 10종 × 공통 조회 통합 회귀와 계산 일관성 검증(§4·§5) | 계산식 변경, 새 시뮬레이션 유형, 응답 계약 변경 |
| MySQL 통합 테스트 추가·실행(§6) | 기존 마이그레이션 수정, 인프라·배포 변경 |
| simulation.md·recommendation-api.md 갱신, 미검증 항목 정리 | 루트 `docs/` API 명세 수정(필요 시 사용자에게 보고) |

## 2. 선행 조건

- `origin/develop` `d11a5cc`가 585·586과 FE 595·602·603을 포함한다. 브랜치는 이를 기준으로 만들어졌다.
- 작업 트리는 깨끗하다(`git status --short --branch` 확인). 착수 시 Simulation 테스트를 먼저 실행해 현재 통과 상태를 기록한다.
- MySQL 검증에는 Docker `mysql:8.0` 컨테이너가 필요하다. 계획 작성 시점에 Docker 데몬이 꺼져 있었다(§9).
- Gradle 실행에는 셸마다 `JAVA_HOME`(`~/.jdks/ms-21.0.11`) 설정이 필요하다.

## 3. 추천 → 시뮬레이션 연결 계약 (핵심 결정)

기획안의 흐름은 "개선안 추천 → 개선 효과 시뮬레이션"이지만 명세 16·17에는 연결 필드가 없고 ERD에도 `simulation`↔`recommendation` FK가 없다. 따라서 **DDL 없이, 기존 응답에 필드를 더하는 방식**으로 연결한다. 아래는 서비스 보완안이며 사용자 확인이 필요하다.

### 3.1 기본안: 추천 응답에 시뮬레이션 프리셋 제공

`GET /recommendations/{id}`(상세)와 `GET /recommendations/priorities`·목록 항목의 `RecommendationResponse`에 `simulation` 필드를 추가한다. 새 엔드포인트는 만들지 않는다(명세 16 경로 불변).

```json
"simulation": {
  "available": true,
  "type": "SPENDING",
  "endpoint": "/api/v1/simulations/spending",
  "request": { "category": null, "monthlyReduction": 120000, "durationMonths": null },
  "unavailableReason": null,
  "assumptions": ["REDUCTION_APPLIES_TO_TOTAL_SPENDING"]
}
```

| 추천 domain/type | 프리셋 | 값의 출처 |
| --- | --- | --- |
| SPENDING / REDUCE_SPENDING | `POST /simulations/spending` `{ category: null, monthlyReduction: effect.monthlyCostSaving }` | 저장된 효과값. 카테고리는 진단 SPENDING_GOAL_RISK가 목표 예산 전체 기준이라 지정하지 않는다 |
| SAVING / INCREASE_MONTHLY_SAVING | `POST /simulations/saving` `{ accountNo: null, monthlySavingAmount: R }` | R = 추천 근거 `GOAL_SAVING_SHORTFALL`의 evidence `requiredMonthlySaving`(생성기가 저장). 추천 효과는 `contributionAmount = R − A`만 있으므로 근거에서 R을 읽는다. 계획 초안은 진단 이슈 재조회였으나 추천이 소유한 저장값으로 충분해 바꿨다. 근거나 필드가 없으면 `available=false`, `unavailableReason=SNAPSHOT_FIELD_MISSING` |
| CASHFLOW / SECURE_SHORTAGE_BUFFER | 없음 | 부족액 대응 행동이 시뮬레이션 10종에 직접 대응하지 않는다. `available=false`, `unavailableReason=NO_MATCHING_SIMULATION_TYPE` |

- `monthlySavingAmount`는 시뮬레이션 계약대로 **변경 후 절대값**이다. 시뮬레이션 기준선의 현재 저축액(A′)이 진단 시점 A와 달라도 목표 달성에 필요한 R 자체는 같은 값이므로 절대값으로 넘기는 것이 델타보다 안전하다.
- `durationMonths`는 null로 두어 시뮬레이션 기본값(12개월)을 따른다. 프리셋은 제안값이며 FE에서 사용자가 수정해 실행한다.
- 프리셋은 재계산이 아니라 저장값의 재배열이다. 추천 상태(REJECTED 등)와 무관하게 제공하고, 실행 여부 판단은 FE에 둔다.
- 요청 예시의 `accountNo`는 항상 null이다. 원문 계좌번호를 응답에 싣지 않는 정책과 FE가 마스킹 값만 가진다는 제약 때문이다.

### 3.2 결정 필요 사항

1. **SAVING 프리셋의 목표 지정.** 저축 시뮬레이션은 `accountNo`로 연결 목표를 좁히고, 없으면 모든 모으기 목표에 남은 금액 비례로 배분한다. 추천은 특정 `goalId`를 겨냥하는데 FE는 원문 계좌번호가 없어 `accountNo`를 보낼 수 없다. 선택지:
   - (a) 이번 범위: 계좌 미지정으로 실행하고 프리셋 `assumptions`에 `TARGET_GOAL_NOT_SELECTABLE_WITHOUT_ACCOUNT`를 남긴다. 목표가 하나면 결과가 같다.
   - (b) 명세 17 Body 확장: `POST /simulations/saving`에 `goalId?`를 추가하고 `accountNo`와 동시 지정은 400. 지문·저장 파라미터에 `goalId` 포함, 소유권 검증은 `RESOURCE_NOT_FOUND`. 루트 API 명세 변경이 필요하므로 **사용자 요청이 있어야** 진행한다.
   - 기본안은 (a)이며 (b)는 FE 146과 함께 결정한다.
2. **프리셋 위치.** 상세·목록·priorities 세 응답 모두에 넣을지, 상세와 priorities에만 넣을지. 목록은 `PageResponse` 항목이 `RecommendationResponse`라 같이 들어간다. SAVING 프리셋은 근거 조회가 필요하므로 목록 N건이면 N번 조회가 되지 않도록 `findAllOwnedIn`으로 한 번에 읽는다. 기본안: 세 응답 모두 제공.
3. **시뮬레이션 → 추천 역참조 저장 여부.** `simulation.request_snapshot`에 `recommendationId`를 남기면 리포트(10-6 결과 추적)에 쓸 수 있지만, 지문에 넣으면 같은 입력이 추천 유무로 별개 저장되고 빼면 재사용 시 연결이 유실된다. **이번 범위에서는 저장하지 않는다.** 필요하면 Improvement/Report 이슈에서 계약을 정한다.

## 4. 계산 일관성 검증 (동일 기준일·입력)

새 계산을 만들지 않고 이미 있는 식들이 서로 같은 답을 내는지 테스트로 고정한다.

| 검증 | 대조 대상 | 기대 |
| --- | --- | --- |
| 기준선 예상 달성일 | `SimulationBaseline.goals[].expectedAchievementDate` vs `GET /goals/{id}` forecast | 이미 `SimulationApiTest.baselineForecastMatchesGoalForecastApi`가 고정. 585·586 유형에서도 같은 기준선을 쓰는지 확인 |
| 추천 효과 vs 저축 시뮬레이션 | `recommendation.effect.improvedForecastDate`(진단 asOf + ceil(remaining/R)) vs `POST /simulations/saving {monthlySavingAmount: R}`의 `goalImpacts[].afterForecastDate` | 진단 asOf = 시뮬레이션 기준일이고 목표가 하나이며 잔여액이 같을 때 일치. 기준일이 다르면 차이가 `기준일 차이`만큼 나야 하며, 그 사실을 문서에 기록 |
| 추천 효과 vs 소비 시뮬레이션 | `effect.monthlyCostSaving` vs `POST /simulations/spending {monthlyReduction}`의 `after.monthlyAvailableFunds − before.monthlyAvailableFunds` | 동일 금액 |
| 단독 vs 복합 | 각 단독 결과의 월 delta 합 vs 복합 `cashflow.months[].delta` | `CombinedSimulationCalculatorTest`가 고정. 10종 전체 조합에서 순서 교환 불변 재확인 |
| 실행 응답 vs 조회 응답 | `POST` 응답 `simulation` vs `GET /{id}`·`/comparison`·`/cashflow`·`/goal-impact`·`/assets`·`/debts`·`/costs` | 10종 각각 JSON 동일(저장값 그대로, 재계산 없음) |
| 멱등 | 같은 날 같은 요청 10종 | `created=false`, 같은 `simulationId`, 저장 행 수 불변. 기준일이 바뀌면 신규 |
| 계산 버전 | `SimulationCalculationVersion` | 이번 작업은 계산식을 바꾸지 않으므로 올리지 않는다. 지문 규칙도 불변 |

세 도메인(Goal·Recommendation·Simulation)의 달성일 식 복제 정리는 546 병합 후 과제([recommendation-api.md](recommendation-api.md) §8)로 이번 범위에 넣지 않는다.

## 5. 통합 회귀 매트릭스

기존 API 테스트가 유형별로 흩어져 있으므로 10종을 한 fixture에서 도는 `SimulationIntegrationApiTest`(가칭)를 추가한다. 이미 있는 검증은 중복 작성하지 않고 누락만 채운다.

| 검증군 | 사례 |
| --- | --- |
| 인증 | 10종 실행·9개 조회·삭제 401 |
| 소유권 | 타인 simulationId로 상세·comparison·cashflow·goal-impact·assets·debts·costs·삭제 → 404 `RESOURCE_NOT_FOUND`; 타인 계좌·카드·대출 참조 실행 → 404; 목록에 타인 건 부재 |
| 빈 결과 | 목표 없음(`goalImpacts=[]`), 소득 미등록(null 유지, coverage), 목록 0건, assets/debts/costs 0건 페이지, 삭제 후 목록·상세 |
| 경계값 | 목록·섹션 `page<0`·`size 0/101` 400, `size=100` 200; `simulationId` 0·음수 400; 10종 필수값 누락·최소/최대(금액 1원, 기간 1/120, 비율 0/100, 복합 2/10개) |
| 멱등·상태 | 같은 날 재사용, 다음 날 신규, 삭제 건 미재사용, 실패 시 0행 저장 |
| 응답 계약 | 10종 `type` enum, `status=COMPLETED`, `request`에 `requestHash`·원문 번호·해시 부재, `scenarios` sequenceNo 순, goalImpact `status/reasons` 유무(551 두 종은 없음) |
| 추천 프리셋 | 추천 3종 각각의 프리셋 존재/부재, 프리셋 그대로 실행 시 201, 타인 추천 404, 진단 이슈 필드 누락 시 `SNAPSHOT_FIELD_MISSING` |

## 6. MySQL 검증

- `RecommendationMySqlIntegrationTest` 방식으로 `SimulationMySqlIntegrationTest`를 추가한다. `SIMULATION_MYSQL_TEST_URL` 설정 시만 실행되며 H2 빌드에는 영향이 없다.
- 확인 항목: V19 적용 후 `ddl-auto: validate` 통과(`Simulation`·`SimulationScenario`·`SimulationGoalImpact` JSON·enum·DATETIME 매핑), `chk_simulation_type` 10종·`chk_simulation_status` CHECK, 복합 N행 scenario 저장과 중간 실패 롤백, `uk_simulation_user_simulation_id`와 본인 조건 조회, `idx_simulation_user_type_created` 순서의 목록 정렬, 소프트 삭제 후 미조회, 동일 사용자 동시 실행 1건 저장(`SimulationUserLock` `SELECT … FOR UPDATE`).
- 시연 seed(491·492·493·522·523)를 적재한 스키마에서 10종 실행이 실제 자료로 성립하는지(대출 `next_repayment_date`, 카드 소유권, 상품 옵션) 1회 수동 확인하고 결과를 simulation.md §7에 기록한다.
- 빈 DB에서 V6→V11 참조 순서 문제(recommendation.md §8)는 기존 과제다. 재현되면 기록만 하고 마이그레이션을 고치지 않는다.
- Docker가 없으면 이 절은 미검증으로 남기고 성공으로 기록하지 않는다.

## 7. FE 인계

- FE 595의 `simulationApi.types.ts`를 백엔드 DTO와 대조했다. `SimulationResponse`·`SimulationRunResponse`·`SimulationSummaryResponse`·`SimulationGoalImpactResponse`(status/reasons optional)·comparison·cashflow 확장 필드(optional)까지 일치한다. 이번 작업으로 이 모양은 바뀌지 않는다.
- FE에 필요한 변경(백엔드에서 수정하지 않고 보고):
  1. 발견 상세의 추천 목록을 `mocks/discovery.ts`에서 `GET /recommendations/priorities`(또는 상세)로 교체하고, 응답의 `simulation` 프리셋으로 `BasicSimulationSection` 입력을 채운 뒤 사용자가 수정·실행.
  2. 실행 후 `GET /{id}/goal-impact`·`/cashflow`로 "전후 비교·목표 영향" 화면 연결(현재 comparison만 사용).
  3. 미연동 8종 실행 API의 `simulationApi` 함수와 타입은 `PendingSimulationType` 자리에 추가. 요청 모양은 simulation.md §9.1·§10.1.
  4. 리볼빙 `paidOff=false`, 복합 `NOT_COMPUTABLE`·`costComplete=false`, `truncated` 등 인계 사항(simulation.md §9.8·§10.6)의 화면 표시.
- FE 146 "[D12] 목표·진단·추천·Simulation 통합"과 겹치므로 프리셋 계약(§3)을 확정하는 즉시 FE 담당에게 공유한다.

## 8. 구현 순서와 완료 기준

1. **현재 상태 고정**: `.\gradlew.bat test --tests 'com.ssafy.dodam.domain.simulation.*'` 실행·기록. 실패가 있으면 587 범위인지 먼저 판단한다.
2. **프리셋 계약 확정(§3)**: 사용자 결정(3.2) 반영 → `RecommendationSimulationPreset` DTO, 진단 이슈 재조회, `RecommendationResponse` 확장, 단위·API 테스트.
3. **일관성 테스트(§4)**: 추천 효과 vs 시뮬레이션, 실행 vs 조회 10종, 멱등 10종.
4. **통합 회귀(§5)**: 누락 사례만 추가. 전체 `test` → `clean build`.
5. **MySQL(§6)**: 컨테이너 준비 → `SimulationMySqlIntegrationTest` → seed 수동 확인.
6. **문서**: simulation.md(§7 검증 결과, §8 미검증 항목 갱신, 프리셋 실행 흐름), recommendation-api.md(§1 범위표의 "Simulation 연동" 이동, 프리셋 계약·오류·가정 코드), remaining-work.md에 남는 항목 등록.

완료 조건: 추천 3종 프리셋 계약 구현·테스트, 10종 × 공통 조회의 인증·소유권·멱등·빈 결과·경계값 통과, 계산 일관성 테스트 통과, 기존 테스트 회귀 없음, MySQL 항목은 실행한 것만 통과로 기록, `git diff --check` 통과.
FE 화면 완료·앱 QA(사용자 QA 이슈 124)·배포는 완료 조건이 아니다.

## 9. 준비 상태와 검증 결과

- 2026-09-15 계획 작성 시 작업 트리는 깨끗했고 `backend/`는 586 병합 이후 변경이 없었다. 착수 시 Simulation 69건 통과를 확인했다.
- Jira 587 원문은 미확인이다(로컬 내보내기 범위 밖). 제목·완료 조건이 이 문서와 다르면 §1을 갱신한다.
- 구현 검증(simulation.md §11): H2 Simulation 72건·Recommendation 58건, `gradlew clean build` 1,051건 통과. MySQL 8.0.46 `SimulationMySqlIntegrationTest` 4건 통과(Docker 컨테이너, V6→V11 순서 우회 + baseline 17).
- 전체 빌드에서 발견한 테스트 인프라 제약: 공유 H2(`jdbc:h2:mem:dodam`)와 `create-drop`을 쓰는 기존 통합 테스트는 Spring 컨텍스트 캐시(기본 32개)가 넘쳐 오래된 컨텍스트가 닫히면 테이블이 지워져 깨진다. 새 컨텍스트를 두 개 더한 첫 실행에서 96건이 `auth_session not found`로 실패했고(같은 커밋의 깨끗한 worktree는 1,037건 통과), 신규 테스트가 기존 컨텍스트(586 URL·같은 MockitoBean)를 공유하도록 바꿔 해소했다. 근본 해결(캐시 크기 조정 또는 테스트별 DB 분리)은 remaining-work.md에 기록했다.
- 하지 않은 것: 시연 seed를 적재한 MySQL에서의 10종 실행·금액 확인, FE 앱 통합, `goalId?` 본문 확장. §4의 "추천 효과 vs 소비 시뮬레이션" 대조는 예산 초과 거래 fixture가 필요해 API 테스트로 고정하지 않았다(프리셋 값 자체는 단위 테스트로 확인). 저축 쪽 대조는 고정했다.
