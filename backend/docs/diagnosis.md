# Diagnosis 저장 구조 (S15P21D105-543)

금융진단(`14 Diagnosis`)의 **저장 계층과 실행 골격 구현 문서**다. 작성일 2026-09-10.

명세의 9개 API는 아직 구현되지 않았다. 이 문서는 그 API들이 읽고 쓸 테이블·엔티티·Repository
계약과, 지금 확정된 정책만 기록한다.

## 1. 범위

| 구현됨 | 미구현(담당 이슈) |
| --- | --- |
| `V5`(`goal` 선반영), `V17` 2테이블 | 진단 실행·이력·latest·상세·summary API (544) |
| 엔티티 2종, 코드값 enum 3종 | 진단 규칙과 종합 상태 판정 (544) |
| Repository 조회 계약과 소유권 조건 | 문제 목록·상세, 군집 근거 (545) |
| 진단 접수·완료·하위행 폐기 경로 | 목표영향·진단변화 (546) |

**Controller와 API 응답 DTO는 없다.** 외부에서 호출할 수 있는 엔드포인트가 추가되지 않았다.
`recommendation` 테이블은 S15P21D105-547의 소관이라 포함하지 않았다.

## 2. 스키마

DDL은 `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`의 정의를 그대로 옮겼다.

| 마이그레이션 | 테이블 | 비고 |
| --- | --- | --- |
| `V5__create_goal.sql` | `goal` | **BE-A 소관 테이블 선반영** |
| `V17__create_diagnosis.sql` | `diagnosis`, `diagnosis_issue` | |

### `goal`을 BE-B 이슈가 만든 이유

`diagnosis.goal_id`는 ERD상 `goal(user_id, goal_id)`를 복합 외래키로 참조하는데 `goal`
마이그레이션이 없었다. 세 가지 선택지 중 **스키마 선반영**을 택했다.

| 선택지 | 판단 |
| --- | --- |
| `goal` 스키마를 V5로 선반영 | **채택.** V3의 `account`와 같은 처리다 |
| `goal` FK를 빼고 나중에 추가 | 기각. 데이터 적재 후 제약 추가가 어렵다(`db/migration/README.md`의 card·loan 결정과 같은 이유). ERD와 스키마가 계속 어긋난다 |
| 마이그레이션을 미루고 엔티티만 추가 | **불가능.** local/prod가 `ddl-auto: validate`라 테이블 없는 엔티티가 있으면 기동이 실패한다 |

`goal` 한 테이블만 만들었다. `saving_goal`·`spending_goal`·`goal_account` 하위행은 Goal API가
소유하며 diagnosis가 참조하지 않는다. V3이 `account`만 만들고 상세 테이블을 두지 않은 것과 같다.

**Goal API(S15P21D105-508~511)는 같은 테이블을 다시 만드는 마이그레이션을 추가하지 않는다.**
V5라는 번호 자체는 BE-A 구간(V1~V9)을 소비하므로 병합 전 BE-A 확인이 필요하다(remaining-work.md).

## 3. 데이터 정책

- **실행 상태**: `diagnosis`에는 상태 컬럼이 없다. ERD가 두지 않았고 `completed_at`의 NULL 여부로
  완료를 판별한다. cashflow의 `ForecastStatus`와 다른 점이다.
  - **실패를 표시할 자리가 없다.** 계산 도중 실패한 진단은 접수된 채 `completed_at`이 NULL로 남아
    진행 중인 진단과 구분되지 않는다. 그래서 진행 중 조회는 접수 시각 하한을 받아 그보다 오래된 행을
    버려진 것으로 본다. 하한값은 호출자(544)가 정한다.
- **집계값의 출처**: `issue_count`와 `high_severity_count`는 `DiagnosisStore.complete`가 **저장하는
  문제 목록에서 직접 센다.** 호출자가 넘기지 않는다. 두 값을 따로 받으면 실제 저장된 행과 어긋난
  집계가 남을 수 있고, `chk_diagnosis_high_severity_count`는 상하한 관계만 검사하므로 그 어긋남을
  잡지 못한다.
- **높은 심각도의 정의**: `HIGH`와 `CRITICAL`이다(`DiagnosisIssue.isHighSeverity`).
- **완료 후 덮어쓰기 금지**: 완료된 진단은 다시 완료할 수 없다(`IllegalStateException`). 재진단은
  기존 행을 덮어쓰지 않고 새 진단 행으로 남긴다.
