# Day 11 실제 진행 기록

> **이 파일에는 Day 11의 두 갈래 기록이 모두 들어 있다.** 섹션 번호는 갈래마다 따로 매겨져 있다.
>
> | 갈래 | 티켓 | 시작 위치 |
> | --- | --- | --- |
> | A. API 19 Improvement · API 20 Report 구현과 09-17 스키마 장애 대응 | S15P21D105-590 | 바로 아래 `## 2026-09-16` |
> | B. 시연 서버 9월 거래 시드 적재와 코치 3종 운영 검증 | S15P21D105-625 · 475 · 589 | 파일 뒤쪽 `## 2026-09-17 — 시연 서버…` |
>
> 2026-09-17 develop 머지에서 두 기록이 같은 파일명으로 부딪혔다(`a2cb5b4`가 A를 192줄 덮어썼다).
> 한쪽을 고르지 않고 **둘 다 원문 그대로** 남긴다. B의 날짜 헤딩 한 줄만 갈래를 구분하려고 덧붙였다.


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

> **인프라 쪽 기록**: [[day-08.md]] 31절에 같은 장애가 **인프라 관점**으로 남아 있다 —
> `validate` 복구 경위(24절)와 드라이런이 V35 기준이어서 V38을 놓친 점, 10:20~11:20 약 60분
> 중단의 타임라인, **롤백이 서비스를 지키지 못한 이유**(배포가 기존 컨테이너를 먼저 교체한다).
> 그 후속으로 배포 전 사전 검증(32절)과 `rollback` 태그 health 조건(33절)이 들어갔다.
> 이 절(원인·수정 방향·재발 방지)과 **둘을 같이 봐야 전모가 보인다.**

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

---

## 2026-09-17 — 시연 서버 시드·코치 검증 (S15P21D105-625 · 475 · 589)

### 1. 시연 서버 9월 거래 시드(625) 적재 여부 확인 — **미적재 확인**

요청: `DODAM_DEMO_SEPTEMBER_SEED.sql`의 9월 거래가 시연 서버에 적재됐는지, 그리고 이 데이터가
U0001~U0004에서만 조회되는 것이 맞는지.

#### 확인 방법

시연 서버(`ssafy-ec2`)에 SSH로 붙어 MySQL 컨테이너에 **읽기 전용 SELECT만** 실행했다.
비밀번호는 `MYSQL_PWD` 환경변수로 넘겨 명령줄·프로세스 목록에 노출되지 않게 했다.

```sql
SELECT COUNT(*) FROM transaction WHERE transaction_id LIKE 'SEED-SEP-U%' AND source_type='SEED';
SELECT COUNT(*), MAX(transaction_date) FROM transaction WHERE source_type='SEED';
```

#### 결과 — 적재되지 않았다

| 확인 | 값 |
| --- | --- |
| `SEED-SEP-U%` 거래 | **0건** |
| 전체 SEED 거래 | 348,192건, 최종일 **2026-08-28** |
| 9월 `subscription_payment_history` | **0건** |

`README.md` 105행의 "시연 서버에는 아직 적재하지 않았다"가 **현재도 정확하다.**
전체 SEED 거래가 8월에서 끊기는 것까지 일치한다(492 시드의 창이 `2026-03-01 ~ 2026-08-31`).

→ 지금 시연 계정으로 앱을 열면 **9월 화면이 비어 보인다.** 이 파일이 존재하는 이유가 정확히 그것이다
(파일 머리말: "the home / ledger screens ... would show an empty September").

#### 시연 계정별 현황

| 계정 | 거래 수 | 최종 거래일 |
| --- | --- | --- |
| U0001 | 132 | 2026-08-28 |
| U0002 | 138 | 2026-08-27 |
| U0003 | 132 | 2026-08-27 |
| U0004 | 142 | **2026-09-16** |

#### 발견 — U0004에 손으로 만든 9월 거래 1건이 있다

U0004만 9월 거래가 있어 출처를 확인했더니 `source_type = MANUAL`, `2026-09-16` 1건이었다.
시드가 아니라 **누군가 수동 거래 등록 API로 만든 테스트 데이터**다.
DB 전체에서 9월 거래는 이 1건이 전부다.

- 625 시드는 `SEED-SEP-U%` + `source_type='SEED'`만 지우고 다시 만들므로 **이 MANUAL 행과 충돌하지 않는다.**
- 다만 U0004는 깨끗한 기준선이 아니다. 진단·패턴 결과를 계정 간 비교할 때 염두에 둘 것.

### 2. U0001~U0004 한정 여부 — **맞다 (파일 구조로 보장)**

`DODAM_DEMO_SEPTEMBER_SEED.sql:160`이 대상 사용자를 이렇게 잡는다.

```sql
FROM (SELECT d.digit AS sequence_no FROM tmp_dodam_625_digit d WHERE d.digit BETWEEN 1 AND 4) seed
JOIN `user` user_row
  ON user_row.email = CONCAT('seed.kmeans.u', LPAD(seed.sequence_no, 4, '0'), '@dodam.invalid')
```

`BETWEEN 1 AND 4`로 범위를 닫고 이메일로 조인하므로 `u0001`~`u0004` 외에는 행이 만들어지지 않는다.
머리말도 "The other 3,996 K-Means users keep the six-month window untouched"로 같은 말을 한다.

**주의**: "조회가 제한된다"가 아니라 "**데이터가 네 명에게만 생성된다**"는 뜻이다. 조회 API에 계정별
필터가 따로 있는 것이 아니라, 나머지 3,996명에게는 9월 행 자체가 없어서 안 보이는 것이다.
K-Means 학습은 `2026-03 ~ 2026-08` 창을 쓰므로 9월 행이 네 명에게만 있어도 군집 결과가 틀어지지 않는다.

### 남은 것

- [x] ~~625 시드 적재~~ — 2절, 90건/4명 적재 완료
- [x] ~~`README.md` 적재 상태 문구 갱신~~ — 2절
- [ ] U0004의 MANUAL 9월 거래 1건 — 시연 전 지울지 남길지 판단 (기준선 비교에 영향)
- [ ] **추천 효과 금액이 비현실적이다** (3절). 월 347만원 저축·364만원 감축은 소득 400만원 대비
      실행 불가능하다. 목표 시드(493)의 목표 금액 설정 문제로 보이며 시연에서 눈에 띈다 — **493 소관**
- [x] ~~배포 후 요약·추천 문장 교정 재확인~~ — 9절(2026-09-21), 두 결함 모두 해소 확인

### 2. 625 시드 시연 서버 적재 완료

1절에서 미적재로 확인된 `DODAM_DEMO_SEPTEMBER_SEED.sql`을 시연 서버에 적재했다.
1~8번이 이미 들어간 서버라 11번만 실행했다.

