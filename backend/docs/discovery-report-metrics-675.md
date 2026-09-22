# 발견·리포트 지표 계약과 비효율 데이터 요건 (S15P21D105-675)

작성일: 2026-09-22. 작성 구동재(BE-A). 확인 대기: 정현진(비효율 항목 판단), FE 114(지표 공유표).

티켓 작업 내용: 정현진과 비효율 항목의 **대상·증상·필요 데이터·기간·기대 판정**을 정한다. FE 114에 **비교군·단위·기준선·null 의미**를 공유하고 필요한 서버 설명과 계약만 보완한다.
완료 기준: 지표 계약과 비효율 항목의 **적용·보류 판단**이 기록되고, 추가 구현은 **담당·범위·완료 조건**을 가진 후속 작업으로 정리된다.

FE 요청 원문: [docs/discovery-backend-request.md](../../docs/discovery-backend-request.md)(607), [docs/report-backend-handoff.md](../../docs/report-backend-handoff.md)(605).
기존 계약: [diagnosis-api.md](diagnosis-api.md), [recommendation-api.md](recommendation-api.md), [improvement-report-contract.md](improvement-report-contract.md).

> 표 안의 **제안**은 구동재가 코드·기획안·seed를 근거로 적은 초안이며 정현진 확인 전이다. 확인되면 `제안` 표시를 지우고 날짜를 남긴다.
> 이 티켓에서 바꾼 서버 코드는 §4의 두 항목(추가 필드)뿐이다. 나머지 구현은 §5 후속 작업으로 나갔다.

---

## 1. 용어

| 용어 | 뜻 |
| --- | --- |
| 비효율 항목 | 진단 규칙 하나가 만드는 `diagnosis_issue`의 `domain/type`. 발견 화면의 한 줄 |
| 대상 | 규칙이 보는 단위(카테고리·대출·계좌·카드·목표·예측 위험) |
| 증상 | "이 값이 기준선보다 이렇게 벗어나면 비효율"의 문장 |
| 기준선 | 비교 대상(직전 기간 평균·시장 공시금리·군집 분포·정책 상수) |
| 기간 | 판정에 쓰는 데이터 기간(진단 접수일 기준) |
| 기대 판정 | 시연 계정(U0001~U0004, 특히 U0003 김싸피)에서 기대하는 결과 |
| 적용/보류 | 적용 = 지금 문제를 만든다 · 비노출 = 만들되 발견에 안 올린다 · 보류 = 만들지 않고 coverage 사유만 남긴다 |

---

## 2. 비효율 항목표 (정현진과 확정)

규칙 버전 `v7-cluster-hybrid`. 소스: `DiagnosisEvaluator`·`SpendingIncreaseRule`·`HighInterestLoanRule`·`ClusterDeviationRule`.

### 2-1. 지금 문제를 만드는 항목

