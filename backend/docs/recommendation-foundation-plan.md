# Recommendation 기반 구현 계획 — S15P21D105-547

작성일: 2026-09-13. 구현 전 계획이다. 547의 기능 코드는 아직 추가하지 않았다.

## 1. 착수 판단과 근거

**547은 544·545 및 546과 분리해 진행할 수 있다.** 선행 조건은 543의 Diagnosis 저장 구조와 Goal/User 테이블이다.
로컬 `origin/develop` 참조에서 V5(goal)·V17(diagnosis) 존재를 확인했다. 실제 구현을 시작할 때 원격 develop을 갱신하고 다시 확인한다.
544·545의 Controller/응답 DTO/실행 서비스를 직접 의존하지 않고 완료된 진단을 읽는 입력 경계를 둔다.

최신 Jira 내용은 이전 연결 시 403으로 조회하지 못했다. 로컬 `SPRINT/Jira (4).csv`의 범위는 다음과 같다.
이 자료는 2026-09-07 내보내기이므로 구현 시작 전에 최신 Jira 설명·완료 조건과 대조해야 한다.

| 이슈 | 로컬 이슈 범위 | 이번 계획과의 관계 |
| --- | --- | --- |
| 547 | Recommendation·Reason·History와 후보 생성 골격 | 저장 기반·입력/출력 계약·후보 생성 확장점 |
| 548 | 생성·목록·요약·우선순위·상세·근거·효과·목표기여 | 실제 규칙·효과 계산·HTTP API 연결 |
| 549 | accept·reject·complete·defer·history | 상태 전이 정책·명령 API·이력 API 연결 |

기준 자료:

- [API 16 Recommendation](../../docs/API명세서/16%20Recommendation%203cdbb6ea3564801eb1bbc55201aee86a.csv): 공개 요청·필터·소유권·중복 방지 조건.
- [ERD SQL](../../docs/ERD/DODAM_ERDCLOUD_IMPORT.sql): 3개 테이블의 컬럼·인덱스·UNIQUE·복합 FK·CHECK.
- [ERD 설명](../../docs/ERD/ERD%20작성%203c9bb6ea356480898bd9eddac2e6840d.md): 근거 보존을 위한 Recommendation/History 하드 삭제 금지.
- [기획안](../../docs/기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md): 5-3 개선안·5-4 우선순위·5-6 개선 효과. 군집은 보조 신호이며 실제 대환 가능성을 확정하지 않는다.
- [마이그레이션 규칙](../src/main/resources/db/migration/README.md), [Diagnosis 저장 구조](diagnosis.md), [개발 일정](../../docs/dev_plan.md).

## 2. 범위와 완료 산출물

이번 변경은 `backend/`로 제한한다. 다음을 구현한다.

1. recommendation·recommendation_reason·recommendation_history 신규 Flyway 마이그레이션.
2. 엔티티 3종과 RecommendationStatus enum.
3. 사용자 소유권이 포함된 Repository 조회 계약.
4. 추천·근거·초기 이력을 원자적으로 저장하는 서비스와 중복 생성 방지.
5. 완료 진단 입력 모델, 추천 후보 모델, 후보 생성기 인터페이스와 조합 골격.
6. 저장·소유권·중복·실패 롤백·JSON/DB 제약 테스트 및 구현 문서.

HTTP API, 실제 금융상품/행동 추천 규칙, 효과·우선순위 점수 계산, 상태 변경 API, ImprovementAction 생성,
Simulation·군집 학습/분류는 추가하지 않는다. 공개 API 명세의 요청 모양을 변경하지 않는다.
후보 생성기의 확장점이 존재하는 것과 실제 추천을 생성할 수 있는 것은 구분해 보고한다.

## 3. 브랜치와 독립 작업

- 브랜치명은 Jira 547의 Create branch로 만든 `S15P21D105-547-api-16-recommendation`을 사용하며, 기준은 최신 `origin/develop`이다.
- 현재 546 브랜치(`S15P21D105-546-api-14`)는 아직 develop에 병합되지 않았고 미추적 파일이 있으므로 구현은 **별도 worktree**(`.worktrees/547`)에서 진행한다. 기존 변경을 stash·이동·초기화하지 않는다.
- 543·Goal 기반(V5·V17)과 544·545(`b90ddb8`)는 2026-09-13 확인 기준 `origin/develop`에 있으므로 546 브랜치에서 연쇄 분기하지 않는다. 546 병합을 기다릴 필요도 없다. 충돌 회피 규칙은 8절을 따른다.
- 없으면 누락된 선행 변경과 의존성을 먼저 보고한다. 이 계획 작성 자체는 브랜치/worktree를 생성하지 않는다.
- 커밋·push·MR 생성은 요청받은 경우에만 한다.