#### 실행 전 확인한 안전장치

운영 DB에 **쓰는** 작업이라 파일이 무엇을 바꾸는지 먼저 읽었다.

| 확인 | 내용 |
| --- | --- |
| preflight | `CHECK` 제약 6개로 잠겨 있다(4명/카드 4/카테고리 20/구독 6/금액 불일치 0/8월 행 4명). 하나라도 어긋나면 INSERT가 실패하고 모든 영구 쓰기가 `ready=1` 조건에 걸려 **아무것도 안 들어간다** |
| DELETE 범위 | `source_type='SEED' AND transaction_id LIKE 'SEED-SEP-U%'` + 4명 조인 → U0004의 MANUAL 행은 대상 밖 |
| 되돌릴 수 없는 변경 | `subscription.next_payment_date`를 10월로 이동. 재실행해도 같은 값이라 멱등하지만 **원래 값은 복구되지 않는다**(README에 명시된 의도된 동작) |
| 롤백 지점 | 당일 04:00 자동 백업 존재(`/home/ubuntu/backups/mysql/dodam-20260917-040001.sql.gz`, 8.6MB) |

#### 실행

```bash
docker compose exec -T -e MYSQL_PWD="$DBP" mysql \
  mysql -h127.0.0.1 -u"$DBU" "$DBN" < .../DODAM_DEMO_SEPTEMBER_SEED.sql
```

비밀번호는 `MYSQL_PWD`로 넘겨 명령줄·프로세스 목록에 남지 않게 했다. `EXIT=0`.

#### 결과 (파일 자체 검증 + 독립 재확인)

| 항목 | 값 |
| --- | --- |
| `SEED-SEP-U%` 거래 | **90건 / 4명 / 2026-09-01~09-27** |
| 사용자별 증가 | 132→154, 138→161, 132→154, 142→165 (각 +22·+23·+22·+23) |
| 9월 구독 결제 이력 | 6건 (거래 불일치 0) |
| `next_payment_date` | 6건 모두 10월로 이동 |
| 계약 위반 | 0건 |
| **시연 계정 4명 밖의 9월 거래** | **0건** |
| U0004 MANUAL 거래 | 4건 그대로 보존 |
| `tmp_dodam_625_*` 작업 테이블 | 파일 끝 cleanup으로 모두 정리됨 |

#### 검증 중 내가 낸 실수 하나

범위 확인에 `email LIKE 'seed.kmeans.u000[1-4]@dodam.invalid'`를 썼는데, **MySQL의 `LIKE`는
`[1-4]` 문자 클래스를 지원하지 않는다**(그건 `REGEXP`). 패턴이 아무것도 매치하지 못해
`NOT LIKE` 쪽이 전부 참이 됐고, "시연 계정 밖 9월 거래 91건"이라는 **거짓 경보**가 나왔다.
`IN (...)`으로 바꿔 재확인하니 실제 값은 0건이었다.

데이터 문제로 보고하기 전에 쿼리부터 의심한 것이 맞았다. 운영 DB 확인에서는 조회 문법 하나가
멀쩡한 데이터를 사고처럼 보이게 만들 수 있다.

#### 문서 갱신

`backend/database/seeds/README.md`의 "시연 서버에는 아직 적재하지 않았다"를 적재 결과로 교체했다.

### 3. 코치 3종 운영 실호출 — 처음으로 전부 확인, 그리고 문장 품질 결함 2건

2절에서 9월 거래가 생기면서 **소비 증가 문제가 탐지**돼, 그동안 대상이 없어 한 번도 호출하지 못했던
`inefficiency-explanation`·`recommendation-explanation`을 처음 검증할 수 있었다.

U0002 재진단(`forceRefresh`) → `diagnosisId=6`, `ruleVersion=v5-copy-tone`, 문제 6건(HIGH 4).

#### 22절 문구 정비가 실제로 반영됐다

| | |
| --- | --- |
| 진단 설명 | `최근 3개월 평균보다 236.5% 높아요.` (19자, 정비 전 63자) |
| 추천 제목·설명 | `기타 지출 줄여보기` / `이전 수준으로 되돌리면 월 109,100원을 아낄 수 있어요.` |
| 비효율 AI 설명 | **74자** (정비 전 239자) — 80자 상한 안, 수치 되풀이 없음, 조언만 |

> 뷰티·미용 지출이 늘어난 달의 내역을 먼저 확인해 보세요. 최근 3개월과 그 이전 3개월의 사용처를
> 비교해 보면 원인을 찾기 쉬워요.

FE 문서의 "기대 결과"와 사실상 같은 형태다. 3종 모두 `source=AI`, `gpt-5.4-mini`.

#### 결함 1 — 요약 둘째 문장이 비문이었다

> 종합 상태는 위험으로 나왔고, **목표 달성 계획 점검과 기타 소비가 늘었어요.**

주요 문제의 **제목 두 개**(`목표 달성 계획 점검`, `기타 소비가 늘었어요`)를 접속사로 엮다가 깨졌다.
제목은 그 자체로 완결된 문장인데(진단 규칙이 `-어요`로 만든다) 모델이 명사구처럼 취급했다.
`facts`에 주요 문제 구간을 주면서 **어떻게 쓰라는 지시를 안 준 것**이 원인이다.

→ 요약 지시에 "제목을 그대로 이어 붙이지 않는다. 어느 영역이 눈에 띄는지만 네 말로 짧게 쓴다" 추가.

#### 결함 2 — 추천 설명이 상한을 넘었다 (102자 / 80자)

내용은 좋았으나 두 문장이 같은 말을 다르게 반복했다("천천히 줄여보시면 무리 없어요" +
"조금씩 조정해 보시면 좋아요").

→ "같은 말을 다르게 바꿔 두 번 쓰지 않는다. 한 문장으로 충분하면 한 문장만 쓴다" 추가.

#### 길이 초과를 관측 가능하게

프롬프트에만 적혀 있던 상한을 `CoachPromptFactory.maxMessageLength(topic)`으로 코드에도 두고,
초과 시 `CoachService`가 WARN을 남기게 했다.

**자르거나 버리지 않는다.** 조금 긴 좋은 문장이 템플릿보다 낫다. 이번 결함도 손으로 세어 보고서야
알았는데, 앞으로는 로그로 드러난다.

#### 프롬프트 버전 상향이 캐시를 갈아 준다

`VERSION 604.1 → 604.2`. 22절에서 캐시 키에 프롬프트 버전을 넣어 둔 것이 바로 이 상황을 위한
장치다 — 저장 데이터는 그대로이므로 facts 해시만으로는 옛 문장이 계속 나갔을 것이다.

#### 범위 밖 발견 — 추천 금액이 비현실적이다

