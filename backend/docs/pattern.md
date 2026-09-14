# Pattern API — 사용자 패턴 분석 실행·결과·소비/자금운용 패턴·우선순위 프로필·군집 메타데이터

D8 "3 Preference(사용자 패턴 분석 알고리즘)"의 하위 티켓 `S15P21D105-516`(API 3 사용자 패턴 분석 실행), `S15P21D105-517`(API 3 패턴 결과·프로필 조회),
`S15P21D105-518`(API 3 군집 메타데이터·Fallback) 구현 기준이다. API 명세 3의 5개를 모두 다룬다.

기획안 5-2 "소비 유형 군집 범위 이탈(보조 탐지)"과 5-4 "개선 우선순위 제시", 14-3 "규칙 기반 + 군집 보조 신호·자동 fallback", 14-6 "익명·집계 통계만 사용"을 따른다.
명세·기획안에 수치가 없는 항목은 **서비스 기본안**으로 정하고 `app.pattern` 설정과 이 문서에 기록했다.

## 모델

| 테이블 | 엔티티 | 내용 |
| --- | --- | --- |
| `pattern_analysis` (V31) | `PatternAnalysis` | 분석 실행 1건. 상태(RUNNING/COMPLETED/FAILED), 분석 기간·건수, 규칙 지표 8종(`spending_regularity` ~ `liquidity_level`), 군집 컬럼 7종(`model_version` ~ `confidence`), 입력 요약 JSON(`feature_summary_json`), 군집 프로필 JSON(`cluster_stats_json`) |

- ERD 정의 그대로다(컬럼 추가 없음). 비율은 `DECIMAL(10,4)`이라 엔티티에서 `BigDecimal` 소수 4자리다. `Double`이면 MySQL `validate`가 타입 불일치로 실패한다.
- JSON 두 컬럼은 `JsonNode`다(`Diagnosis.inputSnapshot`과 같은 이유). 저장 형식은 응답 DTO(`PatternFeatureSummary`, `ClusterProfileResponse`)와 같아
  조회 때 재계산 없이 그대로 읽는다. 과거 결과는 모델을 다시 학습해도 바뀌지 않는다.
- 군집 컬럼은 HYBRID 모드에서 노출 조건을 모두 충족했을 때만 채운다. 그 외에는 모두 NULL이고 사유가 `feature_summary_json.fallbackReason`에 남는다.
- 마이그레이션 번호는 **V31**(BE-A 2차 구간 V30~V49, `db/migration/README.md`). 시연 seed는 없다. 분석 결과는 API 실행으로만 만들어진다.

### 상태

```
RUNNING ──▶ COMPLETED   (정상)
RUNNING ──▶ FAILED      (예상 못한 계산 오류. 원인 요약만 JSON에 남김. 조회 API에 노출되지 않음)
RUNNING ──▶ (삭제)      (데이터 부족 등 선행 조건 위반. 행을 남기지 않음)
```

PENDING은 ERD 호환용이며 동기 실행에서는 쓰지 않는다.

## API

모든 API는 인증이 필요하고 본인 데이터만 다룬다.

| 기능 | Method | Endpoint | 상태 코드 | 티켓 |
| --- | --- | --- | --- | --- |
| 사용자 패턴 분석 실행 | POST | `/api/v1/patterns/analyze` Body `{forceRefresh?, analysisMode?}` | 201 새 결과 / 200 재사용(`cached: true`) | 516 |
| 사용자 패턴 분석 결과 조회 | GET | `/api/v1/patterns/me` | 200 | 517 |
| 소비 패턴 조회 | GET | `/api/v1/patterns/me/spending?months` | 200 | 517 |
| 자금운용 패턴 조회 | GET | `/api/v1/patterns/me/cashflow?months` | 200 | 517 |
| 추천 우선순위 프로필 조회 | GET | `/api/v1/patterns/me/priority-profile` | 200 | 517 |

`clusterProfile`(군집 라벨·기준 범위·표본 수·모델 버전)은 결과 조회와 소비 패턴 조회에 들어가며 조건 미충족이면 `null`이다(518).