## 4. 구현 순서

### 4.1 스키마와 엔티티

현재 V18은 로컬 미사용이지만 V18~V29는 BE-B·Infra 공유 예비 구간이다. 원격 사용 여부와 마이그레이션 규칙을 확인한 뒤 번호를 확정한다.
파일명 후보는 `V18__create_recommendation.sql`이며 번호를 선점한 것으로 간주하지 않는다.
이미 V30까지 적용된 DB가 있을 수 있어 out-of-order 경로도 검증한다. 적용된 기존 마이그레이션은 수정하지 않는다.
DDL은 README 규칙대로 ERD SQL의 세 테이블 정의(905~979행)를 **그대로** 옮긴다. `uk_recommendation_user_recommendation_id (user_id, recommendation_id)`는 후속 `improvement_action` 복합 FK 대상이므로 빠뜨리지 않는다. 복합 FK 대상인 `diagnosis(user_id, diagnosis_id)`·`goal(user_id, goal_id)` UNIQUE는 V17·V5에 이미 있다(2026-09-13 확인).

| 대상 | 보존할 계약 |
| --- | --- |
| Recommendation | userId·diagnosisId 필수, goalId 선택, priority > 0, 상태 5종, 효과 금액 nullable·0 이상 |
| RecommendationReason | recommendationId 필수, code 50자 nullable, description 필수, evidence JSON |
| RecommendationHistory | recommendationId 필수, 상태 5종, reason/memo 각 500자, changedAt 필수 |
| 소유권 | recommendation의 `(user_id, diagnosis_id)`, `(user_id, goal_id)` 복합 FK |
| 중복 | `(user_id, generation_key)` UNIQUE, `(recommendation_id, code)` UNIQUE |

초기 추천 상태는 PENDING을 기본안으로 한다. 상태 enum은 PENDING/ACCEPTED/REJECTED/DEFERRED/COMPLETED만 둔다.
상태 전이 허용표를 547에서 임의 확정하지 않는다. 이력에는 현재 ERD대로 변경 후 상태만 저장하며 이전 상태 컬럼을 추가하지 않는다.
관계는 ID 기반으로 두고 하위 컬렉션의 무조건 eager 로딩·cascade 삭제를 추가하지 않는다.
evidence는 기존 Diagnosis와 동일하게 JsonNode + Hibernate JSON 매핑을 사용한다.
금액은 Long, 비율 계산이 필요한 입력 계약은 BigDecimal을 사용한다. 미계산 효과는 null이고 0원으로 채우지 않는다.
advancedDays의 부호·예측일 간 관계는 ERD에 제약이 없으므로 임의 CHECK를 추가하지 않고 548의 계산 계약에서 확정한다.

### 4.2 소유권과 조회 Repository

- 추천 헤더 조회는 recommendationId와 userId를 함께 조건으로 건다.
- Reason/History에는 userId가 없으므로 부모 Recommendation을 조인해 쿼리 내부에서 소유권을 검사한다.
- 목록은 사용자·status/domain 선택 필터와 Pageable을 받는다. 기본 정렬은 createdAt DESC, recommendationId DESC를 제안한다.
- 근거는 reasonId ASC, 이력은 changedAt DESC, recommendationHistoryId DESC로 안정 정렬한다.
- API 16의 사용자 전체 이력 조회를 위해 recommendationId 없이도 부모 userId로 기간·페이지 조회할 수 있게 한다.
- 목록/이력의 HTTP 기본 page=0, size=20, 최대 100 및 기간 검증은 후속 API 계층에서 적용한다.
- 타인/없는 추천은 동일 empty/Optional.empty 계약이다. 조회 API 계층에서 RESOURCE_NOT_FOUND로 변환한다.
- 재생성·재요청이 기존 ACCEPTED/COMPLETED 등을 PENDING으로 되돌리거나 근거·이력을 덮어쓰지 않도록 한다.