```
저축 조금씩 늘려보기 | 월 3,475,000원을 더 저축하면 '전세 보증금' 달성에 다가갈 수 있어요.
이번 달 소비 줄여보기 | 월 3,639,882원을 줄이면 '월 소비 줄이기' 예산을 지킬 수 있어요.
```

월 347만원 저축·월 364만원 감축은 소득(400만원) 대비 실행 불가능한 수치다. 문구가 아니라
**시드의 목표 금액이 소득에 비해 과하게 설정된 것**으로 보인다. 589 범위 밖이며 목표 시드(493) 소관.
시연에서 눈에 띌 항목이라 아래 남은 것에 등록한다.

## 2026-09-17 — 589 하드닝 (S15P21D105-589)

두 항목이다. 둘 다 "동작은 하는데 확인할 방법이 없다"는 같은 성격의 문제다.

| 항목 | 내용 |
| --- | --- |
| Redis 연결 확인 | 프로젝트 첫 Redis 사용인데 **실패를 삼켜** 틀려도 티가 안 난다 |
| 코치 레이트 리밋 | **외부 과금이 걸린 유일한 엔드포인트**인데 사용자별 호출 제한이 없다 |

### 4. Redis 연결 확인 — 삼켜진 실패를 어떻게 관측하나

#### 문제

Redis를 쓰는 곳은 두 군데다: 코치 응답 캐시(`RedisCoachAnswerCache`)와 멱등 키 가드
(`IdempotencyKeyGuard`). **둘 다 예외를 삼킨다.** 캐시는 기능이 아니라 비용 절감 장치이므로 옳은
설계지만, 대가가 있다.

- 비밀번호·호스트가 틀려도 **응답은 전부 정상이다.** 캐시가 100% 미스로 돌며 요금만 는다.
- H2로 도는 테스트는 이걸 절대 잡지 못한다. 접속 설정은 프로젝트 초기부터 있었지만 Java 사용처가
  없어, **실제로 연결되는지 한 번도 검증된 적이 없었다.**

#### 확인 방법 4가지를 놓고 고른 것

| 방법 | 판단 |
| --- | --- |
| 로그에서 `Coach cache lookup failed` grep | **불완전하다.** 0건이 "정상"인지 "그 경로를 아무도 안 탐"인지 구분이 안 된다 |
| `redis-cli`로 `coach:*` 키 직접 조회 | 가장 직접적이지만 **비밀번호를 읽어야 한다** → 이번 환경에서 운영 읽기 권한에 막혀 실행 못 함 |
| `/actuator/health` | 간접 증거로 유효 (아래) |
| **같은 요청을 연달아 보내 응답 시간 비교** | **채택.** 캐시 적중 여부가 지연으로 드러난다 |

#### 실행과 결과

시연 계정(U0002)으로 `POST /api/v1/coach/financial-summary`를 세 번 연속 호출했다.

```
call1  http=200  time=2.294054s
call2  http=200  time=0.108664s
call3  http=200  time=0.105029s
call1..3  source=AI  model=gpt-5.4-mini  len=78  (문장 동일)
```

**1회차만 2.29초, 이후 0.10초. 문장은 세 번 모두 같다.** 저장과 조회가 모두 동작한다는 뜻이다.
Redis 쓰기가 실패했다면 매 호출이 2초대에 매번 과금됐을 것이고, 읽기가 실패했다면 문장이 매번
달라졌을 것이다(모델 출력은 완전히 결정적이지 않다).

보조 증거도 맞아떨어진다.

- `docker logs backend --since 24h | grep -c "Coach cache lookup failed"` → **0**, write 실패도 **0**
- `/actuator/health` → `{"status":"UP"}`. Spring Boot는 `RedisConnectionFactory` 빈이 있으면
  Redis 헬스 인디케이터를 자동으로 켠다 — 실제로 우리 `application-test.yml`은 테스트를 위해
  `management.health.redis.enabled: false`로 **꺼야 했다.** 즉 운영에서 UP은 PING이 통과했다는 뜻이다.
- `docker compose`가 redis 컨테이너의 `--requirepass`와 backend의 `REDIS_PASSWORD`에 **같은 `.env`
  변수**를 넣는다 → 비밀번호 불일치는 구조적으로 발생하기 어렵다.

#### 확인으로 끝내지 않고 관측 가능하게 만들었다 — `RedisStartupCheck`

이번엔 확인했지만, **다음 배포에서 또 같은 질문을 하게 된다.** 그리고 로그 grep은 여전히 0건의 의미를
구분하지 못한다. 그래서 기동 직후 PING 한 번을 남기는 컴포넌트를 추가했다.

```
INFO  Redis is reachable (ping=PONG). Coach answer cache and idempotency guard are backed by Redis.
WARN  Redis is NOT reachable: ... The coach answer cache will miss on every call (each call is billed)
      and the idempotency guard will pass requests through unprotected.
```

- 배포마다 `docker logs backend | grep "Redis "` **한 줄**로 끝난다. "안 써서 조용한 것"과 "붙어서
  조용한 것"이 구분된다.
- **기동은 막지 않는다.** Redis는 어떤 기능에도 필수가 아니다 — 없으면 비싸고 보호가 약해질 뿐
  화면은 전부 동작한다. 기동을 막으면 대가가 훨씬 크다. 그래서 ERROR가 아니라 WARN이다.
- 테스트에서는 끈다(`app.redis.startup-check.enabled: false`). 안 끄면 모든 `@SpringBootTest`가
  Redis 연결 타임아웃만큼 느려진다.

#### 덤으로 드러난 것 — 멱등 키 가드의 주석이 이제 낡았다

`IdempotencyKeyGuard`의 주석에는 *"Redis 연결이 실제로 검증된 적이 없어 닫아 두면 상환 자체가 막힐 수
있다"*가 fail-open의 근거로 적혀 있다. 그 전제가 오늘 깨졌다. 다만 지금 바꾸면 상환 API의 실패 모드가
달라지므로 589 범위 밖으로 두고 아래 남은 것에 적는다.

### 5. 코치 레이트 리밋 — 무엇을 막으려는 것인지부터

#### 위협 모델

공격자가 아니다. 세 엔드포인트 모두 인증이 필요하다. 실제로 일어날 일은 **FE의 렌더 루프나 재시도
스크립트가 같은 호출을 반복해 요금이 폭주하는 것**이다. 즉 이건 보안 문제라기보다 **비용 문제**다.

nginx에 레이트 리밋이 있지만 이 문제를 풀지 못한다.

| | nginx `api_general` | 필요한 것 |
| --- | --- | --- |
| 기준 | IP(`$binary_remote_addr`) | 로그인한 **사용자** |
| 한도 | 초당 20건 = **시간당 72,000건** | 비용이 되는 수준 |
| 세는 대상 | 모든 HTTP 요청 | **과금되는 호출** |
| 목적 | 플러딩 차단 | 비용 상한 |

