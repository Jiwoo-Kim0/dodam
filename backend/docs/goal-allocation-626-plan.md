# 626 목표별 계좌 할당(입출금 1 : 목표 N) 계획

작성일: 2026-09-16. 상태: **착수 — 2026-09-16 기획 변경 확정, §4·§5·§7은 검토 대안으로 확정.** 619(저축 시뮬레이션 goalId)는 [simulation-619-plan.md](simulation-619-plan.md)로 분리했고 develop에 병합됐다(`193ed07`).

담당: BE 전체(goal·simulation·diagnosis·recommendation 포함)는 이 브랜치에서 한 사람이 진행한다. FE 담기/꺼내기 화면만 FE 담당에게 §4 계약으로 인계한다.

## 0. 착수 조건 (2026-09-16 충족)

- 대상: S15P21D105-626. 브랜치 `S15P21D105-626-fix-1-1-1-n`(Jira 생성, 509 시점 분기)에 `origin/develop`을 머지해 619 위에서 시작한다.
- [기획안](../../docs/기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md) line 380 "모으기 목표, 계좌를 1:N의 관계로 설정 … 합산한 금액"은 **목표 1 : 계좌 N, 잔액 합산**이며 V8 `goal_account`(PK `goal_id+account_id`, `uk_goal_account_account`)로 이미 구현돼 있다. 브랜치명의 "1:1 → 1:N"이 이것을 뜻한다면 626은 실체가 없다.
- 이 문서가 다루는 "입출금 계좌 1 : 목표 N + 금액 할당"은 기획안·명세 12·ERD에 요구가 없다. ERD `goal_account.contribution_amount`(현재 항상 null)가 유일한 흔적이다. 유일한 실사용 동기는 "입출금 계좌가 하나인 사용자가 모으기 목표를 둘 만들면 한쪽에 계좌를 못 붙인다"이다.
- 기획 변경은 2026-09-16 확정했다. 루트 기획안·명세 12·ERD 변경은 필요 사항을 정리해 보고하고 명시 요청 시에만 수정한다.

## 1. 확정된 정책 (사용자 확인, 2026-09-16)

| 구분 | 확정된 방식 |
| --- | --- |
| 예금·적금 | 목적과 무관하게 모으기(SAVING) 목표에 전액 연결. 목표 하나에 여러 예·적금 연결 가능 |
| 예·적금 중복 | 예·적금 계좌 하나는 목표 하나에만 연결. 비율·일부 금액·여러 목표 분할 없음 |
| 자유입출금 | 계좌의 미할당 잔액 중 사용자가 지정한 금액을 목표에 넣는다. 같은 계좌에서 여러 목표에 나눠 넣을 수 있다 |
| 실제 계좌 | 목표 할당은 도담 내부 자금 구분. 은행 이체·실제 잔액 변경 없음 |
| 목표 할당 후 | 할당한 만큼 다음 목표에 담을 수 있는 금액이 줄어든다 |
| 화면 표현 | ‘목표에 담기·꺼내기’. 실제 계좌 잔액 / 목표에 담긴 금액 / 추가로 담을 수 있는 금액을 구분 |
| 출금 후 부족 | 설정액을 유지하고 부족을 표시하여 사용자 조정을 유도. 자동 비례 축소·우선순위 차감 없음 |
| 목표 목적 | 모든 모으기 목표는 SAVING 유지. 목돈·구매·여행 등은 이름·선택 라벨로만 표현하고 계좌 연결 자격을 제한하지 않음 |

`savingPurpose`·LUMP_SUM/PURCHASE enum·목적별 계좌 제한·기존 목표 재분류는 추가하지 않는다. SPENDING은 계좌 연결·할당 불가 그대로.

## 2. 입출금 동작과 금액 계약

실제 계좌 잔액 100만 원:

| 동작 | 실제 잔액 | A 할당 | B 할당 | 추가 할당 가능액 |
| --- | ---: | ---: | ---: | ---: |
| 시작 | 1,000,000 | 0 | 0 | 1,000,000 |
| A에 300,000 담기 | 1,000,000 | 300,000 | 0 | 700,000 |
| B에 200,000 담기 | 1,000,000 | 300,000 | 200,000 | 500,000 |
| A에서 100,000 꺼내기 | 1,000,000 | 200,000 | 200,000 | 600,000 |

B에 80만 원 요청은 남은 70만 원 초과이므로 거절. 꺼내기는 은행 출금이 아니라 할당 해제다.