- **재계산 시 하위행**: `complete`는 하위행을 지우고 다시 넣는다. 이전 문제가 남으면 중복 집계된다.
- **JSON 컬럼**: 5종(`input_snapshot`, `current_value`, `benchmark_value`, `evidence`,
  `cluster_context_json`)을 `JsonNode`로 들고 스키마를 엔티티로 고정하지 않는다. 문제 유형마다
  담기는 항목이 달라 공통 스키마를 만들 수 없고, 고정하면 항목이 바뀔 때 과거 행을 읽지 못한다.
  형식은 544·545가 정한다. 매핑은 `FinancialProduct.benefits`의 `@JdbcTypeCode(SqlTypes.JSON)` +
  `JsonNode` 선례를 그대로 따랐다.
  - **`String`으로 두지 않았다.** `@JdbcTypeCode(SqlTypes.JSON)`가 붙은 `String`은 저장 시 JSON
    문자열 리터럴로 한 번 더 감싸져 `JSON_EXTRACT`가 통하지 않고, JSON이 아닌 값도 받아 MySQL이
    런타임에 거부한다. H2 테스트는 저장·조회가 대칭이라 이 문제를 잡지 못한다.
- **군집 보조 신호**: 명세 14는 "군집 결과가 없으면 규칙 기반으로 진단한다"고 정한다. 따라서
  `detection_source` 기본값은 `RULE`이며, 군집 모델이 붙기 전 저장되는 모든 행은 `RULE`이다.
  `DiagnosisIssue.ofCluster`는 `RULE`을 거부하고 모델 버전을 요구한다. 어떤 모델이 만든 판단인지
  남지 않으면 재현할 수 없다.

## 4. 코드값 집합

| 컬럼 | 값 | 정한 곳 |
| --- | --- | --- |
| `diagnosis.overall_status` | `HEALTHY`, `CAUTION`, `RISK` | ERD `chk_diagnosis_overall_status` |
| `diagnosis_issue.severity` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` | ERD 주요 코드값 표 |
| `diagnosis_issue.detection_source` | `RULE`, `CLUSTER`, `RULE_AND_CLUSTER` | ERD `chk_diagnosis_issue_detection_source` |
| `diagnosis_issue.domain` | **닫지 않음**(`String`) | 규칙을 만드는 544·545 |
| `diagnosis_issue.type` | **닫지 않음**(`String`) | 규칙을 만드는 544·545 |

`domain`과 `type`을 enum으로 두지 않은 것은 ERD 코드값 표에 목록이 없고, 명세 14의 필터가 진단
규칙이 늘어날 때마다 값이 추가되는 자리이기 때문이다. 여기서 집합을 닫으면 규칙을 추가할 때마다
엔티티를 고쳐야 한다(539가 `cashflow_event.type`에서 택한 판단과 같다).

**`IssueSeverity`는 `cashflow`의 `RiskSeverity`와 값이 같지만 재사용하지 않고 별도 enum을 두었다.**
한쪽 도메인의 코드값 enum을 다른 도메인이 import하면 두 도메인이 서로의 코드값 변경에 묶인다.
두 도메인이 같은 집합을 계속 공유한다면 `global/`로 올리는 편이 낫지만, 지금 옮기면 병합 대기 중인
S15P21D105-542와 충돌한다. 통합 여부는 remaining-work.md에 남겼다.

선언 순서가 곧 심각도 순서다. `severity`가 문자열 컬럼이라 SQL 사전순은 심각도순과 다르므로,
정렬이 필요하면 이 순서를 기준으로 한다(542가 `cashflow_risk`에서 택한 방식과 같다).

## 5. 소유권

`diagnosis_issue`에는 `user_id`가 없다. 따라서 문제 조회는 모두 상위 `diagnosis`를 조인해 `user_id`
조건을 **쿼리 안에서** 건다. `diagnosisId`만 믿고 조회하면 타인의 문제 목록이 그대로 노출된다.

진단 헤더 조회도 모든 메서드에 `userId` 조건이 있다. 타인 진단과 없는 진단은 모두 빈 결과 또는
`Optional.empty()`이며 존재 여부를 구분해 노출하지 않는다. `RESOURCE_NOT_FOUND`로 변환하는 것은
API 계층(544~546)의 몫이다.

### `goal_id`의 소유권

`fk_diagnosis_goal`이 단일 컬럼이 아니라 **복합 외래키 `(user_id, goal_id)`**라, 남의 목표 ID로
진단을 만들면 DB가 거부한다. 애플리케이션이 목표 소유권을 따로 조회하지 않아도 성립하는 보호다.
`goal` FK를 빼지 않은 이유가 하나 더 있는 셈이다.

다만 **DB까지 가서 막힌다.** 544가 `goalId`를 검증하지 않으면 남의 목표 ID를 넘겼을 때
`DataIntegrityViolationException`이 나 500이 된다. 400이나 `RESOURCE_NOT_FOUND`로 바꾸는 것은
API 계층의 몫이다. H2 테스트는 이 FK가 없어 이 동작을 재현하지 못한다.

## 6. 코드 구조

```
domain/diagnosis/
  entity/     Diagnosis, DiagnosisIssue
              DiagnosisStatus, IssueSeverity, DetectionSource
  repository/ DiagnosisRepository, DiagnosisIssueRepository
  service/    DiagnosisStore, DiagnosisRuleVersion