게다가 IP 기준은 캠퍼스망처럼 팀 전체가 같은 공인 IP로 나가는 환경에서 엉뚱한 사람을 막는다
(day-08 13절에서 로그인 버킷 때문에 이미 겪은 문제다).

#### 결정 1 — 구현 위치와 방식

| 선택지 | 버린 이유 / 고른 이유 |
| --- | --- |
| nginx `limit_req`에 `/api/v1/coach/` 버킷 추가 | 30초면 끝나지만 **IP 기준**이라 사용자별이 안 되고, 재시작·창 관리가 없어 비용 상한이 되지 못한다 |
| Bucket4j (+ Redis) | 기능은 충분하나 **이 하나를 위해 의존성을 추가**한다. 토큰 버킷의 정교함이 필요한 상황이 아니다 |
| Resilience4j `RateLimiter` | 인메모리 전용이라 **재시작하면 초기화**되고 사용자별 키 관리를 결국 직접 해야 한다 |
| **Redis 고정 창 카운터 직접 구현** | **채택.** 이미 있는 Redis를 쓰고(오늘 검증도 끝났다) 코드가 40줄이다. 재시작·다중 인스턴스에서도 한도가 유지된다 |

#### 결정 2 — 무엇을 셀 것인가: 요청이 아니라 "돈이 나가는 호출"

이게 이번 구현에서 제일 중요한 판단이다. 컨트롤러나 서블릿 필터에서 HTTP 요청 단위로 세는 게 훨씬
쉽지만, 그렇게 하면 **과금되지 않는 호출까지 세어 정상 사용자를 먼저 막는다.**

- 캐시 적중은 돈이 들지 않는다 → 화면을 다시 열었다는 이유로 429를 주게 된다.
- `app.ai.enabled=false`거나 민감정보 검사에 걸려 **애초에 외부로 나가지 않은** 요청도 마찬가지다.

그래서 예산은 `CoachService`가 **외부 호출 직전에만** 깎는다. 상한의 목적이 비용이면 세는 단위도
비용이어야 한다. (호출이 실패해도 되돌리지는 않는다 — 공급자 입장에선 시도된 호출이다.)

#### 결정 3 — 한도를 넘으면 429가 아니라 200 + 템플릿

코치는 원래부터 "어떤 실패에서도 200"이다. 문장은 데이터의 표현 계층이고 수치는 전부 서버에 있다.
같은 원칙을 여기에도 적용해 `source=TEMPLATE`, `degradedReason=RATE_LIMITED`로 내려간다.

429를 버린 이유:

1. FE에 **에러 분기를 새로 요구**한다. 지금 화면엔 그 분기가 없어 사용자에겐 "코치가 깨진 것"으로 보인다.
2. 템플릿 문장은 같은 상황에서 **정상 화면으로 보인다.** 수치·판정은 어차피 동일하다.
3. FE는 이미 모든 `TEMPLATE` 상태를 같은 화면으로 그린다 → **FE 추가 작업이 없다.** 타입 유니온에
   값 하나만 더하면 된다.

#### 결정 4 — Redis가 죽으면? 캐시와 **정반대**로 판단했다

캐시는 실패를 삼키고 그냥 통과시킨다. 레이트 리밋에서 같은 선택을 하면 안 된다.

> Redis가 죽은 순간은 **캐시도 함께 죽어 모든 호출이 과금되는**, 상한이 가장 필요한 상황이다.
> 여기서 열어 두면 레이트 리밋은 정확히 필요한 때에 없는 것과 같아진다.

그렇다고 전부 막으면(fail-closed) 코치가 통째로 템플릿이 된다. 그래서 **프로세스 내 카운터로 내려가
같은 한도를 계속 건다**(`InProcessCallCounter`).

- 인스턴스가 하나인 지금 배포에서는 Redis 카운터와 **결과가 같다.**
- 여러 대로 늘면 인스턴스 수만큼 느슨해진다 — 한도가 없는 것보다 낫다는 판단이다.
- 재시작하면 초기화되는 것도 같은 이유로 받아들인다. 맵은 1만 명에서 통째로 비운다(메모리 상한).

#### 결정 5 — `INCR` + `EXPIRE`를 Lua 한 스크립트로 묶었다

두 명령을 따로 보내면 그 사이에 연결이 끊겼을 때 **TTL 없는 카운터**가 남는다. 값이 한도를 넘은 채
만료되지 않으므로 그 사용자는 **영구히 묶인다**(축출 정책이 지워주기를 기다리는 수밖에 없다).
한 스크립트로 묶으면 그 창 자체가 사라진다.

```lua
local used = redis.call('INCR', KEYS[1])
if used == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
return used
```

첫 증가에만 TTL을 건다. 호출마다 갱신하면 창이 영원히 끝나지 않는다.

슬라이딩 윈도우(정렬 집합)는 창 경계에서 최악 2배가 몰리는 문제를 없애주지만, 호출마다 원소를 쌓고
지워야 한다. 필요한 것은 정밀한 트래픽 정형이 아니라 비용 상한이라 **고정 창**으로 갔다.

#### 기본값: 사용자당 시간당 40건

한 화면을 처음 여는 데 필요한 **생성** 호출은 요약 1 + 문제 설명 최대 6 + 추천 설명 5로 12건 남짓이고,
같은 사실을 다시 부르면 캐시 적중이라 예산을 쓰지 않는다. 40건은 "한 시간에 재진단을 세 번 하고 모든
항목을 새로 펼쳐 보는" 사용보다도 넉넉하다. 반대로 폭주하는 코드는 사용자당 시간당 40건으로 묶인다.

이 값에 닿는 호출 패턴은 사람이 아니라 코드일 가능성이 높아, 막힐 때 WARN을 남긴다
(`Coach call refused by the per-user budget: userId=.. topic=..`).

### 6. 검증

| 항목 | 결과 |
| --- | --- |
| 전체 테스트 | **1,196건 통과 / 실패 0** |
| 신규 `RedisCoachCallRateLimiterTest` | 6건 — 한도 이내/초과, 키 형태, off일 때 Redis 무접촉, **Redis 장애 시 한도 유지**, 스크립트 무응답 |
| 신규 `InProcessCallCounterTest` | 2건 — 사용자별 격리, 창 롤오버 |
| `CoachApiTest` | 15 → **17건** — 한도 초과 시 200 + `RATE_LIMITED` + 수치 그대로, **캐시 적중은 예산을 쓰지 않는다** |
| 운영 Redis 연결 | 실호출 지연 비교로 확인 (4절) |

### 7. 산출물

**신규**

