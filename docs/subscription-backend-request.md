# 구독 목록 화면 백엔드 요청서

기준 화면: `frontend/src/pages/subscriptions/` (내 구독 보기 — 홈 메뉴 → "내 구독 보기").
2026-09-20 기준 FE 작업분에서 **서버 값이 없어 화면에서 우회 처리한 것**과 **BE 없이는 못 고치는 것**을 모았다.
FE는 `GET /api/v1/subscriptions?status=ACTIVE` 연동을 끝냈고 배포 가능한 상태다. 아래는 "지금 깨져 있다"가 아니라
"제대로 하려면 필요하다"에 해당한다.

우선순위는 시연 영향도 순이다.

---

## 1. (P1) 시연 seed로는 화면이 성립하지 않는다 — 구독 seed와 거래 seed를 함께

### 현상

`DODAM_SUBSCRIPTION_SEED.sql:86-98`이 사용자당 구독을 1~2건만 만든다.

```sql
-- 구독 1 (전체 사용자)
CASE MOD(c.sequence_no, 3) WHEN 0 THEN '도담 뮤직' WHEN 1 THEN '도담 비디오' ELSE '도담 클라우드' END,
9900 + MOD(c.sequence_no, 3) * 2500, 12,
CASE WHEN MOD(c.sequence_no, 25) = 0 THEN 'PAUSED' ELSE 'ACTIVE' END,
...
-- 구독 2 (짝수 사용자만)
'도담 리딩', 4900, 25, 'ACTIVE', 'BOOK'
WHERE MOD(c.sequence_no, 2) = 0;
```

결과적으로 **홀수 사용자는 구독이 1건**이다. 실제로 시연 계정 하나를 열어 보니 "도담 뮤직 9,900원" 한 줄만 떴다.
`MOD(seq,3)=0`이면서 `MOD(seq,2)=1`인 계정, 즉 `seq ≡ 3 (mod 6)`이면 그렇게 된다. 버그가 아니라 seed 설계대로다.

서비스 이름도 **도담 뮤직 / 도담 비디오 / 도담 클라우드 / 도담 리딩** 4종뿐이다.

### 왜 문제인가

이 화면은 "이용 중인 구독을 모아 보고, 해지 후보를 골라 월 절감액을 계산"하는 화면이다.
목록이 1줄이면 선택·합산 기능이 의미를 잃는다. 기획 시안은 7건이 깔린 화면을 전제로 그려져 있다.

FE는 `assets/icons/payments`의 브랜드 아이콘(넷플릭스·유튜브·쿠팡 와우·스포티파이·밀리의서재·ChatGPT·네이버)을
서비스 이름으로 매칭해 두었는데, "도담 ○○"는 어디에도 안 걸려 **전부 기타 아이콘(회색 원)으로 표시**된다.

### 요청

시연 계정만이라도 구독을 5~7건으로 늘리고, 이름을 실제 서비스명으로 바꿔 달라.
`SubscriptionServiceCatalog`에 이미 30개 서비스가 정의돼 있으니 거기 이름·요금을 그대로 쓰면 된다
(넷플릭스 17,000 / 유튜브 프리미엄 14,900 / 쿠팡 와우 멤버십 7,890 / 스포티파이 10,900 / 밀리의서재 9,900 /
챗GPT 플러스 29,000 / 네이버플러스 멤버십 4,900).

#### 결정 필요 — 적용 범위

전체 6,000명을 바꿀지, 시연 계정 몇 개만 덮어쓸지 정해야 한다.
전체를 실제 브랜드명으로 바꾸면 또래 비교·리포트 같은 집계 화면의 기존 수치가 함께 움직인다.
`DODAM_DEMO_U0003_STORY_SEED.sql` 같은 데모 전용 seed를 덧씌우는 기존 패턴을 따르는 쪽이 안전해 보이는데,
집계 화면 영향도는 BE 쪽이 더 잘 판단할 수 있어 여기서는 정하지 않았다.

**`DODAM_TRANSACTION_SEED.sql`도 같이 바꿔야 한다.** 두 seed는 같은 결제를 각자 적재하는 계약이다
(`DODAM_SUBSCRIPTION_SEED.sql` 머리말 "Contract with DODAM_TRANSACTION_SEED.sql").

- `subscription` 테이블 1행 → 구독 목록 화면이 읽는다
- `transaction` 원장 6행(6개월치) → 가계부·소비 TOP 3·리포트가 읽는다

거래 seed 4c 블록(`DODAM_TRANSACTION_SEED.sql:318-346`)이 구독 결제를 넣는데, 여기 `merchant_name`이
구독 seed와 같은 `CASE MOD(sequence_no, 3) ... '도담 뮤직' ...` 식으로 하드코딩돼 있다.
구독 이름만 바꾸면 **구독 화면에는 넷플릭스가, 가계부에는 도담 뮤직이** 떠서 서로 어긋난다.

