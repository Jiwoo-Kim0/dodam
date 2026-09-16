# Goal API — 목표 CRUD·요약·연결계좌·계산·이력

JIRA `S15P21D105-455` (D6 Goal 기본 CRUD·계산 API 구현)의 하위 티켓 `S15P21D105-508`(API 12 Goal CRUD),
`S15P21D105-509`(API 12 목표 연결계좌), `S15P21D105-510`(API 12 목표 진행률·필요 저축액),
`S15P21D105-511`(API 12 목표 예측·위험·이력) 구현 기준이다. API 명세 12 Goal 14개를 모두 다룬다.
511 제목의 "예측·위험"(forecast·risk)은 510에서 함께 구현했고, 511에서는 변화 이력(`goal_history`)을 추가했다.

## 모델

| 테이블 | 엔티티 | 내용 |
| --- | --- | --- |
| `goal` (V5) | `Goal` | 공통: 이름, 유형(SAVING / SPENDING), 상태, 현재 모인 금액, soft delete(`deleted_at`) |
| `saving_goal` (V7) | `SavingGoal` | 모으기 목표의 목표 금액·목표일. PK = `goal_id` |
| `spending_goal` (V7) | `SpendingGoal` | 줄이기 목표의 월 예산. PK = `goal_id` |
| `goal_account` (V8) | `GoalAccount` | 모으기 목표에 연결된 계좌. PK = (`goal_id`, `account_id`), 계좌당 목표 1개(`uk_goal_account_account`) |
| `goal_history` (V9) | `GoalHistory` | 변화 시점의 스냅샷(확보 금액·진행률·예상 달성일·event). 한 날짜에 여러 건 가능 |

- FE의 "모으기(saving)"가 SAVING, "줄이기(reducing)"가 SPENDING이다.
- 세 엔티티는 연관관계 없이 같은 `goal_id`로 서비스가 묶는다. `goal_type` 컬럼은 복합 외래키 `(goal_id, goal_type) → goal(goal_id, type)`을
  위해 항상 부모의 `type`과 같은 값이 들어간다.
- `current_amount`(모인 금액)는 연결 계좌 확보액 합이다(626 이전에는 잔액 합; 정의는 「연결계좌」 절). 연결을 바꾸는 PUT(509)과 계산 API(510: progress·required-saving·forecast·risk)를
  호출할 때마다 다시 계산해 저장값도 갱신한다. 그래서 동기화로 잔액이 바뀐 뒤에도 계산 API를 한 번 부르면 목록·요약의 모인 금액이 맞아진다.
  줄이기 목표는 0으로 둔다.
- V5(`goal`)는 543이, V7(`saving_goal`·`spending_goal`)은 507이, V8(`goal_account`)은 509가, V9(`goal_history`)는 511이 만들었다.
  BE-A 구간(V1~V9)은 이것으로 소진됐다.

### 상태 전이

```
ACTIVE ──▶ PAUSED ──▶ ACTIVE
  │           │
  ├──▶ ACHIEVED (종료)
  └──▶ CANCELED (종료) ◀── PAUSED
```

ACHIEVED·CANCELED는 종료 상태다. 어디로도 옮길 수 없고 다른 필드도 바꿀 수 없다(409 `GOAL_NOT_EDITABLE`).
허용되지 않은 전이는 409 `GOAL_STATUS_TRANSITION_NOT_ALLOWED`. 모인 금액이 목표에 도달했을 때 ACHIEVED로 자동 전이하는 것은
510(진행률)에서 다룬다. 지금은 사용자가 PATCH로 바꾼다.

## API

모든 API는 인증이 필요하고 본인 목표만 다룬다. 타인 목표·삭제된 목표·없는 목표는 구분하지 않고 404다.

