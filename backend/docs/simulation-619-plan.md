# 619 저축 시뮬레이션 goalId 지정 계획

작성일: 2026-09-16. 상태: **구현·검증 완료**. 계좌 할당(626) 논의에서 619 부분을 분리했다.

## 0. 구현 결과 (2026-09-16)

- 인수 검토 후 재검증: 관련 테스트 총 143건 중 133건 통과·MySQL 조건부 10건 건너뜀. `gradlew.bat clean build` 성공(총 1,063건 중 1,041건 통과·MySQL 조건부 22건 건너뜀, 실패·오류 0). 소수점 ID 거부, 삭제 목표 404, 기준선 목표 누락 400, 복합에서 선택 목표만 변경하는 사례를 확인했다. `git diff --check` 통과. MySQL 실환경은 재검증하지 않았다.
- §3~§8을 구현했으며 인수 검토에서 기준선 대상 누락 시 거부·소수점 ID 검증을 보완했다. 계약 문서는 simulation.md §4·§7·§11.1, recommendation-api.md §5.1·§8, remaining-work.md 587 절에 반영했다.
- 착수 전 기준: `origin/develop` `d62cd07`로 fast-forward, Simulation 80건·Recommendation 59건 통과.
- 기존 작업자의 검증 기록: H2 Simulation 84건(+4)·Recommendation 59건(건수 불변, 사례 변경) 통과. `gradlew clean build` 전체 1,062건 통과(MySQL 조건부 22건 skip). `git diff --check` 통과. MySQL 전용 SQL 변경이 없어 `SimulationMySqlIntegrationTest`는 재실행하지 않았다.
- 구현 중 계획과 달라진 점: `SavingChangeCalculator.isLinked`를 `isTargeted`·`targetMissAssumption`으로 바꿔 계좌·목표 대상 선택과 미발견 가정 코드를 한 곳에 뒀다(`CombinedGoalProjection`도 같은 메서드 사용). `SavingChangeInput`에 3인자 편의 생성자를 남겨 기존 호출 18곳을 바꾸지 않았다.
- 남은 것: 루트 명세 17 Body 갱신(사용자 요청 시), FE 146 인계(§10), 커밋·MR(요청 시).

## 1. 범위와 착수 근거

- 대상: S15P21D105-619. Jira 원문은 로컬 내보내기에 없다. 원격 브랜치 `origin/S15P21D105-619-587-goalid-17`과 [587 후속 내보내기](../../SPRINT/587-remaining-work.csv) Issue 161 "[587 후속] 저축 시뮬레이션 goalId 지정(명세 17 본문 확장) 결정"(3pt, 2026-09-17)으로 본다. Jira 원문과 다르면 이 절을 먼저 고친다.
- 결정: 확장한다. 추천 프리셋이 겨냥한 목표만 바꾸려면 `goalId?`가 필요하고, 계좌 할당(626)과 무관하게 완결된다.
- 완료 조건(Issue 161): `goalId` 지정 실행이 해당 목표만 바꾸고 추천 프리셋 `request.goalId`가 채워진다. 명세 17·simulation.md·recommendation-api.md §5.1을 갱신한다.
- 근거 문서: [remaining-work.md "저축 프리셋의 목표 지정"](remaining-work.md), [simulation-587-plan.md §3.2](simulation-587-plan.md), [recommendation-api.md §5.1](recommendation-api.md), [simulation.md §4](simulation.md).

### 포함·제외

| 포함 | 제외 |
| --- | --- |
| `POST /simulations/saving` 본문 `goalId?`와 복합(`/simulations/combined`)의 SAVING 자식 | 계좌 할당 모델·목표 확보액 정의 변경(626) |
| 추천 SAVING 프리셋 `request.goalId` | 계산식 변경·계산 버전 상향 |
| simulation.md·recommendation-api.md·remaining-work.md 갱신 | 루트 명세 17 수정(사용자 요청 시), FE 화면 |

## 2. 선행 조건

- 브랜치 `S15P21D105-619-587-goalid-17`은 착수 시 `origin/develop`의 `d62cd07`까지 fast-forward를 완료했다(§0).
- 626 브랜치와 섞지 않는다. 626은 이 작업 병합 뒤 분기한다.
- Gradle은 셸마다 `JAVA_HOME`(`~/.jdks/ms-21.0.11`) 설정이 필요하다.

## 3. 요청 계약

`POST /api/v1/simulations/saving` Body:

```json
{ "goalId": 101, "monthlySavingAmount": 300000, "durationMonths": 12 }
```