- `balance`: 마지막 동기화로 확인한 실제 잔액(기준 시각 함께 표시).
- `allocatedTotal`: 그 계좌에서 삭제되지 않은 모든 목표에 할당한 합. 목표 상태·목록 필터·페이지와 무관.
- `availableToAllocate = max(0, balance − allocatedTotal)`.
- `shortfallAmount = max(0, allocatedTotal − max(0, balance))`.
- 담기 금액은 양의 원 단위 정수, `amount ≤ availableToAllocate`. 꺼내기는 현재 할당액 이하.
- 실제 입금은 미할당만 늘리고 할당액을 자동으로 늘리지 않는다. 실제 출금은 미할당부터 줄어든 것으로 보고, 총 할당액 아래로 내려가면 부족 상태.
- 목표에 담아도 총자산·소득·저축 실적이 늘지 않는다. 거래 테이블에 가짜 이체 행을 만들지 않는다.
- PAUSED·ACHIEVED 목표의 자금은 자동으로 풀지 않는다. 담기(할당 증가)는 편집 가능한 SAVING에만, 꺼내기(할당 감소·0)는 PAUSED·ACHIEVED 목표에서도 소유자가 가능(확정). 목표 삭제 시 할당·연결을 같은 트랜잭션에서 해제하고 이력은 보존.

## 3. 예·적금 연결

- 예·적금 잔액 전체를 연결 목표의 확보액에 합산. 적금 납입으로 잔액이 늘면 목표에도 반영. 정기예금은 예치 원금 전액, 미발생 이자 미포함.
- 다른 목표 중복 연결은 기존처럼 409. 최초 연결 0행 상태의 동시 요청도 차단.
- 이름·표시 목적을 바꿔도 연결·할당·계산 정책은 불변.

## 4. API (확정)

경로·필드는 BE 구현 기준이다. FE 인계 후 명세 12 반영은 별도 보고한다.

### 예·적금 연결 (기존 `GET/PUT /goals/{goalId}/accounts`)

- PUT은 SAVING 목표의 **예·적금** 연결 목록 교체로 한정, Body `accountIds[]` 유지. 빈 배열은 예·적금 연결만 해제하고 입출금 할당은 건드리지 않는다.
- 입출금을 PUT으로 연결하던 클라이언트(현재 없음, remaining-work 509 절)는 명시적 검증 오류로 새 API를 안내한다.
- GET 응답의 계좌 항목에 `balance / allocatedToThisGoal / allocatedElsewhere / availableToAllocate / shortfallAmount / allocationStatus`를 추가한다. 담기 화면은 이 응답만으로 구성하며 별도 할당 가능액 조회 API는 두지 않는다.

### 입출금 담기·꺼내기

절대값 PUT 하나로 담기·꺼내기를 모두 처리한다.

```
PUT /api/v1/goals/{goalId}/allocations/{accountId}   { "amount": 300000 }
```

- 재시도·중복 탭이 자연히 멱등. 꺼내기는 더 작은 값, 0은 행 삭제. `requestId`·멱등 테이블은 두지 않는다.
- 검증은 `amount ≤ balance − (다른 목표 할당 합)` 하나. 잠금은 사용자 행 하나(`SimulationUserLock`과 같은 방식)로 충분.
- 변동 이력은 기존 `goal_history` `ACCOUNTS_UPDATED`로 남긴다. 별도 할당 이력 테이블은 두지 않는다.
- 기존 `PUT /goals/{goalId}/accounts`(교체 의미)와 패턴이 같다.
- A→B 직접 이동은 제공하지 않는다. 필요하면 양쪽을 한 트랜잭션으로 처리하는 별도 계약.

오류: 401, 타인·없는·삭제 목표/계좌 404, 유형(입출금 아님·SPENDING)·입력(0 미만·소수·null) 400, 종료 목표에서 할당 증가 `GOAL_NOT_EDITABLE` 409(감소·0은 허용), 할당 가능액 초과 신규 `GOAL_ALLOCATION_EXCEEDS_AVAILABLE` 409. 기존 공통 응답·`ErrorCode` 사용.

## 5. DB·트랜잭션·기존 자료