| Method | Endpoint | 상태 코드 |
| --- | --- | --- |
| GET | `/api/v1/goals?status&type&page&size` | 200 |
| GET | `/api/v1/goals/summary` | 200 |
| POST | `/api/v1/goals` | 201 |
| GET | `/api/v1/goals/{goalId}` | 200 |
| PATCH | `/api/v1/goals/{goalId}` | 200 |
| DELETE | `/api/v1/goals/{goalId}` | 200 (`data: null`) |
| GET | `/api/v1/goals/{goalId}/accounts?page&size` | 200 |
| PUT | `/api/v1/goals/{goalId}/accounts` | 200 |
| GET | `/api/v1/goals/{goalId}/progress` | 200 (SAVING만) |
| GET | `/api/v1/goals/{goalId}/required-saving` | 200 (SAVING만) |
| GET | `/api/v1/goals/{goalId}/forecast?baseDate&months` | 200 (SAVING만) |
| GET | `/api/v1/goals/{goalId}/spending-progress` | 200 (SPENDING만) |
| GET | `/api/v1/goals/{goalId}/risk` | 200 |
| GET | `/api/v1/goals/{goalId}/history?startDate&endDate&page&size` | 200 |

### 오류 코드

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | 생성 시 `name`·`type` 누락, 이름 150자 초과, 금액 ≤ 0, `goalId` ≤ 0, page/size 범위 밖, 필터 형식 위반 |
| `INVALID_REQUEST` | 400 | 알 수 없는 유형·상태, 유형에 맞지 않는 필드(아래 표), 목표일이 오늘 이전, 공백만인 이름, 모으기 전용 API를 줄이기 목표로(또는 반대로) 호출, `baseDate` 형식 위반 |
| `NO_FIELDS_TO_UPDATE` | 400 | 수정 Body에 바꿀 필드가 없음 |
| `GOAL_NOT_EDITABLE` | 409 | 종료 상태(ACHIEVED·CANCELED) 목표 수정 |
| `GOAL_STATUS_TRANSITION_NOT_ALLOWED` | 409 | 허용되지 않은 상태 전이 |
| `ACCOUNT_ALREADY_LINKED_TO_GOAL` | 409 | 다른(삭제되지 않은) 목표에 이미 연결된 계좌를 지정 |
| `RESOURCE_NOT_FOUND` | 404 | 본인 것이 아니거나 삭제된 목표, 본인 것이 아니거나 없는 계좌("계좌를 찾을 수 없습니다.") |

## 생성 `POST /goals`

| 필드 | SAVING(모으기) | SPENDING(줄이기) |
| --- | --- | --- |
| `name` | 필수, 앞뒤 공백 제거, 150자 | 〃 |
| `type` | `SAVING` (대소문자 무시) | `SPENDING` |
| `targetAmount` | 필수, 1 이상 | **받지 않음**(400) |
| `targetDate` | 필수, 오늘 이후 | **받지 않음**(400) |
| `monthlyBudget` | **받지 않음**(400) | 필수, 1 이상 |

명세 Body는 `targetAmount`·`targetDate`를 필수, `monthlyBudget`을 선택으로 적었지만 ERD `spending_goal`에는 월 예산만 저장할 수 있다.
줄이기 목표에 목표 금액·목표일을 조용히 버리는 대신 400으로 알려 FE와의 계약 불일치가 바로 드러나게 했다(남은 작업 참고).
상태는 ACTIVE로 시작한다.

## 응답 `GoalResponse`

목록·상세·생성·수정이 같은 형태다.

| 필드 | 설명 |
| --- | --- |
| `goalId`, `name`, `type`, `status`, `currentAmount`, `createdAt`, `updatedAt` | 공통 |
| `targetAmount`, `targetDate`, `remainingAmount`, `progressRatePercent`, `daysRemaining` | SAVING만. 진행률은 `currentAmount / targetAmount`(%, 100 상한), `daysRemaining`은 기준일부터 목표일까지(지났으면 음수) |
| `monthlyBudget` | SPENDING만. 이번 달 소비 대비 진행률은 510의 `/spending-progress` 또는 `GET /spending/goal-comparison?goalId=` |

