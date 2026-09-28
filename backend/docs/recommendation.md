# Recommendation 저장 기반 (S15P21D105-547 · 628)

작성일: 2026-09-13, 갱신 2026-09-16(628 `source_issue_id`). API 16 Recommendation의 저장 구조·입력 경계·후보 생성 골격이다.
HTTP API는 없다. 생성·조회 API(548)와 상태 변경·이력 API(549)가 이 위에서 구현한다.
계획과 결정 배경은 [recommendation-foundation-plan.md](recommendation-foundation-plan.md)에 있다.

기준 자료: [API 16](../../docs/API명세서/16%20Recommendation%203cdbb6ea3564801eb1bbc55201aee86a.csv),
[ERD SQL](../../docs/ERD/DODAM_ERDCLOUD_IMPORT.sql) 905~979행, [Diagnosis 저장 구조](diagnosis.md).

## 1. 범위

| 포함 | 제외(후속) |
| --- | --- |
| V18 마이그레이션 3테이블 | HTTP Controller·응답 DTO (548·549) |
| 엔티티 3종 + `RecommendationStatus` | 실제 금융상품·행동 추천 규칙 (548) |
| 소유권 조건이 들어간 Repository | 효과·우선순위 점수 계산 (548) |
| 완료 진단 → 입력 모델 Reader | 상태 전이 정책·accept/reject/complete/defer (549) |
| 후보 생성기 인터페이스·수집기 | `improvement_action` 생성, Simulation, 군집 학습 |
| 추천+근거+초기 이력 원자 저장, 중복 방지 | 전용 `ErrorCode` (548의 HTTP 계약에서 추가) |

~~**운영 생성기는 없다.**~~ 2026-09-13 548에서 운영 생성기 3종(`service/generator/`)과 HTTP API를 추가했다.
계약은 [recommendation-api.md](recommendation-api.md)에 있다. 아래 §1~§8은 547의 저장 기반 설명이며 그대로 유효하다.

## 2. 스키마

`V18__create_recommendation.sql`. ERD SQL의 세 테이블 정의를 그대로 옮겼다(diff로 확인).
V18~V29 BE-B 예비 구간의 첫 번호이며 V30이 먼저 적용된 DB에는 out-of-order로 들어간다.

| 테이블 | 핵심 제약 |
| --- | --- |
| `recommendation` | `(user_id, recommendation_id)` UNIQUE(후속 `improvement_action` FK 대상), `(user_id, generation_key)` UNIQUE, `(user_id, diagnosis_id)`→`diagnosis`·`(user_id, goal_id)`→`goal` 복합 FK, CHECK: status 5종·priority > 0·금액 4종 ≥ 0. **628 `V36`**: `source_issue_id BIGINT NULL` + `(user_id, source_issue_id)` 인덱스 + `diagnosis_issue(issue_id)` 단일 FK |
| `recommendation_reason` | `(recommendation_id, code)` UNIQUE, `evidence` JSON |
| `recommendation_history` | CHECK status 5종, `(recommendation_id, changed_at)` 인덱스 |

`source_issue_id`(628)는 후보의 근거가 된 진단 문제다. 628 이전 행과 여러 문제를 합친 후보는 NULL이며 기존 행은 채우지 않는다(재생성은 같은 `generation_key`로 기존 행을 돌려주므로 값이 생기지 않는다). `diagnosis_issue`에 `(diagnosis_id, issue_id)` UNIQUE가 없어 진단 일치를 복합 FK로 걸 수 없고, 소유권·진단 일치는 Store가 저장 전에 검증한다(§6). 완료 진단의 문제 행은 삭제되지 않으므로(미완료 정리만 삭제) 단일 FK로 충분하다.

엔티티에도 같은 UNIQUE를 `@Table(uniqueConstraints)`로 선언했다. H2 테스트는 Flyway 없이 엔티티로 스키마를
만들기 때문에, 선언이 없으면 중복 방지가 테스트에서 검증되지 않는다. CHECK는 H2에 없으므로 엔티티 생성자가
먼저 막는다(priority ≤ 0, 음수 금액, 빈 문자열, 키 형식).

## 3. 데이터 정책

- **초기 상태**: 새 추천은 `PENDING`, 같은 트랜잭션에서 `recommendation_history`에 PENDING 1건을 남긴다.
  이력에는 ERD대로 변경 후 상태만 저장한다.
