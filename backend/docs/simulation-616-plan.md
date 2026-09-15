# 616 시연 seed MySQL에서 시뮬레이션 10종 실행 확인 계획

작성일: 2026-09-16. 브랜치 `S15P21D105-616-587-seed-mysql-10`(Jira Create branch, `origin/develop` `cf5eb3c` = 587 병합 커밋 기준).
587 후속 4건(616~619) 중 첫 번째이며, 실행 결과는 [simulation.md](simulation.md) §7에 기록하고 [remaining-work.md](remaining-work.md) 587 절의 해당 항목을 닫는다.
코드 변경은 예정에 없다. 실행 중 결함을 찾으면 이 브랜치가 아니라 별도 이슈로 올린다(QA 124와 같은 날이므로 124의 결함 등록 절차를 따른다).

## 1. 범위와 착수 근거

- 대상: S15P21D105-616. 로컬 Jira 내보내기([587-remaining-work.csv](../../SPRINT/587-remaining-work.csv)) Issue ID 158 "[587 후속] 시연 seed MySQL에서 시뮬레이션 10종 실행 확인"(2026-09-17, 3pt)을 616으로 본다. 159→617, 160→618, 161→619 순서 대응이며 Jira 원문 제목이 다르면 이 절을 먼저 고친다.
- 이슈 설명: 시연 seed(491·492·493·522·523)와 상품 카탈로그(525)를 적재한 MySQL에서 실행 10종이 실제 자료로 성립하는지 확인한다(대출 `next_repayment_date`, 카드 소유권, 상품 옵션 기간·금리). `SimulationMySqlIntegrationTest`는 사용자 입력만으로 성립하는 유형(saving·installment·cash-advance·combined)만 다뤘다.
- 완료 기준: 시연 계정으로 10종 실행 201과 goal-impact 날짜를 실측하고 금액을 simulation.md §7에 기록한다.
- 선행: seed 적재 MySQL(`NUMBER_HASH_KEY` 일치). 시연 데이터·QA 버전은 134(BE-C, 09/16) 결과를 쓴다.

### 포함·제외

| 포함 | 제외 |
| --- | --- |
| 시연 계정 4개(U0001~U0004)로 실행 10종 + `goal-impact` 실측 | 계산식·응답 계약·seed SQL 변경 |
| 자료가 성립하지 않는 유형의 **원인과 오류 코드** 기록 | seed 보정(523 `next_repayment_date` 등)은 담당 영역(BE-A/BE-C)에 보고만 한다 |
| simulation.md §7·remaining-work.md 갱신 | 617(MySQL 테스트 3항목)·618(일관성 테스트)·619(goalId 결정) |

## 2. 검증 환경 결정

| 안 | 내용 | 판단 |
| --- | --- | --- |
| **A. 시연 서버 API (기본안)** | `https://j15D105.p.ssafy.io/api/v1`. 2026-09-14에 seed 1~8번이 서버 `NUMBER_HASH_KEY`로 만든 fixture와 함께 적재됐고(seeds/README.md), `develop` 병합 시 Jenkins가 자동 배포하므로 587 코드(`cf5eb3c`)가 올라가 있어야 한다 | 이슈의 "seed 적재 MySQL(`NUMBER_HASH_KEY` 일치)" 조건을 그대로 만족한다. DB에 직접 붙지 않고 API 응답만으로 확인한다(`infra/.env` 접근 금지) |
| B. 로컬 Docker `mysql:8.0` + seed 적재 | 계좌·카드번호 fixture(`tmp_dodam_*_crypto`)를 로컬 키로 다시 만들어야 한다. 09-11 로컬 적재는 SHA2 더미 fixture라 API의 HMAC 조회(`accountNo` → 대출·계좌)가 실패한다 | 서버 접근이 불가하거나 배포 버전이 587 이전일 때의 대안. fixture 생성기(`make_seed_fixtures.py`)는 서버에만 있어 새로 작성해야 하므로 반나절 이상 추가된다 |