## 목록 `GET /goals`

- `status`·`type` 필터(대소문자 무시, 정의되지 않은 값 400), page/size(0 이상, 1~100).
- 삭제된 목표는 나오지 않는다. 정렬은 상태(ACTIVE → PAUSED → ACHIEVED → CANCELED) → 최근 생성 순.
- 필터·정렬·페이지는 메모리에서 한다(사용자당 목표 수십 건 전제).

## 요약 `GET /goals/summary`

상태별·유형별 개수와, **ACTIVE 목표만** 합산한 모으기 목표 금액·모인 금액·진행률(%), 줄이기 월 예산 합. 삭제된 목표는 어디에도 들어가지 않는다.

## 수정 `PATCH /goals/{goalId}`

- 입력된 필드만 바꾼다. null(생략)은 변경 없음, 전부 비면 400 `NO_FIELDS_TO_UPDATE`.
- 유형에 맞지 않는 필드(모으기의 `monthlyBudget`, 줄이기의 `targetAmount`·`targetDate`)는 400이고 **어떤 필드도 바뀌지 않는다**(검증을 먼저 끝낸 뒤 반영).
- `status`는 전이 규칙을 따른다. 종료 상태 목표는 `status` 포함 모든 수정이 409.
- 값이 전부 같으면 `updated_at`을 건드리지 않고 현재 상태를 200으로 돌려준다(중복 요청 방지).

## 삭제 `DELETE /goals/{goalId}`

`deleted_at`을 찍는 soft delete. 하위행(`saving_goal`/`spending_goal`)은 남긴다. 이후 조회·수정·삭제는 404.
소비목표 비교(507)와 진단(543)은 `deleted_at IS NULL`로 걸러 읽는다. 연결 계좌(`goal_account`)는 삭제 시 지우지 않고, 다른 목표가
그 계좌를 연결할 때 삭제된 목표의 연결을 해제하고 이어받는다(509 참고).

## 연결계좌 (509)

모으기 목표에 계좌를 연결하면 그 계좌들의 **확보액** 합이 목표의 `currentAmount`가 된다. ERD의 `fk_goal_account_saving_goal` 때문에
줄이기 목표에는 연결할 수 없다. 계좌당 목표 1개 제약(`uk_goal_account_account`)은 626(V35)부터 예·적금에만 남고
(`exclusive_account_id` 생성 컬럼 + UNIQUE), 입출금은 여러 목표에 금액을 나눠 담을 수 있다.

### 확보액 정의 (626, `GoalAllocationReader`)

목표 API·진단·저축 시뮬레이션 기준선이 모두 이 한 정의를 쓴다. 입출금 하나를 여러 목표에 담아도 같은 잔액이 두 목표에 중복 합산되지 않는다.

| 항목 | 정의 |
| --- | --- |
| 링크 기여액(`allocatedToThisGoal`) | `contribution_amount`가 있으면 그 값. 없으면 계좌 잔액 전체(0 미만은 0) |
| 예·적금 | `contribution_amount`는 항상 null → 잔액 전체가 연결 목표의 확보액 |
| 입출금 | 담은 금액. **null은 626 이전 방식(잔액 전체)** 으로 읽는다. 기존 행과 시연 seed는 그대로 유효하며 처음 담기/꺼내기 시 숫자로 바뀐다 |
| 목표 확보액(`totalBalance` = `currentAmount`) | 그 목표의 링크 기여액 합 |
| 계좌 총 할당 | 그 계좌가 **삭제되지 않은** 모든 목표에 기여하는 금액 합(목표 상태·페이지와 무관) |
| `availableToAllocate` | `max(0, balance − 총 할당)`. 예·적금은 항상 0 |
| `shortfallAmount` | `max(0, 총 할당 − max(0, balance))`. 실제 출금으로 설정액이 잔액을 넘은 상태. 계좌 단위이며 목표별로 합산하지 않는다 |
| `allocationStatus` | `shortfallAmount > 0`이면 `NEEDS_ADJUSTMENT`, 아니면 `OK` |