### 오류 코드

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | `analysisMode`가 RULE_ONLY·HYBRID 밖, `months`가 1~12 밖 또는 숫자가 아님 |
| `FINANCE_NOT_CONNECTED` | 409 | 분석 기간에 거래가 없고 금융망 연결이 CONNECTED가 아님(명세 "금융 데이터 연동 완료 필요") |
| `PATTERN_INSUFFICIENT_DATA` | 409 | 분석 기간에 거래가 없음(연결은 됨). 소비·자금운용 패턴 조회도 같음 |
| `PATTERN_ANALYSIS_IN_PROGRESS` | 409 | 임대 기간(5분) 안의 진행 중 분석이 있음(명세 "중복 분석 요청 방지") |
| `PATTERN_ANALYSIS_EXPIRED` | 409 | 만료된 접수의 완료 시도. 정상 흐름에서는 나지 않음 |
| `PATTERN_ANALYSIS_NOT_FOUND` | 404 | 완료된 분석 결과가 없음 |
| `UNAUTHORIZED` | 401 | 토큰 없음·만료 |

seed 사용자(`seed.kmeans.u0001` ~ `u4000`)는 `finance_connection` 행이 없지만 SEED 거래가 있으므로 분석할 수 있다. 연결 상태보다 **데이터 존재**를 우선한다.

## 분석 기간

기준일이 속한 달을 빼고 **완료된 달** `lookback-months`(기본 6)개다. 기준일 2026-09-14 → 2026-03 ~ 2026-08. 진행 중인 달을 넣으면 월 합계가 날짜에 따라
달라져 규칙성·변동성이 흔들리고, 사용자 간 비교(군집)도 같은 달 수로 맞출 수 없다. 시연 seed 거래 창(2026-03-01 ~ 2026-08-31)이 이 정의로 정확히 6개월이다.

조회 API의 `months`(1~12)는 같은 방식으로 기간을 잡는다. 평균은 **거래가 있는 달**로 나눈다(6개월 창에 3개월만 있으면 3으로 나눔). 거래 없는 달은 `months` 배열에
`transactionCount = 0`으로 남는다.

## 규칙 기반 지표 (516)

소비 정의는 API 6과 같다: `EXPENSE`이고 `spending_type`이 NULL 또는 NON_SPENDING이 아닌 거래, 숨긴 거래 제외. 수입은 INCOME 거래 합, 저축성 지출은 NON_SPENDING EXPENSE 합
(API 6 saving-ratio의 `savingTransfer`와 같은 정의). 재량 소비는 FOOD·CAFE·SHOPPING·BEAUTY·CULTURE·TRAVEL·TAXI 카테고리 소비, 대출상환은 LOAN_REPAYMENT 카테고리다.
`JdbcPatternFeatureReader`가 월별 집계 한 번으로 모두 읽는다.

| 지표 | 정의 | 계산 불가 |
| --- | --- | --- |
| `spendingRegularity` | 1 − 월 소비 변동계수(std/mean), 0~1로 자름 | 거래 있는 달 2개 미만 → null |
| `savingConsistency` | 저축성 지출 > 0인 달 수 / 거래 있는 달 수 | — |
| `fixedExpenseRatio` | Σ고정지출 / Σ소비 | 소비 0 → null |
| `variableExpenseRatio` | Σ변동지출 / Σ소비 (미분류 NULL은 어느 쪽에도 안 들어가 둘의 합이 1 미만일 수 있음) | 소비 0 → null |
| `discretionarySpendingRatio` | Σ재량소비 / Σ소비 | 소비 0 → null |
| `debtBurden` | 월평균 대출상환 / 월 소득 | 소득 NONE → null |
| `cashflowStability` | (그 달 소득, 없으면 월 소득) − 소비 ≥ 0인 달 비율 | 소득 NONE → null |
| `liquidityLevel` | 입출금(DEMAND_DEPOSIT) ACTIVE 계좌 잔액 합 / 월평균 소비 = 몇 달분. ≥3 HIGH, ≥1 MEDIUM, 그 외 LOW | 계좌 행 0개 → null |

월 소득(`incomeBasis`): 기간 INCOME 거래가 있으면 그 월평균(TRANSACTIONS), 없으면 `financial_profile.monthly_income`(PROFILE), 둘 다 없으면 NONE.