A로 진행하고, §4의 사전 확인에서 (1) 배포 버전이 587을 포함하는지, (2) 시연 비밀번호 seed(10번, 09/15 추가)가 서버에 적용됐는지 먼저 본다. 둘 중 하나라도 아니면 134 담당(BE-C)에게 요청하고 결과를 기다리는 동안 617·618을 진행한다.

## 3. 시연 계정과 자료 (seed 규칙에서 도출, 실행 전 API로 확인)

seed 파일의 생성 규칙(`MOD(순번, …)`)으로 4개 계정이 가진 자료를 미리 계산했다. 숫자는 규칙에서 나온 값이며 §4에서 실제 응답으로 확인한다.

| 계정 | 성향 | 카드(522) | 대출(523) | 예·적금(524) | 목표(493) |
| --- | --- | --- | --- | --- | --- |
| U0001 | BALANCED | PRIMARY 1장 | 없음(홀수) | 예금 1(ACTIVE) | 모으기(예금 + 주거래 연결) |
| U0002 | SPENDING_FOCUSED | PRIMARY·SECONDARY | 신한주택대출(아파트) MORTGAGE·EQUAL_PRINCIPAL·240개월, 잔액 141,000,000, 3.9%, 시작 2024-01-17, 만기 2044-01-17, **next_repayment_date 2026-09-15** | 적금 1 | 모으기(적금) + 줄이기 |
| U0003 | SAVING_FOCUSED | PRIMARY 1장 | 없음 | 예금 1 | 모으기(예금) |
| U0004 | VOLATILE | PRIMARY·SECONDARY | ① HF 전월세보증금 대출 JEONSE·BULLET·36개월, 잔액 100,000,000, 3.2%, 시작 2024-01-19, 만기 2027-01-19, **next_repayment_date 2026-09-20** ② 신한 사잇돌 중금리대출 CREDIT·**OVERDUE**, 잔액 9,200,000, 7.9% + 마이너스통장 1 | 적금 1 | 모으기(적금) + 줄이기 |

대출 `next_repayment_date`는 seed가 `'2026-09-01' + (상환일 − 1)`로 **고정**한 값이다(523 snapshot 2026-09-07). 금융망 동기화가 seed 대출을 갱신하지 않으므로 날이 지날수록 오래된 스냅샷이 된다. `LoanScenarios.requireComputable`은 `nextRepaymentDate < 기준일`을 `SIMULATION_LOAN_DATA_UNAVAILABLE`로 거부하므로:

- U0002 대출은 09-16 이후 추가상환·대환 모두 400이다.
- U0004 ①은 **09-20까지만** 추가상환이 되고, 대환은 `nextRepaymentDate == 기준일`도 거부하므로 09-19까지다.
- 09-21 이후에는 시연 계정 4개 어디에도 계산 가능한 대출이 없다.

따라서 616 실측은 **09/17에 해야** 하며, 그 뒤로 미뤄지면 대출 2종은 자료 부족 400으로 기록될 수밖에 없다.

## 4. 사전 확인 (상태 변경 없음, 09/16 가능)

1. `GET /api/health`와 배포 로그로 서버 코드가 `cf5eb3c` 이후인지 확인한다. 확인 수단이 없으면 `GET /recommendations/priorities` 응답에 587이 추가한 `simulation` 필드가 있는지로 판별한다.
2. `POST /auth/login` (`seed.kmeans.u0004@dodam.invalid` / 시연 비밀번호). 401이면 10번 seed 미적용 → BE-C에 요청.
3. 계정별 id 수집(§5 입력에 쓴다):
   - `GET /accounts` — 예·적금 계좌의 `accountId`·마스킹 번호·상태(CLOSED면 저축 대상에서 제외)
   - `GET /cards` — `cardId`(리볼빙)
   - `GET /loans` — 대출 목록의 상태·`nextRepaymentDate`·잔액·만기(§3 표와 대조)
   - `GET /goals` — 모으기·줄이기 목표 id, 예상 달성일(goal-impact 기준선 대조용)
   - `GET /products` — 예금(카카오뱅크 정기예금, term 12 = 3.60%), 적금(자유적금, term 6 = 3.50%), 신용대출(비교군 CREDIT: `PERSONAL_CREDIT`, 옵션 term 12·한도 확인), 대환 대상(비교군 JEONSE: HF 전월세보증금 대출 갈아타기, 옵션 term 6/12/24/36·BULLET)
