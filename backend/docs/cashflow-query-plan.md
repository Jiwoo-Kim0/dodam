# Cashflow 실행·조회 API 진행계획 (S15P21D105-541)

## 1. 문서 성격과 근거

**계획 문서이며 구현 완료 문서가 아니다.** 작성일 2026-09-10.

- 작업 브랜치는 `S15P21D105-541-api-15-cashflow`다. 원격에 이미 생성되어 있었고 `origin/develop`과 차이가 없는 상태에서 시작했다.
- 선행 이슈 538·539·540이 모두 `develop`에 병합되어 있다(`e389a6f`). 연쇄 분기 의존이 없다.
- 연결된 Jira 사이트에서 541을 조회하지 못했다. 로컬 `SPRINT/Jira (4).csv`의 제목은 `[API 15] Cashflow 실행·조회`, 설명은 `예측 실행·최신·일별잔액·이벤트 구현`, 라벨은 `sprint4,day7,be-b,api-15`, 상위 작업은 `S15P21D105-465 [D7] Cashflow 완료 및 Diagnosis 기반 구현`이다. **최신 Jira 완료 조건을 대신하지 않는다.**

기준 자료는 다음과 같다.

| 자료 | 사용 범위 |
| --- | --- |
| `docs/API명세서/15 Cashflow ...csv` | 4개 API의 경로·입력·제한사항 |
| [cashflow.md](cashflow.md) (538) | 엔티티·Repository 조회 계약과 소유권 조건 |
| [cashflow-events.md](cashflow-events.md) (539) | 이벤트 `type`·`sourceType` 값과 불완전 사유 |
| [cashflow-daily-balance.md](cashflow-daily-balance.md) (540) | `CashflowForecastRunner` 호출 계약, 기준일·horizon·캐시 정책 |
| 기존 `minusaccount`·`depositsaving` Controller | 재사용할 Controller·Service·테스트 패턴 |

## 2. 작업 범위

| 포함 | 제외(담당 이슈) |
| --- | --- |
| `POST /api/v1/cashflow/forecasts` | `GET /api/v1/cashflow/risks` (542) |
| `GET /api/v1/cashflow/forecasts/latest` | `GET /api/v1/cashflow/available-today` (542) |
| `GET /api/v1/cashflow/daily-balances` | 위험 탐지·`available_today` 계산 (542) |
| `GET /api/v1/cashflow/events` | |

계산 로직은 추가하지 않는다. 541은 538~540이 만든 실행·조회 계약을 HTTP 경계에 노출하는 작업이다.

## 3. 패키지 구조

```
domain/cashflow/
  controller/ CashflowCommandController   POST /forecasts
              CashflowQueryController     GET /forecasts/latest, /daily-balances, /events
  dto/request/  CashflowForecastRequest
  dto/response/ CashflowForecastResponse, CashflowDailyBalanceListResponse,
                CashflowDailyBalanceResponse, CashflowEventListResponse, CashflowEventResponse
  service/    CashflowQueryService
```

명령과 조회를 나눈 것은 `minusaccount`의 기존 분리와 같다. 실행은 쓰기 트랜잭션(`CashflowForecastRunner`)이고 나머지 셋은 `@Transactional(readOnly = true)`다.

