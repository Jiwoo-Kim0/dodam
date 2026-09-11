# 일별 잔액·캐시 정책 진행계획 (S15P21D105-540)

## 1. 문서 성격과 근거

**계획 문서이며 구현 완료 문서가 아니다.** 작성일 2026-09-10.

- 연결된 Jira 사이트에서 540을 조회하지 못했다. 로컬 `SPRINT/Jira (4).csv` 기준으로 제목은
  `[API 15] 일별 잔액·캐시 정책`, 설명은 `기준일·horizon 계산과 캐시·멱등 구현`, 상태는 `해야 할 일`,
  스프린트는 `Sprint 3 - 개발 1주차`다. **최신 Jira 완료 조건을 대신하지 않는다.**
- 선행 의존은 모두 해소됐다. 538(저장 계층, `964bd1d`)과 539(예정 이벤트 통합, `9329fb3`)가 `develop`에
  병합되어 있어 연쇄 분기 의존이 없다.
- 작업 브랜치는 Jira `Create branch`로 최신 `develop` 기준 `S15P21D105-540-...`를 만들어 사용한다.

기준 자료는 다음과 같다.

| 자료 | 사용 범위 |
| --- | --- |
| `docs/API명세서/15 Cashflow ...csv` | 예측 실행 API의 Body(`baseDate?`, `horizonDays?`, `forceRefresh?`)와 제한사항(중복 요청 방지, 동일 기준일 캐시/멱등 권장) |
| `docs/기획안/기획안 작성 ...md` 839~1050행 | 공통 현금흐름 예측 로직의 계산 순서(현재잔액 → 입금 → 출금 → 날짜별 예상잔액) |
| `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql` | `cashflow_forecast`, `cashflow_daily_balance`, `account` DDL과 CHECK 제약 |
| `backend/docs/cashflow.md` | 538의 저장 계약(`CashflowForecastStore`, `CashflowResultWriter`, Repository 조회 메서드) |
| `backend/docs/cashflow-events.md` | 539의 입력 계약(`CashflowEventCollector.collect`, `CashflowEventCollection.reasons`) |
| `global/policy/PaymentSchedulePeriodPolicy` | 539가 이미 쓰는 기본 3개월·최대 12개월 기간 정책 |

## 2. 작업 범위

540은 **계산과 정책 계층**이다. HTTP 엔드포인트를 추가하지 않는다.

| 포함 | 제외(담당 이슈) |
| --- | --- |
| 기준일·horizon 해석과 검증 | 예측 실행·최신·일별잔액·이벤트 API 계약 (541) |
| `request_hash` 생성 규칙 | 위험 탐지 세분화(부족액·심각도·메시지) (542) |
| 캐시·멱등 판정과 `forceRefresh` 처리 | `available_today` 계산 정의 (542) |
| 시작 잔액 조회와 일별 예상잔액 계산 | 요일별 평균 변동지출(6절 참고, `transaction` 마이그레이션 부재) |
| 수집(539) → 계산 → 저장(538) 오케스트레이션 | |

`minimum_balance`와 `risk_date`는 일별 잔액 시퀀스에서 바로 파생되므로 540이 채운다. 542는 그 위에
`cashflow_risk` 행(유형·심각도·부족액)을 얹는다.

## 3. 정책 결정