- `backend/src/main/java/.../domain/coach/service/CoachCallRateLimiter.java` — 포트
- `backend/src/main/java/.../domain/coach/service/CoachRateLimitProperties.java` — `app.coach.rate-limit`
- `backend/src/main/java/.../infrastructure/ratelimit/RedisCoachCallRateLimiter.java` — Redis 고정 창 + Lua
- `backend/src/main/java/.../infrastructure/ratelimit/InProcessCallCounter.java` — 장애 시 대체 카운터
- `backend/src/main/java/.../global/health/RedisStartupCheck.java` — 기동 PING 로그
- 테스트 2종 (`RedisCoachCallRateLimiterTest`, `InProcessCallCounterTest`)

**수정**

- `CoachService` — 외부 호출 직전 예산 확인 / `CoachDegradedReason` — `RATE_LIMITED` 추가
- `application.yml` (`app.coach.rate-limit`), `application-test.yml` (둘 다 off)
- `CoachApiTest` — 예산 시나리오 2건
- `backend/docs/ai-coach.md` — 12절 신설, 7·10·11절 갱신(연결 확인 결과 포함)
- `backend/docs/ai-coach-fe-guide.md`, `frontend/src/types/coachApi.types.ts` — `RATE_LIMITED` 추가
- `infra/todo/security.md` R6 — 애플리케이션 레벨 사용자별 예산 항목 추가

**환경변수 추가 없음** → `.env`·compose·`check-env.sh` 변경 불필요. 코드 배포만으로 적용된다.

### 8. 남은 것 / 개선할 점

- [ ] **FE에 `RATE_LIMITED` 전달** — 타입 유니온은 추가해 뒀고 화면 동작은 그대로지만, 값이 생겼다는
      사실은 공유해야 한다(배지 문구를 따로 둘지는 FE 판단).
- [ ] **배포 후 확인 2줄** — `docker logs backend | grep "Redis "`(PONG 확인),
      `grep "refused by the per-user budget"`(정상 사용자가 막히면 한도를 올린다).
- [ ] **멱등 키 가드 fail-open 재검토** — 근거였던 "Redis 연결 미검증"이 오늘 해소됐다. 단 상환 API의
      실패 모드가 바뀌므로 별도 판단이 필요하다.
- [ ] **호출량 자체가 관측되지 않는다.** 성공 경로에 로그도 메트릭도 없어 "이번 달 코치를 몇 번 불렀나"에
      답할 수 없다. 지금은 한도가 상한을 보장할 뿐이다. → 아래 Prometheus 항목과 함께 풀어야 한다.
- [ ] **⚠️ 범위 밖 발견 — Prometheus의 backend 잡이 계속 실패하고 있다.**
      `up{job="backend"} = 0`(node·prometheus 잡은 1). 원인은 두 가지가 겹쳐 있다:
      `management.endpoints.web.exposure.include: health`라 `/actuator/prometheus`가 열려 있지 않고
      (`docker exec backend curl` → **401**), 애초에 `micrometer-registry-prometheus` 의존성이 없다.
      Day 6에 대시보드를 붙인 이래 **백엔드 지표는 한 번도 수집된 적이 없다.** 고치려면 의존성 +
      노출 설정 + 내부 경로 인증 예외가 필요해 별도 작업으로 뺀다(nginx는 이미 외부 `/actuator/`를 404로 막는다).

## 2026-09-21 — 배포 후 재확인 (S15P21D105-589)

3절에서 잡은 문장 결함 2건과, 4절에서 직접 확인하지 못했던 Redis 키를 배포본에서 확인했다.

### 9. 문장 결함 2건 교정 확인

U0002, 최신 완료 진단(`diagnosisId=6`) 기준. 3종 모두 `source=AI`, `degradedReason=null`.

| topic | 교정 전 | 교정 후 | 상한 |
| --- | --- | --- | --- |
| 요약 | 103자 · **비문** | **69자** · 정상 | 150 |
| 비효율 | 74자 | 67자 | 80 |
| 추천 | **102자** · 같은 말 반복 | **30자** | 80 |

> 확인한 범위에서는 소비와 목표 쪽에 문제가 눈에 띄어요.
> 다만 10개 항목은 검사하지 못해 전체 상태를 단정할 수는 없어요.

**결함 1 해소**: 문제 제목을 엮지 않고 "소비와 목표 쪽"으로 영역만 짚는다. 지시한 그대로다.
21절의 검사 범위 고지("10개 항목은 검사하지 못해")도 그대로 살아 있다 — 문장을 줄이면서 이게
밀려날 수 있었는데 남았다.

**결함 2 해소**: 추천 설명이 102자 → 30자. 반복 문장이 사라졌다.

숫자는 모두 facts에 있는 값(`10개`)뿐이고 지어낸 수치는 없다.

### 10. Redis 캐시 — 4절에서 못 했던 직접 확인

4절은 `redis-cli` 직접 조회를 "비밀번호를 읽어야 해 실행 못 함"으로 두고, 응답 지연 비교(2.29s → 0.10s)로
간접 확인했다. 이번에는 직접 봤다.

```
coach:640.1:2:FINANCIAL_SUMMARY:a9b378175849ed52
coach:640.1:2:INEFFICIENCY_EXPLANATION:80fc1824c9aeda3c
coach:640.1:2:RECOMMENDATION_EXPLANATION:6dfb1be4b42a2815
TTL = 3578초
```

호출 3건에 키 3개, TTL이 1시간에서 막 깎이기 시작한 값이다. **저장·조회·만료가 모두 설계대로 동작한다.**
`docker compose logs backend --since 30m | grep -c "Coach cache"` → **0**(경고 없음).

→ "Redis 첫 사용처라 연결이 검증된 적 없다"는 미결 항목이 직접 증거로 닫혔다.
비밀번호는 `REDISCLI_AUTH` 환경변수로 넘겨 명령줄에 남기지 않았다.

### 11. 내 로컬이 배포본보다 뒤처져 있었다 — 담당 정리

캐시 키의 프롬프트 버전이 내가 올린 `604.2`가 아니라 **`640.1`**이었고, `coach:chat:*`·`coach:rl:*`처럼
만든 적 없는 키가 보여 확인했다. 배포본에는 09-17 이후 작업이 얹혀 있었다.

| 작업 | 티켓 | 담당 | 시각 |
| --- | --- | --- | --- |
| 코치 3종 · 캐시 · 문구 정비 | 589 | Claude + 김지우 | 09-16~17 |
| 레이트 리밋 · Redis 기동 점검 | 589 | **김지우** (`3db37dc`) | 09-17 13:17 |
| 코치 채팅 도구 계층(A-2) | **640** | **구동재** (`ce0651a`) | 09-18 14:50 |
| "도리에게 묻기" 채팅(A-3) | **640** | **구동재** (`04cc8ba`) | 09-18 15:16 |

