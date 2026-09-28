# Cashflow 저장 구조 (S15P21D105-538)

현금흐름 예측(`15 Cashflow`)의 **저장 계층 구현 문서**다. 작성일 2026-09-10.

명세의 6개 API(예측 실행·최신 조회·일별 잔액·예정 이벤트·위험·오늘 가용금액)는 아직 구현되지
않았다. 이 문서는 그 API들이 읽고 쓸 테이블·엔티티·Repository 계약과, 지금 확정된 정책만 기록한다.

## 1. 범위

| 구현됨 | 미구현(담당 이슈) |
| --- | --- |
| `V16` 마이그레이션 4테이블 | 계좌·구독·카드·대출·적금 → 이벤트 변환 (539) |
| 엔티티 4종, 코드값 enum 3종 | 기준일·horizon 계산, 캐시·멱등 규칙 (540) |
| Repository 조회 계약과 소유권 조건 | 예측 실행·최신·일별잔액·이벤트 API (541) |
| 예측 저장·완료·실패 경로 | 위험 탐지, 가용금액 계산, 위험 API (542) |

**Controller와 API 응답 DTO는 없다.** 외부에서 호출할 수 있는 엔드포인트가 추가되지 않았다.

## 2. 스키마

`V16__create_cashflow.sql`. DDL은 `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`의 정의를 그대로 옮겼다.

| 테이블 | 역할 | 핵심 제약 |
| --- | --- | --- |
| `cashflow_forecast` | 예측 실행 1건과 집계값 | `status IN (PENDING, RUNNING, COMPLETED, FAILED)`, `horizon_days > 0`, FK `user_id` |
| `cashflow_daily_balance` | 날짜별 예상잔액 | `uk (forecast_id, balance_date)` |
| `cashflow_event` | 예정 입·출금 이벤트 | `uk (forecast_id, dedupe_key)`, `direction IN (INFLOW, OUTFLOW)`, `amount > 0` |
| `cashflow_risk` | 탐지된 위험 | `severity IN (LOW, MEDIUM, HIGH, CRITICAL)` |

## 3. 데이터 정책

- **금액 부호**: `cashflow_event.amount`는 방향과 무관하게 항상 양수다. 입·출금은 `direction`으로만
  구분한다(ERD 주요 코드값 표). 누적 계산에 쓸 부호 있는 값은 `CashflowEvent.signedAmount()`가 만든다.
- **음수 잔액**: `cashflow_daily_balance.expected_balance`는 음수를 허용한다. 마이너스통장 사용과
  잔액 부족 구간을 절삭하면 위험 탐지가 성립하지 않는다.
- **중복 반영 금지**: 명세 6개 API의 공통 제한이다. 같은 예측 안에서 동일 예정 이벤트는
  `dedupe_key`로 한 번만 반영한다. 방어선은 두 겹이다.
  1. `CashflowResultWriter.insertEvents`가 같은 키의 첫 건만 저장한다. 배치 INSERT는 한 건만 걸려도
     전체가 실패하므로 예측 저장이 통째로 무산되지 않게 먼저 거른다.
  2. `uk_cashflow_event_forecast_dedupe`가 최종 방어선이다.
  **키 생성 규칙은 S15P21D105-539에서 정한다.**
- **상태 전이**: `PENDING → RUNNING → COMPLETED/FAILED`. 완료·실패한 예측은 다시 완료 처리할 수 없다
  (`IllegalStateException`). 재계산은 기존 행을 덮어쓰지 않고 새 예측 행으로 남긴다.
- **재계산 시 하위행**: `complete`와 `fail`은 하위 3테이블을 지우고 다시 넣는다. 중간에 남은 부분
  결과가 조회되면 실제보다 낙관적인 잔액을 보여주게 된다.
- **금액 타입**: 원 단위 정수는 `long`/`Long`이다. 부동소수점을 쓰지 않는다.

## 4. 소유권

`cashflow_daily_balance`, `cashflow_event`, `cashflow_risk`에는 `user_id`가 없다. 따라서 세 테이블의
조회는 모두 상위 `cashflow_forecast`를 조인해 `user_id` 조건을 **쿼리 안에서** 건다.
`forecastId`만 믿고 조회하면 타인의 예측이 그대로 노출된다.

타인 예측이나 없는 예측은 모두 빈 결과 또는 `Optional.empty()`다. 존재 여부를 구분해 노출하지 않는다.
API 계층에서 `RESOURCE_NOT_FOUND`로 변환하는 것은 541·542의 몫이다.

## 5. 코드 구조

```
domain/cashflow/
  entity/     CashflowForecast, CashflowDailyBalance, CashflowEvent, CashflowRisk
              ForecastStatus, EventDirection, RiskSeverity
  repository/ CashflowForecastRepository, CashflowDailyBalanceRepository,
              CashflowEventRepository, CashflowRiskRepository, CashflowResultWriter
  service/    CashflowForecastStore, CashflowCalculationVersion
```

- 하위 3테이블은 `@ManyToOne`으로 매핑하지 않고 `forecastId` 값만 가진다. 예측 1건당 일별 잔액이
  horizon 일수만큼 생기고, 조회는 언제나 `forecastId` 기준 배치이기 때문이다.