| 검증 | 결과 |
| --- | --- |
| `goalId` 0·음수·정수 아님 | 400 (`@Positive`, 소수점은 `WholeNumberDeserializer`로 거부) |
| `goalId`와 `accountNo` 동시 지정 | 400 `INVALID_REQUEST`. 계좌로 목표를 찾는 경로와 목표 직접 지정을 한 요청에 섞지 않는다 |
| 타인·없는·삭제된 목표 | 404 `RESOURCE_NOT_FOUND` "목표를 찾을 수 없습니다." (`GoalRepository.findByIdAndUserIdAndDeletedAtIsNull`, 진단 `DiagnosisExecutionStore.admit`과 같은 규칙) |
| 본인 SPENDING 목표 | 400 `INVALID_REQUEST`. 소유권 확인 뒤이므로 존재 노출 문제가 없고, 줄이기 목표에는 저축액 배분 대상이 없다 |
| 본인 SAVING이지만 `ACTIVE`가 아님(PAUSED·ACHIEVED·CANCELED) | 400 `INVALID_REQUEST`. 기준선(`SimulationBaselineReader`)이 ACTIVE 목표만 읽으므로 효과를 계산할 수 없다. 조용히 전체 비례 배분으로 바꾸지 않는다 |
| `accountNo`만 지정 | 기존 동작 그대로 |
| 둘 다 없음 | 기존 동작 그대로(모든 모으기 목표 비례 배분) |

`monthlySavingAmount`는 기존과 같이 변경 후 월 저축액(절대값)이며, 626의 "한 번 담는 금액"과 다른 개념이다.

## 4. 계산 — 대상 목표 선택

`SavingChangeCalculator.eligibleGoals`를 확장한다. 우선순위: `goalId` 지정 → 그 목표만. `accountId` 지정이고 연결 목표 있음 → 그 목표만. 그 외 → 전체.

- `goalId`로 지정한 목표는 §3에서 ACTIVE SAVING으로 확인했으므로 기준선에 있어야 한다. `saving_goal` 행 누락 또는 검증 이후 상태 변경으로 기준선에 없으면 400 `INVALID_REQUEST`로 거부한다. 명시적으로 지정한 목표 대신 다른 목표에 배분하지 않는다.
- 대상이 하나면 `SPLIT_BY_REMAINING`은 붙지 않는다(기존 규칙).
- `CombinedGoalProjection`은 `SavingChangeCalculator.isTargeted/eligibleGoals`를 그대로 쓰므로 정적 메서드 하나에서 `goalId`를 함께 처리하면 복합도 같은 대상 선택 규칙을 따른다.
- 계산식은 바뀌지 않는다. `SimulationCalculationVersion.CURRENT`(`v3-card-combined`)를 올리지 않는다(587 계획 §4 규칙).

## 5. 입력·지문·저장

`SavingChangeInput(accountId, goalId, monthlySavingAmount, durationMonths)`.

- 생성자에서 `accountId`와 `goalId` 동시 지정을 거부한다(`IllegalArgumentException`, Resolver가 먼저 400으로 거른다).
- **지문 호환**: 현재 `accountId|amount|duration`. `goalId`가 없을 때 이 문자열이 그대로 나와야 배포 전 같은 날 저장된 건이 계속 재사용된다. `goalId`가 있을 때만 `|g=<goalId>`를 덧붙인다.
- `parameters()`(request_snapshot)에는 `goalId`를 항상 넣는다(없으면 null). 응답 `request`에 `goalId` 키가 추가되므로 FE에 알린다. 이전 저장 건에는 키가 없고 조회 응답은 저장값 그대로이므로 호환된다.
- 같은 날 `goalId` 지정 건과 미지정 건은 지문이 달라 별개 저장된다. 목표가 하나뿐이면 결과는 같지만 재사용하지 않는다(단순성 우선, 587과 같은 규칙).
- 사용자 목표 설정 revision·계좌 연결 변경에 따른 재사용 무효화는 619 범위가 아니다(626 계획 §5).

## 6. 추천 프리셋

`RecommendationSimulationPresetAssembler.build` SAVING 분기:

- `request = { accountNo: null, goalId: r.getGoalId(), monthlySavingAmount: R, durationMonths: null }`.
- `Recommendation.goalId`는 `SavingGoalRiskGenerator`가 진단 evidence `targetId`에서 채우며 null이면 후보를 만들지 않는다. 따라서 저장된 과거 SAVING 추천도 `goalId`가 있고 프리셋에 그대로 실린다. 별도 복원 로직은 없다.
- `goalId`가 실리면 `TARGET_GOAL_NOT_SELECTABLE_WITHOUT_ACCOUNT` 가정을 붙이지 않는다(`assumptions: []`). 코드 상수는 `goalId`가 null인 방어 경로용으로 남긴다.
- 추천이 겨냥한 목표가 그 사이 PAUSED·삭제됐으면 프리셋은 그대로 내고 실행 시 §3 규칙으로 400/404가 난다. 프리셋은 재계산이 아니라는 기존 계약을 유지한다.

## 7. 복합 시뮬레이션

