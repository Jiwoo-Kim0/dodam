# 627 발견 화면 진단 규칙 확장 계획 — 소비 증가·고금리 대출

작성일: 2026-09-16. 상태: **구현 완료(H2 검증)**. 브랜치 `S15P21D105-627`(Jira 생성, develop `ad87d1a` 기준).
구현 계약은 [diagnosis-api.md](diagnosis-api.md) §규칙·근거(627 절)와 [recommendation-api.md](recommendation-api.md) §2·§5.1에 있고, 남은 항목은 [remaining-work.md](remaining-work.md) 627 절에 있다.

## 1. 배경 — FE 요청과 범위 결정

FE는 발견 화면 목업(배달·외식 증가, 평균보다 높은 대출 금리, 미사용 구독, 저금리 계좌 유휴자금)을 실데이터로 채우기 위해 다음을 요청했다.

| FE 요청 | 결정(2026-09-16) |
| --- | --- |
| 소비 증가 진단 `SPENDING/CATEGORY_SPENDING_INCREASE` + 추천 | **이번 범위.** 기존 API 6 소비 급증 기준(20% 초과·월 1만 원 이상) 재사용 |
| 고금리 대출 진단 `LOAN/HIGH_INTEREST_RATE` + 추천 | **이번 범위.** 기존 API 11 고금리 판정(공시 최저금리 중앙값 +1.0%p) 재사용, 대환 절감액은 명세 17 대환 계산 규칙으로 추정 |
| 미사용 구독 진단 | **제외.** 결제 기록은 사용 기록이 아니라 '미사용'을 판정할 수 없다. "정기결제 점검" 표현 또는 사용 기록 데이터가 선행 |
| 유휴자금 진단 | **제외.** 생활비·비상금 보존 기준이 없다 |
| 추천 응답 `sourceIssueId` | **후속.** `recommendation` 테이블에 컬럼이 없다 |
| 발견 요약 통합 수치(중복 제거 절감액·Combined 목표 단축일) | **후속.** summary는 단순 합산이며 통합 계산이 없다 |
| AI Coach는 설명만 | 변경 없음. 코치는 저장된 진단·추천을 읽으므로 새 유형도 코드 변경 없이 설명 대상이 된다 |

FE 예시 수치(350,000 → 385,000이 42%)는 맞지 않는다(35,000원, 10%). 응답 필드는 FE 예시 이름(`categoryCode, monthlyAverage, increaseAmount, increaseRatePercent, consecutiveMonths, previousMonthlyAverage`)을 따르되 값은 서버 계산이다.

## 2. 설계 결정

### 소비 증가

- 기존 `SpendingAnalysisService.getAnomalies`를 호출하지 않고 같은 reader(`JdbcSpendingReader.readByMonthAndCategory`) 한 번으로 6개월 행을 읽어 `SpendingIncreaseRule`에서 계산한다. `consecutiveMonths`에 월별 금액이 필요해 두 번 조회하는 것보다 낫고, 진단은 API 6과 달리 진행 중인 달을 넣지 않는다.
- 임계값은 `SpendingAnalysisService`의 public 상수를 그대로 참조해 두 화면의 판정이 갈라지지 않게 했다. 계산식(HALF_UP 월평균, 소수 1자리 증가율)도 같다.
- 미분류 제외: `changes` 키·추천 candidateKey·시뮬레이션 `category`가 모두 카테고리 id/코드를 요구한다.

### 소비 증가 기준 변경 (S15P21D105-631, 2026-09-17) — FE 요청 검토와 결정

FE 요청 원문: "완료된 최근 3개월 평균 vs 이전 3개월 평균"을 "마지막 완료 월 vs 그 직전 3개월 월평균"(9월 진단 → 8월 vs 5~7월)으로 바꾸고, 진단 수치·문구·`currentValue/benchmarkValue`
기간, 추천 `monthlyCostSaving`·`annualCostSaving`·근거·AI Coach 수치를 같은 기준으로 맞추며, 시뮬레이션 프리셋 400, 기존 결과 보존·버전 구분, 데이터 부족·기준 0원·연도 경계·
"지난달이 전달보다 줄었지만 3개월 평균보다 높은 경우"를 검증해 달라는 것.

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 비교 기간 | 지난달 1개월 vs 직전 3개월 평균. `SpendingIncreaseRule.RECENT_MONTHS = 1`, `BASELINE_MONTHS = 3` | 순수 함수 `evaluate`의 기간 인자만 바뀐다. `recentPeriod.months = 1`, `baselinePeriod.months = 3`, `evidence.monthly[]` 4개월 |
| JSON 키 | `monthlyAverage` 등 키 이름 유지, `lastMonthAmount`(같은 값) 추가 | FE·추천 생성기·코치가 키로 읽는다. 이름을 바꾸면 이전 버전 진단과 두 갈래가 된다 |
| 임계값 | 월 1만 원 이상·20% 초과 **그대로 적용** | 올릴 근거가 아직 없다. 단월 비교는 평활 효과가 없어 탐지 건수가 늘 수 있고 API 6 급증과 판정식이 갈라진다 — 시연 데이터 실측 뒤 조정 |
| 연 절감액 ×12 | **유지** + 근거 evidence `annualizationBasis = SINGLE_MONTH_INCREASE_X12` | 새 기준에서 ×12는 "한 달 이벤트가 1년 지속" 가정이라 근거가 약하다. 그러나 발견 화면 정렬(604)과 `discovery.expectedAnnualCostSaving`이 이 값에 의존해 null로 바꾸면 화면이 빈다. FE가 "지속 시 연간" 표기를 붙이는 것으로 제안 |
| 시뮬레이션 프리셋 400 | **실제로 발생** → 프리셋 조립 시 시뮬레이션 기준선(직전 3개월 카테고리 월평균)을 읽어 상한으로 자름(`REDUCTION_CAPPED_TO_SIMULATION_BASELINE`), 기준선 소비 0이면 `SIMULATION_BASELINE_UNAVAILABLE` | v5는 진단 최근 기간과 시뮬레이션 창이 같아(−3 ~ −1) 증가액 ≤ 상한이 항상 성립했다. 631은 6·7월 0원·8월 300,000이면 증가액 290,000 > 6~8월 평균 100,000 |
| 버전 | `DiagnosisRuleVersion` `v5-copy-tone` → `v6-last-month-vs-3m`, 생성기 `627.2` → `627.3` | 진단 캐시가 버전별, 추천 생성 키에 진단 id 포함 → 기존 3 vs 3 진단·추천은 그대로 보존. 546 `changes`가 버전 변경 표시 |
| 추천 근거·AI 코치 | 진단 JSON을 그대로 읽으므로 자동 일치. 근거 문구만 "지난달 … 직전 3개월 월평균 … 많아요"로 교체 | `CoachEvidenceReader`는 키 경로대로 사실을 펼친다 |
| 테스트 | 기준 0원(판정 제외), 기준 일부 달 결측(0원 희석, 2026-05·06 없음 → 평균 30,000), 연도 경계(2026-01 → 2025-12 vs 2025-09~11, 조회 범위 한 번), 지난달 < 전달이지만 > 3개월 평균(`consecutiveMonths 0`), 프리셋 상한 절단·기준선 없음 | `SpendingIncreaseRuleTest` 5, `RecommendationSimulationPresetAssemblerTest` +1, `DiagnosisApiTest`·`RecommendationGeneratorTest` 갱신 |