### 4.3 후보 생성 경계

예상 구조:

```text
domain/recommendation/
  entity/      Recommendation, RecommendationReason, RecommendationHistory, RecommendationStatus
  repository/  RecommendationRepository, RecommendationReasonRepository, RecommendationHistoryRepository
  dto/         RecommendationInput, RecommendationCandidate, CandidateGenerationResult
  service/     RecommendationInputReader, RecommendationCandidateGenerator,
               RecommendationCandidateCollector, RecommendationStore, RecommendationGenerationKey
```

입력 Reader는 `userId + diagnosisId + optional goalId`를 받아 소유한 **완료 진단**과 저장된 문제/근거를 읽는다.
미완료 진단·타인 진단·없는 진단을 거부한다. 544의 HTTP DTO를 import하지 않는다.
goalId가 명시되면 삭제되지 않은 본인 목표를 검증한다. goalId가 생략되면 진단 goalId를 사용할지에 대해서는 아래 기본안을 따른다.

입력 모델에는 diagnosisId, 진단 ruleVersion, goalId, 문제 domain/type/severity, 현재값·기준값·근거·detectionSource·군집 컨텍스트와
가능한 coverage 정보를 담는다. 옛 543 데이터처럼 snapshot/coverage가 없으면 UNKNOWN으로 보존한다. 목표/진단 원문 스냅샷을 공개 DTO로 그대로 노출하지 않는다.

후보는 stableCandidateKey, domain/type, title/description, 근거 목록, nullable 효과값을 반환한다.
priority는 점수 계산을 547에 넣지 않고 저장 시 호출자가 양수로 제공하도록 한다. 548이 정렬/순위를 결정한다.
Generator는 순수 입력 → 후보 결과 경계이며 저장·외부 API 호출을 직접 수행하지 않는다.
Collector는 지원 가능한 생성기를 선택하고 결정적인 순서로 수집한다.

생성기 미등록/지원하지 않는 문제는 빈 목록과 함께 `NO_GENERATOR`/`UNSUPPORTED_ISSUE_TYPE` 사유를 반환한다.
문제가 없어서 후보가 없는 경우와 생성기 미구현은 구분한다. 데이터 부족·군집 없음은 독립 사유로 보존한다.
테스트용 생성기로 입력 전달·중복 후보 검출·결정적 순서·지원하지 않는 유형을 검증하며 운영용 가짜 추천을 만들지 않는다.

### 4.4 저장 원자성과 중복 방지

기본안: 후보 집합 저장은 짧은 단일 트랜잭션으로 묶고 사용자 행 잠금 아래 기존 키 조회와 신규 저장을 수행한다.
잠금은 `diagnosis/repository/DiagnosisUserLock`과 같은 방식(`User` 행 `PESSIMISTIC_WRITE`)으로 **`recommendation/repository/RecommendationUserLock`을 별도로 둔다.** 진단 패키지의 Repository를 가져다 쓰지 않아 도메인 결합과 8절의 파일 겹침을 피한다. 같은 `user` 행을 잠그므로 진단 완료와 추천 저장이 직렬화되는 것은 의도된 동작이다.
계산·후보 수집은 잠금 밖에서 수행하며 저장 직전에 진단 완료·소유권과 목표 유효성을 다시 확인한다.
추천 본문·Reason 목록·최초 PENDING 이력 1건을 함께 저장한다. 일부 실패하면 전체 롤백한다.

generationKey는 버전이 있는 정규화 입력을 SHA-256으로 해시한 64자리 hex다.
입력은 `keySchemaVersion + userId + diagnosisId + resolvedGoalId(null 표기 고정) + generatorVersion + stableCandidateKey`를 제안한다.
문자열 구분자만 이어붙여 충돌시키지 않고 배열 등 순서가 고정된 직렬화 형식을 쓴다.
시각·리스트 순번·임의 UUID·사용자 표시 문구는 키에 사용하지 않는다. 새로운 진단은 별도 후보 키 공간이다.

DB의 generation_key가 nullable이어도 서비스가 만드는 신규 추천은 키를 필수로 요구한다.
같은 후보를 재저장하면 기존 추천을 반환하며 최초 이력과 근거를 다시 만들지 않는다.
같은 키에 다른 의미의 payload가 들어오면 조용히 덮어쓰지 않고 계약 충돌을 알린다.
DB UNIQUE는 우회 호출의 최종 방어선이다. UNIQUE 예외를 잡은 rollback-only 트랜잭션에서 조회를 이어가는 구현은 금지한다.