- **효과값**: `monthly_cost_saving` 등 8개 컬럼은 547에서 모두 null이다. 미계산을 0으로 채우지 않고,
  연간 절감액을 월×12로 임의 계산하지 않는다. 계산 계약은 548.
- **priority**: 저장 시 호출자가 양수로 넘긴다. 점수 계산·정렬 규칙은 548.
- **generation_key**: DB는 nullable이지만 서비스 경로는 항상 채운다. MySQL은 NULL끼리 UNIQUE 충돌이 없어
  NULL 행은 중복 방지가 되지 않는다.
- **reason.code**: 같은 이유로 서비스 경로에서 필수(1~50자). 한 코드에 근거가 여럿이면 `evidence`를 배열로.
- **하드 삭제 없음**: 재생성은 기존 행을 돌려주고, 상태 변경은 이력 행을 추가한다(ERD 설명의 근거 보존).
- **금액**: `Long` 원 단위. 부동소수점 없음.

### generation_key 구성

`RecommendationGenerationKey.of(userId, diagnosisId, goalId, generatorVersion, candidateKey)`
→ `v1|<len>:<userId>|<len>:<diagnosisId>|n 또는 <len>:<goalId>|<len>:<generatorVersion>|<len>:<candidateKey>`의 SHA-256 hex(64자).

- 길이 접두 직렬화라 `("ab","c")`와 `("a","bc")`가 충돌하지 않는다. null goalId는 `n`으로 빈 문자열과 구분한다.
- 시각·순번·UUID·표시 문구는 넣지 않는다. 새 진단(diagnosisId)·새 규칙(generatorVersion)은 별도 키 공간이다.
- 구성 요소를 바꾸면 `SCHEMA_VERSION`을 올려 옛 키와 충돌하지 않게 한다.

## 4. 입력 경계 (`RecommendationInputReader`)

`read(userId, diagnosisId, requestedGoalId)` → `RecommendationInput`.

| 상황 | 결과 |
| --- | --- |
| 타인·없는 진단 | `ResourceNotFoundException` (존재 여부를 흘리지 않음) |
| 미완료 진단(`completed_at` NULL) | `BusinessException(INVALID_REQUEST)` |
| goalId 생략 | 진단의 goalId를 물려받음. 진단도 null이면 null |
| goalId 명시, 진단 goalId와 같음 | 그대로 사용 |
| goalId 명시, 진단 goalId와 다름 (진단이 null 포함) | `BusinessException(INVALID_REQUEST)` |
| 확정된 goalId가 타인·없음·삭제된 목표 | `ResourceNotFoundException` (불일치보다 먼저 검사해 타인 목표 존재를 흘리지 않음) |

입력 모델은 진단 헤더(`ruleVersion`, `completedAt`, `inputSnapshot`)와 문제 목록(issueId ASC)을 담는다.
`ruleVersion`은 `DiagnosisRuleVersion.CURRENT`와 비교하지 않고 문자열로 보존한다 — 옛 진단도 입력이 된다.
`inputSnapshot`은 543 초기 데이터처럼 null일 수 있으며 생성기는 없음을 전제해야 한다.
544·545의 HTTP DTO와 실행 서비스는 import하지 않는다. `DiagnosisRepository.findByDiagnosisIdAndUserId`,
`DiagnosisIssueRepository.findAllOwned`, `GoalRepository.findByIdAndUserIdAndDeletedAtIsNull`만 사용한다.

## 5. 후보 생성 골격

```text
domain/recommendation/
  entity/      Recommendation(+Effect), RecommendationReason, RecommendationHistory, RecommendationStatus
  repository/  RecommendationRepository, RecommendationReasonRepository, RecommendationHistoryRepository,
               RecommendationUserLock
  dto/         RecommendationInput(+Issue), RecommendationCandidate(+Reason), CandidateGenerationResult(+Skipped, SkipReason)
  service/     RecommendationInputReader, RecommendationCandidateGenerator(+Outcome),
               RecommendationCandidateCollector, RecommendationGenerationKey, RecommendationStore
```

- `RecommendationCandidateGenerator`: `version()`, `supports(issue)`, `generate(input, issue) → Outcome`.
  순수 함수여야 한다(저장·외부 호출·시각 조회 없음). `Outcome`은 후보 목록이거나 `SkipReason` 중 하나다.
- `RecommendationCandidateCollector`: 문제 issueId 순 → 생성기 빈 등록 순(`@Order`)으로 결정적으로 수집한다.
  한 문제에 여러 생성기가 지원을 선언하면 모두 적용한다. `(generatorVersion, candidateKey)` 중복이나
  생성기 버전과 다른 후보 버전은 `IllegalStateException`으로 실패시킨다.
