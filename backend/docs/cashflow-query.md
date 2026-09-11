# 현금흐름 실행·조회 API 구현 계약 (S15P21D105-541)

작성일 2026-09-10. 진행계획은 [cashflow-query-plan.md](cashflow-query-plan.md)에 있고 이 문서가 실제
구현 기준이다. 저장 계층은 [cashflow.md](cashflow.md)(538), 이벤트 수집은
[cashflow-events.md](cashflow-events.md)(539), 실행·계산 로직은
[cashflow-daily-balance.md](cashflow-daily-balance.md)(540)를 따른다.

**위험 조회(`/risks`)와 오늘 사용가능금액(`/available-today`)은 이 작업에 없다.** 542 범위이며
[cashflow-risk.md](cashflow-risk.md)에 있다. 542는 이 문서의 `CashflowForecastResponse`에
`availableToday` 필드를 추가했다.

## 엔드포인트

| Method | Endpoint | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/cashflow/forecasts` | O | 예측 실행. 캐시·멱등 정책에 따라 재사용될 수 있다 |
| GET | `/api/v1/cashflow/forecasts/latest` | O | 최신 완료 예측 헤더 |
| GET | `/api/v1/cashflow/daily-balances` | O | 최신 완료 예측의 날짜별 예상잔액 |
| GET | `/api/v1/cashflow/events` | O | 최신 완료 예측의 예정 금융이벤트 |

모든 응답은 공통 `ApiResponse`로 감싼다. 인증 사용자 ID는 `AuthenticatedUserIdResolver`로 얻는다.

## POST /forecasts

Body는 전부 선택이며 본문 자체를 생략할 수 있다.

| 입력 | 기본값 | 검증 |
| --- | --- | --- |
| `baseDate` | 오늘(Asia/Seoul) | 과거 기준일은 `INVALID_REQUEST`. 미래는 허용하되 시작 잔액은 오늘 잔액이다(540) |
| `horizonDays` | 90 | 1~365. 형식 위반은 `VALIDATION_ERROR`, 정책 위반은 `INVALID_REQUEST` |
| `forceRefresh` | false | 완료된 캐시만 건너뛴다. 진행 중인 계산은 우회하지 못한다 |

응답은 `{ forecast, cached, incompleteReasons }`다.

- **상태 코드는 항상 200**이다. 같은 요청이면 새 예측을 만들지 않고 기존 예측을 돌려주므로 201이
  적절하지 않다. 생성 여부는 `cached`로 구분한다.
- `cached=true`이면서 `forecast.status`가 `PENDING`/`RUNNING`일 수 있다. 같은 요청이 진행 중인 경우이며
  명세의 "중복 요청 방지"다. 이때 하위행은 아직 없으므로 조회 API는 이 예측을 대상으로 삼지 않는다.
- `incompleteReasons`는 수집 단계에서 누락 가능성이 있던 원본의 사유다(539). 저장 컬럼이 없어 캐시를
  재사용하면 비어 있고, 조회 API는 이 값을 제공하지 않는다.

## 조회 API가 읽는 예측

명세에 `forecastId` 파라미터가 없으므로 세 조회 모두 **본인의 최신 `COMPLETED` 예측 1건**을 대상으로
한다.

- `PENDING`·`RUNNING`·`FAILED`는 제외한다. 하위행이 없거나 일부만 있는 상태를 확정값처럼 보여줄 수
  없다.
- **최신은 완료 시각 기준이다.** `baseDate`를 주지 않으면 `completed_at`이 가장 늦은 예측을, 같으면
  나중에 저장된 쪽(`forecast_id`가 큰 쪽)을 고른다. 기준일 내림차순으로 고르면 미래 기준일로 한 번
  실행해 둔 예측이 계속 최신으로 남아 오늘부터의 흐름을 조회할 수 없다. 538이 처음 정의한 기준일
  내림차순 규칙을 541에서 이렇게 바꿨고, `CashflowForecastRepository`의 메서드 이름도 함께 고쳤다.
- `latest`에 `baseDate`를 주면 그 기준일의 실행 중 마지막에 완료된 것을 고른다. 특정 기준일의 결과가
  필요할 때 쓴다.
- `daily-balances`·`events`는 기준일을 받지 않으므로 항상 완료 시각 기준 최신을 쓴다.
- 완료된 예측이 없으면 `RESOURCE_NOT_FOUND`(404)다. 응답에 `forecastId`·기준일을 채울 수 없고, 빈
  배열로 돌려주면 "예측을 한 번도 실행하지 않았다"와 "그 기간에 데이터가 없다"가 구분되지 않는다.
- 타인 예측만 있는 사용자도 같은 404를 받는다. 소유권 조건은 서비스가 아니라 538의 Repository 쿼리에
  들어 있다.

## 조회 기간

공통 `PaymentSchedulePeriodPolicy`를 쓴다. 예측은 미래 구간이라 종료일을 오늘로 막는
`QueryPeriodPolicy`를 쓸 수 없고, 539가 이벤트를 모을 때 쓰는 정책과 같은 클래스를 재사용한다.

| 입력 | 결과 |
| --- | --- |
| 둘 다 없음 | 오늘 ~ 오늘 + 3개월 - 1일 |
| 시작일만 | 시작일 ~ 시작일 + 3개월 - 1일 |
| 종료일만 | 종료일 - 3개월 + 1일 ~ 종료일 |
| 시작일 > 종료일 | `INVALID_REQUEST` |
| 12개월 초과 | `INVALID_REQUEST` |

요청 기간과 예측 구간이 겹치지 않으면 **빈 목록**이며 404가 아니다. 예측 구간 밖은 계산된 적이 없는
날짜이고, 없는 날짜를 0원이나 오류로 만들지 않는다. 응답에는 요청 기간(`startDate`, `endDate`)과 예측
구간(`forecast.baseDate`, `forecast.endDate`)이 함께 들어가므로 호출자가 목록이 비거나 잘린 이유를
구분할 수 있다.

## events의 type 필터

`^[A-Z][A-Z0-9_]{0,49}$` 형식만 검증하고 **값 집합을 고정하지 않는다.**

539가 만드는 값은 `CARD_PAYMENT`, `LOAN_REPAYMENT`, `SAVINGS_PAYMENT`, `SUBSCRIPTION_PAYMENT`다. 다만
계좌 예정 거래는 `AccountScheduledEventSource` 구현체가 자체 `type`을 지정하는 계약이라, 여기서 목록을
닫으면 계좌 어댑터가 붙는 순간 정상 값이 400이 된다. 형식이 맞지만 저장된 적 없는 값은 오류가 아니라
빈 결과다. 형식 위반은 `VALIDATION_ERROR`다.

## 응답 필드

`CashflowForecastResponse`

| 필드 | 설명 |
| --- | --- |
| `forecastId`, `baseDate`, `horizonDays` | 저장값 |
| `endDate` | `baseDate + horizonDays - 1`. 저장하지 않고 파생한다(538의 구간 규칙) |
| `status` | `PENDING`/`RUNNING`/`COMPLETED`/`FAILED` |
| `currentBalance` | 계산 시작 잔액. 활성 `DEMAND_DEPOSIT` 합계(540) |
| `minimumBalance`, `riskDate` | 예측 구간의 최소 잔액과 최초 잔액 부족일. 부족이 없으면 `riskDate`는 null |
| `calculationVersion`, `createdAt`, `completedAt` | 저장값 |

`daily-balances` → `{ forecast, startDate, endDate, balances[{balanceDate, expectedBalance}] }`.
`expectedBalance`는 음수가 될 수 있으며 0으로 절삭하지 않는다.

`events` → `{ forecast, startDate, endDate, type, events[{eventId, eventDate, type, name, direction,
amount, sourceType, sourceId}] }`. `amount`는 방향과 무관하게 양수이고 입·출금은 `direction`으로만
구분한다.

**응답에 넣지 않는 것**: `dedupeKey`(중복 차단용 내부 해시), `requestHash`(캐시 판정용 지문),
`availableToday`(계산 정의가 542에서 확정된다). 정렬은 저장 계층 계약대로 `balanceDate` 오름차순,
`eventDate` → `eventId` 오름차순이다.

## 오류

| 상황 | 코드 | 상태 |
| --- | --- | --- |
| 인증 없음 | — | 401 |
| `horizonDays` 범위·`type` 형식 위반 | `VALIDATION_ERROR` | 400 |
| 과거 기준일, 시작일 > 종료일, 기간 상한 초과 | `INVALID_REQUEST` | 400 |
| 완료된 예측 없음 또는 타인 예측 | `RESOURCE_NOT_FOUND` | 404 |

## 구현 위치

```
domain/cashflow/
  controller/CashflowCommandController   POST /forecasts
  controller/CashflowQueryController     GET /forecasts/latest, /daily-balances, /events
  dto/request/CashflowForecastRequest
  dto/response/CashflowForecastResponse, CashflowForecastRunResponse,
               CashflowDailyBalanceListResponse, CashflowDailyBalanceResponse,
               CashflowEventListResponse, CashflowEventResponse
  service/CashflowQueryService           @Transactional(readOnly = true)