Reason.code는 DB에서 nullable이지만 **547 생성 경로에서는 비어 있지 않은 안정 코드**를 요구해 MySQL의 NULL UNIQUE 예외를 피한다.
같은 추천 안에서 코드가 중복되면 저장 전에 검증한다. 한 코드에 여러 근거가 필요하면 evidence 배열로 표현한다.
Recommendation/Reason/History는 계산 근거 보존을 위해 재생성 시 삭제하지 않는다. 실패한 신규 트랜잭션은 롤백한다.

## 5. 기본안과 후속 확정 사항

| 항목 | 547 기본안 / 후속 작업 |
| --- | --- |
| goalId 생략 | 진단의 goalId를 사용, 진단도 null이면 null. 548 공개 API 문서에 명시 |
| 명시 goalId와 진단 goalId가 다름 | 같은 사용자 목표라도 허용 여부는 548에서 확정. 547 입력 Reader는 기본적으로 불일치 거부해 잘못된 목표 근거 재사용 방지 |
| 삭제된 진단 연결 목표 | 새 생성은 거부, 기존 추천 조회는 저장 당시 근거 보존. 정책을 548에 인계 |
| 초기 이력 | 새 추천에 PENDING 1건, 재요청은 이력 추가 없음 |
| 상태 전이 | enum/저장 구조만 547. accept/reject/complete/defer 전이·멱등 정책은 549 |
| 우선순위 | 양수 제약만 검증. 위험도·효과·기여도·시급성·난이도·군집 가중치는 548 |
| 효과값 | 미계산 null. 연간 절감액을 월 절감액×12로 무조건 채우지 않음 |
| 미구현 진단 규칙 | 기존 입력을 읽을 수 있어도 해당 추천 규칙이 없으면 명시적 미지원. 544·545 전체 완료를 가정하지 않음 |

위 기본안은 ERD/API가 정하지 않은 구현 선택이다. 더 최신 Jira 요구와 충돌하면 코드 작성 전에 수정한다.
미래 목표기여 계산을 위해 546 HTTP API를 호출하는 의존성을 미리 추가하지 않는다.

## 6. 검증 계획

1. 엔티티: PENDING 생성, priority/금액/문자열 길이·필수값, nullable 효과, Reason.code 중복 검증.
2. Repository: 사용자 격리, 타인 추천의 근거/이력 차단, 필터·정렬·동일 시각 tie-break·빈 결과.
3. 입력 Reader: 완료/미완료/없는/타인 진단, 본인/타인/삭제 목표, 불일치 goalId, 과거 snapshot 누락.
4. 후보 골격: 생성기 0개, 지원하지 않는 문제, 테스트 생성기 수집, 안정 키, 중복 후보와 데이터 부족 사유.
5. 저장: 추천+근거+초기 이력 원자성, 중간 실패 롤백, 동일 요청 순차/동시 재시도, 상태·이력 불변성, 키 충돌 검출.
6. MySQL: Flyway 신규/기존 V30 적용 DB 업그레이드, JSON_EXTRACT로 객체 저장 확인, 복합 FK·UNIQUE·CHECK·사용자 잠금 검증.
   **제약**: `application-test.yml`은 H2 + `ddl-auto: create-drop`, `flyway.enabled: false`다. 따라서 (a) H2에서 UNIQUE 위반 테스트가 성립하려면 엔티티 `@Table(uniqueConstraints=...)`에 `(user_id, generation_key)`·`(recommendation_id, code)`를 선언해야 하고, (b) CHECK·복합 FK는 H2 테스트로 검증되지 않으므로 priority > 0·금액 ≥ 0·상태값은 서비스/엔티티 검증으로 막고 MySQL 미검증으로 보고한다. (c) V18 out-of-order 적용은 기존 `FlywayOutOfOrderTest`(`test/resources/db/outoforder`) 방식으로 재현한다.
7. 관련 테스트 → 전체 test/clean build → git diff --check. MySQL 환경이 없으면 H2 성공과 MySQL 미검증을 구분한다.