- `CandidateGenerationResult.skipped`가 "후보 없음"의 이유를 보존한다:
  `NO_GENERATOR`(생성기 0개) / `UNSUPPORTED_ISSUE_TYPE` / `INSUFFICIENT_DATA` / `NO_CLUSTER_CONTEXT`.
  문제가 0개면 skipped도 비어 있다 — 건강한 상태와 미구현을 구분한다.

## 6. 저장 (`RecommendationStore.save(SaveRequest, now)`)

한 트랜잭션: 요청 자체 검증 → 사용자 행 잠금(`RecommendationUserLock`, PESSIMISTIC_WRITE) → 진단 소유·완료
재확인 → 목표 존재·일치 재확인 → 후보 `sourceIssueId`의 진단 일치 재확인(628) → 후보별로 키 조회 → 없으면 추천(+`source_issue_id`)+근거+PENDING 이력 INSERT.

| 상황 | 결과 |
| --- | --- |
| 같은 키가 이미 있음 | 새 행 없이 기존 추천 반환(`Saved.created=false`). 기존 상태·근거·이력 불변 |
| 같은 키인데 domain/type이 다름 | `IllegalStateException` (생성기 키 구성 결함, 덮어쓰지 않음) |
| 요청 안 후보 키 중복 / 한 후보 안 근거 코드 중복 | `IllegalArgumentException`, INSERT 전에 실패 |
| 후보 `sourceIssueId`가 이 진단의 본인 문제가 아님 (628) | `IllegalStateException`, INSERT 전에 실패. 후보는 Reader가 소유권 조회로 읽은 문제에서만 나오므로 어긋나면 생성기 결함. 잠금 아래에서 `findAllOwned`로 문제 목록을 다시 읽어 대조하며, 문제 id가 있는 후보가 없으면 조회하지 않는다 |
| priority ≤ 0 | `IllegalArgumentException` (`PrioritizedCandidate` 생성 시) |
| 타인·없는 진단, 없는 사용자, 타인·삭제 목표 | `ResourceNotFoundException` |
| 미완료 진단, goalId ≠ 진단 goalId (목표 연계 진단에 null 포함) | `BusinessException(INVALID_REQUEST)` |
| 후보 0개 | 아무것도 저장하지 않고 빈 목록 |

잠금은 `DiagnosisUserLock`과 같은 방식이지만 추천 패키지에 별도로 둔다(도메인 결합 회피). 같은 `user` 행을
잠그므로 진단 완료와 추천 저장이 직렬화되는 것은 의도된 동작이다. 후보 수집(계산)은 이 트랜잭션 밖에서 끝낸다.
DB UNIQUE는 잠금을 우회한 호출의 최종 방어선이며, UNIQUE 예외를 잡아 rollback-only 트랜잭션에서 조회를
이어가지 않는다.

## 7. 조회 계약 (Repository)

| 메서드 | 소유권 | 정렬 |
| --- | --- | --- |
| `RecommendationRepository.findByRecommendationIdAndUserId` | userId 조건 | — |
| `findOwned(userId, status?, domain?, pageable)` | userId 조건. status/domain NULL이면 필터 생략 | `createdAt DESC, recommendationId DESC` |
| `findAllByUserIdAndDiagnosisId…` | userId 조건 | 위와 같음 |
| `RecommendationReasonRepository.findOwned(userId, recommendationId)` | `recommendation` 조인 | `reasonId ASC` |
| `RecommendationHistoryRepository.findOwned(userId, recommendationId)` | `recommendation` 조인 | `changedAt DESC, id DESC` |
| `findOwnedInPeriod(userId, from, to, pageable)` | `recommendation` 조인, `[from, to)` | 위와 같음 |

타인·없는 리소스는 모두 `Optional.empty()`·빈 결과다. API 계층(548·549)이 `RESOURCE_NOT_FOUND`로 변환한다.
페이지 기본 20·최대 100, 이력 기간 기본값·최대 기간(`QueryPeriodPolicy`)도 API 계층에서 적용한다.
API 16 priorities의 우선순위 정렬은 점수 계약이 548에 있으므로 여기서 정하지 않았다.

## 8. 검증

H2 (`.\gradlew.bat test --tests 'com.ssafy.dodam.domain.recommendation.*'`, 2026-09-13 통과, 35건):