- `goal_account.contribution_amount`: 입출금은 누적 할당액, 예·적금은 null(전액). 비율 컬럼 없음. PK·복합 FK 유지.
- `uk_goal_account_account`를 예·적금에만 적용: 새 Flyway `V35__goal_account_allocation.sql`에서 UNIQUE를 DROP하고 생성 컬럼 `exclusive_account_id = IF(account_type IN ('DEPOSIT','SAVINGS'), account_id, NULL)` + UNIQUE를 추가한다. H2 테스트 스키마는 `GENERATED ALWAYS AS (CASE WHEN ...)` 방언으로 따로 맞추고 `*MySqlIntegrationTest`로 실측한다. V8은 수정하지 않는다.
- **기존 자료 전환 없음(확정)**: 입출금 `contribution_amount NULL = 전액(구 방식)`으로 읽고 사용자가 처음 담기/꺼내기를 할 때 숫자로 바뀐다. 데이터 마이그레이션 SQL을 두지 않는다.
- **seed**: [DODAM_GOAL_SEED.sql](../database/seeds/DODAM_GOAL_SEED.sql) line 16-17의 DEMAND_DEPOSIT 링크 1,750건(`contribution_amount NULL`)은 위 규칙으로 그대로 유효하다. 입출금 non-null CHECK를 추가하지 않는다. seed는 625가 작업 중이므로 덮어쓰지 않는다.
- **revision 컬럼 없음(확정)**: 할당 변경 뒤 같은 날 시뮬레이션 결과가 재사용되지 않도록 `SimulationRequestHash` 재사용 판단에 사용자 목표 할당 상태(최신 `goal_history` id 또는 할당 변경 시각)를 섞는다. `user_setting`·`goal`에 컬럼을 추가하지 않는다.
- 잠금 순서: 사용자 행 → 계좌 ID 오름차순 → 목표 ID 오름차순. 시뮬레이션 사용자 잠금·계좌 동기화와 교착·격리 수준 대조.
- 한 트랜잭션: 소유권·SAVING·상태 확인 → 최신 잔액·총 할당액 검증 → 할당 갱신 → 목표 확보액 갱신 → `goal_history` → revision 증가. 실패 시 전부 롤백.
- 공유 입출금이 생긴 뒤 구버전 앱 서버가 잔액 전체를 중복 합산하지 않도록 배포 순서를 문서화한다.

## 6. 목표·진단·추천·시뮬레이션 계산

신규 `GoalAllocationReader/Calculator`에서 확보액 정의를 한 번 두고 공유한다.

- 목표 확보액 = 전액 연결 예·적금 잔액 합 + 그 목표에 할당된 입출금 금액 합.
- 목록·요약·상세·진행률·필요 월 저축액·진단·시뮬레이션이 같은 값을 쓴다. [SimulationBaselineReader](../src/main/java/com/ssafy/dodam/domain/simulation/service/SimulationBaselineReader.java) line 110-123은 지금 연결 계좌 잔액을 직접 합산하므로 반드시 바꾼다.
- 총자산은 원본 계좌 기준으로 한 번만 합산. 저장된 과거 진단·추천·시뮬레이션은 당시 결과로 보존.
- 공유 입출금이 생기면 저축 시뮬레이션의 `accountNo`만으로 목표 하나를 특정할 수 없다. 계산기 술어(`SavingChangeCalculator` `g.linkedAccountIds().contains(accountId)`)가 이미 연결된 목표 전체를 고르므로 **`accountNo` 지정 시 그 계좌에 담긴 목표 전체가 대상**이라는 규칙으로 확정하고 simulation.md에 명시한다. 목표 하나만 원하면 619 `goalId`를 쓴다.
- 월 계획액 저장 UI/API, 거래 기반 저축속도 개편, 예측 엔진 변경은 범위 밖. "할당 증가"를 저축 실적으로 간주하는 코드를 만들지 않는다.

## 7. 실제 출금 후 부족 (확정: 필드만 추가)

확정된 정책: 설정액 유지, 부족 표시, 자동 차감 없음, 실제 은행 출금을 차단할 수 있다고 표현하지 않음.

예: 잔액 100만, A 30만·B 20만 담김. 실제 출금으로 잔액 40만이 되면 설정액은 그대로 두고 "이 계좌의 목표 할당이 총 10만 원 부족"을 표시. A에서 10만 꺼내면 해소(추가 할당 가능액 0). 조정 없이 잔액이 50만 이상으로 돌아와도 해소.

공통 동작: 부족 계좌의 추가 할당 가능액 0, 신규 담기 409, 꺼내기·삭제 해제 허용, 부분 꺼내기 허용. 부족액은 계좌 단위이며 목표별로 합산하지 않는다.

확보액은 설정액 기준으로 그대로 계산하고, 계좌·목표 응답에 `shortfallAmount`(계좌 단위)와 `allocationStatus`만 추가한다. `allocationStatus`는 `OK | NEEDS_ADJUSTMENT`. 총자산은 원본 계좌로 합산하므로 자산 부풀림은 없다. 대신 부족 중에는 진행률이 실제보다 높게 보일 수 있으므로 FE가 부족을 함께 표시한다. 목표 `currentAmount`·진행률에 null 전파는 하지 않는다.

