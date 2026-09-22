# S15P21D105-678 — 시연 예적금 계좌 보강 QA 보고서

작성일: 2026-09-21. 계획: [demo-deposit-savings-678-plan.md](demo-deposit-savings-678-plan.md). 브랜치 `S15P21D105-678-db`(원격 생성분을 최신 `develop` 69c0f04까지 fast-forward).

> **2026-09-22 개정**: 679(급여 3,012,400) 수지에 맞춰 적금 수치를 축소했다(청년도약 50만→30만, 목돈적금 100만×78회→50만×60회 만기 완료). 2~4절은 개정 전 수치(2026-09-21)의 실측 기록이며, 개정판의 실측은 **7절**이 대체한다. 현재 seed의 기대값은 seeds README 16번 절과 7절을 따른다.

## 1. 검증 환경

| 항목 | 값 |
| --- | --- |
| DB | 로컬 Docker `mysql:8.0`(빈 스키마 `dodam_678_test`, 비밀번호 없음, 일회용) |
| 마이그레이션 | V1~V41 수동 적용(V6은 V11 뒤). 앱 기동 시 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`, `BASELINE_VERSION=41` |
| 선행 seed | 1번(상품 198·옵션 456) → fixture(SHA2 더미) → 2번(491, 사용자 4,000·계좌 4,001) → fixture → 5번(524, 예적금 4,000·납입 13,000) → 7번(493, 목표 6,000) → 10번(시연 비밀번호) |
| 678 fixture | `make_demo_deposit_savings_fixture.py`를 로컬 테스트 키(임의 Base64 32바이트 `DATA_ENCRYPTION_KEY`, 문자열 `NUMBER_HASH_KEY`)로 실행. 실제 코덱 형식(41바이트 암호문·64 hex 해시·마스킹) |
| 앱 | `bootRun`, `local` 프로필, 같은 키로 기동. Flyway·JPA validate 통과. Redis 7 컨테이너 |
| 기준일 | 실제 실행일 2026-09-21(운영 시계 변경 없음) |
| 운영 서버·금융망 | 사용하지 않음. `infra/.env`는 읽지 않음 |

## 2. Seed 적재 결과

### 2-1. 최초 적재

preflight `ready=1 / user 1 / withdrawal 1 / fixture 3 / valid 3 / distinct 3 / product 1 / payments 211 / future_paid 0 / hash_conflict 0 / goal_name_conflict 0 / marker_goal 0 / link_conflict 0`. 실행 시간 약 1초.

| 계좌 | account_id | 잔액 | 금리 | 개설 | 만기 | 다음 납입 | PAID | PAID 합 | SCHEDULED | 미래 PAID | 출금계좌 | 상품 |
| --- | ---: | ---: | ---: | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 내집마련 목돈적금 | 8190 | 78,000,000 | 3.2500 | 2020-04-15 | 2027-04-15 | 2026-10-15 | 78 | 78,000,000 | 0 | 0 | 4 | NULL |
| 주택청약종합저축 | 8191 | 12,100,000 | 2.8000 | 2016-09-15 | 2036-09-15(합성) | 2026-10-15 | 121 | 12,100,000 | 1 | 0 | 4 | NULL |
| 신한 청년도약계좌 | 8192 | 5,500,000 | 4.5000 | 2025-11-15 | 2030-11-15 | 2026-10-15 | 11 | 5,500,000 | 0 | 0 | 4 | 95(신한 청년도약계좌) |

목표 `내 집 마련 준비`(goal_id 8192): SAVING/ACTIVE, **created_at 2021-09-21 09:00:00**, current_amount **95,600,000**, target 150,000,000, target_date 2029-09-21, 연결 3건(전부 678 계좌, contribution NULL), 확보액 합 95,600,000, progress 63.7333, goal_history **61건**(GOAL_CREATED 2021-09-21 24,100,000 + 월말 AMOUNT_UPDATED 60건, 마지막 2026-08-31 94,000,000).

생성일을 2021-09-21로 둔 이유: 앱의 저축속도가 `currentAmount / 생성 후 경과 개월(최소 1)`이라 기준일에 생성하면 월 9,560만원으로 튄다(사용자 지적, 2026-09-21). 이력은 세 계좌의 실제 PAID 누적합이라 납입 행과 곡선이 일치한다.

### 2-2. 재실행(멱등)

fixture를 새로 만들어(새 IV) 같은 세션 조건으로 다시 실행. VERIFY Q1~Q5 출력이 최초 적재와 **완전히 동일**(`diff` 없음): account_id 8190·8191·8192, goal_id 8192, 납입 체크섬 3864811135, 이력 61건 유지. (2021-09-21 생성일·이력 61건으로 바꾼 뒤 새 DB에서 전 과정을 다시 수행했다.) 두 번째 실행의 preflight는 `marker_goal 1`(기존 678 목표 재사용).

### 2-3. 기존 데이터 보존

VERIFY Q4(678 행을 제외한 전체)가 적재 전 기준값과 동일: 계좌 8,001·잔액 합 68,594,170,000·예적금 상세 4,000·납입 13,000·목표 6,000·목표 확보액 합 46,076,720,000·연결 4,750·이력 30,000·상품 198·옵션 456·사용자 4,000. U0003 기존 목표 `노트북 바꾸기`(goal_id 4, 20,000,000, 연결 1)와 524 예금(account_id 4097, 20,000,000) 그대로. Q5 `0 / 0`.

### 2-4. 실패 격리

| 시나리오 | 결과 |
| --- | --- |
| fixture 3행 중 1행 삭제 후 실행 | preflight `ready=0, fixture 2`, 영구 테이블 변경 없음(VERIFY 동일) |
| U0003에 같은 이름 목표를 다른 created_at으로 추가 후 실행 | preflight `goal_name_conflict 1 → ready=0`, 변경 없음 |
| U0003에 `MANUAL` ACTIVE 입출금 계좌를 하나 더 넣고 재실행 | preflight `withdrawal 1`(SEED `주거래 입출금`만 셈) → `ready=1`, 678 행 전부 불변(VERIFY 동일). 검토 지적 3번 반영 후 확인 |
| 3-3 뒤에 잔액 UPDATE와 `amount=-1` INSERT를 주입한 사본 실행 | `ERROR 3819 chk_savings_payment_amount`에서 클라이언트 중단, 트랜잭션 롤백으로 잔액·회차 원상태(VERIFY 동일). 작업 테이블은 남지만 다음 실행의 `DROP TABLE IF EXISTS`가 정리함을 확인 |

## 3. API 검증 (U0003 로그인, 실제 서버 응답)

| API | 결과 |
| --- | --- |
| `GET /deposit-savings?type=SAVINGS` | 3건(8190·8191·8192), 잔액·금리·개설·만기·월 약정·`detailsAvailable=true`, 마스킹 `110000-**-**340x`만 노출 |
| `GET /deposit-savings/summary` | totalCount 4, totalBalance 115,600,000(예금 1·20,000,000 + 적금 3·95,600,000) |
| `GET /deposit-savings/maturities?withinDays=365` | 목돈적금 1건(2027-04-15, 206일). 청약(2036)·청년도약(2030)은 창 밖 |
| `GET /deposit-savings/8190/maturity-forecast` (신규) | COMPLETE, principal 78,000,000, expectedPrincipal 84,000,000, 세전 9,669,506, 세액 1,489,103, 세후 8,180,403, **expectedAmount 92,180,403**, 206일 |
| `GET /deposit-savings/8191/maturity-forecast` | COMPLETE(합성 만기 2036-09-15, 3,647일), principal 12,100,000, expectedPrincipal 24,000,000(121회 + 예정 119회), 세전 6,754,136, 세액 1,040,136, 세후 5,714,000, **expectedAmount 29,714,000** |
| `GET /deposit-savings/8192/maturity-forecast` | COMPLETE, expectedPrincipal 30,000,000(11회 + 예정 49회), expectedAmount 32,906,253, 1,516일 |
| `GET /deposit-savings/4097/maturity-forecast` (524 예금) | COMPLETE, DEPOSIT, expectedAmount 21,032,120 — 예금 경로도 같은 API로 동작 |
| `GET /savings/{원문}/maturity-forecast` (기존 경로) | 목돈적금 COMPLETE 92,180,403 — 신규 경로와 같은 금액 |
| `GET /savings/{원문}` | 8190 상세(잔액·3.25·1,000,000·다음 납입 2026-10-15·만기 2027-04-15·상품 NULL), 청년도약 `financialProductId 95` → `GET /products/savings/95` = 신한은행 `신한 청년도약계좌` 60개월 4.5 |
| `GET /savings/payment-schedules` | 청약 `STORED_SCHEDULED` 2026-10-15 100,000 + 이후 `ESTIMATED_MONTHLY`, 3계좌 3개월 9건 합 4,800,000, **`complete=true`, reasons 없음** |
| `POST /cashflow/forecasts`, `GET /cashflow/risks` | `SAVINGS_*` 사유 없음(`incompleteReasons`는 `ACCOUNT_SCHEDULE_SOURCE_UNAVAILABLE`뿐, 678과 무관), 위험 0건, 90일 가용 12,660,000(적금 예정 4,800,000 반영) |
| `GET /deposit-savings/rate` | 9월 PAID 1,600,000 / 소득 5,700,000 = **28.07%**(678 이전 U0003은 적금이 없어 0%) |
| `GET /goals?type=SAVING` | 8192 `내 집 마련 준비` 95,600,000 / 150,000,000 / 63.7%, 기존 4 `노트북 바꾸기` 유지 |
| `GET /goals/8192`, `/accounts`, `/progress` | currentAmount 95,600,000, remaining 54,400,000, 63.7%, 연결 3건 totalBalance 95,600,000, startDate 2021-09-21, totalDays 2922·elapsed 1826, expectedProgress 62.5, gap +1.2 → **ON_TRACK** |
| `GET /goals/8192/required-saving` | monthsElapsed 60, **averageMonthlySaving 1,593,333**, requiredMonthlySaving 1,511,112, shortfall −82,221 → **SUFFICIENT** |
| `GET /goals/8192/forecast` | monthsToAchieve 35, expectedAchievementDate **2029-07-26**(목표일 57일 전), onSchedule true |
| `GET /goals/8192/risk` | riskLevel **LOW**, factors 없음 |
| `GET /goals/8192/history` | 기본 3개월 창 3건(2026-06-30·07-31·08-31 = 90.8M·92.4M·94.0M), 12개월 창 11건. 12개월 초과 창은 400(기존 계약) |
| 보안 | 무인증 401; U0003이 타인 계좌 4·입출금 계좌 1 → 404 `RESOURCE_NOT_FOUND`(같은 메시지); `accountId=0` → 400; U0004 목록에 678 계좌 없음, U0004의 8190 forecast·8192 goal → 404 |

H2 통합 테스트: `DepositSavingForecastApiIntegrationTest` 12건 통과(기대값은 위 실측과 같은 92,180,403). 기존 535·536·537 테스트 통과.

## 4. 기대값과의 차이·확인된 제약

- 계획서의 기대값(원금 95,600,000·진행률 63.7%·예상 수령액 92,180,403)과 실측이 일치한다.
- `GET /savings/{accountNo}/payments`는 조회 기간 최대 12개월·종료일 오늘 이하 계약이라 청약 121회를 한 번에 나열할 수 없다(기존 537 계약, 678 결함 아님). 12개월 창 안에서는 정상 조회된다.
- 청약 만기 2036-09-15는 합성값이다. 초안(만기 NULL)에서는 `payment-schedules`가 `complete=false`, 현금흐름 예측에 `SAVINGS_INCOMPLETE_SAVINGS_TERMS`가 남고 10월 이후 청약 납입이 예정에 빠지는 것을 실측했고, 2026-09-21 검토에서 사용자가 합성 만기(안 b)를 택해 위 결과로 바뀌었다. 실제 상품에는 만기가 없으므로 FE·발표에서 상품 조건으로 말하지 않는다.
- 정부기여금·비과세·청약 인정회차·순위·소득공제는 스키마에 없어 표현하지 않았다. U0003은 632 규칙상 43세로 실제 청년도약 요건 밖이다(시연 데이터).
- 청약·목돈적금 상품은 카탈로그에 넣지 않았다(비교·추천 오염 방지). `financial_product_id` NULL.
- 목록 응답 `accountName`의 한글은 서버 응답 자체는 정상이며(목록 호출 출력 확인), 보고서 작성 중 콘솔 인코딩으로만 깨져 보인 경우가 있었다.

## 5. 미검증·후속

- 시연 서버 적재는 하지 않았다(인프라 담당 절차). 서버 키로 fixture를 다시 만들어야 하며 절차는 seeds README 16번 절.
- 09/27 기준 점검은 기준일이 지나야 가능하다. 저장 PAID는 전부 09/15 이전이라 09/27에도 미래 PAID는 없다. 다음 예정일 10/15는 09/27에도 미래다.
- 자산 현황·진단·리포트 화면의 기대값 변화(U0003 자산 +9,560만원)는 확인하지 않았다. 13·14번 시연 스토리 담당과 재확인이 필요하다.
- FE `!` 아이콘·바텀시트는 FE 담당 범위이며 스펙은 `deposit-savings.md` 만기 예상(accountId) 절에 있다.
- 루트 `docs/` API 명세에는 신규 경로를 추가하지 않았다(명시 요청 시 반영).

## 6. 테스트·빌드

- `DepositSavingForecastApiIntegrationTest` 12건 통과. 관련 535·536·537 통합 테스트 통과.
- `.\gradlew.bat clean build`(전체 테스트 포함) **BUILD SUCCESSFUL**, 3분 43초. 테스트 1,326건 실행, 실패 0, 오류 0, 생략 24(MySQL 전용 등 조건부 테스트).
- `git diff --check` 공백 오류 없음. 작업 파일은 로컬 규약대로 CRLF.
- 커밋·push·MR·시연 서버 적재는 수행하지 않았다.

## 7. 2026-09-22 개정판 재검증 (급여 3,012,400 수지 반영)

### 7-1. 변경 수치

| 계좌 | 개정 전 | 개정 후 |
| --- | --- | --- |
| 신한 청년도약계좌 | 500,000 × 11회 = 5,500,000 | **300,000 × 11회 = 3,300,000** (개설·만기·다음 납입 동일) |
| 주택청약종합저축 | 100,000 × 121회 = 12,100,000 | 변경 없음 |
| 내집마련 목돈적금 | 1,000,000 × 78회 = 78,000,000, 만기 2027-04-15, 다음 납입 2026-10-15 | **500,000 × 60회 = 30,000,000 완납**, 개설 2021-03-15, **만기 2026-03-15(도래)**, SCHEDULED 없음, `next_payment_date` = 만기일, 상태 `ACTIVE` |
| 납입 행 | 211(PAID 210 + SCHEDULED 1) | **193(PAID 192 + SCHEDULED 1)** |
| 목표 `내 집 마련 준비` | 95,600,000 / 150,000,000 = 63.7333% | **45,400,000 / 150,000,000 = 30.2667%**, 이력 61건(GOAL_CREATED 9,600,000, 2026-08-31 45,000,000) |
| 월 납입 합(현금흐름 유출) | 1,600,000 | **400,000** |

결정 배경: 679가 U0003 급여를 5,700,000에서 3,012,400으로 낮추면서 8월 기준 저축 여력이 약 45만원이 됐다. 목돈적금은 삭제하지 않고 이미 만기가 지난 계좌로 바꿔 목표 확보액과 자산 합계에 원금 3,000만원을 남겼다. 목표는 의도적으로 뒤처진 상태(`INSUFFICIENT`)가 되어 발견 화면의 `SAVING_GOAL_RISK`에 노출된다.

만기 지난 계좌를 `ACTIVE`로 둔 이유: `CLOSED`는 계좌 요약·자산 합계에서 제외된다(`AccountAnalysisService`). `next_payment_date`를 NULL로 두면 `SavingsScheduleCalculator`가 만기 검사보다 먼저 `INCOMPLETE_SAVINGS_TERMS`를 사용자 예측 전체에 남기므로 만기일을 넣었다. 만기가 지난 `ACTIVE` 적금은 예정 납입을 만들지 않고 사유도 남기지 않는다(실측 확인).

### 7-2. Seed 적재

일회용 Docker `dodam-679-mysql`(`dodam_679_test`, V1~V41 + 1~17번이 개정 전 상태로 적재됨)에서 실행. 기존 세 계좌(9213·9214·9215)의 해시·암호문을 fixture 테이블로 옮겨 같은 키로 만든 fixture와 동일한 재실행 경로를 탔다(새 키의 fixture는 별도 계좌를 추가하므로 쓰지 않았다).

- preflight `ready=1 / user 1 / withdrawal 1 / fixture 3 / valid 3 / distinct 3 / product 1 / payments 193 / future_paid 0 / hash_conflict 0 / goal_name_conflict 0 / marker_goal 1 / link_conflict 0`.
- 계좌·납입·목표가 7-1 표와 일치. `account_id`·`goal_id` 유지.
- 새 fixture로 한 번 더 실행: VERIFY Q1~Q5 출력 `diff` 없음(납입 체크섬 1685565487), 작업 테이블 0.
- 개정 전 상태에서 개정판을 돌려도 계좌 upsert·납입 삭제 후 재생성·이력 재계산으로 수렴한다(위 실행이 그 경로다).

### 7-3. API 실측 (678 JAR, 같은 DB에 개정판 17번까지 적재, U0003 임시 비밀번호로 로그인 후 원복)

| API | 결과 |
| --- | --- |
| `GET /deposit-savings?type=SAVINGS` | 3건. 목돈적금 `expiryDate 2026-03-15`·`monthlyAmount 500,000`·`ACTIVE`, 청년도약 `monthlyAmount 300,000` |
| `GET /deposit-savings/summary` | totalCount 4, totalBalance **65,400,000**(예금 20,000,000 + 적금 45,400,000) |
| `GET /deposit-savings/maturities?withinDays=365` | 0건(목돈적금은 이미 만기, 나머지는 창 밖) |
| `GET /deposit-savings/9215/maturity-forecast` (청년도약) | COMPLETE, principal 3,300,000, expectedPrincipal 18,000,000, 세전 2,061,172, 세액 317,420, 세후 1,743,752, **expectedAmount 19,743,752**, 1,515일 |
| `GET /deposit-savings/9213/maturity-forecast` (목돈적금) | COMPLETE, `daysUntilMaturity` **−191**, expectedPrincipal 30,000,000, 세후이자 2,095,496, expectedAmount 32,095,496. 적금 경로는 만기 경과에 `ALREADY_MATURED` 사유를 내지 않는다(예금 경로 전용) |
| `GET /savings/payment-schedules` | `complete=true`, reasons 없음, 3개월 6건 1,200,000(청약 STORED_SCHEDULED 10/15 + ESTIMATED_MONTHLY) — 목돈적금 예정 없음 |
| `POST /cashflow/forecasts` (9/22, 90일) | `SAVINGS_*` 사유 없음. 이벤트 8건: 대출 10/10 410,331·카드 10/15 845,390·적금 10~12월 매월 300,000 + 100,000. 최소 잔액 15,004,279 |
| `GET /deposit-savings/rate` | 9월 400,000 / 3,012,400 = **13.28%** |
| `GET /goals/8192/progress` | 45,400,000 / 150,000,000, 30.3%, expectedProgress 62.5, gap −32.2 → **BEHIND** |
| `GET /goals/8192/required-saving` | monthsElapsed 60, averageMonthlySaving **756,666**, requiredMonthlySaving 2,905,556, shortfall 2,148,890 → **INSUFFICIENT**, 소득 대비 96.5% |
| `GET /goals/8192/forecast` | monthsToAchieve 139, expectedAchievementDate **2038-03-30**, onSchedule false |
| `GET /goals/8192/risk` | riskLevel **HIGH**, factors `PACE_INSUFFICIENT`·`BEHIND_SCHEDULE` |
| `POST /diagnoses {forceRefresh}` | 5건: `SAVING_GOAL_RISK` HIGH(내 집 마련)·MEDIUM(노트북 바꾸기), FOOD +51.5%, CAFE +45.0%, 고금리 대출 |
| 보안 | 무인증 401 |

### 7-4. 미검증·주의

- Gradle 빌드·테스트는 다시 돌리지 않았다(Java 변경 없음, seed·문서만 개정). H2 통합 테스트 fixture는 seed와 무관하다.
- 시연 서버 적재 절차는 그대로다(서버 키로 fixture 생성 → 16번). 개정 전 16번이 적재된 서버라면 같은 절차의 재실행으로 수렴한다.
- 계획서 `demo-deposit-savings-678-plan.md`의 수치는 개정 전이며 수정하지 않았다.