4. 계좌번호 원문: seed 합성값 `1100{순번6}{유형3}`(README, 예금 301·적금 401·대출 101/102). API 호출에만 쓰고 문서·로그에는 마스킹 값만 남긴다.

### 4.1 사전 확인 결과 (2026-09-16 실행, 시연 서버)

| 항목 | 결과 |
| --- | --- |
| `GET /api/health` | 200 `UP` |
| 로그인 U0001~U0004 / 시연 비밀번호 | 4계정 모두 200 → 10번 비밀번호 seed 적용됨 |
| 배포 버전(587 포함 여부) | **미확정.** 시연 계정에 진단·추천이 없어 `simulation` 필드로 판별 불가, `/v3/api-docs`·actuator·Jenkins는 403. 09/17 진단 생성(QA 124 절차) 뒤 `GET /recommendations/priorities` 항목의 `simulation` 필드로 확정하거나 BE-C(134)에 배포 커밋을 묻는다 |
| 계좌·대출·목표 | §3 예측과 일치. 세부는 아래 표 |
| `GET /cards` | **`cardId`를 내려주지 않는다**(마스킹 번호·이름·상태만). 리볼빙 `cardId`는 `GET /installments` 항목의 `cardId`로 역추적했다(PRIMARY→SECONDARY 순 삽입: U0001=1, U0002=2·3, U0003=4, U0004=5·6). FE가 리볼빙에 `cardId`를 채울 경로가 없으므로 카드 목록 응답에 `cardId` 추가가 필요하다 → remaining-work 후속(카드 API는 BE-B) |

| 계정 | 계좌(`accountId`, 마스킹) | 카드 `cardId` | 대출(마스킹, next, 상태) | 목표(`goalId`) |
| --- | --- | --- | --- | --- |
| U0001 | 예금 5119 `…1301` 10,000,000 / 주거래 1 `…1001` | 1 (ACTIVE) | 없음 | 1 여행 자금 SAVING **remaining 0(이미 달성)** |
| U0002 | 적금 7166 `…2401` 1,800,000 / 주거래 2 / 휴면 4001 | 2 (PRIMARY), 3 | 신한주택대출 `…2101` next **09-15** ACTIVE (stale) | 2 전세 보증금 SAVING remaining 30,200,000 (2027-05-01) / 3 월 소비 줄이기 SPENDING budget 1,800,000 |
| U0003 | 예금 5120 `…3301` 20,000,000 / 주거래 3 | 4 | 없음 | 4 노트북 바꾸기 SAVING remaining 31,300,000 (2027-06-01) |
| U0004 | 적금 7167 `…4401` 3,000,000 / 주거래 4 / 마이너스 4096 −3,500,000 | 5 (ACTIVE), 6 (**EXPIRED**) | ① HF 전월세 `…4101` next **09-20** ACTIVE ② 사잇돌 `…4102` **OVERDUE** | 5 내 집 마련 SAVING remaining 33,000,000 (2027-07-01) / 6 월 소비 줄이기 SPENDING budget 1,620,000 |

상품(`productId`): 예금 **1** 카카오뱅크 정기예금(term 12 = 3.60%), 적금 **2** 자유적금(term 6 = 3.50%, 12 = 3.65%, 한도 3,000,000), 신용대출 **53** KB 직장인든든 신용대출(`PERSONAL_CREDIT`, term 12 옵션에 BULLET·EQUAL_PRINCIPAL 등 여러 코드 → AUTO는 `REPAYMENT_TYPE_REQUIRED`, `EQUAL_PAYMENT` 명시 시 6.04%), 대환 대상 **18** HF 전월세보증금 대출 갈아타기(`LEASE_REFI_HF`, term 6/12/24/36 BULLET, 한도 222,000,000).