별도 협의 항목에 대한 판단(FE 회신용):
- **영화관·게임·OTT별 증가 원인**: 거래에 가맹점명이 있어 지난달 해당 카테고리 거래를 가맹점별로 묶어 상위 N개를 `evidence.topMerchants[]`로 주는 것은 가능하다. 다만 가맹점명 정규화가 공백·대소문자
  제거 수준이라 "넷플릭스"와 "NETFLIX.COM"이 갈린다(513 절). 별도 티켓으로 범위·정규화 규칙을 정한다.
- **3단계 절약 계획**: 서버 데이터가 아니라 기획 산출물이다. 근거 없는 값을 API로 굳히지 않는다. FE가 임의 수치로 채우지 않겠다는 방침에 동의.
- **목표 달성일 단축**: 추천 `GET /recommendations/{id}/goal-impact`와 시뮬레이션 `goalImpacts`에 이미 있다. 새 데이터가 아니라 화면 연결 문제다.

### 고금리 대출

- 판정은 `LoanRateComparisonService.highInterest`를 재호출한다. 기준금리 계산을 복제하면 API 11과 어긋난다.
- 대환 절감액은 시뮬레이션을 실행하지 않고(진단이 시뮬레이션 행을 만들면 안 된다) `ProductOptionSelector.selectLoan` + `LoanRepaymentScheduleCalculator`로 같은 규칙을 적용한다. 대상 상품 후보는 판정 모집단과 같은 조건(같은 비교군·같은 업권·보유 상품 제외)이며, 그중 잔여 회차와 같은 `term` 옵션·같은 상환방식·잔액 한도를 만족하는 상품만 대상이다. 이 때문에 `LoanComparisonPolicy.loanTypesOf`와 `SimulationSnapshotReader.toSnapshot`을 public으로 열었다.
- 기준금리로 대환했을 때의 절감액(상품 없이 계산 가능)을 쓰지 않은 이유: 추천이 실제 상품(`targetProductId`)을 가리켜야 대환 시뮬레이션 프리셋으로 이어진다.
- 추정 불가여도 문제는 만든다(고금리 판정 자체는 성립). 추천은 "대환 조건 비교하기"로 효과 없이 생성한다.

### 추천·프리셋

- 생성기 버전 `627.1`, candidateKey는 카테고리 id·대출 id. Collector가 자동으로 모으므로 생성 서비스 변경은 없다.
- 프리셋 조립기는 SAVING 한정이던 근거 조회를 세 유형으로 넓히되 쿼리는 여전히 한 번이다.
- 대환 프리셋은 `accountNo: null`이라 FE가 그대로 실행할 수 없다(명세 17 본문이 원문 계좌번호만 받음). 619의 `goalId`와 같은 `loanId` 본문 확장은 시뮬레이션 계약 변경이라 후속으로 뺐다.

## 3. 변경 파일

- 진단: `DiagnosisRuleVersion`(v4), `DiagnosisEvaluator`(규칙 2종 호출·coverage·assumptions), 신규 `SpendingIncreaseRule`, `HighInterestLoanRule`
- 추천: 신규 `CategorySpendingIncreaseGenerator`, `HighInterestLoanGenerator`, `RecommendationSimulationPresetAssembler`·`RecommendationSimulationPreset`(프리셋 2종·사유 코드)
- 가시성: `LoanComparisonPolicy.loanTypesOf`, `SimulationSnapshotReader.toSnapshot` public
- 테스트: `SpendingIncreaseRuleTest`(신규), `DiagnosisApiTest`·`RecommendationGeneratorTest`·`RecommendationSimulationPresetAssemblerTest`·`RecommendationApiTest` 추가 사례
- 문서: diagnosis-api.md, recommendation-api.md, remaining-work.md, 이 문서

DB 스키마 변경 없음. 루트 `docs/` 명세 14·16은 수정하지 않았다(응답 필드 추가는 사용자 요청 시 반영).