`featureSummary`에는 요청·적용 모드, fallback 사유, 기간, 소득 출처, 월평균 7종, 변동계수, 유동성 잔액·개월, 모델 표본 수, 군집 입력 특징, 월별 집계가 남는다.

## 실행 흐름 (516)

`PatternAnalysisRunner`: 데이터 확인 → 접수 → 계산 → 완료. Diagnosis(544)와 같은 구조다.

1. 기간 거래가 0건이면 접수하지 않고 409(연결 상태에 따라 코드 분기).
2. `PatternAnalysisExecutionStore.admit`(사용자 행 비관적 잠금): 임대 5분 안의 RUNNING이 있으면 409. `forceRefresh=false`이고 **오늘**(Asia/Seoul) 완료된 결과가
   같은 기간·같은 요청 모드로 있으면 그 결과를 200으로 돌려준다(요청 모드가 다르면 재사용하지 않음). 아니면 RUNNING 행을 만든다.
3. 잠금 밖에서 지표 계산, HYBRID면 군집 배정(아래).
4. `complete`(다시 잠금): RUNNING이고 임대 안이고 이후 접수가 없을 때만 COMPLETED로 바꾼다. 아니면 `PATTERN_ANALYSIS_EXPIRED`.
5. `BusinessException`이면 접수 행 삭제, 그 외 런타임 오류면 FAILED로 남긴다. 군집 모델 오류는 여기까지 오지 않는다(RULE_ONLY 전환).

`forceRefresh=true`는 결과를 다시 계산하고 군집 모델도 다시 학습한다.

## 군집 메타데이터·Fallback (518)

### 모델

`spending-kmeans-v1`. 외부 라이브러리 없는 표준화 K-Means(`KMeansClusterer`, k-means++ 초기화, seed 고정 → 결정적). 입력은 전체 사용자(삭제 제외)의 월별 집계에서
만든 **비율 특징 5종**이고, 거래 원문·식별정보·소득 절대값·연령·성별은 쓰지 않는다(기획안 14-6). 사용자 ID는 표본 수 계산에만 쓴다.

| 키 | 정의 | 상한(클리핑) |
| --- | --- | --- |
| `spendingToIncomeRatio` | 월평균 소비 / 월 소득 | 3.0 |
| `savingRate` | 월평균 저축성 지출 / 월 소득 | 2.0 |
| `fixedExpenseShare` | Σ고정지출 / Σ소비 | 1.0 |
| `discretionaryShare` | Σ재량소비 / Σ소비 | 1.0 |
| `spendingVolatility` | 월 소비 변동계수 | 2.0 |

모집단 특징은 INCOME 거래 월평균만 소득으로 쓴다(금융 기본정보는 읽지 않음). 거래 있는 달이 `min-user-months`(3) 미만, 소득 0, 소비 0인 사용자는 학습에서 빠진다.

`ClusterModelProvider`가 같은 기간의 모델을 `model-ttl`(1시간) 동안 메모리에 재사용한다. 학습 가능 사용자 수가 `min-population`(30) 또는 군집 수(4) 미만이면 모델 없이
표본 수만 돌려준다(이 결과도 TTL 동안 캐시). 시연 seed 4,000명이면 1회 학습에 24,000행 집계 + K-Means 수십 회 반복이다.

### 라벨

K-Means 번호는 학습마다 뒤바뀌므로 `ClusterLabelPolicy`가 원 단위 중심값으로 결정적으로 이름을 붙인다. 우선순위 순으로 아직 이름 없는 군집 중 그 특징 중심값이
가장 큰 군집에 라벨을 주고 마지막 하나는 "균형형"이다.

| 순서 | 특징 | 라벨 |
| --- | --- | --- |
| 1 | savingRate | 저축 중심형 |
| 2 | spendingVolatility | 소비 변동형 |
| 3 | spendingToIncomeRatio | 소비 집중형 |
| 4 | discretionaryShare | 재량소비 집중형 |
| 5 | fixedExpenseShare | 고정지출 집중형 |
| 마지막 | — | 균형형 (후보를 넘으면 "소비 유형 N") |

