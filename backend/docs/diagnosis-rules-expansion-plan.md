# 발견 화면 진단 규칙 확장 계획 — 기획안 5-2 미구현 규칙

작성일: 2026-09-17. 상태: **계획(미착수)**. Jira 이슈 미생성 — 착수 전 이슈를 만들고 MR 단위로 하위 작업을 나눈다.
선행 계획: [diagnosis-627-plan.md](diagnosis-627-plan.md)(소비 증가·고금리 대출). 이 문서는 627이 남긴 `RULE_POLICY_NOT_DEFINED` 규칙을 같은 방식으로 채우는 계획이다.

## 1. 배경 — 기획안 5-2 대비 현재 구현

`DiagnosisEvaluator`가 만드는 문제는 4계열(`GOAL`, `CASHFLOW`, `SPENDING/CATEGORY_SPENDING_INCREASE`, `LOAN/HIGH_INTEREST_RATE`)이고, 추천 유형은 `REDUCE_SPENDING`, `REDUCE_CATEGORY_SPENDING`, `INCREASE_MONTHLY_SAVING`, `REFINANCE_LOAN` 4종이다. FE 발견 화면은 이 결과를 그대로 쓰므로 화면이 허전한 원인은 FE 미연동이 아니라 규칙 수다.

| 기획안 5-2 항목 | 현재 | 판정 로직이 이미 있는 곳 |
| --- | --- | --- |
| 모으기·줄이기 목표 위험 | ✅ `GOAL` | — |
| 공통 현금흐름 위험 3종 | ✅ `CASHFLOW` | — |
| 변동지출 급증 | ✅ 627 `CATEGORY_SPENDING_INCREASE`가 이 항목이다 | — |
| 고금리 부채 | ✅ 627 | — |
| 특정 카테고리 소비 집중 | 기획안이 "경고 출력하지 않음"으로 정함 → **제외** | — |
| 소비 유형 군집 이탈 | 2026-09-16 시연 범위 제외 결정 → **제외** | — |
| 휴면성 소액계좌 | ❌ | `AccountAnalysisService.getInactiveAccounts` (API 5, BE-A) |
| 고정지출 증가 | ❌ | `SpendingAnalysisService.getBreakdown(FIXED)` 월별 행 (API 6, BE-A) |
| 이자비용 부담 | ❌ | `LoanAnalysisService.costComparison` (API 10) |
| 월 상환 부담 | ❌ | `LoanAnalysisService.repaymentBurden` (API 10, 532 보류 영향) |
| 저사용 연회비 카드 | ❌ | `CardAnalysisService.getLowUsageCards` (API 8) |
| 카드 결제 부담 | ❌ | `CardAnalysisService.getPaymentBurden` (API 8) |
| 카드 혜택 미활용 | ❌ | `GET /products/cards/recommended` 비교 로직 (API 18) |
| 할부 부담 | ❌ | `InstallmentQueryService.getBurden` (API 9, !152) |
| 저축으로 인한 현금흐름 부담 | ❌ | `SavingsCashflowService.cashflowBurden` (API 11-2) |
| 예·적금 금리 비교 | ❌ | `DepositRateComparisonService`/`SavingsRateComparisonService.rateComparison` (API 11) |
| 현금서비스·리볼빙 이용 | ❌ | **데이터 없음.** `card` 엔티티·리더에 리볼빙/현금서비스 이용 정보가 없다 → 제외 |

## 2. 설계 원칙 (627과 동일)

- 판정은 기존 도메인 서비스를 **재호출**한다. 복제하면 자산 탭 조회 API와 발견 화면의 판정이 갈라진다. 다른 담당 도메인(계좌·소비)도 서비스 재호출만 하고 코드는 손대지 않는다.
- 진단은 시뮬레이션 행을 만들지 않는다. 효과 추정이 필요하면 계산기만 호출한다.
- 규칙 1개 = `*Rule`(진단) + `*Generator`(추천) + 필요 시 프리셋 분기 + 테스트. 문제가 있으면 추정이 없어도 추천은 만든다(효과는 비움).
- `DiagnosisIssue.currentValue`에 규칙이 계산한 수치를, `evidence`에 `ruleKey`·`targetId`·원천 서비스 응답을 남긴다. 생성기는 `currentValue`만 읽는다.
- 임계값은 상수로 두고 `DiagnosisRuleVersion`을 올린다. 팀 확정 전까지 임의값임을 문서에 남긴다.
- **규칙이 7개 이상 늘어나므로 `DiagnosisEvaluator`에 규칙별 private 메서드를 더 붙이지 않고** `DiagnosisRule` 인터페이스(`ruleKey()`, `evaluate(userId, asOf) → issues + coverage`)를 추가해 목록으로 돈다. 627 규칙 2종도 인터페이스에 맞춘다(동작 변경 없음). `GOAL`·`CASHFLOW`는 스냅샷 구조가 달라 그대로 둔다.