계획 조정: #2 저축은 U0001 목표가 이미 달성 상태라 **U0003(예금 5120, 목표 4)**로 바꾼다. #4는 처음부터 `EQUAL_PAYMENT`를 명시한다(AUTO 400은 보조 확인으로 1회만 실측).

## 5. 실행 계획 (09/17, 기준일 = 서버 Asia/Seoul 날짜)

각 실행 후 `GET /simulations/{id}`·`/goal-impact`로 저장값을 대조한다. "기대"는 §3 자료와 simulation.md §9~§10 계산 정책에서 미리 도출한 것이고, 실측 금액은 §6 표에 채운다.

| # | 유형 | 계정 | 입력 | 기대 | 자료 근거 |
| --- | --- | --- | --- | --- | --- |
| 1 | `spending` | U0002 | `monthlyReduction` = SPENDING 추천의 프리셋값(없으면 100,000), category 없음 | 201, `goalImpacts`에 모으기·줄이기 목표, 달성일 단축 | 492 거래 6개월, 493 목표 |
| 2 | `saving` | U0003 | `monthlySavingAmount` 300,000, `accountNo` = 예금 5120 원문 | 201, 목표 4의 `afterForecastDate` 앞당김 | 493 예금 연결 목표(U0001은 이미 달성이라 제외) |
| 3 | `saving-product` | U0003 | `productId` 1, `monthlyAmount` 10,000,000, `term` 12 | 201, 세전 이자 = floor(10,000,000 × 3.60 × 365 / 36500) = 360,000 (2026-09-17 → 2027-09-17, 365일) | 525 옵션 term 12 |
| 4 | `loan/new` | U0001 | `productId` 53, `principal` 10,000,000, `term` 12, `repaymentType` `EQUAL_PAYMENT` | 201, 6.04% 원리금균등 12회. 생략(AUTO) 재실행 → 400 `REPAYMENT_TYPE_REQUIRED` | 525 옵션 term 12(코드 4종), 한도 100,000,000 |
| 5 | `loan/prepayment` | U0004 ① | `accountNo` = 대출 원문, `prepaymentAmount` 10,000,000, `paymentDate` 생략 | 201, BULLET이라 이후 회차 이자 = 90,000,000 × 3.2%/12 반올림, 절감 = 10,000,000 × 3.2%/12 × 잔여 회차 | next 09-20 ≥ 기준일 |
| 6 | `loan/refinance` | U0004 ① | `accountNo` 위와 같음, `targetProductId` 18 | **400 `SIMULATION_OPTION_UNAVAILABLE` 예상**: 잔여 회차 4~5(09/20·10/20·11/20·12/20·만기 01/19)와 같은 term 옵션이 없다(6/12/24/36만 있음) | §3, `LoanRefinanceCalculator`의 잔여 회차 = 옵션 term 규칙 |
| 7 | `installment` | U0002 | `purchaseAmount` 1,200,000, `months` 12, `feeRate` 12 | 201, 회차 표 12행 | 사용자 입력만 |
| 8 | `revolving` | U0004 | `cardId` 5, `balance` 500,000, `paymentRatio` 50, `rate` 18 | 201, 본인 카드 저장. `cardId` 1(U0001 카드)로 재실행 → 404. `cardId` 6(EXPIRED)은 소유권만 보므로 201 예상 | 522 카드 소유권 |
| 9 | `cash-advance` | U0003 | `amount` 1,000,000, `rate` 18, `repaymentMonths` 생략 | 201 | 사용자 입력만 |
| 10 | `combined` | U0004 | `[saving(적금 7167 원문, 300,000), installment(#7 입력), loan/prepayment(#5 입력)]` | 201, `scenarios` 3행, `goalImpacts`가 단독 실행 #2·#5의 합성과 방향 일치 | 위 세 개의 자료 |