내 커밋 4개(`d24993a`·`fb9cdc3`·`ae1f7ab`·`386283d`)는 배포본에 **전부 포함**돼 있고 604 문구 수정도
서버 파일에서 확인했다. 로컬에만 남은 것은 없다.

**교훈**: 캐시 키에 버전을 넣어 둔 덕분에 "내가 아는 코드와 배포본이 다르다"가 **키 문자열 하나로
드러났다.** 버전을 안 넣었으면 출력만 보고 내 변경이 반영된 줄 알았을 것이다.

### 12. 다음 작업 전에 확인할 것 — 툴콜링과 숫자 가드

640의 툴콜링은 내가 만든 구조의 **전제를 바꾼다.**

| | 589 설계 | 640 툴콜링 |
| --- | --- | --- |
| 사실 수집 | 서버가 **미리 전부** 모아 한 번에 준다 | 모델이 **무엇을 조회할지 스스로** 정한다 |
| 숫자 허용 목록 | `CoachFacts`에서 뽑는다 | 도구가 돌려준 값도 포함돼야 한다 |

`CoachNumberGuard.allowedNumbers()`는 `CoachFacts`만 읽는다. 도구 응답의 수치가 허용 목록에 들어가지
않으면 정상 문장이 `AI_UNVERIFIED_NUMBER`로 탈락하고, 반대로 도구 응답을 통째로 허용 목록에 넣으면
가드가 느슨해진다. 커밋 메시지에 "CHAT 프롬프트/템플릿/숫자 검증"이 적혀 있어 다뤄졌을 가능성이
높지만 **코드로 확인하지 않았다.**

- [ ] 최신 코드를 받은 뒤 640의 숫자 검증 경로를 읽고, 도구 응답 수치가 어떻게 허용되는지 확인한다
- [ ] 확인 결과를 `ai-coach.md` §5에 반영한다(지금 문서는 589 전제로만 쓰여 있다)

**이 WSL에서는 `git fetch`가 자격증명 없이 막혀 최신 코드를 당길 수 없다.** 다음 작업 전에 반드시
로컬을 최신으로 맞춰야 하며, 그전까지 코치 코드를 수정하면 640과 충돌한다.

### 13. 655/656 구독 seed(15번) + 알림 seed(9번) 적재 — 요청서 기대값 전부 일치

BE 요청서(655 "내 구독 보기" P1)대로 시연 계정 구독을 실제 서비스명으로 재생성했다.
요청서의 실행 명령을 그대로 썼다(`--default-character-set=utf8mb4`, `MYSQL_PWD` 주입).

#### 선행 조건 확인 (실행 전)

| 조건 | 상태 |
| --- | --- |
| ① 서버 backend ≥ develop `7420d6c`(656) | ✅ 현재 `4df2a9a`(658)로 더 앞섬 |
| ② 6·8·11번 적재 | ✅ 11번은 09-17에 적재(2절) |
| ③ 새 Flyway 없음 | ✅ |
| 시드 파일 서버 존재 | ✅ 24,822 bytes |

#### 결과 — 기대값 대조

| 기대값 | 실측 | |
| --- | --- | --- |
| preflight `1/4/4/1/24/4/4` | `1/4/4/1/24/4/4` | ✅ |
| 구독 23건 (5/6/7/5) | 5·6·7·5 = **23** | ✅ |
| U0001 월 54,590 | 54,590 | ✅ |
| U0002 83,680 | 83,680 | ✅ |
| U0003 94,490 | 94,490 | ✅ |
| U0004 ACTIVE 4건 37,690 + PAUSED 1 | cnt 5 / active 4 / 37,690 | ✅ |
| 구독 결제 거래 165 | 165 | ✅ |
| 결제 이력 158 | 158 | ✅ |
| 이력-거래 불일치 0 | 0 | ✅ |
| 계약 위반 0 | 0 (카드 불일치 0) | ✅ |
| 다른 사용자 구독 5,994 유지 | 5,994 | ✅ |
| U0003 미등록 반복결제 디즈니+ 9,900 × 7 (3/27~9/27) | 7건, 2026-03-27~09-27 | ✅ |
| `tmp_dodam_demosub%` 0개 | 0개 | ✅ |

9번 재실행 후 알림 문구가 새 구독명을 따라갔다: `도담 비디오 결제 예정` → **`유튜브 프리미엄 결제 예정`**.
알림 4명 × 5~6건, 소스 계약 위반 0.

#### API 스모크 (읽기 전용으로 선확인)

요청서상 BE-A 몫이지만, 선행 조건 ①의 목적(`serviceCode`·`iconKey`)이 실제로 충족됐는지는
먼저 봐 두는 편이 낫다고 판단해 U0003으로 조회만 했다.

```
GET /subscriptions?status=ACTIVE  → 7건, code·icon 전부 채워짐
  유튜브 프리미엄 YOUTUBE_PREMIUM/youtube · 넷플릭스 NETFLIX/netflix
  쿠팡 와우 멤버십 COUPANG_WOW/rocket · 챗GPT 플러스 CHATGPT_PLUS/chatgpt ...
GET /subscriptions/candidates     → 디즈니+ 1건만
```

`subscription` 테이블에는 `service_code`·`icon_key` 컬럼이 **없다.** 응답값은
`SubscriptionServiceCatalog`가 `name`으로 매핑하는 파생값이라, 시드가 실제 서비스명을 넣은 것만으로
아이콘이 붙었다. 이름이 카탈로그에 없으면 조용히 비게 되는 구조다.

#### 확인 필요 — 후보의 `occurrences`가 7이 아니라 6이다

거래는 7건(2026-03-27~09-27)이 맞는데 후보 응답의 `occurrences`는 **6**이다. 조회 창이 완료된 달
기준(3~8월 6회)이면 설명되지만 코드로 확인하지 않았다. 요청서 기대값은 "거래 7건", "후보 1건"이라
둘 다 충족하며 `occurrences`는 명시되지 않았다 — **BE-A에게 의도된 값인지 확인 요청.**

#### 요청서 확인 쿼리를 원문 그대로 돌려 봤다

사용자 요청으로 교정 없이 실행한 결과를 근거로 남긴다.

```
ERROR 1146 (42S02): Table 'dodam.users' doesn't exist
```

`users`·`u.id`·`u.login_id` 세 곳이 실제 스키마(`user`·`user_id`·`email`)와 다르다. `U0001`은 시드
파일 내부의 `synthetic_user_code`일 뿐 DB 컬럼이 아니다. 작성자도 회신에서 같은 내용을 인정했다.
검증은 스키마에 맞춘 쿼리로 수행했다.

### 14. 후보 `occurrences`가 6인 이유 — 조회 창이 today를 따라 움직인다