## 3. 규칙별 설계

문제 domain/type과 추천 domain/type, 심각도, 효과, 프리셋. 심각도 경계는 모두 **임의값(스크럼 확정 대상)**.

### MR 1 — 계좌·카드 (재호출만으로 끝나는 규칙)

| 규칙 | 판정 | 심각도 | 추천 | 효과 | 프리셋 |
| --- | --- | --- | --- | --- | --- |
| `ACCOUNT/DORMANT_ACCOUNT` | `getInactiveAccounts(userId, null, null)` 기본값(N개월·잔액 상한 없음). 계좌당 문제 1건, `targetId = accountId` | LOW | `ACCOUNT/CONSOLIDATE_IDLE_BALANCE` "휴면 계좌 잔액 정리" | 없음 | 없음 |
| `CARD/LOW_USAGE_CARD` | `getLowUsageCards(userId, 기본 lookback, 기본 threshold)`. 카드당 문제 1건, `targetId = cardId` | `annualFee > 0` → MEDIUM, 아니면 LOW | `CARD/CANCEL_LOW_USAGE_CARD` "보유 필요성 점검" | `annualCostSaving = annualFee`, `monthlyCostSaving = annualFee / 12` (연회비 없으면 없음) | 없음 |
| `CARD/CARD_PAYMENT_BURDEN` | `getPaymentBurden(userId, null)` → `burdenRate`. 소득 없으면 coverage `UNAVAILABLE/INCOME_MISSING` | ≥ 40% MEDIUM, ≥ 60% HIGH | `SPENDING/REDUCE_SPENDING` 재사용 | `monthlyCostSaving = 결제예정액 − 소득×40%`, `annual = ×12` | `/simulations/spending`(기존 분기 재사용) |

- 휴면 계좌는 "정리 대상"이 아니라 "점검 대상"이다(기획안). 문구를 단정하지 않는다.
- `getLowUsageCards`는 `LocalDate.now(SERVICE_ZONE)`을 직접 쓴다. 진단 기준일과 최대 하루 어긋날 수 있고 테스트에서 시각을 고정할 수 없다. **`asOf`를 받는 오버로드를 추가**한다(BE-B 도메인이라 가능).
- `CARD_PAYMENT_BURDEN`은 기획안의 "과거 평균 대비 급증" 조건은 이번에 넣지 않는다(청구 이력 3~6개월 조회가 별도 필요).

### MR 2 — 대출·할부·저축 부담 (소득 기반 부담률)