시연에서는 가계부에도 넷플릭스·유튜브 결제가 보이는 편이 훨씬 자연스럽다. 두 파일의 이름·금액·결제일을
한 벌로 맞춰 달라.

---

## 2. (P2) 목록 응답에 서비스 식별자가 없어 FE가 이름으로 추측하고 있다

### 현상

`SubscriptionResponse`에는 `name`(문자열)만 있고 서비스 식별자가 없다. 그래서 FE는
`frontend/src/constants/subscriptionServiceIcons.ts`에서 이름을 정규화한 뒤 키워드로 아이콘을 찾는다.

```ts
// 서버가 아이콘 키를 주지 않고 서비스 이름만 문자열로 내려주므로 이름을 정규화해 키워드로 찾는다.
{ icon: require('@/assets/icons/payments/netflix 1.png'), keywords: ['넷플릭스', 'netflix'] },
```

서버에 이미 같은 일을 하는 코드가 있다. `SubscriptionServiceCatalog.match(merchantName)`이
이름·키워드로 카탈로그 항목을 찾아 주고, `CatalogEntry.iconKey`는 **FE `assets/icons/payments` 파일명과 맞춰 둔 값**이다
(`backend/docs/remaining-work.md:1067`에 명시).

그런데 이 매칭은 `/subscriptions/candidates`에서만 쓰고 목록 API에서는 안 쓴다.

### 요청

`SubscriptionResponseAssembler`에서 `catalog.match(subscription.getName())`을 호출해
`SubscriptionResponse`에 `serviceCode`와 `iconKey`(둘 다 nullable)를 추가해 달라.
매처·아이콘 키가 이미 있으므로 필드 2개 추가 수준이다.

그러면 FE가 이름 추측 매핑을 버리고 `iconKey`로 바로 아이콘을 고를 수 있다. 서버가 카탈로그를 늘릴 때
FE 배포 없이 아이콘이 따라온다.

---

## 3. (P3) 반복결제 탐지가 구독이 아닌 고정비를 잡는다

### 현상

FE에서 `GET /subscriptions/candidates`를 목록에 합쳐 봤더니 **보험료와 주거비(관리비)가 구독으로 올라왔다.**
그래서 탐지 연동을 다시 걷어냈다(`useSubscriptions.ts` 주석에 사유를 남겨 둠).

원인은 `SubscriptionDiscoveryService`의 판정 조건에 "이게 구독인가"를 보는 단계가 없기 때문이다. 조건은 세 개뿐이다.

1. 지출·숨김 아님·가맹점명 있음 (`JdbcRecurringPaymentReader`)
2. 가맹점명(공백·대소문자 무시) + 금액이 정확히 일치 (`SubscriptionDiscoveryService.java:86`)
3. 평균 간격이 25~36일 (`BillingCycle.estimate`)

보험료·관리비는 매달 같은 날 같은 금액으로 같은 이름으로 빠지므로 이 셋을 완벽히 만족한다.
오히려 넷플릭스보다 더 정확히 걸린다.

`RecurringPaymentCandidateResponse`에 `categoryCode`와 `matchedServiceCode`를 담아 내려주고 있지만
**필터로는 쓰지 않는다.**

### seed로 확인한 근거

거래 seed 4b 고정비 블록(`DODAM_TRANSACTION_SEED.sql:296-315`)이 매달 3건을 넣는다.

| kind | 가맹점명 | 카테고리 | 결제일 | 금액 |
| --- | --- | --- | --- | --- |
| 01 | 도담 주거 관리사무소 | `HOUSING` | 1일 | 고정 |
| 02 | 도담 통신·공과금 | `UTILITIES` | 5일 | **매월 변동** (`(90 + MOD(...,21))/100` 계수) |
| 03 | 도담 보험 | `INSURANCE` | 20일 | 고정 |

FE에서 탐지를 붙여 봤을 때 **주거비와 보험은 잡히고 통신비는 안 잡혔다.** 묶음 키가
`(가맹점명, 금액)` 완전 일치라 금액이 매월 달라지는 통신비만 묶이지 않은 것이다.
즉 이 오탐은 우연이 아니라 규칙대로 나온 결과다.

### 요청

후보 판정에 성격 필터를 추가해 달라. 다음 둘 중 하나라도 만족할 때만 후보로 보는 방식을 제안한다.

- `categoryCode == 'SUBSCRIPTION'` — `transaction_category`에 이미 있는 코드다
- `matchedServiceCode != null` — 카탈로그 이름 매칭에 걸린 것

카테고리가 잘 붙은 거래는 1번으로, 안 붙은 거래는 2번으로 건진다.

