## 📌 작업 목적
- 626이 만든 `allocatedToThisGoal`·`availableToAllocate`·`shortfallAmount`는 응답에 나가지만 값을 바꿀 입구가 없어 사실상 죽어 있었다. `goal_account.contribution_amount`가 전 행 NULL이라 "입출금 잔액 전체가 목표에 모인 돈"이 예외가 아니라 상시 경로였다(U0003 진행률 98%, 확보액 2,956만 중 1,746만이 주거래 통장 잔액 전액).
- 세이프박스(통장 안의 목표별 가상 공간) 2단계다. 사용자가 통장에서 **담을 금액을 직접 정하면** 그 금액만 확보액에 들어간다. 계획은 [safebox-allocation-plan.md](backend/docs/safebox-allocation-plan.md) §4-1·§4-2.

## 🛠️ 작업 내용

**담기·꺼내기 `PUT /goals/{goalId}/allocations/{accountId}` (`GoalAccountService.updateAllocation`)**
- Body는 증감이 아니라 **절대값** `{ "amount": 300000 }` 하나다. 현재 값보다 크면 담기, 작으면 꺼내기, 0이면 할당 행 삭제. 재시도·중복 탭이 자연히 멱등이라 `requestId`나 멱등 테이블을 두지 않았다(626 §4 확정).
- 검증은 `amount ≤ max(0, balance) − 다른 살아 있는 목표의 할당 합` **하나**다. 그 계좌를 담은 다른 목표가 soft delete됐으면 세지 않는다(확보액 정의와 같은 기준).
- 종료 목표(`ACHIEVED`·`CANCELED`)는 **늘리기만** 409 `GOAL_NOT_EDITABLE`로 막는다. 이미 담아 둔 돈을 빼지 못하면 사용자가 자금을 되찾을 길이 없다(626 §2).
- 한도 초과는 409 `GOAL_ALLOCATION_EXCEEDS_AVAILABLE`(신규). 실제 출금으로 잔액이 줄어 `NEEDS_ADJUSTMENT`가 된 계좌는 담을 수 있는 금액이 0이라 증가가 막히고 꺼내기는 허용된다 — 사용자가 스스로 조정하게 두는 626 §7 정책이다.

**`PUT /goals/{goalId}/accounts` 예·적금 한정 (§4-2)**
- 입출금이 오면 400으로 담기·꺼내기를 안내한다. 예·적금은 계좌 하나가 목표 하나에 전액 연결되므로 나눠 담을 금액이라는 개념이 없다.
- 빈 배열은 **예·적금만** 해제하고 입출금 할당은 건드리지 않는다. 기존 집합 비교(`setChanged`)도 예·적금 링크만 기준으로 한다.

**동시성 (`GoalAllocationUserLock`)**
- 입출금 한 계좌를 여러 목표가 공유할 수 있어, 할당 가능액 검증과 저장 사이에 다른 요청이 끼면 총 할당액이 잔액을 넘을 수 있다. 연결·할당 변경 **두 경로 모두** 사용자 행을 먼저 비관적 잠금으로 잡는다. 계좌·목표 수와 무관하게 잠금 순서가 일정해 교착도 피한다.

**문서**
- [goal.md](backend/docs/goal.md): 담기·꺼내기 절 신설, `PUT /accounts` 계약 수정, `ACCOUNTS_UPDATED`·`AMOUNT_UPDATED` 출처 갱신.
- 확보액 정의에 **"담은 뒤에는 잔액 변화가 확보액에 자동 반영되지 않는다"**를 명시했다. 담은 금액이 절대값이라 잔액이 늘어도 더 담기지 않고, 줄면 확보액은 그대로인 채 `NEEDS_ADJUSTMENT`로만 드러난다. 잔액을 따라 움직이는 것은 `contribution_amount`가 null인 예·적금과 아직 담기를 하지 않은 입출금 행뿐이다.

## 🔍 리뷰 포인트
- **"담은 뒤 잔액 변화가 반영되지 않는다"가 의도한 제품 동작인지.** 이 MR의 핵심 계약 변화이고, 기존 통합 테스트 2건이 여기서 깨져 시나리오를 예·적금으로 옮겼다(아래 테스트). 잔액이 늘었을 때 자동으로 더 담지 않는 쪽이 "사용자가 정한 금액"이라는 세이프박스 정의에 맞다고 봤다.
- **`availableToAllocate` 계산에서 삭제된 목표의 할당을 제외한 것.** 확보액과 같은 기준이지만, soft delete된 목표에 담긴 돈이 조용히 풀린다는 뜻이기도 하다.
- **사용자 행 잠금의 범위.** 목표·계좌가 아니라 사용자 단위라 같은 사용자의 연결 변경까지 직렬화된다. 교착을 없애는 대신 동시성을 버린 선택이다.
- **`replaceAccounts`의 예·적금 한정 전환.** 기존 FE가 입출금이 섞인 목록을 그대로 PUT하면 이제 400이다. 명세 12 변경 보고와 FE 인계가 선행 조건이다.

## ✅ 테스트
- [x] 빌드 성공
- [x] 기능 테스트 완료

### 테스트 내용
- `./gradlew test` 전체 **1,358건 통과**(실패 0, 스킵 25, 3분 15초). `git diff --check` 이상 없음.
- `GoalAccountApiIntegrationTest` 신규 5건: 절대값 PUT과 멱등, 한 계좌를 두 목표에 나눠 담기와 할당 가능액 상한(409), **동시 담기 2건이 잔액을 넘지 않음**(스레드 2개·`containsExactlyInAnyOrder(200, 409)`·합계 검증), 예·적금 거부와 종료 목표에서의 꺼내기 허용, 입력·소유권·상태·인증 검증.
- `GoalCalculationApiIntegrationTest` / `GoalHistoryApiIntegrationTest`: "연결 후 잔액 동기화" 시나리오를 예·적금 계좌로 옮겼다. 입출금은 담은 금액이 절대값이라 잔액이 늘어도 확보액이 그대로이기 때문이다. 이력 테스트는 예금 연결(0원·속도 0)과 담기(200만)가 각각 `ACCOUNTS_UPDATED` 한 건을 남기는 6건 시나리오로 바꿨고, `AMOUNT_UPDATED`는 예금 잔액 변화로 계속 검증한다.
- **미검증**: 동시 담기 409는 H2로만 확인했다. 사용자 행 잠금과 V35 조건부 UNIQUE의 실제 동작은 MySQL 8.0 실측이 남아 있다(H2에는 조건부 UNIQUE가 없다).
- **병합 시점 주의**: 시연 계정의 확보액·진행률이 움직인다. 계획 §6대로 **리허설 후** 병합 대상이다.

## 📎 참고
- 이슈: S15P21D105-703 (MR 1 `7d3f9b8`에 이어지는 같은 이슈의 두 번째 MR)
- 구현 계획: [safebox-allocation-plan.md](backend/docs/safebox-allocation-plan.md) §4-1·§4-2·§6
- 선행 계약: [goal-allocation-626-plan.md](backend/docs/goal-allocation-626-plan.md) §2·§4·§7
- 계약 문서: [goal.md](backend/docs/goal.md) §확보액 정의, §담기·꺼내기, §이력
- 후속: MR 3 이동 이력(V45)·저축속도 정의 교체, MR 4 꺼내기 영향 미리보기
- **보고 필요**: 명세 12의 `PUT /accounts`가 예·적금 한정으로 바뀌고 `/allocations`가 생긴다. 루트 `docs/` 수정은 하지 않았다.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