```

새 계산 로직과 스키마 변경은 없다. 실행은 540의 `CashflowForecastRunner`를, 조회는 538의 Repository를
그대로 호출한다.

## 테스트

`backend/src/test/java/com/ssafy/dodam/domain/cashflow/CashflowApiIntegrationTest.java` 7건.
`CashflowEventCollector`와 `CashflowBalanceReader`만 대역으로 바꾸고 실행·저장·조회는 실제 경로다.

1. 실행 → 최신 → 일별잔액 → 이벤트가 같은 `forecastId`를 가리키고 기본값(90일, 3개월)이 적용된다
2. 재실행은 `cached=true`로 같은 예측, `forceRefresh=true`는 새 예측이며 그것이 최신이 된다
3. 미래 기준일 예측을 먼저 실행해도 나중에 완료된 오늘 기준 예측이 최신이며, `baseDate`를 지정하면
   미래 기준일 예측을 그대로 조회할 수 있다
4. 인증 없는 4개 요청은 401
5. 타인 예측만 있는 사용자는 세 조회 모두 404이며 예측이 없는 사용자와 응답이 같다
6. 입력 검증: `horizonDays` 0·366, 과거 기준일, 시작일 > 종료일, 형식 위반 `type`
7. 경계: `type` 필터, 형식이 맞는 미지의 `type`은 빈 결과, 예측 구간 밖 기간은 빈 목록, 12개월 초과는 400

기준일이 오늘 이후여야 해서 고정 날짜를 쓸 수 없다. 모든 날짜는 서비스 타임존의 오늘 기준으로
만든다.

## 현재 제한

- `availableToday`와 위험 목록은 응답에 없다. 542에서 계산이 정해지면 추가해야 한다.
- 계좌 예정 거래 어댑터가 없어 `events`에 급여 등 예정 입금이 빠진다(539·540의 한계). API 계약의
  제약은 아니지만 응답 데이터의 현재 한계다.
- 조회 API가 대상 예측을 고르는 규칙은 명세에 없다. 완료 시각 기준 최신 예측 1건은 이 작업에서 정한
  기본안이다.
- MySQL 검증은 하지 않았다. 스키마를 바꾸지 않고 538의 테이블만 읽는다.