보조 확인(같은 날, 저장 없음): 같은 요청 재실행 200 `created:false`, U0002 대출 추가상환 400 `LOAN_DATA_UNAVAILABLE`(오래된 `nextRepaymentDate`), U0004 ② 추가상환 400(OVERDUE).

#6이 예상대로 400이면 "시연 계정 10종 201"은 **달성 불가**하고, 그 원인이 코드가 아니라 seed 자료(잔여 회차 ≠ 카탈로그 term)임을 §6에 적는다. 대환 201을 실측하려면 (a) 잔여 회차가 6·12·24·36 중 하나가 되는 대출을 가진 seed 사용자에게 시연 비밀번호를 부여하거나, (b) 523 seed에 시연 계정용 대출 변형을 추가해야 하며 둘 다 BE-A/BE-C 자료라 요청 사항으로만 남긴다.

## 6. 실측 기록 형식

simulation.md §7 끝에 "616 시연 seed 실측(2026-09-17, 서버 MySQL)" 소절을 두고 다음 표를 채운다. 계좌·카드번호는 마스킹 값만, `simulationId`는 기록한다(재조회용).

| # | 유형 | 계정 | HTTP | simulationId | 핵심 금액 | goal-impact (before → after) | 비고 |
| --- | --- | --- | --- | --- | --- | --- | --- |

실측 결과(2026-09-16 실행, 계획보다 하루 앞당김 — 대출 자료 유효 기간 §3 때문)는 simulation.md §7 "616 시연 seed 실측"에 채웠다. 9종 201, #6 대환은 예상대로 400 `SIMULATION_PRODUCT_OPTION_UNAVAILABLE`. 기준선 달성일 4건이 `GET /goals/{id}/forecast`와 일치했다.

기준선 예상 달성일(`beforeForecastDate`)이 `GET /goals`의 값과 같은지 한 건 이상 대조한다(587 §11.2의 551 대조를 실자료로 재확인).

## 7. 문서 갱신

- simulation.md §7: 위 표. §11.3 "하지 않은 것"에서 seed 10종 실행 문구 삭제.
- remaining-work.md 587 절: "시연 seed MySQL 실행 확인" 항목을 완료로 바꾸거나 제거. #6이 400이면 대환 실측 불가 원인과 요청 사항을 새 항목으로 남긴다(담당 BE-A/BE-C).
- 시연 계정에 남는 시뮬레이션 행(id 7~17): 기본안대로 두었다. 134 시연 기준에 맞지 않으면 `DELETE /simulations/{id}`(소프트 삭제)로 지운다.

## 8. 위험과 미결

- **날짜 민감**: §3의 대출 `next_repayment_date` 때문에 09/17~09/19 사이에만 추가상환 201이 나온다. 그 뒤엔 대출 2종 모두 400으로 기록된다.
- 서버 배포 버전이 587 이전이면 프리셋(#1 입력)과 통합 회귀 대상 코드가 없다. 배포 확인이 첫 단계다.
- ~~10번 비밀번호 seed 미적용 가능성~~ → 09/16 로그인 4계정 200으로 해소.
- 서버 배포 버전 확인 수단이 없다(§4.1). 09/17 진단 생성 후 `simulation` 필드로 확정한다.
- `GET /cards`에 `cardId`가 없다(§4.1). 616 실측은 installments 역추적으로 진행하지만 FE 146 리볼빙 연동에는 카드 API 변경이 필요하다.
- 시연 서버는 QA 124·FE 146이 같은 날 쓴다. 실행은 시연 계정에 시뮬레이션 행을 남기므로 §7의 삭제 여부를 먼저 정한다.
- #4 `loan/new`의 AUTO 확정 여부는 카탈로그 옵션 코드 구성에 달려 있어 실행 전엔 확정할 수 없다. 400이면 명시 재실행으로 기록한다.
- Jira 616 원문 미확인(§1).