부족 상태에서도 확보액은 설정액 그대로 계산한다(진행률이 실제보다 높게 보일 수 있으므로 FE가 부족을 함께 표시). 총자산은 원본 계좌 기준으로
한 번만 합산하므로 자산이 부풀지 않는다. 담기/꺼내기 API(`PUT /goals/{goalId}/allocations/{accountId}`)는 626 MR-B에서 추가한다
([goal-allocation-626-plan.md](goal-allocation-626-plan.md)).

### 조회 `GET /goals/{goalId}/accounts?page&size`

- 계좌 ID 순. 각 항목은 계좌 응답(5)과 같은 필드(`accountId`, `bankCode`, `bankName`, `accountNoMasked`, `accountName`, `accountType`,
  `status`, `balance`)에 `contributionAmount`(저장 원문, null 가능)와 626 할당 필드 `allocatedToThisGoal`, `allocatedElsewhere`,
  `availableToAllocate`, `shortfallAmount`, `allocationStatus`를 더한 것이다. 계좌번호 원문은 주지 않는다.
- `linkedCount`(전체 연결 수), `totalBalance`(목표 확보액 = `currentAmount`; 필드 이름은 509 계약 유지)는 페이지와 무관한 전체 값이다.
- 줄이기 목표나 연결이 없는 목표는 빈 목록(404 아님).

### 변경 `PUT /goals/{goalId}/accounts` Body `{ "accountIds": [11, 13, …] }`

연결 계좌를 요청 목록으로 **통째로 교체**한다. 빈 배열은 모두 해제다. 중복은 한 번으로 본다. 최대 50개.

계좌는 **ID**(계좌 목록(5)·연결계좌 조회 응답의 `accountId`)로 지정한다. 2026-09-16까지는 계좌번호 원문 `accountNos[]`를 받아
서버가 HMAC 해시로 찾았는데, 계좌 응답이 `accountId`와 마스킹 번호만 주고 원문은 응답에 돌려주지 않아 FE가 Body를 만들 수 없었다
(remaining-work.md 515 절에서 seed 환경에서도 같은 이유로 호출하지 못했다고 기록). 원문 계좌번호를 Body로 받는 경로도 함께 없어졌다.
이전 키 `accountNos`를 보내면 `accountIds` 누락으로 400 `VALIDATION_ERROR`다.

| 검증 | 결과 |
| --- | --- |
| `accountIds` 누락, 원소가 null·0 이하, 숫자가 아님 | 400 `VALIDATION_ERROR` / `INVALID_REQUEST` |
| 줄이기(SPENDING) 목표 | 400 "줄이기 목표에는 계좌를 연결할 수 없습니다." |
| 종료 상태(ACHIEVED·CANCELED) 목표 | 409 `GOAL_NOT_EDITABLE` |
| 본인 계좌가 아니거나 없는 계좌 ID | 404 "계좌를 찾을 수 없습니다." (타인 계좌의 존재 여부를 드러내지 않도록 같은 응답) |
| 마이너스통장(MINUS) | 400 "입출금·예금·적금 계좌만 목표에 연결할 수 있습니다." (ERD `chk_goal_account_type`) |
| 해지(CLOSED) 계좌 | 400. INACTIVE는 허용(잔액은 유효) |
| 다른 살아 있는 목표에 연결된 계좌 | 409 `ACCOUNT_ALREADY_LINKED_TO_GOAL`. 삭제(soft delete)된 목표의 연결은 해제하고 이어받는다 |