| domain/type | 대상 | 증상 | 필요 데이터 | 기간 | 기준선·임계값 | 기대 판정(시연) | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SPENDING/CATEGORY_SPENDING_INCREASE | 거래 카테고리(미분류 제외) | 지난달 소비가 직전 3개월 월평균보다 20% 초과·1만 원 이상 많다 | `transaction`(EXPENSE, 숨김 제외, category_id) | 지난 완료 월 vs 그 직전 3개월(631) | 직전 3개월 월평균. 심각도 HIGH ≥ 50% | U0003: 8월 외식 520,000·카페 145,000 정점(636) → 외식·카페 2건 기대. 9월 진단은 8월을 본다 | **적용**. 임계값 20%·1만 원은 API 6과 공유한 초기값 — 단월 비교로 건수가 늘면 조정(**제안**: 유지) |
| LOAN/HIGH_INTEREST_RATE | 잔액 있는 ACTIVE·OVERDUE 대출 | 보유 금리가 같은 비교군·기간 밴드·상환방식 공시 최저금리 중앙값보다 1.0%p 이상 높다 | `loan`, `financial_product(_option)` 공시금리 | 진단 접수일 현재 | 공시 최저금리 중앙값(`DISCLOSED_MIN_RATE_MEDIAN`). HIGH ≥ 2.0%p 또는 연체 | U0003: 연 8.5% 신용대출 1건(633) → 1건, 대환 절감액은 잔여 회차와 같은 `term` 옵션이 있을 때만 | **적용**. 대환 후보 옵션 제약(정확한 잔여 회차) 완화 여부 **제안**: 시연 뒤 |
| CASHFLOW/BALANCE_SHORTAGE | 저장된 최신 현금흐름 예측의 위험 | 예정 출금 직전 예상잔액이 음수 | `cashflow_forecast`(baseDate = 접수일)·`cashflow_risk` | 예측 기간(기본 30일) | 잔액 0 | U0003: 급여 3,012,400·월 40만 적금·구독(679·678)에서 부족 없음 기대. 예측이 당일 것이 아니면 `STALE_FORECAST` | **적용**. 진단이 예측을 새로 돌리지 않는 것은 유지 |
| `*`/CLUSTER_* (+ RULE_AND_CLUSTER 병합) | 패턴 분석 특징 5종 | 배정 군집 5~95분위 범위를 점검 방향으로 `deviation ≥ 0.25` 벗어남 | `pattern_analysis.cluster_stats_json` | 분석 window(6개월), 접수일 −30일 이내 | 군집 분포. HIGH ≥ 1.0 | 시연 4계정은 성향이 갈리게 seed됨 → 계정마다 0~2건 | **적용**(641). 임계값은 초기값, 실측 뒤 조정(**제안**: 유지) |

### 2-2. 만들되 발견에 올리지 않는 항목

| domain/type | 대상 | 증상 | 왜 비노출 | 어디서 쓰는가 | 판단 |
| --- | --- | --- | --- | --- | --- |
| GOAL/SAVING_GOAL_RISK · SPENDING_GOAL_RISK | ACTIVE 목표 | 기한 경과·연결계좌 없음·저축속도 부족·일정 지연 / 예산 초과 | 목표 화면이 같은 판정을 보여 주고, 제목이 고정 문구라 목표 수만큼 같은 줄이 쌓인다(607) | 546 goal-impact(관련 문제)·changes(해소 키), 코치 요약, 저축 늘리기 추천 → 저축 프리셋(619 goalId), 678 seed가 U0003 '내 집 마련 준비'를 일부러 `SAVING_GOAL_RISK`로 만들어 둠 | **비노출 유지**(675 결정). 서버는 발견 개수·요약에서만 뺀다(§4). FE `isHiddenFromDiscovery` 유지. **정현진 확인 필요** — 607은 "만들지 말라"였다 |

### 2-3. 보류 항목 (coverage `RULE_POLICY_NOT_DEFINED`)

