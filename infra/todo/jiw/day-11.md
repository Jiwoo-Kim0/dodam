# Day 11 실제 진행 기록

## 2026-09-16

### 1. 작업 범위 — API 19 Improvement + API 20 Report (S15P21D105-590)

명세(`docs/API명세서/19 Improvement ....csv`, `20 Report ....csv`)가 정의한 19종:

| API | 엔드포인트 수 | 쓰기 | 읽기 |
| --- | --- | --- | --- |
| 19 Improvement | 11 | 재진단 1 | 요약·이력·상세·전후비교·금액 4종·목표영향·월별추이 = 10 |
| 20 Report | 8 | 0 | 데일리·월간·목록·상세·소비패턴·목표변화·예정비용·개선행동요약 |

완료 기준(사용자 지시):
1. 추천 수락과 개선 행동을 **중복 없이** 연결하고 **예상 효과와 실제 관측값을 구분**한다.
2. **소유권·기간·페이지·재진단 중복 방지**와 **리포트 기반 응답**을 검증한다.
3. 리포트 구현 전 `docs/report-backend-handoff.md`(FE 협의안)를 확인하고, 기존
   `backend/docs/improvement-report-contract.md`(Day 10 선공유본)와 다른 항목은 **계산 기준·응답 필드를 함께 확정**한 뒤
   최종 DTO·응답 예시를 공유한다.

### 2. 사전 조사 — 선공유 계약과 FE 협의안이 서로 어긋나 있었다

두 문서를 나란히 놓고 읽으니 **구현 전에 결론을 내야 하는 모순**이 먼저 나왔다. 이걸 그냥 구현했으면
FE가 화면에서 숫자가 안 맞는 걸 발견했을 것이다.

| 충돌 | 선공유본 §2-3 | 선공유본 §2-4 예시 |
| --- | --- | --- |
| `totalExpense`의 정의 | "`totalSaving`은 저축성 지출이며 `totalExpense`에 **포함**된다 — 소비 집계와 같은 규칙" | `totalExpense: 1,850,000`, `bySpendingType.NON_SPENDING: 100,000`, 그런데 `totalSaving`은 400,000 |

- 기존 소비 집계(`JdbcSpendingReader.SPENDING_WHERE`, S15P21D105-506)는 `NON_SPENDING`을 **뺀다.**
  따라서 "소비 집계와 같은 규칙"과 "`totalSaving`을 포함한다"는 **동시에 성립할 수 없다.**
- 같은 문서 안의 예시도 `bySpendingType` 합(1,850,000)과 `totalSaving`(400,000)이 어긋나 있었다.

→ **결정: 두 정의를 모두 내려 준다.** `totalExpense`(EXPENSE 전부) + `totalSpending`(506 소비) +
  `totalSaving`(NON_SPENDING). `totalExpense = totalSpending + totalSaving`이 항상 성립한다.
  한쪽만 주면 FE가 빼기로 나머지를 추정하게 되고, 그 추정이 화면마다 달라진다.
  분모 문제도 같은 방식으로 풀었다 — `MonthlySpendingPattern.ratioBase: "TOTAL_SPENDING"`으로
  **응답이 자기 분모를 직접 밝힌다.**

확정한 항목 12개는 `backend/docs/improvement-report-contract.md` 맨 위 표에 "선공유본에서 바뀐 것"으로 정리했다.
**필드는 하나도 빼지 않았다** — 추가와 계산 기준 확정만 했다(FE가 이미 만든 화면이 깨지지 않게).

### 3. 설계 결정 1 — "중복 없이 연결"을 무엇으로 보장할 것인가

API 19에는 **개선 행동을 만드는 엔드포인트가 없다**(11개 중 쓰기는 재진단 하나뿐). 즉 행동의 입구는
추천 수락(API 16)뿐이다. 여기서 두 가지를 정해야 했다.

**(1) 언제 만드는가** — `RecommendationStatusService`가 상태를 바꾸는 **같은 트랜잭션**에서
`ImprovementActionRecorder`를 부른다. 분리하면 "수락했는데 행동이 없다" 또는 그 반대가 생긴다.

| 추천 전이 | 행동 |
| --- | --- |
| → `ACCEPTED` | 생성(`PLANNED`) 또는 기존 행 **재사용** |
| 수락 다음 달 1일 경과 | `IN_PROGRESS` |
| → `COMPLETED` | `COMPLETED` |
| → `REJECTED`/`DEFERRED` | `CANCELED` |

**(2) 중복을 무엇이 막는가** — 전이표상 `ACCEPTED → DEFERRED → ACCEPTED`가 가능하다.
서비스 코드의 `if (존재하면 스킵)`만으로는 동시 요청에서 뚫린다.
→ `uk_improvement_action_recommendation_id` **UNIQUE 제약**을 DDL과 엔티티 양쪽에 선언했다.
  엔티티에도 선언한 이유: H2 테스트는 Flyway를 끄고 엔티티로 스키마를 만들기 때문에, 엔티티에 없으면
  이 제약이 **테스트에서 검증되지 않는다**(`Recommendation`이 같은 이유로 선언해 둔 것을 따랐다).
  재수락은 새 행을 만드는 대신 `replan()`으로 기준선만 새로 잡는다.

### 4. 설계 결정 2 — "예상 효과와 실제 관측값을 구분"

가장 쉬운 구현은 수락할 때 추천의 예상 효과를 행동 행에 복사해 두는 것이다. 그러면 나중에 두 행이 어긋난다.

→ **행동 테이블에는 예상치를 저장하지 않는다.** `goalId`·`domain`·`type`·예상 효과는 전부
  `recommendation`에서 조인해 읽고, `improvement_action`의 금액 컬럼 7개는 **전부 실측값**이다.
  응답의 `expectedMonthlySaving`(추천) / `actualMonthlySaving`(행동) 구분이 곧 테이블 구분이 된다.
  `title`만 예외로 복사한다 — 수락 당시 사용자가 본 문구를 보존하기 위해서다.