Controller를 추가하지 않으므로 547에 HTTP 인증 테스트를 억지로 추가하지 않는다. 548·549에서 무인증 401·입력 400·소유권 404를 검증한다.

## 7. 문서와 완료 기준

- `backend/docs/recommendation.md`: 실제 저장 계약·입력 경계·초기 상태·정렬·null·중복 정책·현재 제한사항.
- 548·549 인계사항과 MySQL 미검증 범위는 `recommendation.md` 안의 "진행 현황" 절에 둔다. `remaining-work.md`는 546이 수정 중이므로 547 브랜치에서 건드리지 않는다(8.1).
- `backend/src/main/resources/db/migration/README.md`: 버전 표에 V18 행 1줄 추가. 546은 이 파일을 수정하지 않았으므로 겹치지 않으며, MR 직전 develop과 재대조한다.
- 루트 docs의 ERD/API는 기준 자료로 읽고 변경하지 않는다. 차이가 필요하면 근거와 함께 보고한다.

547 완료는 **3개 테이블과 저장/조회 계약, 실제로 검증된 후보 생성 골격이 준비된 상태**다.
실제 금융 추천 규칙이나 공개 Recommendation API가 완료됐다고 보고하지 않는다.
548은 이 기반에 완료 진단의 실제 문제 유형을 연결하고, 549는 이력 저장 구조 위에 상태 변경을 구현한다.

## 8. 546 미병합 상태에서의 충돌 방지 계획

작성일: 2026-09-13. 546(`S15P21D105-546-api-14`, develop 대비 3커밋)과 547이 서로 다른 순서로 develop에 병합되더라도
충돌이 나지 않도록 **547이 손대는 파일 집합을 546이 손댄 파일 집합과 완전히 분리**하는 것이 원칙이다.

### 8.1 546이 변경한 파일 (547 수정 금지 목록)

`git diff --stat origin/develop..S15P21D105-546-api-14` 기준이다. 547 브랜치에서는 아래 파일을 **어떤 이유로도 수정하지 않는다.**

| 구분 | 파일 | 546의 변경 | 547이 필요할 때의 대안 |
| --- | --- | --- | --- |
| 소스 | `domain/diagnosis/controller/DiagnosisController.java` | goal-impact·changes 엔드포인트 추가 | 547은 Controller를 추가하지 않음. 필요 없음 |
| 소스 | `domain/diagnosis/repository/DiagnosisRepository.java` | `findPreviousCompleted` JPQL 추가 | 완료 진단 조회 메서드가 더 필요하면 **`recommendation` 패키지에 별도 Repository/Reader**를 두고 기존 메서드(`findByDiagnosisIdAndUserId` 등)만 호출 |
| 소스 | `domain/diagnosis/service/DiagnosisEvaluator.java` | 진단 입력 스냅샷에 `forecast` 키 추가 | 547 후보 생성기는 `forecast` 키를 **선택적**으로 읽는다(없으면 null 처리). 존재를 전제하지 않는다 |
| 소스 | `domain/diagnosis/service/DiagnosisRuleVersion.java` | `CURRENT`를 `v3-goal-impact`로 변경 | 547은 `ruleVersion`을 진단 행에서 읽은 문자열로 그대로 보존만 하고 상수와 비교하지 않는다 |
| 소스 | `domain/goal/service/GoalCalculationService.java` | 진단용 `getForecast(userId, goalId, today)` 오버로드 추가 | 547은 Goal 계산을 호출하지 않는다. 목표 스냅샷은 진단 `input` JSON에서만 읽는다 |
| 신규 소스 | `DiagnosisChangeService`, `DiagnosisGoalImpactService`, `DiagnosisChangesResponse`, `DiagnosisGoalImpactResponse` | 신규 | 참조하지 않는다 |
| 테스트 | `diagnosis/DiagnosisGoalImpactApiTest.java` | 신규 | 참조하지 않는다 |
| 문서 | `backend/docs/diagnosis-api.md`, `diagnosis.md`, `diagnosis-goal-impact-plan.md` | 계약·현황 갱신 | 547에서 수정하지 않는다 |
| 문서 | `backend/docs/remaining-work.md` | 546 진행 현황 절 추가 | **가장 충돌 가능성이 높은 파일.** 547 브랜치에서는 수정하지 않고 547 진행 현황은 `backend/docs/recommendation.md` 안에 둔다. 두 브랜치가 모두 병합된 뒤 포인터 한 줄만 별도 커밋으로 추가한다 |