| ruleKey | 대상 | 증상(제안) | 필요 데이터 → 보유 | 기간(제안) | 기준선·임계값(제안) | 기대 판정(시연) | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| DORMANT_ACCOUNT | 수시입출금 계좌 | 잔액 > 0이고 N개월 입출금 없음 | `account.balance`·`last_transaction_date`(없으면 적재일) → **있음**, API 5 `/accounts/inactive`(503)가 같은 규칙 | 접수일 −6개월 | 6개월, 소액 상한 없음(503 기본) | 491 seed의 `last_transaction_date` 채움 여부 미확인 — 비어 있으면 적재일 기준이라 0건 | **보류 → 적용 후보 1순위**. 정책만 필요(503 상수 재사용). 새 domain `ACCOUNT` FE 라벨 필요 |
| FIXED_SPENDING_INCREASE / VARIABLE_SPENDING_INCREASE | 지출 유형(FIXED/VARIABLE) | 유형별 지난달 합이 직전 3개월 평균보다 20%·1만 원 초과 | `transaction.spending_type` → **있음** | 627과 같음 | 627과 같음 | U0003 8월 변동소비 994,000은 평이(636) → 0건 기대 | **보류**. 카테고리 문제와 **같은 거래가 두 번** 잡히는 표현(병합 또는 생략) 결정 선행 |
| LOAN_INTEREST_BURDEN | 대출 보유자 | 월 이자 / 월소득 ≥ X% | 월 이자(API 11 532)·`financial_profile.monthly_income` → **부분**(현재 월 전체 상환액 확정 불가, 532 문서) | 다음 상환 회차 | X 미정 | U0003 월 이자 약 9만 / 소득 3,012,400 ≈ 3% | **보류**. 경계·"현재 월" 정의가 다른 도메인 변경 요구 |
| LOAN_REPAYMENT_BURDEN | 대출 보유자 | 월 원리금 / 월소득 ≥ X% | API 11 `repayment-burden`(미래 월만) → **부분** | 다음 달 | X 미정(DSR류 40%는 팀 확정값 아님) | 위와 같음 | **보류**. 위와 같음 |
| LOW_USAGE_CARD | 보유 카드 | 최근 N개월 사용 0건 또는 연회비 > 예상 혜택 | `transaction.card_id`·`card.annual_fee`·혜택 활용도(528) → **있음**(혜택 JSON 없는 카드는 활용도 null) | 접수일 −3개월 | N=3, 사용 0건 | 522 카드 seed 보유, 625·636 카드 거래 있음 → 사용 카드 0건, 미사용 카드 있으면 1건+ | **보류 → 적용 후보**. 사용 건수 기준이면 정책만. 새 domain `CARD` |
| CARD_PAYMENT_BURDEN | 청구월 카드 청구서 | 청구액 합 / 월소득 ≥ X% | `card_billing_statement.total_balance`·월소득, API 8 `burdenRate`(528) → **있음** | 다음 청구월 | X 미정(**제안** 30%) | 522 seed 청구서 존재 여부 미확인(H2 테스트는 빈 테이블) | **보류 → 적용 후보 2순위**. 경계만 필요 |
| SAVING_CASHFLOW_BURDEN | 예정 적금 납입 | 납입 때문에 잔액 부족 | `savings_payment`(SCHEDULED)·예측 이벤트/위험 → **있음** | 예측 기간 | 납입 제외 잔액과 비교 | U0003 월 40만 납입(678)에도 부족 없음 기대 | **보류**. `BALANCE_SHORTAGE`가 이미 납입 포함 부족을 잡는다. "납입 때문에"를 분리하려면 현금흐름 계산 버전 변경(538) |
| goal-impact FINANCING_COST_DELAY | 대출 이자 vs 목표 | 이자를 저축에 돌렸으면 며칠 빨랐나 | 월 이자(532)·목표 forecast(546) → **부분** | 접수일 | `expectedDate(remaining, pace + 월이자)` 비교(696의 소비 증가와 같은 식) | U0003: 월 9만 원 추가 저축 → 1억 5천 목표에서 며칠 수준 | **보류**. 정의 확정 뒤 goal-impact에 필드 추가(추가만) |

627 계획에서 이미 제외한 미사용 구독(사용 기록 없음)·유휴자금(보존 기준 없음)은 결정 유지.

### 2-4. 켤 때 공통으로 따라오는 일

`DiagnosisRuleVersion` v8, 진단 캐시 버전별이라 첫 요청이 새 진단, 효율 점수 INEFFICIENCY 항목(문제 수 감점) 하락 → 시연 계정 점수 변화 확인, 새 domain(`ACCOUNT`·`CARD`)은 FE `discoveryDomainLabel`·`DOMAIN_ICONS`·타입 union 추가(없으면 GOAL 아이콘 폴백), 추천 생성기가 없으면 발견 상세에 개선안이 없다.

---

## 3. FE 114 공유표 — 지표별 비교군·단위·기준선·null 의미

### 3-1. 발견 화면

