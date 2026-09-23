# 목표 통장 이체 구현 계획

작성일: 2026-09-23. 상태: **착수 대기 — B안 확정.**

세이프박스(가상 담기)를 접고([safebox-allocation-plan.md](safebox-allocation-plan.md) 상단 중단 기록),
**SSAFY 금융망의 실제 계좌 이체**로 목표 저축을 구현한다. 사용자가 월급통장에서 목표 통장으로 돈을 옮기면
그 통장 잔액이 곧 목표의 모인 금액이 된다.

가상 세이프박스가 못 지킨 "담아둔 돈은 빠져나가지 않는다"를 **실제로** 지킨다. 목표 통장 돈을 쓰려면
역이체라는 의식적 행동이 필요하다.

### 확정된 결정 (2026-09-23, 정현진)

| 항목 | 결정 |
| --- | --- |
| 방식 | 금융망 실제 이체(2.4.10). 가상 담기 아님 |
| 시연 데이터 | **B안 — U0003의 주거래 통장을 금융망 실계좌로 교체.** 별도 계정(A안)이 아니라 기존 시연 서사를 유지한다 |
| `source_type` | 시연 계좌는 **`SEED` 유지**. 동기화가 SEED를 건드리지 않는 것이 안전장치다(§1-2) |
| 잔액 반영 | 금융망 이체 성공 후 **도담 DB를 직접 갱신**한다. 동기화를 기다리지 않는다 |
| 거래 타입 | 입금 `INCOME`, 출금 `EXPENSE`. `TRANSFER`를 쓰지 않는다(§4) |
| **목표 통장 개수** | **목표당 최대 1개(2026-09-23 확정).** 입출금을 여러 개 연결하면 "목표 통장"이라는 개념이 흐려지고 FE가 받는 곳을 고르게 해야 한다. V35는 예·적금만 배타 제약이므로 **서비스 레벨 검증으로 막는다 — 마이그레이션 불필요** |
| 확보액·속도 식 | **바꾸지 않는다.** 목표 통장 잔액 전액이 확보액이 되므로 기준선·안분·담기가 모두 불필요 |

---

## 0. 확인된 사실

### 0-1. 금융망 API (사용자 제공 명세, 2026-09-23)

| API | 용도 |
| --- | --- |
| 2.4.2 `inquireDemandDepositList` | 수시입출금 상품 조회 → `accountTypeUniqueNo` |
| 2.4.1 `createDemandDeposit` | 상품이 없을 때만 1회 등록(기관 단위, userKey 없음) |
| 2.4.3 `createDemandDepositAccount` | 계좌 생성. 상품 고유번호 필요 |
| 2.4.9 `updateDemandDepositAccountDeposit` | 시연용 잔액 주입 |
| 2.4.10 `updateDemandDepositAccountTransfer` | **이체.** 출금·입금 계좌 모두 금융망 계좌여야 함 |
| 2.4.7 `inquireDemandDepositAccountBalance` | 복구·검증용 단건 잔액 조회 |

잔액 부족은 `A1014`, 이체 한도 초과는 `A1016`(1회)·`A1017`(1일)다. 한도 기본값은 1회 1억·1일 5억이지만
**계좌마다 다를 수 있다**(2.4.4 응답의 `oneTimeTransferLimit`·`dailyTransferLimit`). 안내 문구에 고정값을 쓰지 않는다.

### 0-2. 우리 코드에서 이미 되어 있는 것

- [SsafyHeaderFactory](../src/main/java/com/ssafy/dodam/infrastructure/external/ssafy/SsafyHeaderFactory.java)가
  `institutionTransactionUniqueNo`(전송일자+시각+6자리 난수)를 만든다. 쓰기 API에 그대로 쓸 수 있다.
- [FinanceConnectionService](../src/main/java/com/ssafy/dodam/domain/finance/service/FinanceConnectionService.java)가
  `connect(userId)`로 금융망 회원을 만들고 userKey를 암호화 저장한다. `requireConnected` + `userKeyOf`로 꺼낸다.
- 계좌번호는 `FieldCryptor`(암호문) · `SensitiveNumberHasher`(해시) · `AccountNumberMasker`(마스킹) 세 경로가 있다.
- `transaction_type`에 `INCOME`·`EXPENSE`·`TRANSFER`가 이미 있다(V6 `chk_transaction_type`).

