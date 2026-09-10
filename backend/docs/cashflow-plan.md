# Cashflow 엔티티·Repository 진행계획 (S15P21D105-538)

## 1. 문서 성격과 근거

**계획 문서이며 구현 완료 문서가 아니다.** 작성일 2026-09-10.

- 작업 브랜치는 `S15P21D105-538-api-15-cashflow-repository`다. 작성 시점에 원격에 이미 생성되어 있었고 `origin/develop`과 차이가 없는 상태에서 시작했다.
- 선행 이슈는 모두 해소됐다. 같은 상위 스토리는 아니지만 앞 순번인 535·536·537이 `develop`에 병합되어 있어 연쇄 분기 의존이 없다.
- 연결된 Jira 사이트에서 538을 조회하지 못했다. 로컬 `SPRINT/Jira (4).csv`의 제목은 `[API 15] Cashflow 엔티티·Repository`, 설명은 `Forecast, DailyBalance, Event, Risk 저장 구현`, 라벨은 `sprint4,day6,be-b,api-15`, 상위 작업은 `S15P21D105-464 [D6] Cashflow 예측 엔진 기반 구현`이다. **최신 Jira 완료 조건을 대신하지 않는다.**

기준 자료는 다음과 같다.

| 자료 | 사용 범위 |
| --- | --- |
| `docs/API명세서/15 Cashflow ...csv` | 6개 API의 경로·입력·제한사항. 2절 범위와 6절 저장 항목의 근거 |
| `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql` | `cashflow_forecast`, `cashflow_daily_balance`, `cashflow_event`, `cashflow_risk` DDL |
| `docs/ERD/ERD 작성 ...md` | 주요 코드값(`status`, `direction`, `severity`)과 `(forecast_id, dedupe_key)` 중복 차단 정책 |
| `docs/dev_plan.md` | Day 6 BE2 `15 Cashflow 시작`, Day 7 완료. 2절 분할 근거 |
| `backend/src/main/resources/db/migration/README.md` | 마이그레이션 번호 구간(BE-B는 V10~)과 무결성 규칙 |
| 기존 `minusaccount`·`depositsaving` 도메인 코드 | 재사용할 엔티티·Repository·테스트 패턴 |

## 2. 작업 범위

538은 **저장 계층까지**다. Controller·Service의 API 응답 계약은 만들지 않는다.

| 포함 | 제외(담당 이슈) |
| --- | --- |
| `V16` 마이그레이션 4테이블 | 계좌·구독·카드·대출·적금 → 이벤트 변환 (539) |
| 엔티티 4종과 코드값 enum 3종 | 기준일·horizon 계산, 캐시·멱등 규칙 (540) |
| Repository와 소유권 조회 계약 | 예측 실행·최신·일별잔액·이벤트 API (541) |
| Forecast + 하위행 일괄 저장 경로 | risks·available-today API (542) |
| H2 저장·조회 테스트, MySQL 스키마 검증 테스트 | |

`15 Cashflow` 명세의 6개 API는 모두 541·542에서 구현한다. 538은 그 API들이 읽고 쓸 저장 구조와 소유권 조건을 미리 확정하는 작업이다.

## 3. 스키마

`V16__create_cashflow.sql`에 4테이블을 추가한다. DDL은 `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`의 정의를 그대로 옮기고, 변경이 필요하면 ERD를 먼저 고친다.

| 테이블 | 키·제약 |
| --- | --- |
| `cashflow_forecast` | FK `user_id` → `user`. `chk_..._status` `PENDING/RUNNING/COMPLETED/FAILED`, `chk_..._horizon_days > 0`. 조회 인덱스 `(user_id, base_date, created_at)`, `(user_id, request_hash)` |
| `cashflow_daily_balance` | `uk_..._forecast_date (forecast_id, balance_date)` |
| `cashflow_event` | `uk_..._forecast_dedupe (forecast_id, dedupe_key)`, `chk_..._direction INFLOW/OUTFLOW`, `chk_..._amount > 0`, 인덱스 `(forecast_id, event_date, type)` |
| `cashflow_risk` | `chk_..._severity LOW/MEDIUM/HIGH/CRITICAL` |

마이그레이션 번호는 BE-B 구간의 다음 값인 `V16`이다(현재 `V15`까지 사용). `db/migration/README.md` 표에 행을 추가한다.

## 4. 패키지 구조

```
domain/cashflow/
  entity/     CashflowForecast, CashflowDailyBalance, CashflowEvent, CashflowRisk
              ForecastStatus, EventDirection, RiskSeverity
  repository/ CashflowForecastRepository, CashflowDailyBalanceRepository,
              CashflowEventRepository, CashflowRiskRepository, CashflowResultWriter
  service/    CashflowForecastStore
```