## 4. 설계 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 조회 API가 읽을 예측 | **본인의 최신 `COMPLETED` 예측 1건**. 명세에 `forecastId` 파라미터가 없다 | `daily-balances`·`events`는 예측 결과의 부분 조회다. 진행 중(`PENDING`/`RUNNING`)이나 실패한 예측을 읽으면 하위행이 없거나 일부만 있는 상태를 확정값처럼 보여준다 |
| 예측이 없을 때 | `RESOURCE_NOT_FOUND`(404) | 응답에 `forecastId`·`baseDate`를 채울 수 없다. 빈 배열로 돌려주면 "예측을 한 번도 안 돌렸다"와 "그 기간에 이벤트가 없다"가 구분되지 않는다 |
| 타인 예측 | 존재 여부를 구분하지 않는다. 조회는 모두 `userId` 조건이 쿼리에 포함된 538의 Repository 메서드만 사용한다 | 명세 "본인 리소스만 접근" |
| 조회 기간 정책 | 공통 `PaymentSchedulePeriodPolicy`(기본 3개월, 최대 12개월, 미래 허용) | 예측은 미래 구간이므로 종료일을 오늘로 막는 `QueryPeriodPolicy`를 쓸 수 없다. 539의 수집 기간 정책과 같은 클래스를 재사용한다 |
| 요청 기간과 예측 구간의 관계 | 교집합만 반환하고, 겹치지 않으면 **빈 목록**(404 아님) | 예측 구간 밖은 계산된 적이 없는 날짜다. 없는 날짜를 0원이나 오류로 만들지 않는다. 응답에 예측 구간을 함께 실어 호출자가 잘린 이유를 알 수 있게 한다 |
| `events`의 `type` 필터 | **닫힌 enum을 만들지 않고** `^[A-Z][A-Z0-9_]{0,49}$` 형식만 검증한다 | 539가 만드는 값은 `CARD_PAYMENT`, `LOAN_REPAYMENT`, `SAVINGS_PAYMENT`, `SUBSCRIPTION_PAYMENT`이지만, 계좌 예정 거래는 `AccountScheduledEventSource` 구현체가 자체 `type`을 지정하는 계약이다. 집합을 여기서 고정하면 계좌 어댑터가 붙는 순간 정상 값이 400이 된다. 알 수 없는 값은 빈 결과다 |
| `POST /forecasts` 상태 코드 | 200 OK | 캐시·멱등 재사용이 있어 항상 새 리소스가 생기지 않는다. 생성 여부는 응답 `cached`로 구분한다 |
| `horizonDays` 검증 | Bean Validation `@Min(1) @Max(365)`와 `CashflowForecastPeriodPolicy` 이중 | 형식 오류는 필드 단위 `VALIDATION_ERROR`로, 기준일 규칙은 정책이 판단한다. 상한 상수는 정책의 `MAX_HORIZON_DAYS`를 참조해 값을 두 곳에 적지 않는다 |
| 응답에서 제외 | `dedupeKey`, `requestHash` | 내부 중복 차단·캐시 판정용 해시다. 외부 계약으로 노출하면 바꿀 수 없게 된다 |
| 불완전 사유 | `incompleteReasons`로 실행 응답에만 싣는다 | 저장 컬럼이 없어 캐시 재사용 시 복원할 수 없다(540 문서). 조회 API는 이 값을 제공하지 않는다 |

## 5. 응답 계약 초안

- `CashflowForecastResponse` — `forecastId`, `baseDate`, `horizonDays`, `endDate`, `status`, `currentBalance`, `minimumBalance`, `riskDate`, `calculationVersion`, `createdAt`, `completedAt`, `cached`, `incompleteReasons`. `availableToday`는 542에서 계산이 정해질 때까지 응답에 넣지 않는다.
- `CashflowDailyBalanceListResponse` — `forecastId`, `baseDate`, `horizonDays`, `startDate`, `endDate`(요청 구간과 예측 구간의 교집합), `balances[{balanceDate, expectedBalance}]`.
- `CashflowEventListResponse` — 위와 같은 헤더 + `events[{eventId, eventDate, type, name, direction, amount, sourceType, sourceId}]`.

## 6. 테스트

`backend/src/test/java/com/ssafy/dodam/domain/cashflow/CashflowApiIntegrationTest.java` (SpringBootTest + MockMvc + H2, 기존 API 통합 테스트 패턴)

1. 실행 → 최신 조회 → 일별잔액 → 이벤트가 같은 `forecastId`를 가리킨다
2. 최신은 완료 시각 기준이다. 미래 기준일 예측을 먼저 돌려도 나중에 완료된 예측이 최신이다
3. 같은 요청 재실행 시 `cached=true`이고 `forecastId`가 같다. `forceRefresh=true`면 새 `forecastId`
4. 인증 없으면 401, 타인 사용자로 조회하면 404(존재 노출 없음)
5. 입력 검증: `horizonDays` 0·366, 과거 `baseDate`, `startDate > endDate`, 형식이 틀린 `type`
6. 경계: 요청 구간이 예측 구간을 벗어나면 빈 목록, 예측이 없으면 404, 기간 미지정 기본값

검증 명령은 `.\gradlew.bat test` 후 `.\gradlew.bat clean build`다.

## 7. 남은 확인 사항

- Jira 541의 최신 완료 조건을 사이트에서 확인하지 못했다. 위 범위는 로컬 CSV 기준이다.
- `availableToday`는 542에서 계산 정의가 확정되면 실행·최신 조회 응답에 추가해야 한다.
- 계좌 예정 거래 어댑터가 없어 `events` 결과에 계좌 입·출금이 빠진다(539). API 계약 자체의 제약은 아니지만 응답 데이터의 현재 한계다.