### 0-3. 시연 계정의 현재 상태

- U0001~U0004 계좌는 `source_type = 'SEED'`다. **금융망에 존재하지 않는다.**
- seed가 `finance_connection` 행을 만들지 않는다. **금융망 회원(userKey)도 없다.**
- 거래는 2026-03-01 ~ 2026-08-31 6개월치다.

---

## 1. 안전장치 — 왜 B안이 위험하지 않은가

### 1-1. `account_id`가 유지되면 모든 파생 데이터가 살아 있다

거래·목표 연결·진단·추천은 전부 `account_id`를 참조한다. 계좌번호(`account_no_*`)만 금융망 계좌로 갈아끼우고
`account_id`를 그대로 두면 6개월 거래 이력과 시연 서사가 손상되지 않는다.

### 1-2. 동기화가 SEED 계좌를 건드리지 않는다

[AccountSyncHandler](../src/main/java/com/ssafy/dodam/domain/account/service/AccountSyncHandler.java)의 규칙이다.

> `source_type`이 SEED·MANUAL인 계좌는 계좌도 거래도 덮어쓰지 않는다. 시연 데이터와 마이너스통장 상환(534) 결과를 보존한다.

계좌 대응은 `(user_id, account_no_hash)`로 한다. 금융망 계좌번호로 바꿔도 `source_type`이 SEED면
`existing.isFromFinance()`가 false라 **스냅샷도 거래도 무시된다.** 시연 중 동기화가 돌아도 잔액이 덮이지 않는다.

그래서 **`source_type`은 SEED로 유지한다.** 도담 DB 갱신은 이체 서비스가 직접 한다.

---

## 2. 금융망 어댑터 확장

[SsafyFinanceClient](../src/main/java/com/ssafy/dodam/infrastructure/external/ssafy/SsafyFinanceClient.java)에 추가한다.

| 메서드 | API | 쓰이는 곳 |
| --- | --- | --- |
| `inquireDemandDepositList()` | 2.4.2 | 시연 준비(상품 확보) |
| `createDemandDeposit(bankCode, name, desc)` | 2.4.1 | 상품이 없을 때만 |
| `createDemandDepositAccount(userKey, typeNo)` | 2.4.3 | 시연 준비, 향후 목표 통장 개설 |
| `depositToAccount(userKey, accountNo, amount, summary)` | 2.4.9 | **시연 준비 전용** |
| `transfer(userKey, withdrawalNo, depositNo, amount, summaries)` | 2.4.10 | 이체 API |
| `inquireAccountBalance(userKey, accountNo)` | 2.4.7 | 실패 복구·검증 |

- 기존 `post`·`extractRec`·`convert` 패턴과 `SsafyHeaderFactory`를 그대로 쓴다.
- 오류는 기존대로 `SsafyApiException`/`SsafyUnavailableException`으로 던지고, 서비스에서
  `FINANCE_API_ERROR`/`FINANCE_API_UNAVAILABLE`로 바꾼다.
- **계좌번호 원문을 로그·예외 메시지에 남기지 않는다.** 기존 동기화 핸들러와 같은 규칙이다.

### 2-1. 함께 추가해야 하는 것

| | 내용 |
| --- | --- |
| 응답 record | 계좌 생성(`REC` 객체), 이체(`REC` **배열 2건** — 출금·입금), 입금, 잔액 조회. `SsafyDemandDepositAccount` 패턴을 따른다 |
| **`SsafyErrorCode` 보강** | 현재 enum에 `H****`·`E****`만 있고 **`A1014`(잔액 부족)·`A1016`/`A1017`(이체 한도)가 없다.** §3-2의 409 분기를 하려면 추가해야 한다. `SsafyApiException.is(code)`로 판별한다 |

`SsafyFinanceClient`는 인터페이스이므로 메서드를 추가하면 테스트 스텁·목도 함께 손봐야 한다.

---

## 3. 도담 이체 API

```
POST /api/v1/accounts/transfer
{ "withdrawalAccountId": 11, "depositAccountId": 12, "amount": 500000 }
```