13절에서 남긴 확인 항목. **버그가 아니라 조회 창의 정의 때문이다.**

`SubscriptionDiscoveryService:80-81`

```java
LocalDate today = clock.today();                              // 2026-09-21
LocalDate startDate = today.minusMonths(months).plusDays(1);  // 2026-03-22  (months 기본 6)
// 조회 범위 [startDate, today] — 미래 날짜 거래는 들어오지 않는다
```

디즈니+ 거래 7건(매월 27일, 2026-03-27 ~ 09-27) 중 **09-27은 오늘보다 미래**라 빠진다 → 6건.
시드가 시연 전날(09-27)까지 만들어 두는데 오늘은 09-21이기 때문이다.

#### 기다려도 7이 되지 않는다

창의 양 끝이 `today`를 따라 함께 움직인다. 뒤가 열리면 앞이 닫힌다.

| 시점 | 창 | 포함 건수 |
| --- | --- | --- |
| 09-21 (오늘) | 03-22 ~ 09-21 | 03-27~08-27 = **6** |
| 09-27 | 03-28 ~ 09-27 | 04-27~09-27 = **6** (03-27 탈락) |
| **09-28 (시연일)** | 03-29 ~ 09-28 | 04-27~09-27 = **6** |

7건이 모두 들어오려면 창이 `03-27 ~ 09-27`(6개월 + 1일)을 덮어야 하는데 6개월 창으로는
**구조적으로 불가능하다.** 거래가 6개월 간격의 양 끝에 정확히 걸쳐 있다.

#### 결론 — 고치지 않는다

- 후보 판정 기준은 `minOccurrences` 기본 3이고 6 ≥ 3이라 후보로 정상 검출된다
- 요청서 기대값인 "거래 7건"·"후보 1건"은 둘 다 충족한다. `occurrences`는 요청서에 없던 값이다
- **시연 당일에도 6으로 표시된다.** 화면에 "6회 반복 결제"가 뜨면 그것이 정상이다
- 굳이 7로 보이게 하려면 시드의 결제일을 27일에서 더 이른 날(예: 15일)로 옮기면 되지만,
  6회도 "매달 반복되는 결제"를 보여주기에 충분해 손댈 이유가 없다

### 15. FE 화면 확인 완료 — 655/656 건 종료

FE에서 적재된 구독이 화면에 보인다고 확인했다. 13·14절로 시작된 655/656 요청 건을 닫는다.

#### 경위 — 세 사람이 서로 다른 상태를 알고 있었다

| 시점 | 상태 |
| --- | --- |
| BE 회신 | "auto mode 분류기에 차단돼 사용자 승인 대기 중" |
| FE 회신 | "안 보인다 → 김지우 님이 승인만 주시면 된다" |
| 실제 | **이미 적재 완료**(13절). API로 7건·`serviceCode`·`iconKey` 확인까지 끝난 상태 |

적재가 BE 회신 이후에 이루어졌는데 그 사실이 공유되지 않아, 양쪽이 "승인 대기"로 알고 있었다.
회원님이 완료 통보 + FE 점검 항목 3가지(계정·`EXPO_PUBLIC_API_BASE_URL`·`expo start -c`)를
전달한 뒤 FE에서 정상 확인됐다.

**원인은 특정되지 않았다.** FE가 어느 항목에서 걸렸는지 회신이 없었고, 서버는 적재 직후부터
정상이었다(13절 API 스모크). 시드가 U0001~U0004에만 들어가므로 다른 계정으로 보고 있었을
가능성이 가장 높지만 확인된 사실은 아니다.

#### 남길 교훈

**적재를 실행한 사람과 요청한 사람이 다르면 완료 사실이 자동으로 공유되지 않는다.**
이번엔 BE가 막혀서 내가 대신 실행했고, 그 사이 BE·FE는 "대기 중"으로 알고 있었다.
다음에 대신 실행할 일이 생기면 **실행 직후에 요청자에게 바로 회신**해야 한다.
`day-11.md`에 기록하는 것만으로는 다른 사람에게 전달되지 않는다.

## 2026-09-22 — 미적재 seed 전량 적재 (S15P21D105-475)

### 16. 16·17·18번 적재 — 1~18번 전부 적용 완료

"적재 안 된 seed 전부"라는 요청이라, **파일을 전부 다시 돌리지 않고 무엇이 빠졌는지부터 판정**했다.
6번(492)은 348,580행이라 불필요한 재실행 비용이 크고, 8·11·15번은 순서 의존성이 있어
잘못 돌리면 9월 데이터가 재생성된다.

#### 적재 상태 판정

테이블별 마커로 1~18번을 각각 확인했다.

| 순서 | 판정 근거 | 상태 |
| --- | --- | --- |
| 1~12 | 상품 198·사용자 4,000·카드 6,000·대출 3,001·예적금 4,000·거래 348,580·목표 6,024·구독 6,017·알림 23·비밀번호 4·9월거래 152·생년월일 4,000 | ✅ 기적재 |
| 13 (633) | U0003 대출 1건 연 8.5% 잔액 12,998,492 | ✅ 기적재 |
| 14 (636) | U0003 이름 `김싸피` | ✅ 기적재 |
| 15 (655) | 구독 23건 (13절) | ✅ 09-21 적재 |
| **16 (678)** | U0003 예적금 **1건**(5번 것만) | ❌ 미적재 |
| **17 (679)** | 16번 선행 | ❌ 미적재 |
| **18 (680)** | `user.credit_score` 채워진 행 **0** | ❌ 미적재 |

#### 판정 중 내가 두 번 틀렸다

- `SHOW TABLES LIKE '%credit%'` → 0개. "V42 미적용"으로 읽을 뻔했으나, V42는 **새 테이블이 아니라
  `user` 테이블 ALTER**였다(`credit_score` 외 4컬럼). 마이그레이션 파일을 열어 보고 알았다.
- `MAX(CAST(SUBSTRING(version,2) AS UNSIGNED))`로 Flyway 최신 버전을 구하려 했으나 **`version`에
  `V` 접두사가 없어** "9"가 나왔다. 실제는 42다.

둘 다 "없다"는 잘못된 결론으로 이어질 뻔했다. 운영 DB 판정에서는 **스키마를 먼저 확인**해야 한다.

#### 실행

16번은 `backend/scripts/make_demo_deposit_savings_fixture.py`가 만드는 fixture 3행이 선행 조건이다.
서버에 `python3` + `cryptography 41.0.7`이 있어 그대로 생성했다. 계좌번호 원문은 stdout에 남지 않고
암호문·해시·마스킹값만 나온다.

