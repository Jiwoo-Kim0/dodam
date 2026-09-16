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