또한: 부족 상태 발생·해소는 금융 데이터 변화이므로 진단·시뮬레이션 재사용 전 최신 부족 여부를 확인하고(§5 revision), 확정할 수 없는 금액을 0원·비례값으로 이력에 저장하지 않는다.

## 8. 구현 순서와 MR 분할

| 단계 | 작업 | 주요 대상 |
| --- | --- | --- |
| 0 | ~~기획 변경 확정, §4·§5·§7 대안 선택~~ (2026-09-16 완료). Jira·명세 12·ERD 변경은 보고 | 팀 결정 |
| 1 | 할당 계약·기존 자료 점검 | 응답 DTO, FE 담기/꺼내기 계약, 부족 상태 표현 |
| 2 | DB·모델·읽기 계산 | GoalAccount, GoalAccountRepository, Flyway, `GoalAllocationReader/Calculator` |
| 3 | 예·적금 연결 한정·입출금 담기/꺼내기 | GoalAccountService, 신규 할당 서비스·Controller·DTO, 잠금, ErrorCode |
| 4 | 계산·이력 일관성 | GoalService, GoalCalculationService, GoalHistoryRecorder, DiagnosisEvaluator, 캐시·알림 소비 경로 |
| 5 | 시뮬레이션·추천 연동 | SimulationBaselineReader, SavingChangeCalculator 대상 규칙(§6), 지문 revision |
| 6 | 회귀·문서·FE 인계 | MySQL·H2·전체 빌드, 시연 예제, goal.md·simulation.md·recommendation-api.md·diagnosis-api.md·remaining-work.md |

MR 분할(검토 단위):

- **MR-A** (단계 2): V35 + `GoalAllocationReader` + `GoalCalculationService`·`SimulationBaselineReader`·`GoalAccountService`의 잔액 합산을 Reader로 교체 + GET `/accounts` 필드 확장. 동작 변화 없음(NULL=전액).
- **MR-B** (단계 3·4): `PUT /goals/{goalId}/allocations/{accountId}` + `PUT /accounts` 예·적금 한정 + `ErrorCode` + 부족 필드 + 이력.
- **MR-C** (단계 5·6): 시뮬레이션 재사용 지문·`accountNo` 대상 규칙 문서화·문서·seed 정합.

625와 섞지 않는다.

## 9. 검증·완료 기준

| 검증군 | 필수 사례 |
| --- | --- |
| 예·적금 | SAVING에 예금+적금 여러 개 연결 + 입출금 할당 병행, 다른 목표 중복 409, 이름 변경 시 연결 유지 |
| 입출금 | 100만 → A 30만 → B 20만 → 미할당 50만, A 10만 해제 → 60만, 실제 잔액 불변 |
| 경계값 | 1원·잔액 전액·0/음수/소수·초과, 할당액 초과 꺼내기, 0원 계좌, 빈 연결 |
| 멱등·동시성 | 같은 PUT 반복 시 결과 불변, 두 목표 동시 할당에서 잔액 초과 방지, 예·적금 중복 연결 경쟁, 실패 시 롤백 |
| 소유권·상태 | 401, 타인·없는 목표/계좌 404, SPENDING·MINUS·해지 계좌 400, PAUSED·종료 목표, 삭제 시 해제·이력 보존 |
| 부족 | 설정액 유지, 신규 담기 거절·부분 꺼내기 허용, 수동 해제·실제 입금으로 복구, 계좌 부족액 중복 합산 방지 |
| 계산 일관성 | 목표/진단/시뮬레이션 동일 확보액, 총자산 중복 없음, 목록 필터·페이지 무관 |
| 전환 | V8 기존 자료·NULL 기여액 점검, 조건부 UNIQUE MySQL 검증, seed·fixture·schema validate |

순서: 순수 계산기 → Goal API → Diagnosis/Recommendation/Simulation → MySQL 8.0 마이그레이션·동시성 → 전체 `gradlew.bat test`·`clean build` → `git diff --check`. H2 통과를 MySQL 검증으로 대신하지 않는다.

완료 조건: 같은 SAVING 목표에 여러 예·적금 전액 연결 + 입출금 할당 병행, 입출금은 남은 돈 범위에서 여러 목표에 담고 꺼낼 수 있으며, 모든 계산·추천·시뮬레이션에서 같은 돈이 중복되지 않는다. 루트 기획안·ERD·명세 12 변경은 사용자 요청 시 수행한다.