- `CashflowResultWriter`는 JDBC `batchUpdate`로 하위행을 넣는다. AUTO_INCREMENT PK에 JPA `saveAll`을
  쓰면 INSERT가 배치로 묶이지 않는다.
- 하위행 삭제는 파생 삭제가 아니라 `@Modifying` 벌크 DML이다. 파생 삭제는 Hibernate가 엔티티를
  지연 삭제해 DELETE가 커밋 시점에 나가는데, 삽입은 JDBC로 즉시 들어가므로 이전 행이 남은 상태에서
  삽입이 일어나 `uk_cashflow_daily_balance_forecast_date`를 위반한다.
- 두 유니크 제약은 엔티티 `@Table(uniqueConstraints = ...)`에도 선언했다. H2 테스트는 엔티티로
  스키마를 만들기 때문에, 선언하지 않으면 위와 같은 순서 오류를 테스트가 잡지 못한다.
- `CashflowForecastStore`가 저장 순서와 트랜잭션 경계를 책임진다. 계산 로직은 포함하지 않는다.

### Repository 조회 계약

| 메서드 | 용도 |
| --- | --- |
| `CashflowForecastRepository.findByForecastIdAndUserId` | 소유권 확인용 단건 |
| `findFirstByUserIdAndBaseDateAndStatusOrderByCompletedAtDescForecastIdDesc` | 기준일 지정 최신 예측(541) |
| `findFirstByUserIdAndStatusOrderByCompletedAtDescForecastIdDesc` | 기준일 미지정 최신 예측(541). 기준일이 아니라 **완료 시각** 기준이다. 미래 기준일 예측이 계속 최신으로 남는 것을 막는다(541에서 정정) |
| `findFirstByUserIdAndRequestHashAndStatusOrderByCreatedAtDesc` | 캐시·멱등 재사용 조회(540) |
| `CashflowDailyBalanceRepository.findOwnedByPeriod` | 기간 조회, `balance_date` 오름차순 |
| `CashflowEventRepository.findOwnedByPeriod` | 기간 조회, `type` 선택 필터 |
| `CashflowRiskRepository.findOwned` | `type`·`severity` 선택 필터 |

선택 필터는 NULL이면 조건에서 빠진다. 위험 정렬은 `risk_date` 오름차순(NULL 마지막)이다.
`severity`가 문자열 컬럼이라 사전순이 심각도순과 다르므로 심각도 정렬 규칙은 542에서 정한다.

## 6. 테스트

| 테스트 | 확인 범위 |
| --- | --- |
| `CashflowForecastStoreTest` (9건) | 접수·완료·실패 저장, 상태 전이 제한, `dedupe_key` 중복 제거, 하위행 교체, 음수 잔액 보존, 금액·키 검증 |
| `CashflowRepositoryTest` (6건) | 타인 예측 격리, 기간·`type`·`severity` 필터, 미완료 실행 제외, 빈 결과 |
| `CashflowMySqlIntegrationTest` (1건) | 실제 `V16` 위에서 `ddl-auto: validate`, `CHECK` 5개 존재, 유니크·금액·심각도·horizon 제약, 조회 3종의 MySQL 방언 실행 |

**MySQL 검증 완료(2026-09-10).** 빈 `dodam_538_test` 스키마에 Flyway가 `V1`~`V16`을 적용한 뒤 통과했다.
일회용 컨테이너에서 수행했고 운영 DB와 인프라 구성은 건드리지 않았다. 이 과정에서 두 가지를 고쳤다.

- `dedupe_key`·`request_hash`가 DDL에서 `CHAR(64)`인데 엔티티가 VARCHAR로 매핑돼 `validate`에
  실패했다. `columnDefinition = "CHAR(64)"`를 명시했다(529의 `card` CHAR 컬럼과 같은 사례).
- 선택 필터의 `:type is null`과 `nulls last` 정렬은 방언마다 생성 SQL이 달라 MySQL에서 직접 실행해
  확인했다.

MySQL 테스트는 `CASHFLOW_MYSQL_TEST_URL` 환경변수가 있을 때만 실행된다. 빈 `dodam_538_test`
스키마를 가진 MySQL 8.0을 띄우고
`CASHFLOW_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:<port>/dodam_538_test`를 설정한다.

## 7. 현재 제한사항

- `CashflowMySqlIntegrationTest`가 만드는 fixture는 최소 데이터다. 시연 seed를 적재한 상태의 동작은
  확인하지 않았다.
- `calculation_version`은 `CashflowCalculationVersion.CURRENT = "v1"` 상수다. 팀 표기 규칙이 정해지면
  교체한다.
- `request_hash`는 컬럼과 조회 메서드만 있다. 지문 생성 규칙은 540에서 정했다([cashflow-daily-balance.md](cashflow-daily-balance.md)).
- `cashflow_event.type`과 `cashflow_risk.type`은 `String`이다. ERD 코드값 표에 두 컬럼의 값 목록이
  없어 539·542에서 확정한 뒤 ERD와 함께 갱신한다.
- `available_today`의 계산 정의가 확정되지 않았다(`remaining-work.md`). 538은 컬럼만 저장한다.