546이 건드리지 않은 공용 파일 중 547도 건드리지 않을 것: `build.gradle`, `application*.yml`, `ErrorCode`, `global/` 전반, `db/migration/README.md`는 예외로, 버전 표에 V18 행 1줄만 추가한다(README 규칙상 각 도메인 티켓이 자기 버전을 기록한다).

### 8.2 547이 새로 만드는 파일만으로 완결

547의 모든 산출물은 아래 경로에 **신규 파일**로만 추가한다. 기존 파일 수정이 필요하면 그 시점에 이 절을 갱신하고 이유를 남긴다.

- `backend/src/main/resources/db/migration/V18__create_recommendation.sql`
  2026-09-13 기준 원격 9개 브랜치 어디에도 V18~V29가 없음을 확인했다. 착수 직전 `git fetch --all` 후 같은 검사를 반복하고, 누군가 V18을 선점했으면 V19 이후로 옮긴다. `V30`이 이미 있으므로 out-of-order 적용 테스트를 포함한다.
- `backend/src/main/java/com/ssafy/dodam/domain/recommendation/{entity,repository,service,dto}/**`
- `backend/src/test/java/com/ssafy/dodam/domain/recommendation/**`
- `backend/docs/recommendation.md`(구현 기준 문서), 이 계획 문서

진단 데이터 접근은 `domain/recommendation/service/CompletedDiagnosisReader`(가칭)로 한정하고, 이 Reader는 `DiagnosisRepository`·
`DiagnosisIssueRepository`(엔티티 `DiagnosisIssue`)의 **develop에 이미 있는 메서드만** 호출한다. 546은 이 두 파일 중 `DiagnosisRepository`만 수정했고 `DiagnosisIssueRepository`·`DiagnosisUserLock`은 건드리지 않았다. 546이 추가한 메서드를 호출하면 546 병합 전에는 컴파일이 되지 않으므로 금지한다.

### 8.3 진행 절차

1. 현재 546 worktree의 미추적 파일 정리: `backend/docs/recommendation-foundation-plan.md`는 547 산출물이므로 547 worktree로 **복사**한다. 546 worktree의 원본과 `backend/docs/reviews/`는 사용자가 처리하며 에이전트가 삭제하지 않는다.
2. `git fetch origin` 후 `git worktree add .worktrees/547 -b S15P21D105-547-api-16-recommendation origin/develop` (Jira Create branch로 원격에 이미 만들어졌다면 `-b` 대신 원격 브랜치를 checkout).
3. 착수 직후 V18 선점 여부(8.2)와 `origin/develop`에 V5·V17·`b90ddb8`이 여전히 포함되는지 재확인한다.
4. 구현 중 커밋 단위는 (a) 마이그레이션+엔티티 (b) Repository+서비스 (c) 후보 생성 골격 (d) 테스트 (e) 문서 순으로 나눠, 병합 시 부분 되돌리기가 가능하게 한다.
5. MR 생성 직전 `git fetch origin && git merge origin/develop`(또는 rebase)으로 최신화하고 `.\gradlew.bat test`를 다시 돌린다. 이때 546이 먼저 병합됐다면 `DiagnosisRuleVersion.CURRENT`가 바뀌어 있으므로 547 테스트가 상수를 하드코딩하지 않았는지 확인한다.
6. 병합 순서는 546→547, 547→546 어느 쪽이든 무방하다. 두 브랜치 모두 병합된 뒤에만 `remaining-work.md`에 547 포인터를 추가한다.

### 8.4 검증 명령

MR 전 두 브랜치의 변경 파일 교집합이 비어 있어야 한다. 출력이 없으면 파일 단위 충돌은 없다.

```bash
comm -12 <(git diff --name-only origin/develop..origin/S15P21D105-546-api-14 | sort) \
         <(git diff --name-only origin/develop..HEAD | sort)
```

실제 병합 시뮬레이션(작업 트리를 바꾸지 않음):

```bash
git merge-tree $(git merge-base origin/develop HEAD) HEAD origin/S15P21D105-546-api-14 | grep -c "<<<<<<<"
```

0이 아니면 8.1의 금지 목록에 어긋난 변경이 있는 것이다.

