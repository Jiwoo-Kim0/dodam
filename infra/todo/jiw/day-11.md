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