`SimulationInputResolver.resolve(userId, ScenarioRequest)`의 `SAVING` 분기가 단독과 같은 `resolve(userId, SavingSimulationRequest)`를 호출하므로 §3 검증이 그대로 적용된다. `CombinedScenarioValidator`의 "SAVING 자식 1개" 제한은 유지한다.

## 8. 변경 파일

| 파일 | 변경 |
| --- | --- |
| `dto/request/SavingSimulationRequest` | `goalId?` 추가(`@Positive`), Javadoc |
| `dto/SavingChangeInput` | `goalId` 필드, 동시 지정 거부, 지문·parameters |
| `service/SimulationInputResolver.resolve(userId, SavingSimulationRequest)` | 동시 지정 400, 목표 소유권 404, SAVING·ACTIVE 400. `GoalRepository` 주입 |
| `service/calculator/SavingChangeCalculator` | `eligibleGoals`·`isTargeted`에 `goalId` 분기, 기준선 대상 누락 거부 |
| `service/calculator/CombinedGoalProjection` | 가정 코드 분기 확인(정적 메서드 재사용이면 변경 없음) |
| `recommendation/.../RecommendationSimulationPresetAssembler` | `request.goalId`, 가정 분기 |
| 테스트 | §9 |
| 문서 | §10 |

## 9. 검증

| 검증군 | 사례 |
| --- | --- |
| 계산(`SimulationCalculatorTest`) | 목표 2개에서 `goalId` 지정 시 대상 목표만 속도 변경·다른 목표 기존 속도 유지·`SPLIT_BY_REMAINING` 없음; 기준선에 없는 goalId → `INVALID_REQUEST`로 거부 |
| 입력(`SimulationCalculatorTest`) | `goalId` 없는 지문이 변경 전 문자열과 동일; `goalId` 있음/없음 지문 상이; 동시 지정 예외 |
| API(`SimulationApiTest`) | `goalId` 201·`request.goalId` 저장; `goalId`+`accountNo` 400; 타인·없는·삭제 목표 404; 본인 SPENDING 400; PAUSED 400; `goalId` 0·음수·소수점 400; 상세 누락 400; 같은 날 같은 `goalId` 재실행 200 `created=false`; 미지정 요청은 기존 결과 재사용 |
| 복합(`SimulationCardCombinedApiTest`·`CombinedSimulationCalculatorTest`) | SAVING 자식 `goalId` 지정 시 단독과 같은 대상, 타인 목표 404 |
| 프리셋(`Recommendation*Test`) | SAVING 추천 프리셋 `request.goalId` = 추천 goalId, `assumptions=[]`; 목록 N건도 동일 |
| 일관성(`RecommendationApiTest`) | 목표 2개일 때 프리셋 그대로 실행한 `goalImpacts[goalId].afterForecastDate` = `effect.improvedForecastDate`(587 §4는 목표 1개만 고정) |
| 소유권·인증 | 401, 타인 goalId 404 — 위에 포함 |

순서: 계산기 단위 → API → 프리셋 → 전체 `gradlew.bat test` → `clean build` → `git diff --check`. MySQL 전용 SQL 변경이 없으므로 `SimulationMySqlIntegrationTest` 재실행은 선택(요청 시).

## 10. 문서

- `simulation.md` §4 저축 표(검증 행 4개 추가), 대상 목표 규칙, §11.1 프리셋 예시, §8 제한사항에서 goalId 항목 제거.
- `recommendation-api.md` §5.1 표·예시(`request.goalId`, `assumptions: []`), "보류" 문구 제거.
- `remaining-work.md` "저축 프리셋의 목표 지정" 항목 완료 처리.
- 루트 [명세 17](../../docs/API명세서/17%20Simulation%203cdbb6ea356480a38af4c6f56a5280f2.csv) `POST /simulations/saving` Body에 `goalId?`와 "accountNo와 동시 지정 불가"가 필요하다. 루트 문서는 사용자 요청 시 수정한다.
- FE(146) 인계: 프리셋 `request`에 `goalId`가 실리며 그대로 POST하면 된다. 응답 `request`에 `goalId` 키가 추가된다.

## 11. 완료 조건과 미결정

완료: §9 통과, 계산 버전 불변, 기존 Simulation·Recommendation 테스트 회귀 없음, §10 중 backend/docs 항목 반영, `git diff --check` 통과. 커밋·MR은 사용자 요청 시.

미결정 없음. 아래는 이 문서에서 정한 기본안이며 FE·팀 의견이 다르면 바꾼다.

- 본인 SPENDING·비ACTIVE 목표는 404가 아니라 400.
- `goalId` 지정 건과 미지정 건은 같은 날에도 별개 저장.
- 가정 코드 `TARGET_GOAL_NOT_SELECTABLE_WITHOUT_ACCOUNT`는 이름을 바꾸지 않는다.