`INSURANCE`·`HOUSING`을 빼는 블랙리스트 방식은 권하지 않는다. 헬스장(`MEDICAL`)·학원(`EDUCATION`)·
통신비(`UTILITIES`)·대출 상환(`LOAN_REPAYMENT`)·정기 배송(`GROCERY`)이 전부 같은 조건을 만족하므로
제외 목록이 계속 늘어난다.

**seed는 이 필터에 이미 맞게 돼 있다.** 거래 seed 4c 블록이 구독 결제를
`JOIN tmp_dodam_492_categories cat ON cat.code = 'SUBSCRIPTION'`으로 넣는다.
즉 구독 결제는 `categoryCode = 'SUBSCRIPTION'`, 고정비는 `HOUSING`·`UTILITIES`·`INSURANCE`라
1번 조건만으로도 시연 데이터에서 정확히 갈린다.

다만 현재 seed의 구독 거래는 이름이 "도담 ○○"이라 `matchedServiceCode`는 계속 null이다.
1번 항목(seed 정비)을 같이 하면 두 조건 모두로 검증할 수 있다.

---

## 4. (P3) 주기 판정 규칙 3건

`backend/docs/remaining-work.md:1060`에 "임계값은 seed 거래가 잡히도록 잡은 값이며 조정 대상"으로 이미
열려 있는 항목이다. 조정할 때 아래를 같이 봐 달라.

### 4-1. YEARLY는 기본 설정에서 도달할 수 없다

조회 창은 `today.minusMonths(months).plusDays(1)` ~ `today`이고 `months`는 최대 12다. 즉 창 길이가 365일이라
**창 안 두 날짜의 최대 간격은 364일**이다.

`minOccurrences` 기본값 3이면 간격이 2개 필요하므로, 평균 350일을 만들려면 전체 기간이 700일 이상이어야 한다.
창에 들어갈 수 없다. `minOccurrences=2` **그리고** `lookbackMonths=12`를 동시에 주고 간격이 350~364일인
좁은 구간에서만 걸린다.

의도한 동작인지, 아니면 YEARLY 판정을 빼거나 조회 창을 늘려야 하는지 확인이 필요하다.

### 4-2. WEEKLY는 오탐 쪽이 크다

3회 × 평균 5~9일이면 10~18일 안에 만족한다. 주간 결제 구독은 국내에 드문 반면, 매주 같은 금액으로 결제되는
습관적 소비(커피·식비 등)는 흔하다. 탐지 대상에서 빼는 것을 검토해 달라.

### 4-3. 간격의 규칙성을 검사하지 않는다

`SubscriptionDiscoveryService.java:107-111`의 `totalGap` 누적은 중간항이 상쇄되어
수학적으로 `(마지막 결제일 - 첫 결제일)`과 같다. 즉 실제 계산은 이렇다.

```
평균 간격 = (마지막 결제일 - 첫 결제일) / (결제 횟수 - 1)
```

개별 간격이 얼마나 들쭉날쭉한지는 보지 않는다. 예를 들어 **1/1, 1/2, 3/2**에 같은 금액이 결제되면
(이틀 연속 결제 + 두 달 공백) 전체 60일 ÷ 2 = 평균 30일이라 MONTHLY로 판정된다.

FE 쪽 의견으로는 "매달 비슷한 날(일자 기준 ±2~3일)에 2회 이상"이 현실의 구독에 더 가깝다.
`suggestedPaymentDay`를 이미 최빈값으로 구하고 있으니(`:118`) 그 값에서 벗어난 결제를 세는 식으로
규칙성을 볼 수 있다. 말일 결제(1/31 → 2/28)와 주말 이연 때문에 완충은 필요하다.

---

## 5. (P4) API 명세서 상태가 실제와 다르다

`docs/API명세서/7 Subscription ...csv`의 10개 API가 전부 **"시작 전"**으로 적혀 있다.
실제로는 컨트롤러·서비스·엔티티·통합테스트(3종)까지 구현돼 있고 FE가 연동 중이다.

FE가 처음에 이 표를 보고 "백엔드 미구현"으로 판단해 Mock 데이터로 갈 뻔했다. 상태를 갱신해 달라.

---

## 참고 — FE 현재 구현

- 화면: `frontend/src/pages/subscriptions/SubscriptionsScreen.tsx`
- 조회: `GET /api/v1/subscriptions?status=ACTIVE&page&size` (전 페이지 순회)
  - 정지·만료·해지는 "이용 중"이 아니라고 보고 `ACTIVE`만 조회한다
- 금액: `monthlyEquivalentAmount`를 그대로 표시하고 합산한다. 주기가 `MONTHLY`가 아니면
  "연 결제를 월로 환산했어요" 캡션을 덧붙인다
- 선택한 구독의 월 환산 금액을 합쳐 "이 N개 구독을 해지하면 매달 X원이 남아요"로 보여준다
- `/subscriptions/candidates`는 3번 사유로 쓰지 않는다
