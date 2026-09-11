# 일별 잔액·캐시 정책 구현 계약 (S15P21D105-540)

작성일 2026-09-10. 진행계획은 [cashflow-daily-balance-plan.md](cashflow-daily-balance-plan.md)에 있고 이
문서가 실제 구현 기준이다. 저장 계층은 [cashflow.md](cashflow.md)(538), 이벤트 수집은
[cashflow-events.md](cashflow-events.md)(539)를 따른다.

**HTTP 엔드포인트는 이 작업에서 추가하지 않는다.** 예측 실행·조회 API 계약은 541, 위험 탐지와
`available_today`는 542 범위다.

## 호출 계약

`CashflowForecastRunner.run(userId, baseDate, horizonDays, forceRefresh)` → `CashflowForecastResult`

| 입력 | 필수 | 기본값 | 검증 |
| --- | --- | --- | --- |
| `userId` | O | — | 양수. 인증 경계에서 `AuthenticatedUserIdResolver`로 얻은 값을 전달한다 |
| `baseDate` | X | 오늘(Asia/Seoul) | 과거 기준일은 `InvalidRequestException` |
| `horizonDays` | X | 90 | 1 이상 365 이하. 벗어나면 `InvalidRequestException` |
| `forceRefresh` | X | false | 완료된 캐시만 건너뛴다. 진행 중인 계산은 우회하지 못한다 |

출력 `CashflowForecastResult`는 `forecast`, `cached`, `reasons` 셋이다. `cached`가 true면 재계산 없이
기존 예측을 돌려준 것이다.

HTTP 인증 검증은 541의 Controller 책임이다. 이 클래스는 이미 인증된 `userId`를 받는다.

## 기준일·기간 정책

`CashflowForecastPeriodPolicy`가 담당한다. 구간은 `[baseDate, baseDate + horizonDays - 1]`로 양 끝을
포함하며, 저장되는 `cashflow_daily_balance` 행 수는 정확히 `horizonDays`다.

- **과거 기준일을 거부한다.** 계산의 시작점이 저장된 *현재* 계좌 잔액이다. 과거 기준일을 허용하면 그
  시점의 잔액을 복원한 것처럼 보이지만 실제로는 오늘 잔액을 과거에 놓은 값이 나온다. 539 문서도 이
  기능이 백테스트가 아님을 명시했다.
- **미래 기준일은 허용한다.** 명세가 `baseDate`를 선택 입력으로 두었고, 특정일 이후의 흐름을 미리 보는
  용도가 성립한다. 다만 시작 잔액은 여전히 오늘 잔액이다.
- 기본 90일과 상한 365일은 539가 이벤트를 모을 때 쓰는 `PaymentSchedulePeriodPolicy`의 기본 3개월,
  최대 12개월에서 옮긴 값이다. 수집 가능한 범위보다 긴 예측을 허용하면 뒷부분이 이벤트 없는 평탄한
  잔액이 되어 실제보다 안전해 보인다.

## 요청 지문과 캐시·멱등

`CashflowRequestHash.of(userId, period, calculationVersion)` → SHA-256 hex 64자

지문 입력은 `userId`, `baseDate`, `horizonDays`, `calculationVersion` 넷이다. `CashflowEventKeys`와 같은
길이 접두 인코딩을 써서 자릿수가 다른 조합이 같은 입력 문자열로 합쳐지지 않는다. 민감번호는 넣지
않는다. 계산 규칙이 바뀌어 `CashflowCalculationVersion.CURRENT`가 올라가면 기존 캐시는 자동으로
무효화된다.

판정 순서는 다음과 같다.

1. **진행 중 확인** — 같은 지문의 `RUNNING` 또는 `PENDING` 예측이 있으면 그 행을 돌려준다(`cached=true`).
   명세의 "중복 요청 방지"다. `forceRefresh=true`로도 우회하지 못한다. 같은 예측을 두 번 돌리면 하위행
   삭제와 삽입이 교차해 결과가 깨진다.
   단, **생성 후 10분이 지난 행은 무시한다.** 계산 도중 프로세스가 내려가면 헤더가 `PENDING`으로 남는데,
   만료가 없으면 그 요청 조합을 영원히 다시 계산할 수 없다. 수집·계산이 10분보다 오래 걸리는 환경이면
   같은 예측이 두 번 돌 수 있으므로 실제 소요 시간을 확인한 뒤 조정한다.