| 화면 값 | 서버 필드 | 비교군 | 단위 | 기준선 | null 의미 |
| --- | --- | --- | --- | --- | --- |
| "진단에서 N건 발견" | `GET /diagnoses/latest`·`/{id}`·`POST /diagnoses` → `discoveryIssueCount`(675) | 본인 최신 완료 진단 | 건 | — | 없음(항상 정수). `issueCount`(GOAL 포함)와 다르다 |
| "전체 개선 시 연 N원 절감" | `GET /recommendations/summary` → `discovery.expectedAnnualCostSaving` | 최신 진단의 열린 추천(PENDING·ACCEPTED), GOAL 문제 추천 제외(675) | 원/년 | 문제별 최대 대안 하나 합산 | 금액 있는 추천 없음(`ANNUAL_SAVING_UNAVAILABLE`). 0원이 아니다 |
| "목표 N일 단축" | `discovery.expectedGoalAdvanceDays` | 같음 | 일 | 추천별 `advancedDays` 최댓값(합산 아님, `GOAL_ADVANCE_IS_MAX_NOT_COMBINED`) | 값 있는 추천 없음. 675 시점엔 GOAL 제외로 대체로 null → 696 뒤 소비 증가 추천이 채움 |
| 발견 한 줄 "연 N원" | 추천 `effect.annualCostSaving` | 그 문제의 대안 중 최대 | 원/년 | 단월 증가 ×12(`annualizationBasis`) | 효과 없는 추천(군집 이탈·대환 추정 불가) |
| 소비 증가 상세 | `currentValue.monthlyAverage`(=지난달) vs `benchmarkValue.previousMonthlyAverage` | 본인 과거 | 원/월, `increaseRatePercent` % | 직전 3개월 월평균(`baselinePeriod`) | 키 없음 = v5 이전 진단(`ruleVersion`으로 분기) |
| 대출 상세 | `currentValue.interestRate` vs `referenceRate`, `rateDifference` | 같은 비교군·기간 밴드·상환방식 공시 상품(`comparableProductCount`) | 연 %, %p | 공시 최저금리 중앙값(`referenceMethod`) | `refinance.available=false` → 절감액 없음(`unavailableReason`: LOAN_OVERDUE·SCHEDULE_NOT_COMPUTABLE·NO_ELIGIBLE_PRODUCT). 수수료 제외(`feesIncluded=false`) |
| 현금흐름 상세 | `currentValue.riskDate`, `expectedBalance` | 본인 예측 | 원(출금 직전 잔액, 음수 = 부족액) | 0 | 필요 금액·부족 비율은 **저장 안 함** — `evidence.forecastId`로 이벤트 API 조회(§5) |
| 군집 카드 | `clusterContext.userValue`, `p5`·`p95`, `deviation`, `percentileInCluster` | 배정 군집(`clusterLabel`, `sampleSize`) | 비율(0~1) 또는 특징 단위, 분위 | 군집 5~95분위 | `clusterContext` 없음 = 군집 신호 없는 규칙 문제 |
| 상세 지연 배너 | 진단 `goal-impact.goals[].daysDifference` | 목표 전체 | 일(양수 = 늦음) | 목표일 | forecast 없음(v3 이전) 또는 저축속도 0. 카테고리 귀속 지연은 696 뒤 추천 `effect.advancedDays` |

읽어도 되는 스냅샷 키 전체는 §6 표. 키는 진단 `ruleVersion` 안에서 고정이며 추가만 한다.

### 3-2. 월간 리포트