- 검증을 모두 끝낸 뒤 반영하므로 부분 반영은 없다.
- 반영 후 `goal.current_amount`를 확보액으로 다시 놓는다(`updated_at` 갱신). 집합이 같고 확보액도 같으면 아무것도 바꾸지 않는다.
  집합은 같아도 잔액이 바뀌었으면 모인 금액만 갱신되므로, FE가 "새로고침" 용도로 같은 목록을 다시 PUT해도 된다.
- 새로 연결한 링크의 `contribution_amount`는 null(전액)이다. 이미 담은 금액이 있는 입출금 링크를 같은 목록으로 다시 PUT해도 할당액은 유지된다.
- MR-A 시점: PUT은 아직 입출금도 받는다(626 이전과 같음). MR-B에서 예·적금 한정으로 바뀐다.
- 응답은 조회와 같은 형태(연결 계좌 전체, 1페이지).

## 계산 (510)

기획안 「모으기 목표 달성 위험」·「줄이기 목표 초과 위험」의 파생 데이터를 구현했다. 모두 조회 시점(`GoalClock`) 기준이다.

| 용어 | 정의 |
| --- | --- |
| 현재 확보 금액 `currentAmount` | 연결 계좌 확보액 합(조회 시 다시 계산, 626 정의) |
| 경과 개월 `monthsElapsed` | 목표 생성일 → 기준일, 최소 1 (첫 달에도 속도를 정의) |
| 현재 저축속도 `averageMonthlySaving` | currentAmount / monthsElapsed |
| 남은 개월 `monthsRemaining` | 기준일 → 목표일, 올림. 목표일이 지났으면 0 |
| 필요 월 저축액 `requiredMonthlySaving` | 남은 금액 / 남은 개월(올림). 남은 개월 0이면 남은 금액 전체 |
| 기대 진행률 `expectedProgressRatePercent` | 경과 일수 / (생성일→목표일 전체 일수) × 100, 0~100 |

### 진행률 `GET /goals/{goalId}/progress` (SAVING만, 줄이기는 400)

`targetAmount`, `currentAmount`, `remainingAmount`, `progressRatePercent`(100 상한), 기간(`startDate`=생성일, `targetDate`, `asOfDate`,
`totalDays`, `daysElapsed`, `daysRemaining`), `expectedProgressRatePercent`, `gapPercentPoint`(실제 − 기대), `paceStatus`
(ACHIEVED / AHEAD ≥ +5pt / ON_TRACK / BEHIND ≤ −5pt), `linkedAccountCount`.

### 필요 월 저축액 `GET /goals/{goalId}/required-saving` (SAVING만)

위 정의대로 `requiredMonthlySaving`, `requiredDailySaving`(남은 금액 / 남은 일수, 올림), `averageMonthlySaving`, `monthlyShortfall`
(필요 − 속도, 양수면 지금 속도로는 미달), `feasibility`(ACHIEVED / SUFFICIENT / INSUFFICIENT / DEADLINE_PASSED),
`requiredRatioOfIncomePercent`(필요 월 저축액 / 금융 기본정보 월 소득, 소득이 없으면 null). 기획안 예시 문장
("매월 약 43만 원의 저축이 필요합니다. 현재 월평균 저축액은 35만 원…")을 이 응답으로 만들 수 있다.

### 예상 달성일 `GET /goals/{goalId}/forecast?baseDate&months` (SAVING만)

- `baseDate`(기본 오늘)에 `monthsToAchieve = ceil(남은 금액 / 저축속도)`개월을 더한 날이 `expectedAchievementDate`.
  이미 달성했으면 0개월(= baseDate), 속도가 0이면 둘 다 null(현재 속도로는 달성 불가).
- `daysDifference` = 예상일 − 목표일(음수면 조기 달성), `onSchedule` = 예상일 ≤ 목표일.
- `projection[]`: baseDate 다음 달부터 `months`개월(기본 12, 1~120) 동안 `min(목표, 현재 + 속도 × n)`과 달성 여부.
- 속도는 목표 생성 이후 평균이다. 이력(511)이 쌓이면 최근 N개월 속도로 바꿀 수 있다(남은 작업).

