# Goal API — 목표 CRUD·요약

JIRA `S15P21D105-455` (D6 Goal 기본 CRUD·계산 API 구현)의 하위 티켓 `S15P21D105-508`(API 12 Goal CRUD) 구현 기준이다.
API 명세 12 Goal 14개 중 목록·요약·생성·상세·수정·삭제 6개를 다룬다. 연결계좌(509), 진행률·필요 월 저축액·예상 달성일·
소비목표 진행률·위험상태(510), 변화 이력(511)은 다음 티켓 범위다.

## 모델

| 테이블 | 엔티티 | 내용 |
| --- | --- | --- |
| `goal` (V5) | `Goal` | 공통: 이름, 유형(SAVING / SPENDING), 상태, 현재 모인 금액, soft delete(`deleted_at`) |
| `saving_goal` (V7) | `SavingGoal` | 모으기 목표의 목표 금액·목표일. PK = `goal_id` |
| `spending_goal` (V7) | `SpendingGoal` | 줄이기 목표의 월 예산. PK = `goal_id` |

- FE의 "모으기(saving)"가 SAVING, "줄이기(reducing)"가 SPENDING이다.
- 세 엔티티는 연관관계 없이 같은 `goal_id`로 서비스가 묶는다. `goal_type` 컬럼은 복합 외래키 `(goal_id, goal_type) → goal(goal_id, type)`을
  위해 항상 부모의 `type`과 같은 값이 들어간다.
- `current_amount`(모인 금액)는 이 티켓에서 바꾸지 않는다. 연결 계좌 잔액 합으로 갱신하는 것은 509다. 줄이기 목표는 0으로 둔다.
- V5(`goal`)는 543이, V7(`saving_goal`·`spending_goal`)은 507이 선반영했다. `goal_account`·`goal_history`는 509·511에서 V8로 추가한다.

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

### 오류 코드

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | 생성 시 `name`·`type` 누락, 이름 150자 초과, 금액 ≤ 0, `goalId` ≤ 0, page/size 범위 밖, 필터 형식 위반 |
| `INVALID_REQUEST` | 400 | 알 수 없는 유형·상태, 유형에 맞지 않는 필드(아래 표), 목표일이 오늘 이전, 공백만인 이름 |
| `NO_FIELDS_TO_UPDATE` | 400 | 수정 Body에 바꿀 필드가 없음 |
| `GOAL_NOT_EDITABLE` | 409 | 종료 상태(ACHIEVED·CANCELED) 목표 수정 |
| `GOAL_STATUS_TRANSITION_NOT_ALLOWED` | 409 | 허용되지 않은 상태 전이 |
| `RESOURCE_NOT_FOUND` | 404 | 본인 것이 아니거나 삭제된 목표 |

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
소비목표 비교(507)와 진단(543)은 `deleted_at IS NULL`로 걸러 읽는다. 연결 계좌(509)를 만든 뒤에는 삭제 시 연결 해제도 함께 해야 한다.

## 테스트

- `GoalApiIntegrationTest`(14건): 유형별 생성·검증 8종, 목록 정렬·필터·페이지·타인 제외·삭제 제외, 요약 합산(ACTIVE만), 상세·타인 404,
  부분 수정·중복 요청·유형 불일치·검증 실패 시 미반영, 상태 전이 허용/거절·종료 상태 잠금, soft delete.
- fixture `sql/goal-test-data.sql`이 `goal`·`saving_goal`·`spending_goal`을 ERD 컬럼으로 다시 만든다. 507 fixture(`transaction-test-data.sql`)도
  같은 세 테이블을 다시 만들도록 맞췼다(한쪽만 DROP하면 뒤에 실행되는 테스트가 깨진다).

## 남은 작업

[remaining-work.md](remaining-work.md)의 "S15P21D105-508" 절에 기록한다.