| 화면 카드 | 서버 필드 | 비교군 | 단위 | 기준선 | null 의미 |
| --- | --- | --- | --- | --- | --- |
| 효율 점수 6항목 | `efficiency.dimensions[]`(635·680) | 본인 그 달 | 0~100점, `value`는 비율 0~1·건·점수 | 항목별 임계값(improvement-report-contract §6-1) | `score=null`+`unavailableReason`(INCOME_MISSING·NO_SPENDING·CREDIT_SCORE_MISSING·FORECAST_NOT_FOUND·DIAGNOSIS_NOT_FOUND) |
| 전월 점수 차 | `efficiency.previousMonthDifference` | 전월 저장본(같은 산식) | 점 | 전월 | 첫 거래 이전 달 또는 한쪽 점수 없음 |
| 합계·전월 비교 | `totalIncome/Expense/Spending/Saving`, `previousMonth`, `changeFromPreviousMonth` | 본인 | 원 | 전월 | `previousMonth=null` = 비교 대상 없음(첫 거래 이전). 전월 거래 0건은 0원 |
| 카드 사용액 | `cardSpending`·`PreviousMonth`·`Difference` | 본인 | 원 | 전월 | 전월 없음 |
| 또래 비교 | `GET /patterns/me/peer-comparison`(632·635) | 동년배·군집 | 비율·분위 | 군집 중앙값 | `status=unavailable`+message |
| 다음 달 예상 지출 | `GET /reports/monthly/upcoming-expenses` | 본인 예정 | 원 | — | `partial`·`warnings`로 미수집 원본 표시 |
| **주요 지표·금융 생활 패턴** | 지금은 FE가 개별 API 조합 → **697**에서 `metrics.keyMetrics[]`·`patterns[]` | 본인·전월 | 비율 0~1 / 원 | 저축률 20%(제안), 진단 규칙, 예측 위험 | 697 계약: UNAVAILABLE(자료 없음)과 GOOD(위험 없음) 구분, 대출 잔액 전월은 `HISTORY_NOT_STORED` |

### 3-3. 신용

| 값 | 서버 필드 | 비교군 | 단위 | null 의미 |
| --- | --- | --- | --- | --- |
| 신용점수 | `GET /users/me` → `creditScore`, `creditScoreType`(ESTIMATED/VERIFIED), `creditScoreModelVersion`(680) | 4,000명 합성 | 1~1,000점 | 산정 전. ESTIMATED는 "도담 예상 신용점수"로 표기, KCB/NICE로 표기 금지 |
| 신용등급 | `creditRating` | SSAFY 금융망 자산 기준 | A~E | 동기화 전. 점수와 다른 축이다 |

---

## 4. 이 티켓에서 보완한 서버 계약 (코드 변경, 추가 필드만)

| 변경 | 정의 | 왜 |
| --- | --- | --- |
| `DiagnosisResponse.discoveryIssueCount` | `issueCount − GOAL 도메인 문제 수` | 발견 홈이 목록 길이를 세지 않게. `issueCount`·`highSeverityCount`·`overallStatus`는 GOAL 포함 그대로 |
| `recommendations/summary.discovery` | `issueCount` = 위 값, `openRecommendationCount`·절감액·단축일은 GOAL 문제에서 나온 추천 제외, `notes`에 `GOAL_ISSUES_EXCLUDED` | 화면 목록에 없는 항목의 숫자가 요약에 들어가지 않게(607 요청 1의 실제 문제) |

GOAL 문제 자체는 계속 만든다(§2-2). FE는 현재 코드 그대로 동작하며, 전환하려면 `DiagnosisApiResponse.discoveryIssueCount: number` 타입 추가 뒤 `DiscoveryHome`의 개수·`discoverySummary()`를 서버 값으로 바꾼다.

---

## 5. 후속 작업 (담당·범위·완료 조건)