```

- `diagnosis_issue`는 `@ManyToOne`으로 매핑하지 않고 `diagnosisId` 값만 가진다. 조회는 언제나
  `diagnosisId` 기준 배치이고, 명세의 문제 목록은 필터·페이지가 붙는 별도 API(545)라 헤더를 읽을
  때마다 컬렉션을 끌고 올 이유가 없다.
- `goalId`도 값으로만 둔다. `goal`은 BE-A 소관이라 엔티티가 없고, 매핑을 만들면 diagnosis가 Goal
  엔티티의 형태 변경에 묶인다.
- 하위행 저장은 `saveAll`이다. **538이 cashflow에 쓴 JDBC 배치를 따라가지 않았다.** cashflow는 예측
  1건이 horizon 일수(90행 이상)를 만들지만 진단 문제는 규칙 수만큼이라 규모가 다르고, JSON 컬럼이
  4개다. JDBC로 직접 넣으면 Hibernate의 JSON 변환을 우회해 `{"a":1}`이 객체가 아니라 JSON 문자열로
  저장된다(H2에서 재현 확인). 방언별 JSON 처리를 직접 떠안는 대신 Hibernate에 맡긴다.
- `DiagnosisIssueRepository.deleteByDiagnosisId`는 벌크 DML이다. 파생 삭제(`deleteByDiagnosisId`
  자동 구현)를 쓰면 Hibernate가 엔티티를 지연 삭제해 DELETE가 이어지는 INSERT보다 늦게 나갈 수 있다
  (538이 cashflow에서 겪은 문제와 같다).
- 접수와 완료를 한 트랜잭션으로 묶지 않는다. 규칙 실행이 여러 도메인을 조회하며 수 초가 걸릴 수 있어,
  묶으면 계산하는 동안 커넥션을 붙잡는다.

## 7. 검증

`.\gradlew.bat clean build` 전체 통과(H2).

- `DiagnosisStoreTest`: 접수 시 집계값 0, 완료 시 문제 목록에서 집계값 산출, 문제 0건 완료,
  완료된 진단 재완료 거부, 진단별 하위행 격리, 실패분 폐기, 상하한 위반 거부, 군집 결과의 모델 버전
  강제, 군집 컨텍스트 저장
- `DiagnosisRepositoryTest`: 소유권 격리(헤더·문제 목록·문제 상세·페이지 조회), 최신 완료 진단
  선택, 목표별 최신, 이력 최신순과 사용자 격리, 진행 중 조회의 만료 처리, 필터 조합과 없는 값의
  빈 결과
- `DiagnosisMySqlIntegrationTest`: **환경변수 `DIAGNOSIS_MYSQL_TEST_URL`이 있을 때만 실행된다.**
  일반 test는 건너뛴다.

### MySQL 실행 결과 (2026-09-10, MySQL 8.0.46)

일회용 컨테이너에서 `DiagnosisMySqlIntegrationTest` 3건 통과. H2가 검증할 수 없던 항목을 실제로
확인했다.

- `V1`~`V17` 13개 마이그레이션이 모두 성공하고 `ddl-auto: validate`를 통과한다.
- `fk_diagnosis_goal`이 `goal`을 참조하는 상태로 생성된다.
- JSON 컬럼 5종이 MySQL `json` 타입으로 만들어지고, `JSON_EXTRACT(input_snapshot, '$.asOf')`가
  값을 돌려준다. 이중 인코딩되지 않는다.
- `chk_diagnosis%` CHECK 5개가 살아 있고 위반을 막는다.

**운영 배포 경로(out-of-order)도 실제 파일로 재현했다.** V16까지 적용된 스키마에 `V5`·`V17`을
추가하면 두 건이 적용되고 `V5`가 `OUT_OF_ORDER` 상태로 기록되며, `fk_diagnosis_goal`이 정상 생성되고
재기동(재실행)에도 문제가 없다. 이 확인은 일회성이라 테스트로 남기지 않았다. 일반화된 out-of-order
동작은 기존 `FlywayOutOfOrderTest`가 검증한다.

### 알아둘 점: CHECK 위반의 예외 타입

Spring은 MySQL의 CHECK 위반(error 3819)을 `DataIntegrityViolationException`이 아니라
`UncategorizedSQLException`으로 매핑한다. 544가 이 오류를 응답으로 바꿀 때 고려해야 한다.

## 8. 현재 제한사항

- 진단 규칙이 없다. 문제를 실제로 탐지하는 코드는 544·545가 만든다. 이 작업은 저장 경로만 제공한다.
- 종합 상태(`overall_status`) 판정 규칙이 없다. `DiagnosisStore.complete`가 호출자에게 받는다.
- `input_snapshot`의 형식과 항목이 정해지지 않았다.
- `rule_version` 표기 규칙이 없다. `DiagnosisRuleVersion.CURRENT`는 `"v1"` 상수다.
  cashflow의 `CashflowCalculationVersion`과 같은 상황이다.
- 실패 상태를 저장할 컬럼이 없어 실패한 진단과 진행 중인 진단이 구분되지 않는다. 접수 시각 만료로만
  판단할 수 있다.