기존 [AccountController](../src/main/java/com/ssafy/dodam/domain/account/controller/AccountController.java)가
`/api/v1/accounts`에 매핑돼 있고 FE `assetApi`도 `/v1/accounts`를 쓴다. **경로 관례에 맞으므로 이 컨트롤러에 추가한다.**

### 3-1. 처리 순서

1. 인증 사용자 확인(`AuthenticatedUserIdResolver`), 두 계좌의 **소유권**과 `DEMAND_DEPOSIT`·비CLOSED 확인
2. 같은 계좌면 400, 금액이 0 이하면 400
3. `FinanceConnectionService.requireConnected(userId)`로 userKey 확보
4. **금융망 이체 호출**(트랜잭션 밖)
5. 성공하면 한 트랜잭션에서 도담 DB 갱신 — 출금계좌 `balance -= amount`, 입금계좌 `balance += amount`,
   거래 2건 INSERT(§4), `last_transaction_date` 갱신

⚠️ **`Account`에 잔액을 바꾸는 메서드가 없다.** 지금은 `applyFinanceSnapshot(FinanceSnapshot, now)` 하나뿐이고
스냅샷 전체를 덮어쓰는 용도다. 이체용으로 `withdraw(amount, now)`·`deposit(amount, now)`를 엔티티에 추가한다.
`chk_account_balance_by_type`이 `DEMAND_DEPOSIT`은 `balance >= 0`을 요구하므로 출금 시 음수 방지를 엔티티에서 막는다.

금융망 호출을 트랜잭션 밖에 두는 것은 `AccountSyncHandler`가 이미 지키는 계약이다(외부 호출과 저장을 섞지 않는다).

### 3-1-1. 동시성

도담 `balance`를 직접 갱신하므로 같은 계좌로 이체가 동시에 오면 잔액이 어긋난다. **§3-1의 5단계
(도담 DB 갱신 트랜잭션)에 진입하기 전에 사용자 행을 비관적 잠금으로 잡는다.** MR 2가 만든
[GoalAllocationUserLock](../src/main/java/com/ssafy/dodam/domain/goal/repository/GoalAllocationUserLock.java)이
정확히 그 용도이므로 **되돌릴 때 이것만 남겨 재사용한다**(§6). 계좌가 아니라 사용자 단위로 잡아야
두 계좌를 동시에 다루면서 교착을 피한다.

⚠️ 다만 이 클래스는 **goal 도메인 repository**인데 이체는 account 도메인이다. 이름도 `GoalAllocation...`이라
오해를 부른다. **`global/`로 옮기고 `UserWriteLock` 류로 개명한다** — 여러 도메인이 공유하는 정책은
`global/`에 둔다는 규칙에 맞춘다.

### 3-1-2. 응답 본문

```json
{ "withdrawalAccountId": 11, "withdrawalBalance": 16960000,
  "depositAccountId": 12, "depositBalance": 500000, "transferredAmount": 500000 }
```

갱신된 두 잔액을 돌려준다. 목표 진행률은 담지 않는다 — 계산 API가 따로 있고 여기서 중복 계산하면 계약이
갈라진다. **FE는 목표 상세를 재조회한다.**

### 3-2. 오류

| 상황 | HTTP / 코드 |
| --- | --- |
| 미인증 | 401 |
| 타인·없는 계좌 | 404 `RESOURCE_NOT_FOUND` (존재를 드러내지 않음) |
| 같은 계좌, 금액 0 이하, 입출금 아님, CLOSED | 400 `INVALID_REQUEST` |
| 금융망 미연결 | 409 `FINANCE_NOT_CONNECTED` |
| 잔액 부족 `A1014` | 409 (신규 `TRANSFER_INSUFFICIENT_BALANCE`) |
| 이체 한도 초과 `A1016`·`A1017` | 409 (신규 `TRANSFER_LIMIT_EXCEEDED`) |
| 금융망이 오류를 응답 | **502** `FINANCE_API_ERROR` |
| 금융망에 닿지 못함(통신 실패·타임아웃) | **503** `FINANCE_API_UNAVAILABLE` |

### 3-3. 멱등성 — 앞선 판단 정정

`institutionTransactionUniqueNo` 중복은 금융망이 `H1007`로 거절하지만, **`SsafyHeaderFactory`가 호출마다 난수로
새 번호를 만든다.** 따라서 같은 요청을 두 번 보내면 **두 번 다 성공해 이중 이체가 된다.** 은행이 막아주지 않는다.

