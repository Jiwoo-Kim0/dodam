# 617 `SimulationMySqlIntegrationTest` 미작성 3항목 추가 계획

작성일: 2026-09-16. 브랜치 `S15P21D105-617-587-simulationmysqlintegrationtest-3`(Jira Create branch, `origin/develop` `cf5eb3c` 기준).
587 후속 4건(616~619) 중 두 번째. 결과는 [simulation.md](simulation.md) §11.3에 기록하고 [remaining-work.md](remaining-work.md) 587 절 "MySQL 검증에서 빠진 항목"을 닫는다.

## 1. 범위와 착수 근거

- 대상: S15P21D105-617. 로컬 내보내기([587-remaining-work.csv](../../SPRINT/587-remaining-work.csv)) Issue ID 159 "[587 후속] SimulationMySqlIntegrationTest 미작성 3항목 추가"(2026-09-16, 2pt). Jira 원문 제목이 다르면 이 절을 먼저 고친다.
- 이슈 설명: [simulation-587-plan.md](simulation-587-plan.md) §6에 있었지만 587에서 넣지 않은 세 가지를 MySQL 테스트에 추가한다.
  1. 복합 N행 scenario 저장 중 중간 실패 시 `simulation`·`simulation_scenario`·`simulation_goal_impact` 0행 롤백
  2. `idx_simulation_user_type_created` 순서(`created_at DESC`)의 목록 정렬
  3. `uk_simulation_user_simulation_id`(user_id, simulation_id) 조합의 본인 조건 조회
- 완료 기준: 세 항목이 MySQL 8.0에서 통과하고 simulation.md §11.3에 결과를 기록한다.
- 변경 파일: `backend/src/test/java/com/ssafy/dodam/domain/simulation/SimulationMySqlIntegrationTest.java`(테스트 3건 추가), `backend/docs/simulation.md` §11.3, `backend/docs/remaining-work.md`. **운영 코드·마이그레이션은 바꾸지 않는다.** 테스트가 운영 코드의 결함을 드러내면 기록하고 별도 이슈로 올린다.

### 현재 상태

`SimulationMySqlIntegrationTest`(587)는 4건: 스키마·JSON·CHECK·FK 존재, 실행·저장·재사용·삭제, 복합 N행 저장과 CHECK·UNIQUE·복합 FK 위반, 동시 실행 1건 저장. `SIMULATION_MYSQL_TEST_URL`이 있을 때만 실행되며 H2 빌드에는 영향이 없다. 기존 클래스에 메서드만 더하므로 Spring 컨텍스트 수는 늘지 않는다(587에서 겪은 컨텍스트 캐시 축출 문제 회피).

## 2. 선행 조건

