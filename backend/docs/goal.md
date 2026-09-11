# Goal API — 목표 CRUD·요약·연결계좌·계산

JIRA `S15P21D105-455` (D6 Goal 기본 CRUD·계산 API 구현)의 하위 티켓 `S15P21D105-508`(API 12 Goal CRUD),
`S15P21D105-509`(API 12 목표 연결계좌), `S15P21D105-510`(API 12 목표 진행률·필요 저축액) 구현 기준이다.
API 명세 12 Goal 14개 중 13개를 다룬다. 변화 이력(511)은 다음 티켓 범위다.

## 모델

| 테이블 | 엔티티 | 내용 |
| --- | --- | --- |
| `goal` (V5) | `Goal` | 공통: 이름, 유형(SAVING / SPENDING), 상태, 현재 모인 금액, soft delete(`deleted_at`) |
| `saving_goal` (V7) | `SavingGoal` | 모으기 목표의 목표 금액·목표일. PK = `goal_id` |
| `spending_goal` (V7) | `SpendingGoal` | 줄이기 목표의 월 예산. PK = `goal_id` |
| `goal_account` (V8) | `GoalAccount` | 모으기 목표에 연결된 계좌. PK = (`goal_id`, `account_id`), 계좌당 목표 1개(`uk_goal_account_account`) |

- FE의 "모으기(saving)"가 SAVING, "줄이기(reducing)"가 SPENDING이다.
- 세 엔티티는 연관관계 없이 같은 `goal_id`로 서비스가 묶는다. `goal_type` 컬럼은 복합 외래키 `(goal_id, goal_type) → goal(goal_id, type)`을
  위해 항상 부모의 `type`과 같은 값이 들어간다.
- `current_amount`(모인 금액)는 연결 계좌 잔액 합이다. 연결을 바꾸는 PUT(509)과 계산 API(510: progress·required-saving·forecast·risk)를
  호출할 때마다 다시 계산해 저장값도 갱신한다. 그래서 동기화로 잔액이 바뀐 뒤에도 계산 API를 한 번 부르면 목록·요약의 모인 금액이 맞아진다.
  줄이기 목표는 0으로 둔다.
- V5(`goal`)는 543이, V7(`saving_goal`·`spending_goal`)은 507이, V8(`goal_account`)은 509가 만들었다. `goal_history`는 511에서 V9로 추가한다.

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

모으기 목표에 계좌를 연결하면 그 계좌들의 잔액 합이 목표의 `currentAmount`가 된다. ERD의 `fk_goal_account_saving_goal` 때문에
줄이기 목표에는 연결할 수 없고, `uk_goal_account_account` 때문에 한 계좌는 한 목표에만 연결된다.

### 조회 `GET /goals/{goalId}/accounts?page&size`

- 계좌 ID 순. 각 항목은 계좌 응답(5)과 같은 필드(`accountId`, `bankCode`, `bankName`, `accountNoMasked`, `accountName`, `accountType`,
  `status`, `balance`)에 `contributionAmount`를 더한 것이다. 계좌번호 원문은 주지 않는다.
- `linkedCount`(전체 연결 수), `totalBalance`(연결 계좌 잔액 합)는 페이지와 무관한 전체 값이다.
- 줄이기 목표나 연결이 없는 목표는 빈 목록(404 아님).

### 변경 `PUT /goals/{goalId}/accounts` Body `{ "accountNos": ["1234567890123456", …] }`

연결 계좌를 요청 목록으로 **통째로 교체**한다. 빈 배열은 모두 해제다. 중복은 한 번으로 본다. 최대 50개.

| 검증 | 결과 |
| --- | --- |
| 줄이기(SPENDING) 목표 | 400 "줄이기 목표에는 계좌를 연결할 수 없습니다." |
| 종료 상태(ACHIEVED·CANCELED) 목표 | 409 `GOAL_NOT_EDITABLE` |
| 본인 계좌가 아니거나 없는 계좌번호 | 404 |
| 마이너스통장(MINUS) | 400 "입출금·예금·적금 계좌만 목표에 연결할 수 있습니다." (ERD `chk_goal_account_type`) |
| 해지(CLOSED) 계좌 | 400. INACTIVE는 허용(잔액은 유효) |
| 다른 살아 있는 목표에 연결된 계좌 | 409 `ACCOUNT_ALREADY_LINKED_TO_GOAL`. 삭제(soft delete)된 목표의 연결은 해제하고 이어받는다 |

- 검증을 모두 끝낸 뒤 반영하므로 부분 반영은 없다.
- 반영 후 `goal.current_amount`를 연결 계좌 잔액 합으로 다시 놓는다(`updated_at` 갱신). 집합이 같고 잔액도 같으면 아무것도 바꾸지 않는다.
  집합은 같아도 잔액이 바뀌었으면 모인 금액만 갱신되므로, FE가 "새로고침" 용도로 같은 목록을 다시 PUT해도 된다.
- `contributionAmount`(계좌별 기여 금액)는 명세 Body에 대응 필드가 없어 null로 두고 잔액 전체를 기여로 본다.
- 응답은 조회와 같은 형태(연결 계좌 전체, 1페이지).

## 계산 (510)

기획안 「모으기 목표 달성 위험」·「줄이기 목표 초과 위험」의 파생 데이터를 구현했다. 모두 조회 시점(`GoalClock`) 기준이다.

| 용어 | 정의 |
| --- | --- |
| 현재 확보 금액 `currentAmount` | 연결 계좌 잔액 합(조회 시 다시 계산) |
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

## 테스트

- `GoalApiIntegrationTest`(14건): 유형별 생성·검증 8종, 목록 정렬·필터·페이지·타인 제외·삭제 제외, 요약 합산(ACTIVE만), 상세·타인 404,
  부분 수정·중복 요청·유형 불일치·검증 실패 시 미반영, 상태 전이 허용/거절·종료 상태 잠금, soft delete.
- `GoalAccountApiIntegrationTest`(9건): 연결·모인 금액·진행률 갱신, 페이지, 빈 목록, 집합 교체·전체 해제, 같은 집합 idempotent·잔액 재반영,
  유형·상태·소유권·형식 검증 시 미반영, 다른 목표 연결 409·삭제된 목표 이어받기, 줄이기·종료 목표·타인 거절.
- `GoalCalculationApiIntegrationTest`(10건): 진행률(잔액 재계산·저장, 기대 진행률·BEHIND/AHEAD/ACHIEVED), 필요 저축액(남은 개월 올림, 속도 비교,
  소득 비율, 목표일 지남), 예상 달성일(속도 기준, 기준일 변경, 속도 0, 파라미터 검증), 소비목표 진행률(숨김 제외, 예상·절감액), 위험 판정(요인·수준·NONE).
  생성일은 JDBC로 "오늘 − N개월"로 되돌려 날짜 의존을 없앴다.
- fixture `sql/goal-test-data.sql`이 `goal`·`saving_goal`·`spending_goal`·`goal_account`·`account`를 ERD 컬럼으로 다시 만든다. 507 fixture(`transaction-test-data.sql`)도
  같은 세 테이블을 다시 만들도록 맞췼다(한쪽만 DROP하면 뒤에 실행되는 테스트가 깨진다).

## 남은 작업

[remaining-work.md](remaining-work.md)의 "S15P21D105-508" ~ "S15P21D105-510" 절에 기록한다.