2. **완료 캐시 확인** — `forceRefresh`가 아니면, 같은 지문의 `COMPLETED` 예측 중 `completedAt`이 **같은
   서비스일**인 것만 재사용한다. 잔액과 예정 내역은 수시로 바뀌므로 무기한 재사용하면 낡은 잔액을
   확정값처럼 보여준다. 하루를 넘기면 기준일의 의미 자체가 달라진다. 당일 안에서 최신 데이터가 필요하면
   `forceRefresh`를 쓴다.
3. 둘 다 아니면 새로 계산한다. `forceRefresh`도 기존 예측을 덮어쓰지 않고 **새 행**을 만든다. 완료된
   예측을 덮어쓰지 않는 538의 상태 전이 규칙을 유지해야 결과 비교가 가능하다.

기준일이 오늘인 요청은 다음 날 과거 기준일이 되어 거부되므로, 완료 캐시의 하루 만료는 실질적으로 미래
기준일 요청에서만 관찰된다.

## 시작 잔액

`CashflowBalanceReader.readStartingBalance(userId)`

`SELECT COALESCE(SUM(balance), 0) FROM account WHERE user_id = ? AND status = 'ACTIVE' AND account_type = 'DEMAND_DEPOSIT'`

| 계좌 유형 | 포함 | 근거 |
| --- | --- | --- |
| `DEMAND_DEPOSIT` | O | 즉시 출금 가능한 현금. 카드대금·대출상환·적금납입이 실제로 빠져나가는 계좌다 |
| `DEPOSIT`, `SAVINGS` | X | 만기 전 출금이 불가하거나 불이익이 있다. 더하면 실제보다 낙관적인 잔액이 되어 없는 여유를 보고한다 |
| `MINUS` | X | `chk_account_balance_by_type`상 잔액이 0 이하인 **사용액**이며 현금이 아니다. 한도 여력은 minusaccount 도메인 정보이므로 542의 위험 판단에서 다룬다 |
| `status <> 'ACTIVE'` | X | `INACTIVE`·`CLOSED`는 출금 경로로 쓸 수 없다고 보수적으로 본다 |

연동 계좌가 없으면 0원이며 오류가 아니다. `FinanceSummaryReader`도 유형별 합계를 돌려주지만 자산 요약용
계약이고 `status <> 'CLOSED'`라 `INACTIVE`를 포함한다. 기준이 달라 재사용하지 않는다.

## 일별 잔액 계산

`CashflowDailyBalanceCalculator.calculate(forecastId, period, startingBalance, events)`

기획안 "공통 현금흐름 예측 로직"의 순서를 따르는 순수 계산이며 DB에 접근하지 않는다.

1. 잔액을 시작 잔액으로 둔다.
2. 하루씩 진행하며 그날의 이벤트를 **INFLOW 먼저, OUTFLOW 나중에** 반영한다. 순서를 바꾸면 같은 날
   급여가 들어오는데도 카드대금이 잔액 부족으로 판정된다.
3. 그날의 마감 잔액을 `expected_balance`로 저장한다. **음수를 0으로 절삭하지 않는다.** 부족 구간의
   깊이가 남아야 542의 위험 탐지가 성립한다.
4. `minimum_balance`는 일별 마감 잔액 중 최소값이다. 시작 잔액은 기준일 이벤트를 반영하기 전 값이라
   저장되는 어느 날짜의 잔액도 아니므로 후보에 넣지 않는다.
5. `risk_date`는 잔액이 처음 음수가 되는 날이며 없으면 NULL이다. 542가 그 위에 유형·심각도·부족액을 담은
   `cashflow_risk` 행을 얹는다.
6. 구간 밖 이벤트는 무시한다. 539가 이미 걸러내지만 계산기 단독으로도 행 수가 `horizonDays`와 어긋나지
   않게 한다.
7. 금액 누적에 `Math.addExact`/`subtractExact`를 써서 조용한 `long` 오버플로 대신 예외를 낸다.