명세와 문서에 값이 없어 540이 정해야 하는 항목이다. 모두 근거와 함께 구현 문서에 남긴다.

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| `baseDate` 기본값 | `LocalDate.now(Asia/Seoul)` | 기존 `PaymentSchedulePeriodPolicy`와 같은 서비스 타임존을 쓴다 |
| `baseDate` 과거 | 거부(`InvalidRequestException`) | 계산이 **현재** 계좌 잔액을 시작점으로 쓴다. 과거 기준일을 허용하면 당시 잔액을 복원한 것처럼 보이는 틀린 값이 나온다. 539 문서도 백테스트가 아님을 명시했다 |
| `horizonDays` 기본값 | 90 | 539가 재사용하는 `PaymentSchedulePeriodPolicy.DEFAULT_PERIOD_MONTHS`(3개월)와 맞춘다 |
| `horizonDays` 범위 | 1 이상 365 이하 | 하한은 `chk_cashflow_forecast_horizon_days > 0`, 상한은 같은 정책의 최대 12개월 |
| 예측 구간 | `[baseDate, baseDate + horizonDays - 1]` 양 끝 포함, 행 수 = `horizonDays` | `(forecast_id, balance_date)` 유니크와 1:1로 맞아 날짜 누락·중복이 생기지 않는다 |
| `request_hash` 입력 | `userId`, `baseDate`, `horizonDays`, `calculationVersion` | 이 넷이 같으면 같은 계산이다. 민감정보는 넣지 않는다 |
| `request_hash` 형식 | SHA-256 hex 64자, 접두사 `cashflow-request-v1`, 길이 접두 인코딩 | 컬럼이 `CHAR(64)`다. 인코딩은 `CashflowEventKeys`와 같은 방식이라 구분자 삽입으로 다른 입력이 같은 문자열이 되지 않는다 |
| 캐시 재사용 조건 | 같은 `requestHash`의 `COMPLETED` 예측이 있고 `completedAt`이 **같은 서비스일**이면 재사용 | 계좌 잔액과 예정 내역은 수시로 바뀐다. 무기한 재사용하면 낡은 잔액을 확정값처럼 보여준다. 하루를 넘기면 `baseDate` 자체의 의미가 달라진다 |
| `forceRefresh=true` | 캐시를 건너뛰고 새 예측 행을 만든다 | 완료된 예측을 덮어쓰지 않는 538의 상태 전이 규칙을 유지한다. 이력이 남아야 결과 비교가 가능하다 |
| 중복 요청 방지 | 같은 `requestHash`의 `PENDING`/`RUNNING` 예측이 있으면 새로 만들지 않고 그 행을 돌려준다 | 명세 제한사항 "중복 요청 방지". 같은 계산을 두 번 돌려 하위행 삭제·삽입이 교차하는 것을 막는다 |
| 수집 사유(`reasons`)가 있을 때 | 계산을 진행하고 `COMPLETED`로 저장한다. 사유는 실행 결과에 담아 541에 넘긴다 | 현재 `AccountScheduledEventSource` 구현체가 없어 `ACCOUNT_SCHEDULE_SOURCE_UNAVAILABLE`가 **항상** 발생한다. `FAILED`로 두면 API가 언제나 실패한다. 다만 사유를 저장할 컬럼이 ERD에 없어 조회 API에서는 노출되지 않는다(8절) |
| 계산 실패 | `CashflowForecastStore.fail`로 전이하고 예외를 전파한다 | 부분 결과가 남은 예측이 조회되지 않게 한다 |

### 시작 잔액 정의

`SELECT COALESCE(SUM(balance), 0) FROM account WHERE user_id = ? AND status = 'ACTIVE' AND account_type = 'DEMAND_DEPOSIT'`

| 계좌 유형 | 포함 여부 | 근거 |
| --- | --- | --- |
| `DEMAND_DEPOSIT` | 포함 | 즉시 출금 가능한 현금이다. 카드·대출·적금 출금이 실제로 빠져나가는 계좌다 |
| `DEPOSIT`, `SAVINGS` | 제외 | 만기 전 출금이 불가하거나 불이익이 있다. 포함하면 실제보다 낙관적인 잔액이 되어 없는 여유를 보고한다 |
| `MINUS` | 제외 | `chk_account_balance_by_type`상 잔액이 0 이하인 **사용액**이며 현금이 아니다. 한도 여력은 별도 도메인 정보이므로 542의 위험 판단에서 다룬다 |
| `status <> 'ACTIVE'` | 제외 | `INACTIVE`·`CLOSED`는 출금 경로로 쓸 수 없다고 보수적으로 본다 |

계좌가 없으면 0원이며 오류가 아니다.

기존 `FinanceSummaryReader.readAccountsByType`도 유형별 잔액 합계를 돌려주지만 재사용하지 않는다. 그쪽은
`status <> 'CLOSED'`라 `INACTIVE` 계좌를 포함하고, 자산 요약용 계약이라 현금흐름의 필요에 맞춰 바뀌면
양쪽이 함께 흔들린다. 조회는 한 줄짜리 SQL이므로 cashflow 경계에 따로 둔다.

## 4. 추가할 클래스

```
domain/cashflow/
  repository/ CashflowBalanceReader          시작 잔액 조회(소유권 조건 포함)
  service/    CashflowForecastPeriodPolicy   baseDate·horizonDays 해석과 검증
              CashflowRequestHash            요청 지문 생성
              CashflowDailyBalanceCalculator 일별 예상잔액 계산(순수 함수, DB 접근 없음)
              CashflowForecastRunner         캐시 판정 + 수집 + 계산 + 저장 오케스트레이션
  dto/        CashflowForecastPeriod         (baseDate, horizonDays, endDate)
              CashflowBalanceProjection      (points, minimumBalance, firstShortageDate)
              CashflowForecastResult         (forecast, cached, reasons)
```

마이그레이션·엔티티·ERD 변경은 없다. 538이 만든 컬럼과 조회 메서드를 그대로 쓴다.

## 5. 계산 규칙

기획안 839~1050행의 순서를 따른다.

