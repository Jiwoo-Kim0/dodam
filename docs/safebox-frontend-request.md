# 목표 연결 계좌 API 변경 — FE 회귀 수정 요청

> ⛔ **폐기(2026-09-23).** 이 문서의 요청은 [goal-transfer-plan.md](goal-transfer-plan.md)로 대체되었습니다.
> 여기 적힌 "입출금은 목표에 연결할 수 없다"와 계좌 필터 수정 요청 2건은 **더 이상 유효하지 않습니다.**
> 목표 통장 이체 방식으로 바뀌면서 입출금 연결이 다시 허용됩니다. 기록으로만 남깁니다.

작성일: 2026-09-23. BE 이슈: S15P21D105-703. 대상: FE 담당.
배포 커밋: `389f544` [S15P21D105-703] [BE] Feat: 목표 담기·꺼내기 API와 연결 계좌 예·적금 한정

---

## 0. 요약

**목표에 연결할 수 있는 계좌가 예·적금으로 좁아졌습니다.** 목표 만들기와 목표 상세의 계좌 선택이
입출금 계좌를 그대로 보내고 있어 **지금 실패합니다.** 필터 수정 두 곳이 필요합니다.

같은 배포에 담기·꺼내기 API(`PUT /allocations`)도 들어갔지만 **화면은 만들지 않기로 했습니다**(§4).
FE에 요청드리는 것은 아래 회귀 수정뿐입니다.

---

## 1. 무엇이 바뀌었나

`PUT /v1/goals/{goalId}/accounts`

| | 이전 | 지금 |
| --- | --- | --- |
| 받는 계좌 유형 | 입출금·예금·적금 | **예금·적금만** |
| 입출금 id를 보내면 | 정상 연결 | **400** `INVALID_REQUEST` |
| 빈 배열 | 전체 해제 | 예·적금만 해제 |

오류 메시지: `예금·적금 계좌만 연결할 수 있습니다. 입출금 계좌는 담기·꺼내기를 이용해 주세요.`

**왜 바꿨나.** 입출금 계좌를 목표에 연결하면 그 **잔액 전체**가 목표의 "모인 금액"이 됐습니다.
시연 계정에서 주거래 통장 1,746만원이 통째로 잡혀 진행률이 98%로 보이던 것이 이 때문입니다.
이제 목표의 모인 금액은 예·적금 기준이고, 입출금 잔액은 섞이지 않습니다.
입출금 거래는 저축속도(예상 달성일) 계산에는 계속 쓰입니다.

---

## 2. 수정할 곳 두 군데

두 화면 모두 계좌 선택 목록에서 `MINUS`와 `CLOSED`만 걸러내고 **입출금을 그대로 포함**합니다.

### 2-1. 목표 만들기 — 피해가 가장 큽니다

[frontend/src/pages/goalSetup/GoalSetupScreen.tsx:110-113](../frontend/src/pages/goalSetup/GoalSetupScreen.tsx#L110-L113)

```ts
setAvailableAccounts(
  accounts.filter(
    ({ accountType, status }) =>
      accountType !== 'MINUS' && status !== 'CLOSED',
  ),
);
```

**증상**: 목표 생성 → 계좌 연결 400 → catch의 롤백이 `deleteGoal`을 호출합니다
([같은 파일 L322-L330](../frontend/src/pages/goalSetup/GoalSetupScreen.tsx#L322-L330)).
연결만 실패하는 게 아니라 **방금 만든 목표까지 지워지고** 사용자는 "목표를 만들지 못했어요"만 봅니다.
입출금을 하나라도 고르면 목표 생성 자체가 불가능합니다.

### 2-2. 목표 상세 — 연결 계좌 변경

[frontend/src/pages/goalDetail/hooks/useGoalDetail.ts:181-184](../frontend/src/pages/goalDetail/hooks/useGoalDetail.ts#L181-L184) (선택 목록),
[frontend/src/pages/goalDetail/GoalDetailScreen.tsx:53-70](../frontend/src/pages/goalDetail/GoalDetailScreen.tsx#L53-L70) (저장)

**증상**: 이미 입출금이 연결된 목표에서 계좌 편집을 열면 그 계좌가 체크된 채로 뜹니다.
**아무것도 바꾸지 않고 저장만 해도 400**입니다.

### 2-3. 수정 방향

- 필터를 `DEPOSIT`·`SAVINGS`만 허용으로 바꿉니다.
- 입출금은 목록에서 빼거나, 비활성으로 두고 "목표에는 예·적금만 연결할 수 있어요" 안내를 붙입니다.
- 목표 상세에서 이미 연결된 입출금이 있으면 편집 목록에 넣지 않습니다(서버가 요청 목록에 없어도 유지합니다).

### 2-4. 확인 방법

1. 목표 만들기에서 입출금 계좌를 선택 → 저장. 지금은 목표가 만들어지지 않습니다.
2. 입출금이 연결된 목표 상세 → 계좌 편집 → 그대로 저장. 지금은 400입니다.

---

## 3. 화면 표시에 관한 변화

목표의 `currentAmount`가 예·적금 기준으로 바뀌므로 **기존 목표의 진행률이 내려갑니다.**
버그가 아니라 통장 잔액이 섞여 있던 것이 빠지는 것입니다. 별도 대응은 필요 없습니다.

`GET /v1/goals/{goalId}/accounts`의 응답 형태와 필드는 바뀌지 않았습니다.

---

## 4. 담기·꺼내기 화면은 만들지 않습니다

`PUT /v1/goals/{goalId}/allocations/{accountId}`가 배포돼 있지만 **화면 구현 요청은 철회합니다.**

실제 이체 없이 표시만 하는 방식이라, 사용자가 그 통장에서 돈을 쓰면 "담아둔 금액"이 곧바로 거짓이 됩니다.
그걸 가리려면 보정 장치를 계속 쌓아야 해서 기능을 빼기로 했습니다.

이전에 전달드린 [backend/docs/safebox-fe-handover.md](../backend/docs/safebox-fe-handover.md)의
담기 화면 요청은 **무효**입니다. 그 문서는 기록으로만 남깁니다.

---

## 5. 참고

- BE 계약: [backend/docs/goal.md](../backend/docs/goal.md) §연결 계좌
- 중단 결정 배경: [backend/docs/safebox-allocation-plan.md](../backend/docs/safebox-allocation-plan.md) 상단