seed 성향 4종(BALANCED·SPENDING_FOCUSED·SAVING_FOCUSED·VOLATILE)에 맞춘 k = 4 기본값에서는 1~3번과 균형형이 나온다.

### clusterProfile

```json
{
  "clusterId": 1, "clusterLabel": "저축 중심형", "modelVersion": "spending-kmeans-v1",
  "sampleSize": 13, "populationSize": 49, "confidence": 0.7421,
  "featureWindowMonths": 6, "windowStart": "2026-03", "windowEnd": "2026-08",
  "deviationScore": 0.0,
  "features": [
    {"feature": "savingRate", "displayName": "저축률", "userValue": 0.3333, "centroid": 0.3333,
     "min": 0.3333, "max": 0.3333, "p5": 0.3333, "p95": 0.3333, "mean": 0.3333, "std": 0.0,
     "deviation": 0.0, "direction": "WITHIN"}
  ]
}
```

- `confidence` = (두 번째로 가까운 중심 거리 − 가장 가까운 중심 거리) / 두 번째 거리, 0~1. 두 중심 정확히 가운데면 0.
- 기준 범위는 이상치 보정용 **p5~p95**(기획안 "5~95 분위수"), `min`·`max`는 참고용 실제 범위다.
- `deviation`(기획안 범위 이탈): 범위 안 0, 밖이면 (경계 − 값) / scale, scale = max(p95 − p5, 표준편차). scale 0이면 0.
- `deviationScore`(기획안 군집 이탈 점수) = 특징별 deviation 평균(가중치 균등). 기획안의 특징별 가중치는 문서에 값이 없어 균등으로 둔다.
- 이 JSON이 `cluster_stats_json`에 그대로 저장된다.

### Fallback (HYBRID → RULE_ONLY 자동 전환)

아래 순서로 검사하고 하나라도 걸리면 규칙 지표만 저장한다. 사유는 응답 `fallbackReason`과 `featureSummary.fallbackReason`에 남는다.

| 순서 | 사유 | 조건 |
| --- | --- | --- |
| 0 | `RULE_ONLY_REQUESTED` | 요청이 RULE_ONLY(전환은 아니지만 형식을 맞춤) |
| 1 | `USER_DATA_TOO_SHORT` | 거래 있는 달 < `min-user-months`(3) — 기획안 "사용자 데이터 3개월 이상" |
| 2 | `FEATURES_UNAVAILABLE` | 소득 NONE 또는 소비 0이라 비율 특징을 만들 수 없음 |
| 3 | `MODEL_FAILURE` | 모델 학습·배정 중 예외(서버 로그에만 상세) |
| 4 | `INSUFFICIENT_POPULATION` | 학습 가능 사용자 수 < `min-population`(30) |
| 5 | `SMALL_CLUSTER_SAMPLE` | 배정 군집 표본 수 < `min-cluster-sample-size`(10) — 기획안 "표본 수가 작은 군집의 범위는 노출하지 않는다" |
| 6 | `LOW_CONFIDENCE` | `confidence` < `min-confidence`(0.2) |

기획안의 4번째 노출 조건 "군집 이탈 점수 ≥ 임계값"은 **진단(API 14)이 문제를 만들 때** 적용할 조건이라 여기서는 점수만 제공하고 프로필 노출을 막지 않는다.
사용자 본인의 프로필은 이탈이 없어도(정상 범위) 보여줄 가치가 있다.

## 조회 (517)

### GET /patterns/me

최근 COMPLETED 1건(`analyzed_at` 내림차순). RUNNING·FAILED는 보이지 않는다. 없으면 404.

### GET /patterns/me/spending?months

요청 시점 거래로 계산한다. 기간 합·월평균·변동계수·규칙성·고정/변동/재량 비율·소비/소득·마지막 달과 그 앞 달들의 평균 대비 변화율(%)·월별·카테고리(합 내림차순, 미분류
`UNCATEGORIZED`)·`clusterProfile`. `clusterProfile`은 **최근 완료 분석**의 것이다(`months`와 무관, 없으면 null). 기간에 거래가 없으면 409.

### GET /patterns/me/cashflow?months