| 규칙 | 판정 | 심각도 | 추천 | 효과 | 프리셋 |
| --- | --- | --- | --- | --- | --- |
| `LOAN/LOAN_REPAYMENT_BURDEN` | `repaymentBurden(userId, null)` → `burdenRate`. `INCOME_MISSING/ZERO`면 UNAVAILABLE. `reasons`가 남으면 EVALUATED + `PARTIAL_MONTHLY_TOTAL`(532 보류 사유) | ≥ 30% MEDIUM, ≥ 40% HIGH | `LOAN/PREPAY_HIGHEST_RATE_LOAN` "금리 높은 대출부터 상환" | 이번 범위 없음 | `/simulations/loan/prepayment`에 `loanId`만 채운 프리셋(금액은 FE 입력) |
| `LOAN/LOAN_INTEREST_BURDEN` | `costComparison(userId, 12)` → 향후 12개월 이자 총액과 대출별 비중. 총액 ≥ 30만 원 AND 한 대출 비중 ≥ 50% → 그 대출 1건 | MEDIUM | 같은 대출에 `HIGH_INTEREST_RATE`가 있으면 **추천 생략**(`SkipReason` 신설 `COVERED_BY_OTHER_ISSUE`), 없으면 `PREPAY_HIGHEST_RATE_LOAN` | 없음 | 위와 동일 |
| `INSTALLMENT/INSTALLMENT_BURDEN` | `getBurden(userId, null)` → `burdenRatePercent`(가용금액 대비). `unavailableReason`이 있으면 UNAVAILABLE | ≥ 30% MEDIUM, ≥ 50% HIGH | `INSTALLMENT/REVIEW_INSTALLMENT_PLAN` "할부 계획 점검" | 없음 | 없음 |
| `SAVING/SAVING_CASHFLOW_BURDEN` | `cashflowBurden(userId, null)` → `disposableAmount`. `complete=false`면 UNAVAILABLE. `negativeDisposable` 또는 `disposableAmount ≤ 기준금액(20만 원)` | 음수 HIGH, 기준 이하 MEDIUM | `SAVING/REVIEW_SAVING_PLAN` "저축 계획 점검" | 없음 | `/simulations/saving`(`SavingChangeCalculator`가 감액을 받는지 확인 후 결정) |

- 기획안 "월 상환 부담"의 "고정지출·저축 차감 후 가용금액"은 `SavingsCashflowResponse.disposableAmount`가 이미 계산하므로 `LOAN_REPAYMENT_BURDEN` evidence에 같이 싣는다.
- 세 규칙이 모두 `FinancialProfile.monthlyIncome`에 의존한다. **시연 seed의 `financial_profile`에 소득이 없는 계정은 세 규칙이 전부 UNAVAILABLE**이 된다 → 착수 전 seed 확인.
- 기획안 "최근 반복적인 잔액 부족"은 `CASHFLOW` 규칙이 이미 잡으므로 `SAVING_CASHFLOW_BURDEN`에서 중복 판정하지 않는다.

### MR 3 — 새 계산이 필요한 규칙

| 규칙 | 판정 | 심각도 | 추천 | 효과 | 프리셋 |
| --- | --- | --- | --- | --- | --- |
| `SPENDING/FIXED_SPENDING_INCREASE` | `getBreakdown(FIXED, 최근 6개월)`의 월별 행으로 최근 2개월 평균 vs 이전 3개월 평균(진행 중인 달 제외). 증가율 ≥ 20% AND 증가액 ≥ 1만 원(627과 같은 API 6 기준) | ≥ 50% HIGH, 아니면 MEDIUM | `SPENDING/REVIEW_FIXED_SPENDING` "고정지출 항목 점검" | `monthlyCostSaving = 증가액` | `/simulations/spending`(카테고리 없음) |
| `SAVING/LOW_INTEREST_RATE` | 보유 예·적금별 `rateComparison` → 시장 중앙값 대비 −1.0%p 이상 낮고 잔여 기간 ≥ 6개월 | MEDIUM | `SAVING/SWITCH_SAVING_PRODUCT` "더 높은 금리 상품 비교" | `interestSaving = (상품 최고금리 − 현재금리) × 잔액 × 잔여기간`(단리, 중도해지 손실 `early-termination` 차감) | `/simulations/saving-product`에 `targetProductId` |
| `CARD/BENEFIT_MISMATCH` | 최근 3개월 상위 소비 카테고리에 보유 카드 혜택이 없음. `/products/cards/recommended` 로직 재사용 | LOW | `CARD/SWITCH_CARD` | 없음 | 없음 |

- `FIXED_SPENDING_INCREASE`는 `SpendingIncreaseRule`의 기간 정책(최근/이전 3개월·진행 중인 달 제외)과 다르다. 기획안이 고정지출은 "최근 1~2개월"로 정했기 때문이다. 공통 코드는 월평균 계산 유틸만 뽑는다.
- `VARIABLE_SPENDING_INCREASE`는 별도로 만들지 않는다. coverage 목록에서 제거하고 `CATEGORY_SPENDING_INCREASE`가 그 항목임을 `diagnosis-api.md`에 명시한다.
- MR 3은 셋 다 값 계산이 새로 들어가므로 시연 우선순위에 따라 `LOW_INTEREST_RATE`(FE 목업의 "저금리 계좌") → `FIXED_SPENDING_INCREASE` → `BENEFIT_MISMATCH` 순으로 하고, 시간이 없으면 뒤를 뺀다.