1. 잔액을 시작 잔액으로 초기화한다.
2. `baseDate`부터 하루씩 진행하며 그날의 이벤트를 **INFLOW 먼저, OUTFLOW 나중에** 반영한다.
   순서를 바꾸면 같은 날 입금이 있는데도 잔액 부족으로 판정된다.
3. 그날의 마감 잔액을 `expected_balance`로 저장한다. **음수를 0으로 절삭하지 않는다.** 부족 구간을
   그대로 남겨야 542의 위험 탐지가 성립한다.
4. `minimum_balance`는 시퀀스의 최소값, `risk_date`는 잔액이 처음 음수가 되는 날(없으면 NULL)이다.
5. 구간 밖 이벤트는 539가 이미 걸러내지만, 계산기도 방어적으로 무시한다.
6. 금액 합산은 `Math.addExact`로 누적해 조용한 `long` 오버플로를 만들지 않는다.
7. `available_today`는 이번 범위가 아니므로 NULL로 남긴다(542).

## 6. 이번 범위에서 제외하는 변동지출

기획안은 요일별 평균 변동지출을 매일 차감한다. 그 입력은 `transaction` 테이블인데 **해당 마이그레이션이
아직 없다**(현재 `V1`~`V4`, `V10`~`V16`. `V5`~`V9` 구간은 다른 담당 영역이다). 따라서 540은 확정 예정
이벤트만 반영하고 변동지출은 넣지 않는다.

결과적으로 예상잔액은 실제보다 **낙관적**이다. 이 한계를 구현 문서와 `remaining-work.md`에 남기고,
`transaction` 마이그레이션이 들어온 뒤 담당 이슈에서 추정치를 더한다. 지금 빈 인터페이스만 만들어 두지는
않는다. 사용할 구현체가 없는 경계는 검증할 수 없다.

## 7. 테스트

`backend/src/test/java/com/ssafy/dodam/domain/cashflow/`

1. `CashflowForecastPeriodPolicyTest` — 기본값(오늘, 90일), 과거 `baseDate` 거부, `horizonDays` 0·366 거부,
   경계값 1·365 허용, 종료일이 `baseDate + horizonDays - 1`인지
2. `CashflowRequestHashTest` — 동일 입력 동일 지문, 64자 hex, 필드 경계가 밀린 조합(`horizonDays` 자릿수
   변화 등)에서 지문이 갈리는지
3. `CashflowDailyBalanceCalculatorTest` — 행 수 = `horizonDays`, 같은 날 입금·출금 순서, 음수 잔액 보존,
   최소잔액·최초 부족일, 이벤트 없음, 구간 밖 이벤트 무시, 오버플로 입력
4. `CashflowBalanceReaderTest` — 유형·상태 필터, 타인 계좌 미포함, 계좌 없음 시 0
5. `CashflowForecastRunnerTest`(H2) — 캐시 히트 시 재계산·새 행 없음, 하루 지난 캐시는 재계산,
   `forceRefresh` 시 새 행, 진행 중 예측 재사용, 계산 실패 시 `FAILED` 전이와 하위행 삭제

검증 명령은 `.\gradlew.bat test` 후 `.\gradlew.bat clean build`다.

**사전 조건**: `develop`에는 540과 무관한 H2 테스트 실패 30건(`CardQueryApiIntegrationTest`,
`DepositSavingApiIntegrationTest`의 `NULL not allowed for column "currency"`)이 이미 있다
(`remaining-work.md` 참고). 작업 시작 전에 baseline을 먼저 측정해 540이 새로 만든 실패와 구분한다.

## 8. 남은 확인 사항

- Jira 540의 최신 완료 조건을 사이트에서 확인하지 못했다. 위 범위는 로컬 CSV 기준이다.
- 3절의 정책값(기본 90일, 상한 365일, 캐시 유효기간 당일, 시작 잔액 = 활성 `DEMAND_DEPOSIT` 합계)은
  명세에 없어 540이 정한 기본안이다. 팀 결정이 다르면 교체한다.
- 539의 `reasons`를 저장할 컬럼이 ERD에 없다. 실행 응답에서만 노출되고 `latest` 조회에서는 사라진다.
  캐시를 재사용할 때도 사유를 복원할 수 없다. 컬럼 추가가 필요하면 ERD 변경을 먼저 논의한다.
- `AccountScheduledEventSource` 구현체가 없어 급여 등 예정 입금이 반영되지 않는다. 6절의 변동지출과
  합쳐서 현재 예측은 확정 출금 위주다.
- `CashflowCalculationVersion.CURRENT`는 여전히 `"v1"`이다. 지문 입력에 포함되므로 표기 규칙이 정해져
  값이 바뀌면 기존 캐시는 자동으로 무효화된다.