- `RecommendationGenerationKeyTest` 5: 안정성, 구성 요소별 변화, null goal 구분, 경계 충돌 없음, 빈 값 거부
- `RecommendationCandidateCollectorTest` 8: 생성기 0개, 문제 0개, 미지원 유형, 결정적 순서, 사유 보존, 중복 키·버전 불일치 실패
- `RecommendationRepositoryTest` 4: 헤더·근거·이력 소유권 격리, 필터·정렬 tie-break, 기간 반개구간, size 1 페이지로 count 쿼리 실행
- `RecommendationStoreTest` 18: Reader 6(완료/미완료/타인/goal 상속·불일치·타인·삭제), Store 11(원자 저장, 멱등, 버전·진단별 키 공간, 계약 충돌, 요청 단위 실패, 요청 내 키 중복, priority, 재검증, goal 일치, 빈 요청, DB UNIQUE), 엔티티 검증 1

MySQL 8.0.46 (`RecommendationMySqlIntegrationTest`, `RECOMMENDATION_MYSQL_TEST_URL` 설정 시만 실행, **2026-09-13 3건 통과**):
V18·V30 Flyway 적용, `ddl-auto: validate` 통과(V18 DDL ↔ 엔티티 매핑 일치), 복합 FK 2종, CHECK 4종, `evidence` JSON 타입,
JSON_EXTRACT, 타인 진단 참조 FK 거부, priority/금액/status CHECK 위반.

### 알아둘 점: 빈 DB에서 Flyway가 V6에서 실패한다 (develop 기존 문제)

`V6__create_transaction…`이 `card`(V11)를 FK로 참조하는데 버전 순서상 V6이 먼저 실행된다. 운영·기존 로컬 DB는
V11이 먼저 적용된 뒤 V6이 out-of-order로 들어가 영향이 없지만, **새 DB(신규 팀원·CI·일회용 컨테이너)에서는
기동에 실패한다.** 547 범위 밖이라 고치지 않았다. 위 검증은 V1~V17을 V6을 V11 뒤로 돌려 수동 적용하고
`SPRING_FLYWAY_BASELINE_ON_MIGRATE=true SPRING_FLYWAY_BASELINE_VERSION=17`로 실행했다(테스트 Javadoc 참고).
이 방식에서는 V18의 out-of-order 적용 자체는 확인되지 않는다(`FlywayOutOfOrderTest`가 담당).

## 9. 현재 제한사항과 인계

- [ ] **develop 기존 문제 보고**: 빈 DB에서 V6이 V11보다 먼저 실행돼 Flyway가 실패한다(§8). BE-A(V6 소관)와 확인해 V6의 `card` FK를 분리하거나 번호를 조정해야 한다.
- [x] 548: 전용 `ErrorCode` — `DIAGNOSIS_NOT_COMPLETED`(409)·`RECOMMENDATION_GOAL_MISMATCH`(400). Reader·Store가 `INVALID_REQUEST` 대신 사용.
- [x] 548: goalId 불일치는 계속 거부한다(recommendation-api.md §4).
- [x] 548: 우선순위·효과·priorities 정렬·운영 생성기 3종 (recommendation-api.md §2~§5).
- [x] 549: 전이표·같은 상태 재요청 409·`Recommendation.changeStatus` + `RecommendationHistory.of` (recommendation-api.md §6).
- [x] 546의 `forecast` 키는 읽지 않기로 했다. 생성기가 스냅샷의 금액 필드로 현재·개선 달성일을 같은 식으로 계산한다.
- 진단 삭제·목표 삭제 이후 기존 추천의 표시 정책은 정하지 않았다(저장 당시 근거는 보존).

## 10. 628 검증 (2026-09-16)

- H2: `RecommendationStoreTest` +1(후보 `sourceIssueId` 저장·null 허용, 같은 사용자의 다른 진단 문제·타인 진단 문제 → `IllegalStateException`과 전체 롤백, `pending(sourceIssueId ≤ 0)` 거부), 기존 `Recommendation.pending` 호출부 시그니처 갱신.
- MySQL 8.0 (`dodam-626-mysql` 컨테이너, DB `dodam_547_test`에 V1~V17 수동 적용 + baseline 17): `RecommendationMySqlIntegrationTest` 3건 통과, Flyway가 V18~V36 적용, `ddl-auto=validate` 통과. 존재하지 않는 `source_issue_id` INSERT는 `fk_recommendation_source_issue`로 거부됨을 mysql 클라이언트로 확인.