월별 소득·소비·저축성 지출·대출상환·순흐름(소득 − 소비)과 월평균, 저축률(저축성 지출/소득), 저축 일관성, 현금흐름 안정성, 부채부담, 유동성(잔액·개월·수준). 기간에 거래가 없으면 409.

### GET /patterns/me/priority-profile

근거를 저장 분석(`PATTERN_ANALYSIS`) → 현재 거래 즉시 계산(`LIVE_CALCULATION`) → 기본 프로필(`DEFAULT`, 데이터 없음) 순으로 고른다. 기획안 "성향검사는 선택, 기본값으로 주되
패턴분석을 하면 성향검사 제외"에 맞춰 데이터가 없어도 200이다.

`PriorityProfileCalculator` 기본안:

```
기본 가중치: 위험 .25 / 효과 .25 / 목표기여 .20 / 시급성 .15 / 실행난이도 .15
부채부담 ≥ 0.30                          → 위험 +.10, 시급성 +.05
유동성 LOW                               → 위험 +.05, 시급성 +.05
재량소비 ≥ 0.40 또는 소비/소득 ≥ 0.80    → 효과 +.10
저축 일관성 ≥ 0.80 이고 저축률 ≥ 0.20    → 목표기여 +.10
가중치는 합 1로 정규화

집중 영역 점수(0~1, 내림차순 정렬):
  SPENDING_REDUCTION = 재량소비 비중 + max(0, 소비/소득 − 0.6)
  DEBT_REPAYMENT     = 부채부담 / 0.4
  SAVING_INCREASE    = 1 − 저축률 / 0.3   (소득 미상이면 0.5)
  LIQUIDITY_BUFFER   = LOW 1.0 / MEDIUM 0.5 / HIGH 0.0 / 계좌 없음 0.0
  FIXED_COST_REVIEW  = (고정지출 비중 − 0.3) / 0.4
profileType = 1위 영역 → SPENDING_FOCUSED(SPENDING_REDUCTION·FIXED_COST_REVIEW) / DEBT_FOCUSED / SAVING_FOCUSED / LIQUIDITY_FOCUSED.
              1위 점수 < 0.3이면 BALANCED
clusterDeviationAlpha = 군집 결과가 있으면 0.2 × confidence, 없으면 0   (기획안 "최종 점수 = 규칙 점수 + α × 군집 이탈 점수", 군집 없으면 α = 0)
```

적용한 규칙은 `assumptions`에 문장으로 나간다. 추천 엔진(API 16)이 이 값을 쓸지, 쓰면 어느 필드를 쓸지는 BE-B와 맞춘다(remaining-work 517 절).

## 설정 (`app.pattern`)

| 키 | 기본 | 의미 |
| --- | --- | --- |
| `lookback-months` | 6 | 분석 기간(완료된 달) |
| `min-user-months` | 3 | 군집 비교 최소 개월 |
| `min-population` | 30 | 모델 학습 최소 사용자 수 |
| `min-cluster-sample-size` | 10 | 배정 군집 최소 표본 |
| `min-confidence` | 0.2 | 신뢰도 하한 |
| `cluster-count` | 4 | K-Means k |
| `model-ttl` | PT1H | 모델 메모리 재사용 |
| `lease-duration` | PT5M | 진행 중 접수 유효 시간 |

## 테스트

- 단위: `KMeansClustererTest`(분리된 집단·결정성·표준편차 0), `ClusterLabelPolicyTest`, `PatternFeatureCalculatorTest`(기간·지표 정의·소득 출처·모집단 특징 조건),
  `PriorityProfileCalculatorTest`.
- 통합(H2, `PatternApiTest`): 성향 4종 × 12명 모집단 + 본인(저축형 + 대출상환 + 입출금 계좌). HYBRID 배정("저축 중심형")·같은 날 재사용·forceRefresh, RULE_ONLY 요청·모드 불일치
  비재사용·잘못된 모드 400, 모집단 부족 fallback, 데이터 2개월 사용자 fallback, 데이터 없음(연결 상태별 409)·조회 404/409·기본 프로필, 소비/자금운용/우선순위 조회와
  최근 분석의 clusterProfile 반영, 인증·소유권, 접수 중복·폐기·임대 만료.
- MySQL 검증은 remaining-work 516 절 참고.