**측정 기간 정책**(`ImprovementMeasurementPolicy`)이 이 작업에서 제일 조심한 부분이다.

```
 ...  6월   7월   8월  │   9월   │  10월  11월  12월  ...
     └──── before ────┘ 수락한 달 └──── after ─────┘
```

- 수락한 달과 **진행 중인 달**을 양쪽 모두에서 뺀다. 20일치 소비를 한 달치와 비교하면
  **무조건 "개선"으로 보인다.** 이런 숫자를 "실제 관측값"이라고 내보내면 API 전체가 의미를 잃는다.
- 개월 수가 다를 수 있으므로 합계가 아니라 **월평균**으로 비교한다(잔액성 값인 부채만 시점 값).
- 실측값은 모두 `max(0, ...)`다. 악화된 경우를 음수로 더하면 합계가 상쇄돼 "효과 0원"이 된다.
  방향은 전후 비교(#4)의 `delta`(부호 그대로) + `improved`(지표별 좋은 방향)가 보여 준다.

**배치가 없다** → 조회 시점이 측정 시점이다(`ImprovementMeasurementService.refresh`). 그래서
`ImprovementQueryService`는 읽기 전용 트랜잭션이 아니다. 완료 월이 1→2→3으로 늘면 다시 측정해 창을 넓히고,
3개월이 차면 멈춘다 — 그 뒤의 변화는 이 행동의 결과라고 말할 수 없다.

### 5. 설계 결정 3 — "리포트 기반 응답"과 404의 범위

선공유본은 "해당 기간 리포트가 없으면 404"였다. FE 협의안 3번은 정반대로
"리포트 미생성 / 거래 0건 / 일부 계산 불가를 **404 하나로 표현하면 사용자 안내를 나눌 수 없다**"고 요청했다.

→ **`/daily`·`/monthly`에서 404를 없앴다.** 끝난 기간이면 저장본을 돌려주고, 없으면 만들어 저장한 뒤 200.
  상태는 `dataStatus`(`READY`/`NO_TRANSACTIONS`/`INSUFFICIENT_DATA`) + `unavailableReason`으로 나눈다.
  404는 **리포트 상세(#5)** 에만 남는다(식별자로 부르는 조회라 만들 기간이 없다).
  대신 **끝나지 않은 기간(오늘·이번 달)과 상한 초과는 400**이다 — 진행 중인 기간을 리포트로 굳히면
  볼 때마다 숫자가 달라진다.

캐시 정책도 여기서 갈렸다. `READY`는 **고정**(같은 달을 다시 봐도 `generatedAt`까지 그대로),
`NO_TRANSACTIONS`/`INSUFFICIENT_DATA`는 **다음 조회 때 재계산**한다. 금융망 동기화가 늦게 도착해
거래가 나중에 생기는 경우가 실제로 있기 때문이다.

**"리포트 기반"의 구현**: 월간 리포트를 만들 때 소비패턴·목표변화·개선행동 요약을 함께 계산해
`monthly_report`의 JSON 컬럼에 저장하고, #4·#6·#8은 **저장본을 읽는다.** 각자 다시 집계하면
같은 달의 합계가 서로 어긋난다.

**단 하나의 예외가 #7 예정비용이다.** 미래 스케줄이라 생성 시점에 굳히면 다음 날부터 낡은 값이 된다.
→ 조회 시점 계산으로 두고, 원본은 현금흐름 예측과 **같은 수집기**(`CashflowEventCollector`)를 쓴다.
  카드 청구·대출 상환·구독을 따로 읽으면 구독이 카드 청구서에 이미 들어 있는 경우 두 번 잡힌다.
  수집기가 dedupe 키로 이미 막고 있다.

### 6. 설계 결정 4 — "못 만든 것"과 "덜 모은 것"을 분리

예정비용을 구현하다 발견한 것. 선공유 계약의 규칙("`unavailableReason`이 null이 아니면 수치 대신 안내 문구")을
그대로 따르면, 원본 하나(`AccountScheduledEventSource` 구현체가 아직 없다)만 비어도
**카드 전체가 숨겨져 쓸 수 있는 금액까지 사라진다.**

→ 두 축으로 나눴다.
- `unavailableReason != null` → 합계를 못 만들었다. `totalAmount: null`, 안내 문구.
  (`PERIOD_ELAPSED` / `SOURCE_CONFLICT` / `SOURCE_UNAVAILABLE`)
- `partial: true` + `warnings[]` → 합계는 있지만 과소집계다. **수치를 보여 주되 표시를 덧붙인다.**

### 7. 구현 중 실제로 부딪힌 것

**(1) `UnexpectedRollbackException` — 예외를 잡았는데 500이 났다**

예정비용에서 수집기 실패를 `catch`해 사유로 바꿔 담으려 했는데 응답이 500이었다.
원인은 `UpcomingExpenseService`에 `@Transactional(readOnly = true)`를 걸어 둔 것. 수집기가 같은 트랜잭션에
참여하다 실패하면서 **rollback-only 표시**를 남겼고, 예외를 잡아도 커밋 단계에서 터졌다.

→ **이 서비스에서 트랜잭션을 뺐다.** 수집기가 자기 읽기 전용 트랜잭션에서 돌아야 그 트랜잭션만 롤백되고
  바깥에서 사유로 바꿔 담을 수 있다. 예외 분류도 나눴다:
  `InvalidRequestException`(원본 간 금액·날짜 충돌) → `SOURCE_CONFLICT`,
  그 외 → `SOURCE_UNAVAILABLE`. 둘 다 `log.warn`으로 원인을 남긴다.

**(2) H2 테스트에는 `card_billing_statement`·`loan_repayment` 테이블이 없다**

두 테이블은 JPA 엔티티가 없어(JDBC로만 읽는다) H2 기본 스키마(`ddl-auto: create-drop`)에 만들어지지 않는다.
운영은 Flyway가 만들므로 문제가 없지만, 예정비용 수집기가 두 테이블을 읽어 테스트에서 `BadSqlGrammarException`이 났다.
→ `ReportApiTest`가 비어 있는 채로 두 테이블을 만든다(`CashflowSourceReaderTest`가 쓰는 방식과 같다).
   결과적으로 (1)의 graceful degradation 경로도 실제로 밟아 보고 고칠 수 있었다.

**(3) `monthly-trend`의 월 검증은 고정 시계를 쓰지 않는다**

공용 `YearMonthPeriodPolicy`가 `YearMonth.now()`로 미래 월을 막는다. 개선 도메인은 `ImprovementClock`을
주입받는데 이 정책만 실제 시계를 본다 → 테스트에서 시계를 미래로 돌려도 그 월은 400이다.
공용 정책을 이 티켓에서 바꾸는 건 범위를 넘어서므로 **동작은 그대로 두고 계약 문서에 명시**했다(§1-5).

**(4) `year_month` 컬럼명**

MySQL의 `INTERVAL` 단위 키워드와 철자가 같다. 엔티티에서 ``@Column(name = "`year_month`")``로 따옴표를 강제했다.

### 8. 스키마 — ERD를 먼저 고치고 마이그레이션을 썼다

`db/migration/README.md` 규칙("DDL은 ERD 정의를 그대로 옮기고, 변경이 필요하면 ERD를 먼저 고친다")을 따라
`docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`과 ERD 마크다운을 함께 갱신했다.

| 버전 | 내용 |
| --- | --- |
| V37 | `improvement_action` |
| V38 | `report`, `daily_report`, `monthly_report` |

ERD 대비 추가한 컬럼과 이유:

| 테이블 | 추가 | 이유 |
| --- | --- | --- |
| `improvement_action` | `user_id` | 소유권 조회를 추천 조인 없이 하고, **복합 FK `(user_id, recommendation_id)`** 로 추천과 같은 사용자임을 DB가 보장 |
| | `planned_at`, `canceled_at` | ERD의 `started_at`은 "실제 실행 시작"이라 수락 시각과 의미가 다르다. 상태만 있고 시각이 없으면 이력을 복원할 수 없다 |
| | `created_at`, `updated_at` | 다른 도메인 테이블과 같은 감사 컬럼 |
| `daily_report` | `data_status`, `financial_summary`, `notable_events`, `coach_message` | §5의 상태 구분과 응답 계약이 요구하는 저장 위치. ERD의 `upcoming_events`는 "앞으로 예정"이라 `notable_events`("그날 일어난 일")와 의미가 다르다 |
| | `available_today` NOT NULL → NULL | 브리핑은 "전날에 대한" 요약이라 조회 시점 가용액을 과거 리포트에 굳힐 근거가 없다 |
| `monthly_report` | `data_status`, `diagnosis_id`, `overall_status` | 응답 계약의 필드. JSON에 묻으면 조회 조건으로 쓸 수 없다 |

번호 구간: README의 BE-A 2차 구간(V30~V49)에서 V37·V38을 잡고 표와 "다음은 V39"를 갱신했다.

### 9. 검증

| 테스트 | 건수 | 확인한 것 |
| --- | --- | --- |
| `ImprovementApiTest` | 10 | 수락→행동 1건 생성, **재수락 시 행 미증가**, 거절 시 취소, 타인 행동 404, 완료 월 전 `AFTER_PERIOD_NOT_READY`, 완료 월 후 실측(월 50만 감소 → `savings.monthlyAmount` 500,000 / `totalAmount` 6,000,000), `IN_PROGRESS` 전이, 추이 0 채움, 기준선 없을 때 `BASELINE_FORECAST_UNAVAILABLE`, 재진단 201→200, page/기간 400 |
| `ReportApiTest` | 12 | `totalExpense = totalSpending + totalSaving`, 전월 비교, **첫 달은 previousMonth null**, 거래 0건은 `NO_TRANSACTIONS`(0원이지 계산 불가가 아님), 두 번 조회해도 `reportId`·`generatedAt` 동일, 하위 응답이 저장본과 같은 숫자, `bySpendingType` 합 = `totalExpense`, 타인 목표 404, 예정비용 `PERIOD_ELAPSED` vs `partial` |
| `ImprovementMeasurementPolicyTest` | 3 | before 3개월 고정, 완료 월 0개면 측정 불가, 창이 3개월에서 멈춤 |
| `ReportPeriodPolicyTest` | 4 | 기본값(직전 완료 월/어제), 미완료 기간 거부, 형식·상한 거부, 예정 월은 과거 허용 |

전체 스위트 실행: `BUILD SUCCESSFUL in 2m 11s` (기존 테스트 회귀 없음 — 추천 수락 경로를 건드렸기 때문에 반드시 확인해야 했다).

> 로컬 WSL에 JDK가 없어 처음에는 컴파일조차 못 했다. Temurin 21을 세션 스크래치패드에 내려받아
> `JAVA_HOME`을 넘겨 실행했다(프로젝트에는 아무것도 추가하지 않았다).
> Gradle 배포판은 `~/.gradle/wrapper/dists`에 캐시돼 있어 `--offline`으로 돌렸다.

### 10. 산출물

| 구분 | 경로 |
| --- | --- |
| 마이그레이션 | `backend/src/main/resources/db/migration/V37__create_improvement_action.sql`, `V38__create_report.sql` |
| Improvement | `backend/src/main/java/com/ssafy/dodam/domain/improvement/` (entity 2, repository 1, dto 8, service 8, controller 1) |
| Report | `backend/src/main/java/com/ssafy/dodam/domain/report/` (entity 5, repository 5, dto 7, service 10, controller 1) |
| 연결 | `RecommendationStatusService` → `ImprovementActionRecorder` |
| 조회 추가 | `GoalHistoryRepository.findTopByGoalIdAndRecordedDateLessThanEqual...`, `DiagnosisRepository.findFirstByUserIdAndCompletedAtBetween...` |
| 계약 문서 | `backend/docs/improvement-report-contract.md` (확정본 + FE 협의안 항목별 회신 §4) |
| 협의안 | `docs/report-backend-handoff.md` 상단에 회신 완료 표시 |
| ERD | `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`, `docs/ERD/ERD 작성 ....md` |

### 11. 남은 것 / FE에 전달할 것

1. **FE 확인 필요**: 계약 문서 맨 위 "선공유본에서 바뀐 것" 12개 항목. 특히 `totalExpense` 정의 변경과
   `totalSpending` 추가 — `useReportData`의 임시 집계를 교체할 때 분모를 `ratioBase`로 확인할 것.
2. **금융 효율 점수·금융 생활 패턴 문구는 제공하지 않기로 했다**(회신 §4). 점수 산식과 비교 모집단이
   확정되지 않았고, 근거 없는 점수를 API로 굳히면 되돌리기 어렵다. 대신 `overallStatus` + 진단 문제 API를 쓴다.
3. `AccountScheduledEventSource` 구현체가 없어 예정비용은 항상 `partial: true`다. 구현되면 경고가 사라진다.
4. 측정이 조회 시점에 일어난다. 사용자가 오래 안 들어오면 그동안 측정이 밀린다 —
   배치가 생기면 `ImprovementMeasurementService.refresh`를 그대로 스케줄에 걸면 된다.
5. 6개월 소비 추이 전용 엔드포인트(협의안 "지난 소비")는 이번 범위에 넣지 않았다. 필요하면 별도 티켓.

---

## 2026-09-17

### 12. 운영 기동 실패 대응 — `monthly_report.year_month` 타입 불일치

인프라에서 9/16에 `ddl-auto`를 `none` → `validate`로 되돌린 뒤 backend가 기동하지 못했다.

```
Schema-validation: wrong column type encountered in column [year_month]
in table [monthly_report]; found [char], but expecting [varchar(7)]
```

원인은 내가 Day 11에 만든 매핑이다. V38은 ERD대로 `CHAR(7)`로 선언했는데 엔티티에는
`@Column(length = 7)`만 붙여 Hibernate가 `varchar(7)`을 기대했다. `none`이었을 때는 그냥 넘어가던 것이
`validate` 복구와 함께 드러났다.

### 13. 먼저 한 일 — "이것 하나뿐인가"를 증거로 확인

**validate는 첫 번째 불일치에서 멈춘다.** year_month만 고쳐 배포하면 다음 컬럼에서 같은 장애가 반복된다.
요청받은 MySQL 게이트 테스트를 돌리려 했으나 이 개발기에는 Docker도 sudo도 없어(MySQL을 띄울 수 없다)
DB 없이 같은 결론을 내는 방법을 썼다.

1. Hibernate metadata를 MySQLDialect로 띄워 **엔티티가 기대하는 컬럼 타입 465개**를 뽑았다.
2. 마이그레이션 28개를 파싱해 **DDL이 선언한 컬럼 타입 502개**를 뽑았다.
3. 둘을 대조했다.

결과: **실제 불일치는 `monthly_report.year_month` 하나뿐이다.** 나머지 후보 39개는 전부
`@Enumerated(STRING)` 컬럼(DDL `VARCHAR`, Hibernate 표기 `enum (...)`)과 `TINYINT(1)` 불리언으로,
저장·검증이 같은 계열이라 통과한다 — `account`·`user`·`transaction`처럼 몇 주째 운영 중인 테이블들이
같은 모양이라는 점이 그 증거다.

중간에 한 번 잘못 짚었다. `finance_connection`의 해시 컬럼 2개가 `CHAR(64)` DDL에 `columnDefinition`이
없어 두 번째 장애로 보였는데, 실제로는 `@JdbcTypeCode(SqlTypes.CHAR)`를 쓰고 있었다.
`@Column` 줄만 grep해서 놓친 것이다 — 그래서 눈으로 훑지 않고 metadata를 뽑아 대조하는 쪽으로 바꿨다.

### 14. 고친 방향 — DB가 아니라 엔티티

요청은 `V39 ALTER TABLE ... MODIFY VARCHAR(7)`이었고 판단은 맡겨 주셨다. **엔티티에
`columnDefinition = "CHAR(7)"`을 붙이는 쪽**을 골랐다.

| 근거 | 내용 |
| --- | --- |
| 저장소 관례 | CHAR 컬럼 16개가 **전부** 이미 이 방식이다(`columnDefinition` 14개, `@JdbcTypeCode(SqlTypes.CHAR)` 2개). 여러 엔티티에 "빼면 validate에 실패한다"는 주석까지 달려 있다 |
| 같은 모양의 선례 | `card.expiry_month`가 똑같은 `yyyy-MM` CHAR(7)이고 `columnDefinition = "CHAR(7)"`을 쓴다 |
| ERD 일치 | ERD가 CHAR(7)이다. DB를 바꾸면 ERD도 또 고쳐야 하고, 월 컬럼 중 이것만 VARCHAR이 된다 |
| 범위 | `yearMonth`는 `MonthlyReport` 한 곳에서만 쓴다(제안에 적힌 "엔티티가 여러 곳에서 쓰이면"에 해당하지 않는다) |
| 위험 | 운영 DDL·데이터를 건드리지 않는다. 어차피 양쪽 다 재배포가 필요하므로 복구 속도 차이는 없다 |

> **둘을 같이 넣으면 안 된다.** V39로 컬럼을 VARCHAR로 바꾸고 엔티티에 CHAR(7)을 붙이면 반대 방향으로
> 다시 어긋나 같은 장애가 난다. 한쪽만 적용한다.

### 15. 재발 방지 — `SchemaTypeConsistencyTest`

13에서 한 대조를 일회성으로 두지 않고 상시 테스트로 만들었다.
(`backend/src/test/java/com/ssafy/dodam/global/schema/SchemaTypeConsistencyTest.java`)

- 엔티티 metadata(MySQLDialect)와 마이그레이션 DDL을 **타입 계열** 단위로 대조하고, 어긋나는 컬럼을
  **전부 한 번에** 보여 준다(validate처럼 첫 개에서 멈추지 않는다).
- **DB가 필요 없다.** `*MySqlIntegrationTest`는 로컬 MySQL이 있을 때만 돌아 정작 마이그레이션을 추가하는
  순간에는 아무도 실행하지 않는다. 이 테스트는 `./gradlew test`에 그냥 포함된다.
- 처음에는 Hibernate `AbstractSchemaValidator`의 판정식을 글자 그대로 옮겼는데, 드라이버가
  메타데이터를 보고하는 방식까지 재현해야 해서(`TINYINT(1)` → `BIT`, STRING enum → `enum (...)`)
  없는 문제를 40개 만들어 냈다. **계열 비교로 바꿨다** — 우리를 문 사고(CHAR ↔ VARCHAR)와 누락 컬럼은
  그대로 잡으면서 잡음이 없다. 길이는 보지 않는다(Hibernate 검증도 타입만 본다).
- 모르는 타입이 나오면 조용히 넘기지 않고 실패한다(표를 늘리라는 뜻).

**가드가 실제로 잡는지 확인**: 수정을 잠시 되돌리고 돌려서 운영과 같은 컬럼이 나오는지 봤다.

```
monthly_report.year_month — DDL은 CHAR(7)(TEXT_FIXED)인데 엔티티는 varchar(7)(TEXT_VARIABLE)을 기대한다
```

한 건만 나온다 = 다른 불일치가 없다는 것도 같이 확인된다. 이후 수정을 복구하고 전체 스위트 통과
(`BUILD SUCCESSFUL in 1m 44s`).

`db/migration/README.md`에도 규칙과 두 가지 매핑 예시를 적었다.

### 16. 남은 것

1. **재배포 필요.** DB 변경은 없다(엔티티만 고쳤다). 배포 후 기동 로그에 validate 오류가 없는지 확인.
2. MySQL 게이트 테스트는 이번에 돌리지 못했다(개발기에 Docker·sudo 없음). 로컬 MySQL이 있는 환경에서
   `PATTERN_MYSQL_TEST_URL`을 걸고 V1~V38 적용 후 한 번 확인해 주면 좋겠다. `SchemaTypeConsistencyTest`가
   타입은 덮지만 제약·인덱스는 여전히 게이트 테스트의 몫이다.
3. 이번 대조에서 **엔티티가 없는 테이블**(`card_billing_statement`, `loan_repayment`)은 validate 대상이
   아니라 검사에서 빠진다. 이 테이블들은 JDBC로만 읽으므로 타입이 어긋나도 기동은 되지만 런타임에 깨진다.

---

## 결정 기록 (2026-09-16 ~ 09-17 작업 전체)

앞의 §1~16이 "무엇을 했는지"라면 여기는 **"왜 그것을 골랐고 무엇을 버렸는지"**다.
같은 문제를 다시 만났을 때 되짚을 수 있도록, 고른 것만이 아니라 **버린 대안과 버린 이유**를 함께 적는다.

### 17. API 19 Improvement — 결정과 버린 대안

| # | 결정할 것 | 고른 것 | 버린 대안 | 버린 이유 |
| --- | --- | --- | --- | --- |
| 1 | 개선 행동을 언제 만드나 | 추천 상태 변경과 **같은 트랜잭션**(`ImprovementActionRecorder`) | 별도 쓰기 엔드포인트 | 명세 11종에 그런 API가 없다. 만들면 명세에 없는 API가 생긴다 |
| | | | 이벤트/비동기 처리 | "수락했는데 행동이 없다"가 생긴다. 원자성이 이 기능의 전부다 |
| 2 | 중복 생성을 무엇이 막나 | DB `UNIQUE(recommendation_id)` + 재수락 시 `replan()` | 서비스의 `if (존재하면 스킵)` | 동시 요청에서 뚫린다. `ACCEPTED → DEFERRED → ACCEPTED` 전이가 실제로 가능하다 |
| | | | 재수락 시 새 행 추가 | 같은 개선안의 효과가 두 번 집계된다 |
| 3 | 예상 효과를 어디 두나 | 행동 행에 **저장하지 않고** 추천에서 조인 | 수락 시 추천의 예상치를 행동 행에 복사 | 구현은 쉽지만 두 행이 시간이 지나며 어긋난다. "예상과 실측을 구분"이 완료 기준인데 출처가 흐려진다 |
| 4 | 비교 기간의 경계 | 수락한 달과 **진행 중인 달을 양쪽에서 제외** | 수락일 기준 전/후 N일 | 수락한 달은 전·후가 섞여 있다 |
| | | | 진행 중인 달 포함 | 20일치를 한 달치와 비교하면 **무조건 개선으로 보인다.** 이 수치를 "실제 관측값"이라 부를 수 없다 |
| 5 | 기간 길이가 다를 때 | **월평균**으로 비교(잔액성 값인 부채만 시점 값) | 기간 합계 비교 | before 3개월 합과 after 1개월 합을 비교하게 된다 |
| 6 | 무엇을 측정 대상으로 보나 | 취소되지 않은 모든 행동 | `COMPLETED`만 | 한 달치 데이터가 쌓인 `IN_PROGRESS` 행동이 아무것도 못 보여 준다. 선공유 계약의 `ACTION_NOT_COMPLETED`를 `ACTION_NOT_MEASURABLE`로 바꾼 이유(계약 문서 변경표 #9) |
| 7 | 측정을 몇 번 하나 | 완료 월이 1→2→3으로 늘면 **다시 측정**, 3개월에서 멈춤 | 1회만 측정 | 1개월 표본으로 효과를 확정하게 된다 |
| | | | 계속 넓힘 | 반년 뒤의 소비 변화를 이 행동의 결과라고 말할 수 없다 |
| 8 | 악화된 경우 | `max(0, ...)`로 0 기록, 방향은 비교 API의 `delta`·`improved`가 노출 | 음수 그대로 합산 | 좋아진 행동과 나빠진 행동이 상쇄돼 합계가 "효과 0원"이 된다 |
| 9 | 측정 실행 시점 | **조회 시점**(`ImprovementMeasurementService.refresh`) | 스케줄러 | 프로젝트에 `@Scheduled`가 하나도 없다. 인프라를 새로 들이는 건 이 티켓 범위 밖이다. 배치가 생기면 이 메서드를 그대로 걸면 된다 |
| 10 | 월별 추이의 월 배정 | 건수는 `completedAt`, 금액은 `measuredAt` | 하나의 기준으로 통일 | 완료와 측정은 다른 달에 일어난다. 하나로 묶으면 같은 행동이 두 달에 중복 집계된다 |
| 11 | 추이 기본 구간 | 양 끝을 **모두** 생략했을 때만 6개월 | 공용 `YearMonthPeriodPolicy` 기본값(3개월) 그대로 | 차트에 점 3개는 추세로 보이지 않는다 |
| | | | 공용 정책의 기본값을 6으로 변경 | 다른 도메인이 다 쓰는 정책이다. 이 티켓에서 바꿀 범위가 아니다 |
| 12 | 목표 기준선을 어느 행동에서 | 그 목표에 연결된 **가장 이른** 행동의 수락 시점 스냅샷 | 가장 최근 행동의 기준선 | 먼저 실행한 행동이 앞당긴 만큼이 기준선에 이미 반영돼 효과가 사라진다 |
| 13 | 재진단 중복 방지 | 진단 실행 러너(`DiagnosisRunner`)를 그대로 호출 | 자체 중복 방지 로직 | 같은 규칙을 두 곳에 두면 갈라진다. 진단 API가 이미 "같은 날 같은 규칙 버전이면 재사용"을 정해 뒀다 |
| 14 | `forceRefresh` 노출 | 열지 않음 | body에 추가 | 개선 결과 화면의 버튼을 연타하면 같은 날 진단이 쌓인다. 그게 명세의 "중복 요청 방지"가 막으려던 것이다. 강제 재실행이 필요하면 진단 API를 직접 쓴다 |

### 18. API 20 Report — 결정과 버린 대안

| # | 결정할 것 | 고른 것 | 버린 대안 | 버린 이유 |
| --- | --- | --- | --- | --- |
| 15 | 리포트가 없을 때 | **만들어서 200** (404는 상세 조회에만) | 선공유 계약대로 404 | FE 협의안 3번: 미생성/거래 0건/계산 불가를 404 하나로는 안내를 나눌 수 없다 |
| 16 | 상태 표현 | `dataStatus` 3종 + `unavailableReason` | 빈 값(0원)으로만 표현 | "0원을 썼다"와 "못 읽었다"가 같아진다 |
| 17 | 저장본 재계산 | `READY`는 고정, 그 외는 다음 조회에 재계산 | 매번 재계산 | 같은 달을 볼 때마다 숫자가 달라진다 |
| | | | 한 번 저장하면 영구 고정 | 금융망 동기화가 늦게 도착한 거래가 영원히 반영되지 않는다 |
| 18 | 하위 응답의 출처 | 생성 시점에 계산해 JSON으로 저장, 조회는 **저장본을 읽음** | 엔드포인트마다 그때 집계 | 같은 달의 합계가 화면마다 어긋난다. 완료 기준의 "리포트 기반 응답"이 이것을 말한다 |
| 19 | 예정비용만 예외 | 저장하지 않고 조회 시점 계산 | 다른 하위 응답처럼 저장 | 미래 스케줄이라 생성 다음 날부터 낡은 값이 된다 |
| 20 | 예정비용의 원본 | 현금흐름 예측과 **같은 수집기**(`CashflowEventCollector`) | 카드·대출·구독을 각각 조회 | 구독이 카드 청구서에 이미 들어 있으면 두 번 계상된다. 수집기가 dedupe 키로 이미 막고 있다 |
| 21 | 예정비용 `yearMonth`의 뜻 | **집계할 월 그 자체**(생략 시 오늘의 다음 달) | "리포트 기준월 + 1개월" | 기준월이 직전 완료 월이면 그 다음 달은 이미 진행 중이다. 선공유 예시(2026-08 리포트 → 2026-10)도 "생성 시점의 다음 달"이었다 |
| 22 | 원본 일부를 못 읽었을 때 | `partial` + `warnings`로 분리(수치는 보여 줌) | 계약대로 `unavailableReason` 하나 | 계좌 예정 거래 원본 하나가 없다고 카드 전체를 숨기면 쓸 수 있는 금액까지 사라진다 |
| 23 | "지출"의 정의 | `totalExpense`/`totalSpending`/`totalSaving` **셋 다** 내려줌 | 하나만 고름 | 화면마다 필요한 지출이 다르다. 하나만 주면 FE가 빼기로 나머지를 추정하고, 그 추정이 화면마다 달라진다 |
| 24 | 카테고리 비율의 분모 | 분모를 `ratioBase` 필드로 **응답이 직접 밝힘** | 문서로만 약속 | 선공유본이 그렇게 했다가 예시 안에서 분모가 어긋나 있었다(§2) |
| 25 | 월간 목표변화의 출처 | `goal_history`만 읽음 | 현재 잔액으로 재계산 | 지난달 리포트에 오늘 상태가 나온다 |
| 26 | 데일리 `notableEvents` | 관측 사실 4종(SQL 한 줄로 되짚을 수 있는 것만) | "어제 소비가 목표 달성일을 N일 바꿨다" 류 | 근거 계산이 따로 필요하다. 협의안도 "확인되지 않은 값은 만들지 않는다"고 요청했다 |
| 27 | 금융 효율 점수 | **제공하지 않음** | 저축률·고정비 비율로 점수 합성 | 산식과 비교 모집단에 근거가 없다. API로 굳히면 되돌리기 어렵다. `overallStatus`(진단 결과)로 대체 |
| 28 | 금융 생활 패턴 문구 | **제공하지 않음** | 리포트에 패턴 문장 목록 추가 | 진단 문제(API 14)가 이미 `domain`·`type`·`severity`·`evidence`를 갖고 있다. 같은 것을 두 번 정의하게 된다 |
| 29 | ERD의 미사용 컬럼 | 매핑하지 않고 남겨 둠(`upcoming_events`·`risks`·`recommendations`·`llm_comment`) | 삭제 / 억지로 재사용 | 브리핑 확장 자리다. `upcoming_events`("앞으로 예정")를 `notableEvents`("그날 일어난 일")로 쓰면 의미가 뒤집힌다 |

### 19. 스키마·하드닝 결정

| # | 결정할 것 | 고른 것 | 버린 대안 | 버린 이유 |
| --- | --- | --- | --- | --- |
| 30 | ERD와 다른 컬럼이 필요할 때 | **ERD를 먼저 고치고** 마이그레이션 작성 | 마이그레이션에만 추가 | `db/migration/README.md`의 규칙이다. 어긋나면 다음 사람이 ERD를 믿고 틀린다 |
| 31 | 소유권 보장 | `improvement_action.user_id` + 복합 FK `(user_id, recommendation_id)` | `recommendation_id` 단일 FK(ERD 원안) | 조회마다 추천을 조인해야 하고, 다른 사용자의 추천에 행동이 붙는 것을 DB가 못 막는다 |
| 32 | 통합 테스트 클래스 수 | 2개(`ImprovementApiTest`·`ReportApiTest`)로 제한 | 엔드포인트별로 분리 | `build.gradle` 주석: Spring 컨텍스트 캐시 상한(64)을 넘으면 LRU로 닫히는 컨텍스트가 H2 스키마를 드롭해 **무관한 테스트 98건이 깨진 전례**가 있다(2026-09-16). 컨텍스트를 몇 개 더 만드느냐가 비용이라 꼭 필요한 만큼만 늘렸다 |
| 33 | 09-17 타입 불일치 수정 방향 | **엔티티**에 `columnDefinition = "CHAR(7)"` | V39 `ALTER TABLE ... MODIFY VARCHAR(7)` | 마이그레이션의 CHAR 컬럼 16개 중 **엔티티가 있는 15개는 전부** 엔티티 쪽에서 타입을 명시한다(`columnDefinition` 또는 `@JdbcTypeCode`). 나머지 1개(`card_billing_statement.billing_month`)는 엔티티가 없어 validate 대상이 아니다. `card.expiry_month`가 같은 `yyyy-MM` CHAR(7) 선례다. ERD도 CHAR(7)이라 DB를 바꾸면 ERD를 또 고쳐야 하고 월 컬럼 중 이것만 VARCHAR이 된다. 운영 DDL·데이터를 건드리지 않고, 어차피 양쪽 다 재배포라 복구 속도 차이도 없다 |
| | | | **둘 다 적용** | 반대 방향으로 다시 어긋나 같은 장애가 난다. 반드시 한쪽만 |
| 34 | 가드 테스트의 판정 방식 | 타입 **계열** 비교 | Hibernate `AbstractSchemaValidator` 판정식을 그대로 이식 | 드라이버의 메타데이터 보고 방식까지 재현해야 한다(`TINYINT(1)`→`BIT`, STRING enum→`enum (...)`). 실제로 이식해 봤더니 **없는 문제를 40개** 만들어 냈다. 잡음이 섞이면 진짜 한 건이 묻힌다 |
| 35 | 가드 테스트를 어디서 돌리나 | DB 없는 일반 테스트 | `*MySqlIntegrationTest`에 추가 | 그 테스트들은 로컬 MySQL이 있을 때만 돈다. **정작 막아야 할 시점(마이그레이션을 추가하는 순간)에 아무도 실행하지 않는다** |
| 36 | 모르는 타입을 만났을 때 | 테스트 실패 | 건너뛰기 | 조용히 통과하면 가드가 있다는 착각만 남는다 |

### 20. 어떻게 진행했나 (순서와 방법)

**Day 11 구현 (09-16)**

1. **읽기 먼저.** 명세 CSV 2개 → FE 협의안 → 선공유 계약 → ERD 순. 여기서 §2의 모순(`totalExpense` 정의)이
   나왔다. 코드를 쓰기 전에 나온 게 다행이었다 — 구현 후였으면 FE가 화면에서 발견했을 것이다.
2. **붙일 데이터가 이미 있는지 확인.** `JdbcSpendingAnalysisReader`(소비·수입·저축성 지출),
   `GoalCalculationService.getForecast`(예상 달성일), `goal_history`(과거 시점 목표 상태),
   `CashflowEventCollector`(확정 예정 이벤트), `DiagnosisRunner`(재진단). **새로 계산할 것이 거의 없었다** —
   대부분 "이미 있는 계산을 어떤 기준으로 묶을까"의 문제였고, 그래서 §17~18이 계산식이 아니라 기준 결정으로 채워졌다.
3. **기존 관례 확인 후 작성.** `RecommendationController`(컨트롤러 형태), `Recommendation`(엔티티 주석·제약 선언
   방식), `RecommendationApiTest`(테스트 셋업) 를 먼저 읽고 같은 모양으로 썼다.
4. 마이그레이션 → 엔티티 → 리포지토리 → DTO → 서비스 → 컨트롤러 순으로 쓰고, 도메인 하나가 끝날 때마다 컴파일.
5. **테스트는 계약을 그대로 옮겼다.** "재수락해도 행이 늘지 않는다", "완료 월 전에는 측정하지 않는다",
   "두 번 조회해도 `generatedAt`이 같다"처럼 문서에 적은 문장이 곧 테스트 이름이 되게 했다.
6. 마지막에 전체 스위트. 추천 수락 경로를 건드렸으므로 회귀 확인이 필수였다.

**09-17 장애 대응**

1. **범위부터 확정.** 보고된 컬럼 하나만 고치면 validate가 다음에서 또 멈춘다 → "다른 불일치가 없다"를
   증명하는 것을 먼저 할 일로 잡았다.
2. **처음엔 눈으로 훑었다가 한 번 틀렸다.** CHAR 컬럼 16개를 grep해 엔티티와 대조했더니
   `finance_connection`의 해시 2개가 두 번째 장애로 보였다. 실제로는 `@JdbcTypeCode(SqlTypes.CHAR)`를
   쓰고 있었는데 `@Column` 줄만 봐서 놓쳤다. → **눈 대신 기계로 대조하는 방법으로 전환.**
3. Hibernate metadata(MySQLDialect)에서 기대 타입 465개를 뽑고, 마이그레이션 28개를 파싱해 선언 타입
   502개를 뽑아 대조. 실제 불일치는 하나뿐이고 나머지 후보 39개는 enum·불리언 계열이라는 것을 확인.
4. 수정 → **가드가 진짜 잡는지 확인**(수정을 잠시 되돌려 운영과 같은 메시지가 나오는지) → 수정 복구 →
   전체 스위트. 되돌려 확인하지 않으면 "통과하는 테스트"와 "동작하는 가드"를 구분할 수 없다.

### 21. 환경 제약과 우회 (기록용)

| 제약 | 확인한 것 | 우회 |
| --- | --- | --- |
| 개발기(WSL)에 **JDK 없음** | `java`/`javac` 부재, `/usr/lib/jvm` 비어 있음, `gradlew` 실행 권한 없음 | Temurin 21을 세션 스크래치패드에 받아 `JAVA_HOME`으로 전달. 프로젝트에는 아무것도 추가하지 않았다. Gradle 배포판·의존성은 `~/.gradle`에 캐시돼 있어 `--offline`으로 실행 |
| **Docker 없음** (WSL 통합 꺼짐), sudo 없음 | `docker info` 실패, `sudo -n` 실패 | 요청받은 MySQL 게이트 테스트를 실행할 수 없음 |
| MySQL 서버를 직접 받으려 시도 | `cdn.mysql.com`의 8.0.40/8.0.39/8.4.3 minimal 전부 404 | 포기 |
| MariaDB 11.4.4는 받을 수 있었음(HTTP 200) | MariaDB의 `JSON`은 `longtext` 별칭이라 JSON 컬럼 전부가 **거짓 불일치**로 잡힌다 | 쓰지 않음. 타입 검증이 목적인데 타입 보고가 다른 DB는 근거가 안 된다 |
| H2 기본 스키마에 없는 테이블 | `card_billing_statement`·`loan_repayment`는 JPA 엔티티가 없어 `ddl-auto: create-drop`으로 만들어지지 않는다 | `ReportApiTest`가 비어 있는 채로 생성(`CashflowSourceReaderTest`와 같은 방식). 덕분에 예정비용의 실패 처리 경로를 실제로 밟아 보고 §7-(1)을 고쳤다 |

**결론적으로 검증되지 않은 것**: 제약·인덱스·nullable은 MySQL 게이트 테스트의 몫이고 이번에 돌리지 못했다
(§16-2). 타입은 `SchemaTypeConsistencyTest`가 상시로 덮는다.

### 22. 커밋 구성

각 커밋이 **혼자서 빌드되도록** 순서를 잡았다(`improvement`는 `report`를 참조하지 않는다 — grep으로 확인).

| 커밋 | 범위 | 나눈 이유 |
| --- | --- | --- |
| `b788e5d` feat | Improvement 도메인 + V37 + 추천 수락 연결 + 테스트 | 리포트가 개선 행동을 참조하므로 먼저 와야 한다 |
| `a89c8ce` feat | Report 도메인 + V38 + 테스트 | |
| `f0825dc` docs | 계약 확정본·FE 회신·ERD·마이그레이션 README·day-11 | 코드 리뷰와 계약 리뷰를 분리해서 볼 수 있게 |
| `283d766` fix | 09-17 장애 수정 + 가드 테스트 + README 규칙 | 장애 대응 이력이 기능 커밋에 묻히지 않게 |

본문은 MR 템플릿 형식(📌 작업 목적 / 🛠️ 작업 내용 / 🔍 리뷰 포인트 / ✅ 테스트 / 📎 참고)을 따랐다.