## 4. 공통 변경

- `DiagnosisRuleVersion`: MR마다 올린다(v5 → v6 → v7). 버전이 바뀌면 `GET /diagnoses/{id}/changes`에서 새 유형이 `NEW`로 나타난다.
- `DiagnosisEvaluator`: 규칙 인터페이스 도입, `RULE_POLICY_NOT_DEFINED` 목록에서 구현한 규칙 제거. `assumptions`에 `INCOME_FROM_CURRENT_PROFILE` 추가.
- `RecommendationSimulationPresetAssembler`: `loan/prepayment`, `saving-product` 분기 추가. `unavailableReason` 상수가 늘어난다 → FE에 문자열 타입 확장 요청(이미 3-4 후속 항목).
- `RecommendationPriorityPolicy`: 새 유형의 우선순위 가중치 확인. 효과 없는 추천(점검류)은 효과 있는 추천 뒤로 가야 한다.
- 추천 요약 `discovery.expectedAnnualCostSaving`은 단순 합산이다. `CANCEL_LOW_USAGE_CARD`의 연회비가 더해지는 건 맞지만, 같은 대출에 대환·중도상환이 함께 잡히면 이중 계산이다 → `LOAN_INTEREST_BURDEN` 추천 생략 규칙으로 막는다.

## 5. FE·타 담당에 알릴 것 (BE에서 수정하지 않음)

- FE `discoveryModel.ts`의 `DOMAIN_LABELS`·`DOMAIN_ICONS`가 `CASHFLOW/GOAL/LOAN/SPENDING` 4개뿐이다. `ACCOUNT`, `CARD`, `INSTALLMENT`, `SAVING` 라벨·아이콘이 필요하다(없으면 도메인 코드가 그대로 노출된다).
- 새 추천 유형은 `BasicSimulationSection`이 다루는 `SPENDING/SAVING` 프리셋 외에 `loan/prepayment`·`saving-product`를 낸다. FE 시뮬레이션이 2종뿐이므로 해당 추천은 "프리셋 있음·실행 불가" 상태로 보인다.
- seed: `financial_profile.monthly_income`이 시연 계정에 있는지, 휴면 계좌·저사용 카드·연회비 카드·이자 비중 큰 대출이 시연 계정에 실제로 있는지 확인. 없으면 규칙이 맞아도 화면에 안 나온다.
- 루트 `docs/` 명세 14·16의 문제·추천 유형 표는 요청 시 반영.

## 6. 검증

- 규칙별 단위 테스트(경계값·UNAVAILABLE 사유·문제 0건), `DiagnosisApiTest`에 규칙별 정상/빈 결과/소유권 사례, `RecommendationGeneratorTest`·`RecommendationSimulationPresetAssemblerTest` 추가.
- 소득 없는 사용자·계좌 없는 사용자에서 진단이 실패하지 않고 coverage만 남는지.
- MySQL: `current_value` JSON에 `BigDecimal`·`LocalDate`가 새로 들어가므로 627과 같은 직렬화 확인을 시연 seed로 수행.
- `git diff --check`, 전체 테스트.

## 7. 착수 전 결정할 것

1. MR 3 범위 — 세 규칙을 이번 스프린트에 넣을지, `LOW_INTEREST_RATE`만 넣을지.
2. 심각도 경계값(§3 표)을 초기값으로 갈지, 스크럼에서 정하고 갈지. 627 값과 함께 한 번에 확정하는 편이 낫다.
3. `LOAN_REPAYMENT_BURDEN`이 532 보류(현재 월 전체 상환액 미확보) 상태의 부분값을 근거로 문제를 만들어도 되는지. 아니면 `reasons`가 남으면 UNAVAILABLE로 둘지.
4. 효과 없는 점검류 추천(`CONSOLIDATE_IDLE_BALANCE`, `REVIEW_*`)을 발견 목록에 넣을지, 문제만 만들고 추천은 생략할지. FE 목록은 연 절감액 내림차순이라 효과 없는 항목은 자동으로 뒤로 간다.