| 티켓 | 범위 | 담당 | 완료 조건 | 상태 |
| --- | --- | --- | --- | --- |
| **S15P21D105-696** [BE] 소비 증가 추천 목표 기여 계산 | `REDUCE_CATEGORY_SPENDING`에 `currentForecastDate`·`improvedForecastDate`·`advancedDays`·`contributionAmount`, 대상 목표 → `Recommendation.goalId`(후보 goalId), 근거 `GOAL_FORECAST_ADVANCE`. 607 요청 2 | 구동재 | 생성기 `675.1`→`696.1`, 단위·통합 테스트, U0003 재진단 실측, FE effect 타입 3종 안내 | 코드·테스트 완료, 브랜치 준비 |
| **S15P21D105-697** [BE] 월간 리포트 주요 지표·금융 생활 패턴 | `MonthlyReport.metrics`(V43), 조립기 `report-metrics-v1`, 기존 리포트 보강. 605 요청 | 구동재 | V43, 통합 테스트, 서버 8월 리포트 확인, FE 개별 호출 9회 대체·`unavailable` 상태 안내 | 코드·테스트 완료, 브랜치 `S15P21D105-697-report-metrics` |
| (미배정) 휴면계좌 규칙 | §2-3 DORMANT_ACCOUNT. 503 재사용, v8, FE domain ACCOUNT | BE-A | seed `last_transaction_date` 확인 → 규칙 → 추천 생성기(계좌 정리 권고) | 정책 확정 대기 |
| (미배정) 카드 결제 부담 규칙 | §2-3 CARD_PAYMENT_BURDEN. 528 재사용, 경계 X% | BE-A | seed 청구서 확인 → 규칙 → 추천 | 정책 확정 대기 |
| (미배정) 현금흐름 위험 필요 금액 저장 | `cashflow_risk.required_amount` 또는 이벤트 API 조회 가이드 | 538 담당 | FE 현금흐름 상세 타임라인 | 시연 뒤 |
| (미배정) 가맹점 정규화 → `topMerchants[]` | 진단 스냅샷에 가맹점 분해 저장 | BE-A | FE 집계 제거 | 시연 뒤 |
| (미배정) 대출 잔액 월별 스냅샷 | 697 `LOAN_BALANCE` 전월 비교 | BE-B(loan) | `HISTORY_NOT_STORED` 제거 | 시연 뒤 |

---

## 6. 발견 상세가 읽어도 되는 스냅샷 키 (도메인별)

규약: `currentValue`·`benchmarkValue`·`clusterContext`의 아래 키는 진단 `ruleVersion` 안에서 고정이다. 키 추가는 언제든, 삭제·의미 변경은 `DiagnosisRuleVersion`을 올리며 한다. 표에 없는 키는 읽지 않는다.

| domain/type | `currentValue` | `benchmarkValue` | 화면 계산 규칙 |
| --- | --- | --- | --- |
| SPENDING/CATEGORY_SPENDING_INCREASE | `categoryId, categoryCode, categoryName, monthlyAverage, lastMonthAmount, increaseAmount, increaseRatePercent, consecutiveMonths, recentPeriod{startMonth,endMonth,months}` | `previousMonthlyAverage, thresholdRatePercent, minimumIncrease, baselinePeriod{startMonth,endMonth,months}` | 지금 FE가 읽는 키 그대로 확정 |
| LOAN/HIGH_INTEREST_RATE | `loanId, accountName, institutionName, loanType, repaymentType, status, balance, interestRate, comparisonGroup, referenceRate, referenceConfidence, rateDifference, comparableProductCount, overdue, maturityDate, refinance{available, unavailableReason, targetProductId, targetProductName, targetCompanyName, appliedRate, optionTerm, remainingInstallments, beforeInterest, afterInterest, interestSaving, monthlySaving, feesIncluded, assumptions[]}` | `thresholdMode, rateSpreadThreshold, referenceMethod, referenceRate, highSeverityRateSpread` | 내 금리 `interestRate` vs 시장 `referenceRate`, 편차 `rateDifference`(%p). 절감액은 `refinance.available`일 때 `interestSaving`·`monthlySaving`, "수수료 제외". 계좌번호 없음 |
| CASHFLOW/BALANCE_SHORTAGE | `riskId, riskDate, type, severity, message, expectedBalance` | 없음 | 예정 출금일 `riskDate`, 부족액 `−expectedBalance`. 타임라인은 `evidence.forecastId`로 `GET /cashflows/…/events` |
| `*`/CLUSTER_* · RULE_AND_CLUSTER | 단독: `feature, displayName, userValue, direction, deviation` | 단독: `p5, p95, mean, sampleSize, deviationThreshold, highSeverityDeviation` | 병합·단독 공통 `clusterContext`(diagnosis-api.md 641 절). `deviationScore`는 개별 `deviation`보다 클 수 없다 |
| GOAL/*_GOAL_RISK | `requiredMonthlySaving, averageMonthlySaving, progressRatePercent, expectedProgressRatePercent` / `monthlyBudget, spent, projectedMonthEnd, usageRatePercent` | 목표 위험 정책 상수 | 발견 비노출. 목표 화면·goal-impact가 쓴다 |