## 5. 설계 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 연관관계 매핑 | 하위 3테이블은 `@ManyToOne` 대신 `forecastId` 값만 보유한다 | 예측 1건당 `daily_balance`가 horizon 일수만큼 생긴다. 컬렉션 매핑은 조회 N+1과 대량 flush 비용이 크고, 조회는 항상 `forecastId` 기준 배치다 |
| 하위행 저장 | JDBC `batchUpdate` | AUTO_INCREMENT PK에 `saveAll`을 쓰면 Hibernate가 배치를 묶지 못한다. 기존 도메인도 JPA와 `NamedParameterJdbcTemplate`을 병용한다 |
| 코드값 | Java `enum` + `@Enumerated(STRING)` | DDL `CHECK` 문자열과 1:1. ERD 주요 코드값 표가 기준 |
| `cashflow_event.type`, `cashflow_risk.type` | `String`으로 두고 enum을 만들지 않는다 | ERD 코드값 표에 두 컬럼의 값 목록이 없다. 값 집합은 이벤트를 실제로 만드는 539·542에서 확정한다 |
| 소유권 | 하위 3테이블 조회는 모두 `cashflow_forecast.user_id` 조건을 쿼리에 포함한다 | 하위 테이블에 `user_id`가 없어 서비스 검증만으로는 타인 예측이 노출될 수 있다. 조건을 서비스가 아니라 쿼리에 둔다 |
| 없는 예측·타인 예측 | 빈 결과 또는 `Optional.empty()` | 명세의 "본인 리소스만 접근". 존재 여부를 구분해 노출하지 않는다 |
| `dedupe_key` 중복 | Writer가 `forecastId` 안에서 같은 키의 첫 건만 저장한다 | 명세 공통 제한 "예정 이벤트 중복 반영 금지"와 ERD의 `(forecast_id, dedupe_key)` 정책. 키 생성 규칙 자체는 539 범위 |
| `request_hash` | 컬럼과 조회 메서드만 제공하고 해시 규칙은 만들지 않는다 | 540 `기준일·horizon 계산과 캐시·멱등 구현` 범위 |
| `calculation_version` | 상수 `CashflowCalculationVersion.CURRENT = "v1"` | 저장 시 NULL을 남기지 않기 위한 기본값. 팀 표기 규칙이 정해지면 교체한다 |
| 금액 | `long`/`Long` | 원 단위 정수. 부동소수점을 쓰지 않는다 |

## 6. Repository 계약

- `CashflowForecastRepository`
  - `findByForecastIdAndUserId` — 소유권 확인용 단건
  - `findFirstByUserIdAndBaseDateOrderByCreatedAtDesc` — 기준일 지정 최신 조회(541)
  - `findFirstByUserIdOrderByBaseDateDescCreatedAtDesc` — 기준일 미지정 최신 조회(541)
  - `findFirstByUserIdAndRequestHashAndStatusOrderByCreatedAtDesc` — 멱등 재사용 조회(540)
- `CashflowDailyBalanceRepository` — `forecastId` + `userId` + 기간으로 조회, `balance_date` 오름차순
- `CashflowEventRepository` — `forecastId` + `userId` + 기간, `type` 선택 필터, `event_date` 오름차순
- `CashflowRiskRepository` — `forecastId` + `userId`, `type`·`severity` 선택 필터
- `CashflowResultWriter` — 하위 3테이블 배치 insert와 `forecastId` 단위 삭제(재계산 시 교체)

`CashflowForecastStore`는 예측 시작(`PENDING`)과 완료 저장(하위행 + `COMPLETED` 전이)을 한 트랜잭션으로 묶는다.

## 7. 테스트

`backend/src/test/java/com/ssafy/dodam/domain/cashflow/`

1. `CashflowForecastStoreTest` — 저장 후 재조회, 상태 전이(`PENDING → RUNNING → COMPLETED/FAILED`), 재계산 시 하위행 교체, `dedupe_key` 중복 제거
2. `CashflowRepositoryTest` — 타인 `forecastId` 조회 시 빈 결과, 기간·`type`·`severity` 필터, 빈 결과
3. `CashflowMySqlIntegrationTest` — 환경변수로만 활성화. 실제 Flyway `V16` 위에서 `ddl-auto: validate`를 통과시키고 MySQL `CHECK`(`amount > 0`, `direction`, `severity`, `status`, `horizon_days`)와 유니크 제약을 확인한다

검증 명령은 `.\gradlew.bat test` 후 `.\gradlew.bat clean build`다.

**한계**: 일반 test 프로필은 H2 `create-drop`이고 Flyway가 꺼져 있다. H2 통과가 MySQL 전용 제약 검증을 대신하지 않으므로, MySQL 테스트를 실행하지 못한 경우 미검증 범위로 보고한다.

## 8. 남은 확인 사항

- Jira 538의 최신 완료 조건을 사이트에서 확인하지 못했다. 위 범위는 로컬 CSV 기준이다.
- `available_today`의 계산 정의가 확정되지 않았다(`backend/docs/remaining-work.md`). 538은 컬럼 저장만 하고 계산은 542에서 정한다.
- `calculation_version` 표기 규칙이 팀에서 정해지지 않았다. 위 기본값을 적용하고 결정되면 상수를 교체한다.
- `cashflow_event.type`·`cashflow_risk.type`의 값 집합이 ERD에 없다. 539·542에서 확정하면 ERD 코드값 표와 함께 갱신한다.