시연 범위 대응: 짧은 창(예: 10초) 안의 동일 `(userId, 출금계좌, 입금계좌, 금액)` 요청을 409로 거절하고,
FE는 전송 중 버튼을 비활성화한다. 제품으로 가려면 요청 ID 기반의 결정론적 `institutionTransactionUniqueNo`가 필요하다.

### 3-4. 부분 실패

금융망 이체가 성공한 뒤 도담 저장이 실패하면 **돈은 이미 옮겨진 상태**다. 보상 이체(역이체)는 다시 실패할 수 있어
자동으로 하지 않는다. 실패를 `ERROR` 로그로 남기고, 다음 조회 때 2.4.7 잔액 조회로 맞춘다.
**시연 전 리허설에서 이 경로가 나오지 않는지 확인한다.**

---

## 4. 거래 기록 규칙

[JdbcGoalPaceReader:59](../src/main/java/com/ssafy/dodam/domain/goal/repository/JdbcGoalPaceReader.java#L59)가
순현금흐름을 이렇게 계산한다.

```sql
CASE WHEN entry.transaction_type = 'INCOME' THEN entry.amount ELSE -entry.amount END
```

**`INCOME`만 +다.** 목표 통장 입금을 `TRANSFER`로 넣으면 음수로 집계되어 저축속도가 **내려간다.** 그래서:

| 계좌 | `transaction_type` | 근거 |
| --- | --- | --- |
| 입금(목표 통장) | `INCOME` | 속도에 +로 잡혀야 한다. `AccountSyncHandler`도 금융망 "1"(입금)을 `INCOME`으로 매핑한다 |
| 출금(월급통장) | `EXPENSE` | 금융망 "2"(출금) 매핑과 동일 |

나머지 컬럼은 이렇게 채운다(V6 `transaction` 기준).

| 컬럼 | 값 | 비고 |
| --- | --- | --- |
| `transaction_id` | 금융망 `transactionUniqueNo` | PK. 두 계좌에 각각 다른 번호가 온다 |
| `category_id` | `SAVING` 카테고리의 id | V6에 `('SAVING', '저축·투자', 'NON_SPENDING', 1)`로 있다. **코드로 조회해서 id를 얻는다** |
| `spending_type` | `NON_SPENDING` | 소비 분석·리포트에 지출로 섞이지 않는다 |
| `source_type` | `SSAFY` | NOT NULL이고 `SSAFY`·`MANUAL`·`SEED`만 허용된다. 금융망에서 온 거래다 |
| `amount` | 이체 금액 | `chk_transaction_amount`가 `> 0`이라 0원 이체는 애초에 400으로 막는다 |
| `balance_after` | 갱신 후 잔액 | |

`JdbcAccountTransactionWriter`의 `INSERT_SQL`을 실측한 결과는 이렇다.

| 컬럼 | 현재 값 | 이체에 필요한 값 |
| --- | --- | --- |
| `source_type` | `'SSAFY'` 고정 | **그대로 쓰면 된다** |
| `payment_method` | `'ACCOUNT'` 고정 | 그대로 쓰면 된다 |
| `category_id` | **`NULL` 고정** | `SAVING`의 id |
| `spending_type` | **`NULL` 고정** | `NON_SPENDING` |

그래서 필요한 변경은 **두 컬럼을 파라미터로 받도록 `NewTransaction`과 `INSERT_SQL`을 넓히는 것**뿐이다.
동기화 경로가 깨지지 않도록 기본값을 지금과 같은 `NULL`로 둔다. 별도 INSERT 경로를 만들 필요가 없다.

⚠️ 카테고리를 비우면 **출금 거래(`EXPENSE`)가 소비 분석에 지출로 잡힐 수 있다.** 월급통장에서 목표 통장으로
옮긴 돈이 '이번 달 지출'로 보이면 안 되므로 `SAVING`·`NON_SPENDING` 지정이 필수다.

---

## 5. 시연 데이터 준비 (B안)

**리허설 전에 1회 수행한다. 시연 당일에 하지 않는다.**

### 5-0. ⚠️ 먼저 정해야 하는 것 — 시연 대상 목표와 시작 잔액

**어느 목표로 시연할지가 정해져 있지 않다.** U0003에는 목표가 둘일 수 있다 — GOAL_SEED가 만든 기존 목표와
678 seed가 `created_at` 마커로 새로 만든 목표(1.5억, 적금 3개, 30%)다. 준비 전에 실측해 대상을 특정한다.

그리고 **목표 통장을 0원에서 시작하면 시연이 약하다.** 목표가 1.5억인데 50만원을 이체하면 진행률이
0.3% 오를 뿐이라 화면에서 거의 움직이지 않는다. 무대에서 보여줄 변화가 없다.

권장: **시연용 목표를 목표 금액이 작은 것으로 고르고, 목표 통장에 미리 일정 금액을 넣어 둔다.**

```
목표 금액        10,000,000원
목표 통장 시작    7,000,000원  (2.4.9 입금으로 미리 주입)  → 진행률 70%
시연 이체         1,000,000원                              → 진행률 80%
```

이렇게 하면 한 번의 이체로 진행률 막대가 눈에 띄게 움직이고 예상 달성일도 확실히 당겨진다.
금액은 리허설에서 화면을 보며 조정한다.

### 5-0-1. 금융망과 도담의 역할이 다르다

**금융망에는 과거 날짜 거래를 만들 수 없다.** 입금(2.4.9)·출금(2.4.8)·이체(2.4.10) 요청에 날짜 파라미터가
없고 거래일자는 금융망이 호출 시점으로 찍는다.

그럴 필요도 없다. **저축속도·확보액·달성일은 전부 도담 DB의 `transaction`·`account`를 읽는다.**
금융망을 조회하지 않는다. 그래서 이렇게 나눈다.

| | 넣는 것 | 이유 |
| --- | --- | --- |
| **금융망** | 잔액만 (오늘 날짜 입금 1건) | 이체할 때 `A1014` 잔액 부족이 나지 않게 |
| **도담 DB** | 과거 거래까지 (직전 3개 완료 월) | 저축속도의 원천이다 |

양쪽 거래 내역이 달라지지만 이 계좌는 `source_type = 'SEED'`라 동기화가 통째로 건너뛴다(§1-2).
불일치가 드러날 경로가 없고, 시연에서 보여주는 것도 도담 화면이다.

| 단계 | 내용 |
| --- | --- |
| 0 | **시연 대상 목표를 정하고, 필요하면 목표 금액을 §5-0 권장에 맞게 조정한다.** U0003의 기존 목표는 1.5억 등으로 커서 그대로 쓰면 이체가 화면에 드러나지 않는다 |
| 1 | U0003 이메일로 금융망 회원 생성 → userKey. `FinanceConnectionService.connect(userId)` 재사용 |
| 2 | 2.4.2로 수시입출금 상품 조회. 없으면 2.4.1로 1회 등록 → `accountTypeUniqueNo` |
| 3 | 2.4.3으로 계좌 2개 생성 — **주거래 대체용**, **목표 통장** |
| 4 | 주거래 대체 계좌에 2.4.9로 **현재 seed 잔액과 같은 금액**을 입금 |
| 5 | 도담 DB: U0003 주거래 `account` 행의 `account_no_encrypted`·`account_no_hash`·`account_no_masked`·`bank_code`·`bank_name`을 금융망 계좌 값으로 갱신. **`account_id`와 `balance`는 그대로, `source_type`도 `SEED` 유지** |
| 6 | 목표 통장에 2.4.9로 **시작 잔액 주입**(§5-0에서 정한 금액). 0원이면 생략 |
| 7 | 도담 DB: 목표 통장 `account` 행 신규 INSERT(`source_type = 'SEED'`, `balance` = 6단계 금액)와 **직전 3개 완료 월 입금 거래**를 함께 넣는다. 아래 주의사항을 지킨다 |
| 8 | **목표에서 기존 주거래 통장 링크를 해제**하고 목표 통장을 연결한다 |
| 9 | 검증 — 2.4.7 잔액과 도담 `balance` 일치, 진행률이 §5-0에서 의도한 시작값인지 |

⚠️ **8단계의 링크 해제가 빠지면 시연이 깨진다.** MR 2는 새 연결만 막았고 기존 링크는 지우지 않았다.
U0003 목표에는 지금도 주거래 통장이 `contribution_amount = NULL`(잔액 전액)로 연결돼 있어 **진행률이 98%다.**
§6에서 예·적금 한정을 해제해도 이 링크는 그대로 살아 있으므로, 해제하지 않으면 이체를 해도 98% 위에
50만원이 얹힐 뿐이고 "통장 잔액이 목표 돈으로 둔갑한다"는 원래 문제가 무대에 그대로 보인다.

해제 후 진행률은 목표 통장 잔액 기준으로 떨어진다. **이것이 의도한 동작이다** — 시연은 그 상태에서
이체로 숫자가 올라가는 것을 보여준다. 재실행 가능한 seed SQL로 작성한다.

**7단계 거래를 심을 때 지킬 것**

- 창은 **기준일이 든 달을 뺀 직전 3개 완료 월**이다. 오늘이 9월이면 6·7·8월이다. 당월에 넣으면 창 밖이라 속도에 안 잡힌다.
- `transaction_type`은 `INCOME`, 카테고리는 `SAVING`, `spending_type`은 `NON_SPENDING`. 이체 거래와 같은 규칙이다.
- `transaction_id`에 **seed 접두어를 붙인다.** 금융망 거래번호(`61` 같은 작은 수)와 충돌하면 안 된다. 기존 seed의 `SEED-U%` 방식을 따른다.
- `balance_after`가 누적으로 맞아야 하고, 마지막 값이 `account.balance`·금융망 잔액과 같아야 한다. 셋이 어긋나면 화면에 드러난다.
- 월 납입액 × 3이 곧 저축속도가 된다. §5-0에서 정한 시작 잔액과 앞뒤가 맞게 나눈다.

- 5단계에서 **주거래 잔액을 건드리지 않는 것**이 핵심이다. 금융망 쪽을 도담에 맞춘다(4단계).
- **5단계는 `bank_code`·`bank_name`도 바꾼다.** 시연 화면의 은행명이 달라지므로 어색하지 않은 은행을
  3단계에서 고른다(2.4.2 상품 목록에서 선택).
- 재실행 가능해야 한다. 이미 교체된 계좌면 건너뛴다.
- 운영 데이터 삭제나 광범위한 초기화 SQL을 쓰지 않는다.

---

## 6. 되돌릴 것

MR 2(`389f544`)는 세 가지를 했다. **통째로 revert하지 않고 항목별로 처리한다.**

| MR 2가 한 것 | 처리 |
| --- | --- |
| `PUT /allocations` + `UpdateGoalAllocationRequest` + `GOAL_ALLOCATION_EXCEEDS_AVAILABLE` | **제거.** 쓰지 않는데 남기면 계약이 둘이 된다. MR 2가 추가한 `GoalAccountApiIntegrationTest` 5건도 함께 제거 |
| `PUT /accounts` 예·적금 한정 + 빈 배열이 예·적금만 해제하는 의미 변경 | **되돌린다.** 입출금을 다시 받고 빈 배열은 전체 해제로 복원. **`GoalCalculationApiIntegrationTest`·`GoalHistoryApiIntegrationTest`의 fixture도 함께 되돌린다** — MR 2가 `link` 헬퍼를 담기 방식으로 바꾸고 예금 계좌를 추가했다 |
| `GoalAllocationUserLock`(사용자 행 비관적 잠금) | **남긴다.** §3-1-1에서 이체 동시성 제어에 재사용한다 |

되돌리면서 **목표당 입출금 1개 제한**을 `replaceAccounts`에 넣는다 — 요청 목록의 `DEMAND_DEPOSIT`이
2개 이상이면 400. V35는 예·적금만 배타 제약이라 DB가 막아주지 않는다.

부수 효과로 [docs/safebox-frontend-request.md](../../docs/safebox-frontend-request.md)의 FE 회귀 2건이
**함께 해소된다.** 목표 만들기·목표 상세의 계좌 선택이 원래대로 동작한다.

---

## 7. 계산에 미치는 영향

**식을 하나도 바꾸지 않는다.**

| | 어떻게 되나 |
| --- | --- |
| 확보액 | 목표 통장 잔액 전액. `contribution_amount`는 null(전액) 그대로 |
| 저축속도 | 목표 통장의 `INCOME` 거래 = 이체받은 금액. 기존 순현금흐름 정의 그대로 |
| 98% 문제 | 목표 통장을 쓰면 발생하지 않는다. 다만 §6에서 입출금 연결을 **다시 허용**하므로 사용자가 주거래 통장을 연결하면 그대로 재현된다 — 막는 장치가 아니라 **더 나은 선택지를 주는 것**이다(FE 안내 문구로 완화) |
| 기준선·안분·담기 | 전부 불필요 |

**이체 직후 저축속도는 변하지 않는다.** 창이 "기준일이 든 달을 뺀 직전 3개 **완료** 월"이라 당월 거래는
창 밖이다. 시연에서 즉시 움직이는 것은 **모인 금액·진행률·예상 달성일**(남은 금액이 줄어서)이다.
**"속도가 오릅니다"라고 말하면 화면과 어긋난다.**

### ⚠️ 저축속도가 0이면 예상 달성일이 아예 안 나온다

```
예상 달성일 = 남은 금액 ÷ 저축속도
```

**갓 만든 목표 통장만 연결하면 속도가 0이다.** 창(직전 3개 완료 월)에 거래가 하나도 없기 때문이다.
9월에 이체해도 `monthsCovered`가 0을 돌려줘 입출금 항 자체를 건너뛴다. 분모가 0이면 날짜는
당겨지는 게 아니라 **사라진다.**

속도를 만드는 원천은 둘이고, 시연에는 **둘 다 필요하다.**

| 원천 | 조건 |
| --- | --- |
| 예·적금 납입 | 창 안에 `savings_payment` **PAID 행이 있어야** 한다. 잔액이 아니라 납입 이력을 읽으므로, 만기된 적금이나 예금은 0이다 |
| 목표 통장 거래 | §5-7에서 심는 직전 3개 완료 월 입금 |

§5-7의 거래 심기는 화면을 채우려는 장식이 아니라 **달성일이 표시되게 하는 필수 조건**이다.

---

## 8. 검증

| 검증군 | 사례 |
| --- | --- |
| 이체 정상 | 잔액 이동, 거래 2건 생성, 타입·카테고리, `last_transaction_date` |
| 입력 검증 | 같은 계좌, 0·음수, 예·적금, CLOSED, 타인 계좌(404) |
| 인증·소유권 | 미인증 401, 타인 계좌 404 |
| 금융망 오류 | `A1014` 잔액 부족 → 409, `A1016`/`A1017` 한도 → 409, 금융망 오류 응답 → 502, 통신 실패 → 503. **도담 DB가 변하지 않아야 한다** |
| 중복 방지 | 같은 요청 연속 2회 → 두 번째 409, 잔액은 1회분만 이동 |
| 동기화 간섭 | SEED 계좌에 ACCOUNT 동기화를 돌려도 잔액·거래가 덮이지 않는지 |
| 목표 반영 | 이체 후 확보액·진행률 상승, 예상 달성일 당겨짐, **저축속도 불변** |
| 목표 통장 1개 제한 | `PUT /accounts`에 입출금 2개를 보내면 400. 1개는 정상 |
| `transaction_id` 충돌 | 금융망 `transactionUniqueNo`를 그대로 PK로 쓴다(동기화와 같은 방식). **전역 유일한지 확인**하고, 아니면 접두어를 붙인다 |

금융망 호출은 테스트에서 `SsafyFinanceClient`를 스텁으로 두고, 실제 호출은 리허설에서 1회 확인한다.

---

## 9. 위험과 대응

| 위험 | 대응 |
| --- | --- |
| 시연 중 금융망 응답 지연·실패 | 리허설에서 응답 시간 측정. 실패 시 보여줄 대체 화면 준비 |
| 이중 이체 | §3-3 중복 창 거절 + FE 버튼 비활성화 |
| 부분 실패로 잔액 불일치 | §3-4. 리허설에서 재현되지 않는지 확인 |
| §5-5 계좌 교체 실수 | 리허설 전 수행, 교체 전 `account` 행 백업, §5-9 검증 통과 후 진행 |
| FE 이체 화면 미완 | 화면이 없으면 시연에서 이체를 뺀다. 이때 §5-8의 링크 해제는 **그대로 수행한다** — 해제해야 진행률이 정직해진다 |
| 일정 부족 | 9/27까지 4일에 BE 1.5~2일 + 시연 준비 + 리허설. **여유가 없다.** 9/25까지 §10의 1~3이 끝나지 않으면 중단하고 아래 되돌리기로 간다 |

### 되돌리기 (중단 기준과 절차)

이 작업을 접어야 할 때 돌아갈 지점을 미리 정해 둔다.

1. 이체 API·어댑터는 배포된 채로 두어도 되지만, **인증된 사용자면 누구나 호출할 수 있다**는 점은 남는다.
   목표와 무관한 임의 계좌 간 이체가 가능해지므로, 시연에 쓰지 않기로 확정되면 컨트롤러만 제거한다.
   어댑터(`SsafyFinanceClient` 메서드)는 호출되지 않으므로 남겨도 무해하다.
2. §6(예·적금 한정 해제)은 되돌리지 않는다. 되돌리면 FE 회귀가 다시 생긴다.
3. §5-8의 **기존 주거래 링크 해제는 유지한다.** 이체가 없어도 진행률이 정직해지는 쪽이 낫다.
4. 시연은 목표 통장 없이 예·적금 기준 진행률로 진행한다.

즉 중단해도 **시연 데이터 정리(§5-8)만은 반드시 한다.** 이게 98% 문제의 최소 해결이다.

---

## 10. 작업 순서

| 순서 | 내용 | 규모 |
| --- | --- | --- |
| **0** | **선행 확인 3건** — ①시연 대상 목표·시작 잔액(§5-0) ②백엔드 인스턴스 수(§11 중복 방지) ③U0003 실측 | 착수 전 |
| 1 | 어댑터 6개 메서드 + 단위 테스트 | 0.5~1일 |
| 2 | 이체 API·서비스·거래 기록 + 통합 테스트 | 0.5일 |
| 3 | MR 2 예·적금 한정 해제, `/allocations` 제거 | 1시간 |
| 4 | 시연 데이터 준비 스크립트(§5) | 0.5일 |
| 5 | 리허설 검증 | — |

**BE 1.5~2일.** FE 이체 화면은 별도다.

---

## 11. 범위 밖 / 확인 필요

### ⚠️ U0003 외의 시연 계정은 이체가 실패한다

U0001·U0002·U0004의 계좌는 금융망에 존재하지 않으므로 이체를 시도하면 `A1003`(계좌번호가 유효하지 않습니다)이
난다. §5는 U0003만 준비한다. **시연 중 다른 계정으로 이체 화면에 들어가지 않도록 시나리오를 고정한다.**

그리고 **이체와 별개로 그 계정들의 목표 진행률은 여전히 부풀어 있다.** 주거래 통장이 전액으로 연결된
상태가 그대로이기 때문이다. 시연에서 다른 계정의 목표 화면을 보여줄 계획이 있으면 §5-8의 링크 해제를
그 계정들에도 적용할지 정해야 한다. **시연 시나리오가 확정돼야 답할 수 있다.**

### 중복 방지 구현 위치 (확인 완료)

`infra/docker-compose.yml`의 `backend` 서비스에 `replicas` 설정이 없다 — **단일 인스턴스다.**
따라서 §3-3의 중복 창 거절은 **메모리(프로세스 내)로 충분하다.** 별도 테이블을 두지 않는다.
다중 인스턴스로 확장하면 그때 옮긴다.

### 그 밖에

- **확인 필요**: 시연 서버에서 U0003의 실제 잔액·목표 연결 상태.
- 스키마 변경은 없다. **Flyway 마이그레이션을 추가하지 않는다.**
- 계좌 해지(2.4.14), 이체 한도 변경(2.4.11)은 연동하지 않는다.
- 일반 사용자용 "목표 통장 개설" 플로우는 시연 범위 밖이다. 어댑터는 준비되므로 후속으로 열 수 있다.
- 결정론적 `institutionTransactionUniqueNo`(진짜 멱등성)는 제품화 시점 과제다.
- 루트 `docs/` 기획안·명세·ERD는 수정하지 않는다. 명세 12 변경은 보고만 한다.