### 소비목표 진행률 `GET /goals/{goalId}/spending-progress` (SPENDING만, 모으기는 400)

이번 달 소비(506 정의, 오늘까지)를 월 예산과 비교한다. `spent`, `remainingBudget`, `usageRatePercent`, `dailyBudgetRemaining`(남은 예산 /
남은 일수, 초과면 0, 남은 날이 없으면 null), `projectedMonthEnd`(소비 × 그 달 일수 / 경과 일수), `previousMonthSpending`,
`expectedSavingAmount`(= max(0, 전월 소비 − 예상 월말 소비), FE "예상 절감액"), `status`(ON_TRACK < 80% ≤ WARNING ≤ 100% < EXCEEDED).
`GET /spending/goal-comparison?goalId=`(507)와 같은 숫자를 목표 축으로 돌려준다.

### 위험상태 `GET /goals/{goalId}/risk`

| 유형 | 요인 코드 | 수준 |
| --- | --- | --- |
| 공통 | 종료 상태(ACHIEVED·CANCELED) 또는 모으기 목표 달성 | `NONE`, 요인 없음 |
| SAVING | `DEADLINE_PASSED` 목표일 지남·남은 금액 있음 | HIGH |
| SAVING | `PACE_INSUFFICIENT` 필요 월 저축액 > 저축속도 | 부족률 ≥ 30% HIGH, 미만 MEDIUM |
| SAVING | `BEHIND_SCHEDULE` 기대 진행률 − 실제 > 10pt | MEDIUM |
| SAVING | `NO_LINKED_ACCOUNTS` 연결 계좌 없음 | MEDIUM |
| SPENDING | `OVER_BUDGET` 누적 소비 > 월 예산 | HIGH |
| SPENDING | `PACE_EXCEEDS_BUDGET` 예상 월말 소비 > 월 예산 | MEDIUM |
| 그 외 | — | LOW |

`riskLevel`은 요인 중 가장 높은 수준이고, `metrics`에 판정에 쓴 수치(모으기: 필요·평균 저축액·실제·기대 진행률 / 줄이기: 예산·소비·예상·사용률)를 함께 준다.
기준값(5pt, 10pt, 30%, 80%)은 `GoalCalculationService` 상수다. 진단(14)이 같은 규칙을 쓰도록 맞추는 일은 남은 작업.

## 변화 이력 (511)

목표를 바꾸는 모든 경로가 반영 직후 `goal_history`에 스냅샷을 남긴다(`GoalHistoryRecorder`). 값이 실제로 바뀌지 않은 요청(같은 값 PATCH,
같은 집합 PUT, 잔액 변화 없는 계산 API 호출)은 이력을 남기지 않는다.

| event | 시점 | 기록 위치 |
| --- | --- | --- |
| `GOAL_CREATED` | 생성 (확보 금액 0) | `GoalService.create` |
| `GOAL_UPDATED` | 이름·목표 금액·목표일·월 예산 수정 | `GoalService.update` |
| `STATUS_CHANGED` | 상태 전이(다른 필드 수정과 함께 오면 이 값) | `GoalService.update` |
| `ACCOUNTS_UPDATED` | 연결 계좌 집합 변경 | `GoalAccountService.replaceAccounts` |
| `AMOUNT_UPDATED` | 연결 계좌 잔액 변화로 확보 금액이 달라짐(같은 집합 PUT, 510 계산 API 호출 시) | `GoalAccountService`, `GoalCalculationService` |
| `GOAL_DELETED` | soft delete | `GoalService.delete` |

각 행에는 그 시점의 `currentAmount`, `progressRatePercent`(모으기만, 4자리), `forecastDate`(모으기만, 510과 같은 규칙으로 계산한
예상 달성일, 속도 0이면 null)가 함께 남는다. 이력을 이어 보면 기획안 「금융목표 예상 달성 시점 변화」("기존 예상 달성일 → 현재 예상 달성일")를
만들 수 있다.