- Docker Desktop 기동(계획 작성 시점에는 꺼져 있었다). `mysql:8.0` 컨테이너, `dodam_587_test` 스키마, 빈 비밀번호 root.
- 빈 스키마에서는 V6이 V11의 `card`를 참조해 Flyway가 실패하므로(recommendation.md §8) V1~V17을 V6을 V11 뒤로 돌려 수동 적용한 뒤 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true SPRING_FLYWAY_BASELINE_VERSION=17`로 V18 이후를 Flyway에 맡긴다(587과 같은 방식, simulation.md §11.3).
- Gradle은 셸마다 `JAVA_HOME` 설정이 필요하다.
- 테스트 파일은 CRLF다. 편집 후 줄끝을 유지한다.

## 3. 검증 설계

### 3.1 저장 중간 실패 롤백

- **왜 트리거인가**: `SimulationStore.save`는 `simulation` 1행 → `simulation_scenario` N행(IDENTITY라 `save`마다 즉시 INSERT) → `simulation_goal_impact` 순으로 한 트랜잭션(`SimulationRunner.run`의 `@Transactional`)에 넣는다. 운영 코드를 건드리지 않고 "N행 중간"에서 실패시키려면 DB 쪽에서 특정 행의 INSERT를 거부해야 한다. 테스트가 `BEFORE INSERT` 트리거를 만들어 `SIGNAL SQLSTATE '45000'`로 거부하고, `finally`에서 지운다. Mockito로 리포지토리를 대체하는 방법은 컨텍스트가 하나 더 생겨 587의 캐시 축출 문제를 재발시킨다.
- 두 지점에서 실패시킨다.
  - (a) `simulation_scenario`의 `sequence_no = 3` INSERT 거부 → `simulation` 1행과 scenario 2행이 이미 들어간 상태에서 실패.
  - (b) `simulation_goal_impact` INSERT 거부 → `simulation` + scenario N행이 모두 들어간 뒤 goal_impact에서 실패. 복합에 `SAVING` 자식을 넣어 goal_impact 행이 생기게 한다(setup의 목표 587901 사용).
- 확인: 5xx 응답(전역 `Exception` 핸들러가 500으로 돌려주지만 그 계약은 이 테스트의 관심이 아니므로 `is5xxServerError()`까지만 단언), 세 테이블 모두 사용자 행 0. 트리거 제거 후 같은 요청 → 201(부분 저장 행이 남아 재사용 200이 되지 않음). `SimulationUserLock`의 `FOR UPDATE` 잠금이 롤백으로 풀렸는지는 이 201로 함께 확인된다.
- 복합 본문: `SAVING(300,000)` + `INSTALLMENT(1,200,000/2/12)` + `CASH_ADVANCE(1,000,000/12)` 3건. (a)는 3번째 자식에서, (b)는 그 뒤 goal_impact에서 실패한다.

### 3.2 목록 정렬과 `idx_simulation_user_type_created`

- 데이터: 같은 사용자·같은 유형(`CASH_ADVANCE`) 3건을 clock을 하루씩 옮겨 만든다(같은 날 같은 요청은 재사용되므로 날짜를 바꿔야 3행이 된다). 다른 유형 1건(`INSTALLMENT`), 타인 1건을 더한다. 그중 2건은 jdbc로 `created_at`을 같은 값으로 맞춰 동률 정렬(`simulation_id DESC`)도 본다.
- 확인 1(동작): `GET /simulations?type=CASH_ADVANCE`가 `created_at DESC, simulation_id DESC` 순서, `GET /simulations`(유형 없음)도 같은 규칙, 타인 행 미포함. **유형 없는 목록 조회는 MySQL에서 처음 실행된다** — JPQL `(:type is null or s.type = :type)`의 null 바인딩은 587에서 H2로만 검증했고 MySQL 테스트는 `?type=SAVING`만 호출했다. 결함이 나오면 운영 코드를 고치지 않고 기록·별도 이슈.
- 확인 2(인덱스): `information_schema.STATISTICS`에서 `idx_simulation_user_type_created`의 컬럼 순서가 `user_id, type, created_at`인지. 그리고 `SimulationRepository.findOwned`가 만드는 SQL 모양(`WHERE user_id = ? AND deleted_at IS NULL AND (? IS NULL OR type = ?) ORDER BY created_at DESC, simulation_id DESC`)을 `EXPLAIN`해 `possible_keys`에 인덱스가 오르는지 본다. EXPLAIN 대상은 Hibernate가 만든 SQL이 아니라 같은 모양을 손으로 쓴 SQL이다(실제 SQL 캡처는 과하다). 테스트 주석에 원본 JPQL(`SimulationRepository.findOwned`) 위치를 적어 JPQL이 바뀌면 함께 고치게 한다. **`key`(실제 선택)는 단언하지 않는다** — 행이 몇 개 없는 테스트 테이블에서는 옵티마이저가 풀스캔을 고를 수 있어 단언하면 불안정하다. 실측된 `key`·`Extra`(filesort 여부)는 §11.3에 기록만 한다.
- 알아둘 점: JPQL의 `(:type is null or s.type = :type)`는 type 필터 없이 호출하면 인덱스의 두 번째 컬럼을 못 쓴다. 이는 587 설계이고 사용자·하루 단위 양이 작아 문제 삼지 않는다. EXPLAIN 결과에 차이가 보이면 기록한다.

### 3.3 `uk_simulation_user_simulation_id`와 본인 조건 조회

- 이 UNIQUE KEY의 역할은 (user_id, simulation_id) 조합을 `simulation_goal_impact`의 복합 FK가 참조하게 하는 것이다(소유자가 다른 goal_impact를 DB가 거부). 조회 자체는 PK로도 되므로 "본인 조건 조회"는 동작 + 제약 두 면에서 본다.
- 확인 1(스키마): `information_schema.STATISTICS`에서 `NON_UNIQUE = 0`, 컬럼 순서 `user_id, simulation_id`. `REFERENTIAL_CONSTRAINTS`에서 `fk_simulation_goal_impact_simulation`의 `UNIQUE_CONSTRAINT_NAME`이 이 키인지.
- 확인 2(동작): 본인 `GET /{id}`·`/goal-impact`·`DELETE` 200/204, 타인은 셋 다 404 `RESOURCE_NOT_FOUND`(존재 미노출), 존재하지 않는 id 404. 587 2번 테스트가 상세 404만 봤으므로 goal-impact·삭제 경로를 더한다.
- 확인 3(제약): goal_impact가 달린 시뮬레이션의 `simulation.user_id`를 타인으로 UPDATE → `fk_simulation_goal_impact_simulation` 위반으로 거부. 소유권이 바뀌면 자식 행이 고아가 되는 것을 DB가 막는다는 뜻이다. `EXPLAIN SELECT … WHERE user_id = ? AND simulation_id = ? AND deleted_at IS NULL`의 `possible_keys`에 `PRIMARY`와 이 키가 함께 오르는지도 본다(단언은 `possible_keys`까지).

## 4. 진행 순서

1. Docker 기동 → 컨테이너·스키마 준비 → 기존 4건 통과 확인(기준 상태 기록).
2. 3.1 → 3.2 → 3.3 순서로 테스트 추가, 각각 단독 실행.
3. 클래스 7건 전체 실행, 이어서 `.\gradlew.bat test --tests 'com.ssafy.dodam.domain.simulation.*'`(H2, MySQL 건은 skip)로 회귀 없음 확인. 전체 `clean build`는 테스트 파일만 바뀌었으므로 마지막에 1회.
4. simulation.md §11.3에 3건 결과·EXPLAIN 실측을 추가하고 "하지 않은 것"에서 제거. remaining-work.md 587 절 항목 완료.
5. `git diff --check`, CRLF 확인, 커밋.

## 5. 위험과 미결

- 트리거는 스키마 전체의 INSERT에 걸린다. 같은 클래스의 다른 테스트(특히 동시 실행 4스레드)와 겹치면 오염되지만 JUnit 기본은 메서드 순차 실행이고 트리거는 `try/finally`로 제거한다.
- 트리거 생성에는 `TRIGGER` 권한이 필요하다. 일회용 컨테이너 root라 문제없지만, 테스트가 중간에 죽으면 트리거가 남아 다음 실행의 복합 저장이 전부 실패한다 → `@BeforeEach`에서도 `DROP TRIGGER IF EXISTS`를 실행한다.
- MySQL `SIGNAL` 오류는 Spring이 `UncategorizedSQLException`으로 옮기고 전역 핸들러가 500으로 돌려준다. 이 500 응답 계약은 587에서 정한 것이 아니라 현재 동작이며, 롤백 검증의 부수 확인일 뿐이다.
- EXPLAIN 실제 선택(`key`)은 단언하지 않는다(§3.2). 팀이 실제 인덱스 사용 보장을 원하면 별도 항목으로 올린다.
- H2에서는 세 테스트 모두 돌지 않는다. `SIMULATION_MYSQL_TEST_URL` 없이 빌드하면 7건 전부 skip이며, 통과 근거는 이 문서와 §11.3의 실행 기록뿐이다.
- Jira 617 원문 미확인(§1).