`available_today`는 이 작업에서 채우지 않고 NULL로 남긴다(542).

## 저장과 실패 처리

`CashflowForecastRunner`는 트랜잭션을 직접 열지 않는다. 경계는 `CashflowForecastStore`에 있다. 원본
수집과 계산은 수 초가 걸릴 수 있어 하나의 트랜잭션으로 묶으면 계산하는 동안 커넥션을 붙잡는다. 대신
헤더를 먼저 `PENDING`으로 남겨 진행 중인 계산을 다른 요청이 알아볼 수 있게 한다.

계산 중 예외가 나면 `store.fail`로 `FAILED` 전이와 하위행 삭제를 수행한 뒤 예외를 그대로 전파한다.
접수만 된 채 남은 예측이 조회되면 계산이 끝난 것처럼 보이기 때문이다.

## 수집 사유(`reasons`) 처리

539의 `CashflowEventCollection.reasons`가 비어 있지 않아도 **예측은 `COMPLETED`로 저장한다.** 현재
`AccountScheduledEventSource` 구현체가 없어 `ACCOUNT_SCHEDULE_SOURCE_UNAVAILABLE`가 항상 발생하므로,
사유를 실패로 취급하면 API가 언제나 실패한다.

사유는 `CashflowForecastResult.reasons`로만 전달된다. ERD에 저장 컬럼이 없어 다음 한계가 있다.

- `latest` 등 조회 API에서는 사유가 보이지 않는다.
- 캐시를 재사용한 응답의 `reasons`는 항상 비어 있다. 재계산하지 않았으므로 복원할 수 없다.

## 현재 제한사항

- **변동지출을 반영하지 않는다.** 기획안은 요일별 평균 변동지출을 매일 차감하지만, 입력이 될
  `transaction` 테이블의 마이그레이션이 아직 없다(현재 `V1`~`V4`, `V10`~`V16`). 확정 예정 이벤트만
  반영하므로 예상잔액은 **실제보다 낙관적**이다.
- 예정 입금 원본이 없다. `AccountScheduledEventSource` 구현체가 없어 급여 등 입금이 반영되지 않는다.
  결과적으로 현재 예측은 확정 출금 위주다.
- `CashflowCalculationVersion.CURRENT`는 여전히 `"v1"`이다.
- **중복 요청 방지는 경합에 안전하지 않다.** 같은 요청이 동시에 들어오면 두 요청 모두 진행 중인 예측을
  찾지 못하고 각자 새 예측을 만들 수 있다. `(user_id, request_hash, status)`에 유니크 제약이 없고 조회와
  생성이 한 트랜잭션이 아니다. 순차 재요청은 막지만 동시 요청은 막지 못한다. 실제로 문제가 되면 제약
  추가나 잠금 방식을 ERD 변경과 함께 논의한다.
- MySQL 검증은 하지 않았다. 이 작업은 스키마를 바꾸지 않고 538이 만든 테이블만 읽고 쓰므로 H2 테스트로
  계산·판정 로직을 검증했다. 새 SQL은 `CashflowBalanceReader`의 집계 한 건이다.

## 테스트

| 클래스 | 검증 |
| --- | --- |
| `CashflowForecastPeriodPolicyTest` | 기본값, 구간 양 끝 포함, 과거 기준일 거부, 미래 기준일 허용, horizon 범위와 경계값 |
| `CashflowRequestHashTest` | 동일 입력 동일 지문, 64자 hex, 입력별 지문 변화, 인접 필드 충돌 없음 |
| `CashflowDailyBalanceCalculatorTest` | 행 수, 입금·출금 순서, 음수 보존과 최초 부족일, 최소잔액 후보, 구간 밖 이벤트, 오버플로 |
| `CashflowBalanceReaderTest` | 유형·상태 필터, 타인 계좌 제외, 계좌 없음 |
| `CashflowForecastRunnerTest` | 저장 결과와 집계값, 당일 캐시 재사용, 다른 날 재계산, `forceRefresh` 새 행, horizon 다르면 캐시 미사용, 진행 중 재사용, 버려진 진행 중 행 만료, 실패 전이, 사유 전달 |