### 조회 `GET /goals/{goalId}/history?startDate&endDate&page&size`

- 기간 정책은 거래내역과 같은 `QueryPeriodPolicy`(기본 최근 3개월, 최대 12개월, 오늘까지). page ≥ 0, size 1~100.
- `recordedDate` 내림차순, 같은 날이면 나중에 기록된 것 먼저. 공통 `PageResponse`.
- 삭제된 목표의 이력은 행은 남지만 API로는 404다(목표 조회 규칙과 같음).
- 응답 항목: `historyId`, `recordedDate`, `currentAmount`, `progressRatePercent`, `forecastDate`, `event`, `createdAt`.

## 테스트

- `GoalApiIntegrationTest`(14건): 유형별 생성·검증 8종, 목록 정렬·필터·페이지·타인 제외·삭제 제외, 요약 합산(ACTIVE만), 상세·타인 404,
  부분 수정·중복 요청·유형 불일치·검증 실패 시 미반영, 상태 전이 허용/거절·종료 상태 잠금, soft delete.
- `GoalAccountApiIntegrationTest`(9건): 연결·모인 금액·진행률 갱신, 페이지, 빈 목록, 집합 교체·전체 해제, 같은 집합 idempotent·잔액 재반영,
  유형·상태·소유권·형식 검증 시 미반영, 다른 목표 연결 409·삭제된 목표 이어받기, 줄이기·종료 목표·타인 거절.
- `GoalCalculationApiIntegrationTest`(10건): 진행률(잔액 재계산·저장, 기대 진행률·BEHIND/AHEAD/ACHIEVED), 필요 저축액(남은 개월 올림, 속도 비교,
  소득 비율, 목표일 지남), 예상 달성일(속도 기준, 기준일 변경, 속도 0, 파라미터 검증), 소비목표 진행률(숨김 제외, 예상·절감액), 위험 판정(요인·수준·NONE).
  생성일은 JDBC로 "오늘 − N개월"로 되돌려 날짜 의존을 없앴다.
- `GoalHistoryApiIntegrationTest`(4건): 모으기 목표의 생성→연결→잔액 변화→수정→상태 변경이 event 순서대로 남고 값 없는 변경은 남지 않음,
  줄이기 목표(진행률 null)·삭제 기록, 기간·페이지·최신순·검증, 타인 404.
- fixture `sql/goal-test-data.sql`이 `goal`·`saving_goal`·`spending_goal`·`goal_account`·`goal_history`·`account`를 ERD 컬럼으로 다시 만든다. 507 fixture(`transaction-test-data.sql`)도
  같은 세 테이블을 다시 만들도록 맞췼다(한쪽만 DROP하면 뒤에 실행되는 테스트가 깨진다).

## 남은 작업

[remaining-work.md](remaining-work.md)의 "S15P21D105-508" ~ "S15P21D105-511" 절에 기록한다.

## MySQL 회귀 (2026-09-11, S15P21D105-515)

Subscription(V30) 추가 뒤 Docker `mysql:8.0`에 V1~V17+V30과 시연 seed(목표 6,000·연결 4,750·이력 30,000)를 적재하고 seed 사용자로 Goal API를 호출했다.
조회 13개의 응답이 seed 기대값(연결 잔액 합 = `currentAmount`, 월말 이력 7건, 전월 소비 합)과 일치했고, 생성 → 수정 → 상태 전이(허용·거부) → 삭제 흐름이
MySQL CHECK·복합 외래키가 살아 있는 상태에서 동작했다. `PUT /goals/{goalId}/accounts`만 seed 계좌번호 원문이 없어 MySQL에서는 확인하지 못했다(H2 통합 테스트로 확인).
상세 수치는 [remaining-work.md](remaining-work.md) 515 절.