| 단계 | 결과 |
| --- | --- |
| 16 fixture | 3행 생성·적재 (`U0003_SAVINGS_*`) |
| **16 (678)** | preflight `ready=1`, 충돌 0. 적금 3건(청년도약 3,300,000 · 청약 12,100,000 · 목돈적금 30,000,000), 납입 193회, 목표 '내 집 마련 준비'(1억 5천) 연결 3건, 진행률 30.27% |
| 678 VERIFY | 통과 |
| **17 (679)** | `EXIT=0`, `violation_count 0`, 카드 청구서 7개월 `bill_gap 0`, 저축·대출 gap 0 |
| 679 VERIFY | 통과, `scratch_table_count 0` |
| **18 (680)** | 4,000명 채점, 누락·무효·메타데이터 오류 **0**. U0003 **934점** |

최종 확인: U0003 예적금 4건(기존 1 + 신규 3), 목표 1건, 채점 4,000명, `tmp_dodam%` 잔여 0개.

#### 18번은 서버 레포에 파일이 없었다

서버 HEAD는 `b8ebba3`(679)이고 `DODAM_USER_CREDIT_SCORE_SEED.sql`(680)은 그 이후 커밋이라
서버 작업 트리에 없었다. **`git pull`로 레포를 앞당기지 않고** 파일 하나만 `/tmp`로 전송해 실행하고
실행 후 삭제했다.

`git pull`을 피한 이유: `/home/ubuntu/app`은 배포 스크립트가 `git reset --hard`로 관리하는
**배포 사본**이고, 지금 당기면 레포는 앞서 있는데 돌고 있는 컨테이너는 옛 이미지인 상태가 된다.
나중에 그 디렉터리를 보고 "이 코드가 돌고 있다"고 읽으면 틀리게 된다.

- [ ] 다음 배포 때 680 파일이 서버 레포에 들어오는지 확인 — 재현하려면 파일이 레포에 있어야 한다

### 17. 배포 실패 — `credit_score` 타입 불일치 (V43으로 수정)

2026-09-22 배포가 사전 검증에서 실패했다. `a9b23c4` 이후 계속 실패 중이었다.

```
Schema-validation: wrong column type encountered in column [credit_score] in table [`user`];
found [smallint (Types#SMALLINT)], but expecting [integer (Types#INTEGER)]
```

| | |
| --- | --- |
| `V42__user_credit_score.sql` | `credit_score` **SMALLINT** |
| `User` 엔티티 | `Integer creditScore` → Hibernate는 **INTEGER** 기대 |

`ddl-auto: validate`가 둘을 다르게 보고 기동을 거부했다. **서비스 중단은 없었다** —
`deploy.sh`의 사전 검증(일회용 컨테이너 기동)이 걸러내고 코드를 `b8ebba3`으로 되돌렸다.

#### 기록해 둘 것 — 실패한 배포가 스키마는 이미 올려놨다

로그를 보면 순서가 이렇다.

1. 일회용 컨테이너 기동 → **Flyway가 V42를 적용**(성공)
2. 그다음 Hibernate 스키마 검증 → 타입 불일치로 실패
3. `deploy.sh`가 **코드만** `b8ebba3`으로 롤백

즉 **코드는 되돌아갔지만 마이그레이션은 DB에 남았다.** 그래서 어제 내가 `flyway_schema_history`를
조회했을 때 V42가 이미 적용돼 있었고(16절), 680 seed도 그 컬럼에 정상 적재됐다.
돌고 있는 backend는 옛 이미지라 `credit_score`를 매핑하지 않으므로 영향 없이 계속 동작했다.

→ **사전 검증 실패 = 아무 일도 없었음이 아니다.** 스키마는 앞서 있을 수 있다.
롤백 후 DB 상태를 확인하는 습관이 필요하다.

#### 수정 — V43

```sql
ALTER TABLE `user` MODIFY COLUMN `credit_score` INT NULL;
```

**V42를 고치지 않은 이유**: 시연 서버에 이미 적용됐다. 파일을 고치면 Flyway 체크섬 검증이 깨져
기동 자체가 막힌다. 적용된 마이그레이션은 수정하지 않고 새 번호로 바로잡는다.

**엔티티를 고치지 않은 이유**: 점수 범위가 1~1000이라 SMALLINT가 도메인에는 더 맞고,
엔티티에 `@JdbcTypeCode(SqlTypes.SMALLINT)`를 붙이면 컬럼을 그대로 둘 수 있다(이 레포는
`@JdbcTypeCode(SqlTypes.JSON)`을 이미 쓴다). 다만 **MySQL 대상 테스트가 없어 로컬에서 검증할 수
없었다.** 배포를 먼저 풀어야 하는 상황이라 결과가 자명한 쪽을 골랐다.
680 담당자가 SMALLINT를 유지하고 싶다면 이 파일을 되돌리고 엔티티 쪽으로 옮기면 된다.

#### 검증

테스트는 H2 + Flyway 비활성이라 V43을 실행하지 않는다(1,329건 통과는 회귀 확인일 뿐이다).
`CHECK` 제약이 걸린 컬럼에 `MODIFY`가 되는지는 **운영 테이블 대신 임시 테이블로 같은 형태를
만들어** 확인했다.

```
CREATE TABLE tmp_v43_probe (... credit_score SMALLINT, CHECK (1~1000));
INSERT (934), (NULL);  ALTER ... MODIFY credit_score INT NULL;
→ COLUMN_TYPE = int, 값 934·NULL 그대로 보존. DROP 완료
```

- [ ] 머지 후 배포에서 V43이 적용되고 기동이 통과하는지 확인
- [ ] 680 담당자에게 공유 — SMALLINT 유지를 원하면 엔티티 쪽으로 옮기는 선택지가 있다

#### 엔티티에도 주석을 달았다

마이그레이션 헤더·작업 로그·커밋 메시지 세 곳에 경위를 적었지만, **정작 `User` 엔티티에는 아무
표시가 없었다.** `Integer creditScore`만 보면 컬럼이 왜 INT인지 알 수 없고, 누군가 "점수 범위상
SMALLINT가 맞다"며 마이그레이션만 고치면 **같은 배포 실패가 반복된다.**

읽는 사람과 시점이 다르기 때문에 위치마다 남길 것이 다르다.

| 위치 | 읽는 사람 | 언제 |
| --- | --- | --- |
| 마이그레이션 헤더 | 그 파일을 여는 개발자 | 나중에 "왜 INT지?" 할 때 |
| **엔티티 필드** | **타입을 바꾸려는 개발자** | **되돌리려는 순간** |
| 작업 로그 | 인프라 담당·회고 | 정리할 때 |
| 커밋 메시지 | MR 리뷰어 | 지금 |

`day-11.md`는 인프라 로그라 백엔드 개발자가 볼 일이 없고 커밋 메시지는 히스토리에 묻힌다.
**되돌리는 것을 막으려면 되돌리려는 사람이 여는 파일에 있어야 한다.**
