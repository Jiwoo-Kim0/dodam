# 시계열 예측 모델 도입 설계

- Jira: S15P21D105-706
- 브랜치: `S15P21D105-706-db` (develop 4e75e68에서 분기, 원격에 있고 내용은 develop과 동일)
- 작성일: 2026-09-23 (rev.14)
- 상태: 설계. 코드·데이터 변경 없음.

## 1. 결정 사항

| 항목 | 결정 | 근거 절 |
| --- | --- | --- |
| 예측 대상 | 월별 소비 지출, 일별 계좌 잔액, 목표 달성일 단축 | 2 |
| 모델 실행 | 오프라인 학습 → JSON 아티팩트 → Java 추론 (K-Means 전례와 동일) | 4 |
| 데이터 | 4,000명 거래 이력 24개월 확장, 예측 결과 테이블 신설, 파생 결과 재계산 | 8 |
| `spending_type` NULL | 카테고리 기본값으로 해석, 그것도 NULL이면 `VARIABLE` | 2.1 |
| 예측 기반 위험 | `cashflow_risk`에 기록하되 **알림과 효율 점수에서 제외** | 6.3, 6.4 |
| 소급 범위 | 굳힌 스냅샷만 재촬영. 과거 리포트·목표 이력은 그대로 둔다 | 7 |
| 이슈 구성 | 이슈 706 하나, 브랜치 하나, MR 10개를 **순차로** 낸다 | 12 |
| 범위 | 축소안 없이 전체 진행(cashflow 포함). 시뮬레이션 미사용 3종은 QA 후순위 | 2.4, 12 |

## 2. 모델이 실제로 예측하는 값은 하나다

세 산출물을 각각 예측하지 않는다. **변동 소비 V(u, m) 하나만 예측하고 나머지는 조립한다.**

| 성분 | 출처 | 모델 대상 |
| --- | --- | --- |
| 수입 | `financial_profile.monthly_income` 또는 과거 INCOME 거래 평균 (2.2) | 아니오 |
| 고정비 | 고정비·구독·대출 상환 예정 이벤트 | 아니오 |
| 변동 소비 | 거래 이력 | **예** |

이미 날짜와 금액이 확정된 항목에 예측 오차를 얹을 이유가 없다. Cashflow가 확정 예정 이벤트만
누적하도록 만들어진 이유와 같다. 조립은 이렇게 된다.

- 월별 소비 지출 = 확정 고정비 + V̂
- 일별 잔액 = 확정 이벤트 + V̂를 일별로 분배
- 목표 단축 = V̂가 만드는 저축 여력 변화 (7절, 스코프 주의)

**"확정 고정비"의 출처를 정해야 한다.** `CashflowEventCollector`가 만드는 예정 이벤트는 일 단위
horizon용이고, 월 예측은 최대 12개월 앞을 본다. 구독은 `next_payment_date`와 주기로, 대출·할부는
상환 스케줄로 그만큼 펼칠 수 있지만 주거·공과금처럼 스케줄이 없는 고정비는 그렇지 않다.

**결정**: 스케줄이 있는 항목은 스케줄로 펼치고, 없는 항목은 **최근 3개 완료 월의 `fixedSpending`
중앙값을 매월 반복**한다. 중앙값을 쓰는 이유는 평균이 연납 보험료 같은 한 달짜리 큰 건에 끌리기
때문이다. 어느 경로로 채웠는지를 월별로 구분해 응답에 싣는다.

이 값의 오차는 11.1의 평가에 잡히지 않는다(평가는 V̂만 잰다). `predicted_total`을 서버가 낼지
V̂만 내고 화면에서 조립할지는 13절 미결이다.

`transaction.spending_type`이 `FIXED` / `VARIABLE` / `NON_SPENDING`으로 나뉘어 있고
`transaction_category`의 `SUBSCRIPTION`은 `default_spending_type = 'FIXED'`라, 확정 이벤트와 V̂는
**겹치지 않는다.**

### 2.2 "확정 수입"은 없다 — 급여 예정 이벤트가 구현되어 있지 않다

rev.10까지 수입 출처를 "급여 예정 이벤트"라고 적었다. **그런 이벤트는 만들어지지 않는다.**

`CashflowEventCollector`가 직접 만드는 이벤트는 카드대금·대출상환·적금납입·구독 넷이고 **전부
`OUTFLOW`**다. 입금은 확장점 `AccountScheduledEventSource`를 통해서만 들어올 수 있는데
**구현체가 하나도 없다**(`backend/src/main/java` 전체에 `implements AccountScheduledEventSource`
가 없고, 컬렉터는 비어 있을 때 `ACCOUNT_SCHEDULE_SOURCE_UNAVAILABLE` 사유를 남긴다).

따라서 이 모델이 쓰는 수입은 `PatternFeatures.incomeBasis`가 이미 정의해 둔 것을 그대로 따른다.
문서에서 **기준 수입**이라 부른다.

| `IncomeBasis` | 값 |
| --- | --- |
| `TRANSACTIONS` | 창 안 INCOME 거래의 월평균 |
| `PROFILE` | `financial_profile.monthly_income` |
| `NONE` | 없음 → 예측하지 않는다 (4.4) |

### 2.1 그러나 덮지도 않는다 — `spending_type`은 NULL을 허용한다

V6의 `chk_transaction_spending_type`은 `spending_type IS NULL`을 허용하고,
`JdbcPatternFeatureReader`의 `IS_SPENDING`은 **NULL을 소비로 센다**
(`spending_type IS NULL OR spending_type <> 'NON_SPENDING'`). 반면 `fixedSpending`과
`variableSpending`은 각각 명시값을 요구한다. 따라서

```
spending ≥ fixedSpending + variableSpending      (등호가 아니다)
```

이고 그 차이가 **NULL 잔여 버킷**이다. seed는 항상 명시값을 넣지만 금융망 동기화로 들어온 거래는
그렇지 않을 수 있다. `월별 소비 지출 = 확정 고정비 + V̂`를 그대로 쓰면 이 잔여만큼 과소 집계된다.

**결정**: NULL은 `transaction_category.default_spending_type`으로 해석하고, 그것도 NULL이면
`VARIABLE`로 본다(예측 대상에 포함). 신규 카테고리×월 쿼리와 `V` 정의 모두 같은 규칙을 쓴다.
쿼리마다 규칙이 갈리면 `predicted_total`과 리포트 합계가 어긋난다.

이 규칙 덕에 `predicted_total`을 `spending`과 같은 축에서 비교할 수 있다. 대신 카테고리가
`HOUSING`·`INSURANCE`인데 `spending_type`이 NULL인 거래는 카테고리 기본값이 `FIXED`라 예측
대상에서 빠진다. 무조건 `VARIABLE`로 두는 것보다 이 편이 맞다.

### 2.3 이 예측이 쓰이는 곳

예측값이 닿는 화면은 신규 API 하나가 아니다. 두 개의 주입 지점(cashflow 합성 이벤트, 목표 속도
배율)을 거쳐 기존 API 다수로 번진다. MR 순서와 회귀 범위가 이 표에서 나온다.

**① 직접 — 신규 API (MR 5)**

| Endpoint | 쓰이는 값 |
| --- | --- |
| `POST /api/v1/spending-forecasts` | 실행 |
| `GET /api/v1/spending-forecasts/latest` | 월별 V̂·구간·카테고리 배분 |

**② cashflow — 합성 이벤트 경로 (MR 7)**

V̂를 일별 `CashflowEvent`로 넣으므로 예측을 읽는 모든 cashflow 조회가 바뀐다.

| Endpoint | 영향 |
| --- | --- |
| `GET /api/v1/cashflow/daily-balances` | 일별 예상잔액이 변동 소비만큼 내려간다 |
| `GET /api/v1/cashflow/forecasts/latest` | `minimum_balance`, `risk_date` |
| `GET /api/v1/cashflow/available-today` | 오늘 쓸 수 있는 돈 |
| `GET /api/v1/cashflow/risks` | 예측 기반 위험이 추가된다(구분 값 포함) |
| `GET /api/v1/cashflow/events` | 합성 이벤트가 목록에 나타난다 |

**③ 목표 — 속도 배율 경로 (MR 9)**

`predictedMonthlyPace()`가 `GoalCalculationService`·`GoalHistoryRecorder`·
`SimulationBaselineReader` 세 곳에 들어가면서 번진다.

| Endpoint | 영향 |
| --- | --- |
| `GET /api/v1/goals/{goalId}/forecast` | 예상 달성일, `daysDifference`, `onSchedule` |
| `GET /api/v1/goals/{goalId}/required-saving` | `averageMonthlySaving` |
| `GET /api/v1/goals/{goalId}/risk` | 위험상태 |
| `GET /api/v1/goals/summary` | 목표 요약의 달성일 |
| `GET /api/v1/goals/{goalId}/history` | **이후 기록되는 행부터**. 과거 행은 그대로(7절) |

**④ 하류 — 코드를 고치지 않아도 따라오는 곳**

③의 값을 그대로 받으므로 별도 작업이 없다. 회귀 테스트 대상은 여기가 가장 넓다.

| Endpoint | 경로 |
| --- | --- |
| `POST /api/v1/simulations/*` (실사용 7종, 2.4) | `SimulationBaseline.averageMonthlySaving` |
| `GET /api/v1/simulations/{id}/comparison`, `/cashflow` | 위 기준선 |
| `GET /api/v1/diagnoses/{id}/goal-impact` | `GoalCalculationService` |
| `GET /api/v1/recommendations/{id}/goal-impact`, `/summary`, `/{id}/effect` | 진단이 굳힌 `goalSnapshot` |
| `GET /api/v1/improvements/goal-impact`, `/summary` | `goalCalculations.getForecast()` |
| `POST /api/v1/coach/chat` 외 | 시뮬레이션·추천 결과 문장 속 숫자 |

**⑤ 명시적으로 제외하는 곳**

| Endpoint | 이유 |
| --- | --- |
| `GET /api/v1/reports/monthly/upcoming-expenses` | 확정 지출 목록이다. 예측이 섞이면 안 된다 (6.1) |
| `GET /api/v1/reports/monthly` 효율 점수 | 예측 위험을 입력에서 뺀다 (6.4) |
| 알림 (`notification`) | 예측 기반 부족은 알림을 만들지 않는다 (6.3) |

### 2.4 시뮬레이션 실사용 7종 / 미사용 3종

`SimulationController`의 실행 엔드포인트는 10개이고 **전부** `SimulationBaselineReader:120`의
`paceReader.monthlyPace()`를 기준선으로 쓴다. 그중 **할부·리볼빙·현금서비스 3종은 현재 쓰지
않는다**(2026-09-23 확인).

| 구분 | Endpoint |
| --- | --- |
| 실사용 (7) | `/spending`, `/saving`, `/saving-product`, `/loan/new`, `/loan/prepayment`, `/loan/refinance`, `/combined` |
| 미사용 (3) | `/installment`, `/revolving`, `/cash-advance` |

**미사용이라고 회귀 대상에서 빠지지는 않는다.** 두 가지 이유다.

1. `CombinedSimulationRequest`는 `scenarios[]`의 각 원소로 **COMBINED를 제외한 9종 전부**를
   받는다. 즉 미사용 3종도 `/combined`를 통해 들어올 수 있고, `/combined`는 실사용이다.
2. 기존 테스트가 있다 — `CardSimulationCalculatorTest`, `SimulationCardCombinedApiTest`,
   `CombinedSimulationCalculatorTest`, `SimulationIntegrationApiTest`,
   `SimulationMySqlIntegrationTest`. 속도 정의가 바뀌면 이 테스트들이 깨질 수 있고, 화면에서
   안 쓴다는 사실은 테스트를 고치지 않아도 되는 이유가 되지 못한다.

**달라지는 것은 QA 우선순위뿐이다.** MR 9 검증에서 실사용 7종은 화면까지 확인하고, 미사용 3종은
테스트 통과로 끝낸다.

## 3. 재사용 가능한 자산

`JdbcPatternFeatureReader`가 이미 학습·추론에 필요한 행렬을 만든다. 새로 만들 쿼리는 하나뿐이다.

| 필요한 것 | 현재 | 조치 |
| --- | --- | --- |
| 사용자 × 월 집계 | `readUserMonthly()` — income, spending, fixedSpending, **variableSpending**, discretionary, savingTransfer, loanRepayment, count | 그대로 사용 |
| 인구 × 월 집계 (학습 입력) | `readPopulationMonthly()` | 그대로 사용 |
| 원천 지문 | `readSourceFingerprint()` — cnt, amountSum, lastCreatedAt, lastUpdatedAt (700에서 확립) | 그대로 사용 |
| 군집 배정 | `ClusterAssigner`, `ClusterModelProvider` | 그대로 사용 |
| 아티팩트 로드 + 폴백 | `ClusterModelArtifactLoader` (기동 실패 금지, 경고 후 폴백) | 구조 복제 |
| **사용자 × 월 × 카테고리** | **없음** — `USER_CATEGORY_SQL`은 창 전체를 합칠 뿐 월 분해가 없다 | **신규 쿼리 추가** |

인덱스는 이미 맞는 것이 있다. V6의 `idx_transaction_user_date (user_id, transaction_date)`가
사용자 단위 월·카테고리 집계를 받는다. 반면 `readPopulationMonthly()`는 `user_id` 조건이 없어
24개월 구간을 전부 훑는다(약 1.34M행). **학습 전용 경로이므로 수용하되 런타임에서 부르지 않는다.**

### 3.1 결측 월 처리 — 0이 아니라 결측이다

`PatternFeatures.months`는 거래 없는 달을 **0으로 채운다.** 패턴 지표에는 맞지만 예측 입력에
그대로 쓰면 안 된다. 0을 소비 관측치로 넣으면 level이 끌려 내려가고, 휴면 구간이 있는 사용자의
예측이 실제의 절반 이하가 된다.

예측 입력에서는 `transactionCount == 0`인 달을 **결측으로 다루고 추정에서 제외한다.** 이 구분은
구현 시 놓치기 쉬우므로 `SpendingForecastFeatures`에서 `PatternFeatures.months`를 그대로 받지 말고
`readUserMonthly()` 원본 행을 직접 받는다.

## 4. 모델 식

### 4.1 아티팩트에 들어가는 것

사용자별 파라미터는 넣을 수 없다. 운영 중 사용자가 늘고, 4,000명 × 카테고리 계수는 아티팩트
크기와 재학습 주기를 같이 망가뜨린다. **공유 파라미터만 아티팩트에, 사용자별 성분은 요청 시 추정한다.**

`backend/src/main/resources/ml/spending-ts-v1.json` (수십 KB 예상). 키 구성은
`spending-kmeans-v2.json` 관례를 따른다.

```
modelVersion, algorithm, trainedAt, trainedBy, window{start,end,months},
referenceDate, minUserMonths, populationSize, evaluation{...},
trainedAgainst: "spending-kmeans-v2",                  # 이 계수를 학습할 때 쓴 군집 모델 (아래)
seasonal:  { "<ClusterFeature|BALANCED>": [12개], "ALL": [12개] }  # month-of-year 지수, 기하평균 1
dow:       { "<ClusterFeature|BALANCED>": [7개],  "ALL": [7개]  }  # 요일 계수, 평균 1
category:  { "<ClusterFeature|BALANCED>": { "<code>": [12개] } }   # 월별 카테고리 비중, 각 월 합 1
coldStart: { "<ClusterFeature|BALANCED>": {"variableToIncome": 0.xx} }  # 이력 부족 사용자용 (4.4)
damping:   0.85
alpha:     0.5
```

`coldStart`가 없으면 4.4의 "데이터 월 수 < 3" 폴백이 참조할 값이 없다.

`"ALL"` 칸은 군집 배정이 `RULE_ONLY`로 떨어진 사용자용이다. 군집이 없다고 예측을 포기하지 않는다.

#### 군집 키는 번호도 라벨도 아니다

`ClusterModel.ClusterSummary`의 `clusterId`는 **0부터의 정수**이고 K-Means는 재학습할 때마다
중심 순서가 바뀔 수 있다. 두 아티팩트(`spending-kmeans-*`, `spending-ts-*`)를 서로 다른 시점에
학습하면 `seasonal["0"]`이 학습 때와 다른 성향을 가리킨다. **조용히 틀린 값이 나오고 예외도
로그도 남지 않는다.**

`ClusterLabelPolicy`가 이미 같은 문제를 같은 이유로 풀어 두었다. 그 Javadoc의 표현 그대로다.

> K-Means 번호는 학습마다 뒤바뀔 수 있어 그대로 노출하면 뜻이 없다. 원 단위 중심점을 보고
> 결정적으로 이름을 정한다.

**그러나 라벨 문자열을 키로 쓰는 것도 안전하지 않다.** 같은 Javadoc이 "라벨은 운영자 부여
성격이라 50자 안에서 바꿔도 저장 구조에는 영향이 없다"고 한다. 누가 "저축 중심형"을 "저축형"으로
바꾸면 아티팩트 키가 조용히 안 맞는다. 군집 수가 후보보다 많을 때 붙는 `"소비 유형 N"`은 아예
군집 번호 순서에서 나오므로 처음부터 불안정하다.

- 아티팩트 키는 `ClusterLabelPolicy.representativeFeature(label)`이 돌려주는 **`ClusterFeature`
  enum 이름**(`SAVING_RATE`, `SPENDING_VOLATILITY` …)으로 잡고, 균형형은 `BALANCED` 센티널로 둔다.
  enum은 코드이므로 표시 문구를 바꿔도 흔들리지 않는다
- ts 아티팩트에 학습에 쓴 K-Means 아티팩트의 `modelVersion`을 `trainedAgainst`로 기록한다.
  군집 수나 특징 집합이 바뀌면 라벨 체계 자체가 달라지는데, 이건 키 규칙으로는 못 잡고
  버전 비교로만 잡힌다
- 런타임에 현재 `ClusterModel`의 `modelVersion`과 다르면 군집별 계수를 쓰지 않고 `"ALL"`로
  내려간다(`fallback_reason = MODEL_NOT_AVAILABLE`)

두 모델을 항상 같이 재학습한다는 규칙만으로는 부족하다. 규칙은 깨지고 그때 증상이 안 보인다.

### 4.2 추론 (요청 시, 사용자별)

입력은 최근 12개월 V(u, m)(결측 제외). c는 그 사용자의 **군집 키**(4.1의 `ClusterFeature` 이름,
번호도 표시 라벨도 아니다).

1. **계절 제거** — Ṽ(m) = V(m) / seasonal[c][month(m)]
2. **이상치 클리핑** — med = median(Ṽ), mad = median(|Ṽ − med|),
   Ṽ를 [med ± 3 × 1.4826 × mad]로 자른다.
   U0003 시연 데이터에는 **5월 여행 아웃라이어가 의도적으로 심어져 있다**(636 스토리 seed).
   클리핑이 없으면 그 한 달이 level과 추세를 둘 다 끌어올린다.
3. **level** — 클리핑된 Ṽ에 지수평활(alpha) 적용, 최신 달이 마지막
4. **trend** — Theil–Sen 기울기(모든 점쌍 기울기의 중앙값). 최소제곱은 남은 이상치 하나에도 끌린다
5. **예측** — 감쇠 추세는 **누적 합**이다. `φ = damping`일 때

   ```
   D(h) = φ + φ² + … + φ^h = φ × (1 − φ^h) / (1 − φ)
   base(h) = max(0, level + D(h) × trend)
   V̂(m+h) = base(h) × seasonal[c][month(m+h)]
   ```

   `D(h)`는 `φ / (1 − φ)`(φ=0.85면 5.67)로 **단조 수렴한다.** `φ^h × h` 같은 형태를 쓰면
   h가 커질 때 값이 늘었다가 다시 줄어(φ=0.85, h=20에서 0.78배) 예측선이 level 쪽으로 되꺾인다.
   감쇠가 아예 없으면 6개월 뒤 값이 비현실적으로 벌어진다
6. **구간** — σ는 **계절 제거 축(Ṽ)의 잔차 표준편차**다. V̂는 계절을 곱한 값이므로 구간도 같이
   곱해야 단위가 맞는다.

   ```
   [하한, 상한] = max(0, base(h) ∓ 1.28 × σ × √h) × seasonal[c][month(m+h)]
   ```

   정확도를 주장하는 값이 아니라 화면 표시용이며 응답 문서에 그렇게 적는다
7. **카테고리 배분** — 개인 비중과 군집 비중을 섞는다.
   w = 0.7 × min(1, 데이터월수 / 12),
   share = w × 개인 + (1 − w) × category[c][code][month].
   데이터가 적은 사용자일수록 군집 쪽으로 기운다

### 4.3 학습 (오프라인)

`backend/scripts/train_spending_ts.py` — 기존 `make_demo_*_fixture.py`와 같은 위치·방식. 수동 실행,
산출 아티팩트는 커밋한다.

- `readPopulationMonthly()`와 동일한 SQL로 24개월 인구 행을 읽는다
- 사용자별 정규화 r(u, m) = V(u, m) / mean_u(V) — 절대 금액이 큰 사용자가 계절지수를 지배하지 않게
- 군집 × month-of-year 별 r의 **중앙값** → seasonal[c], 기하평균 1로 정규화
- 표본이 임계(제안: 30 사용자-월) 미만인 (c, month) 칸은 1.0
- 요일 계수·카테고리 비중도 같은 절차

### 4.4 폴백

사유 이름은 `pattern`의 `FallbackReason` **어휘를 그대로 쓰되 enum은 따로 둔다.** 도메인 간
코드값 enum을 재사용하지 않는 것은 `IssueSeverity`가 `cashflow`의 `RiskSeverity`를 쓰지 않은 것과
같은 판단이다. 어휘까지 새로 만들면 같은 상황이 도메인마다 다른 이름으로 불린다.

| 조건 | 결과 | `fallback_reason` |
| --- | --- | --- |
| 아티팩트 없음·형식 오류 | seasonal = dow = 1, 카테고리는 개인 비중 | `MODEL_NOT_AVAILABLE` |
| 군집 미배정 또는 K-Means 버전 불일치 | `"ALL"` 칸 사용 | `MODEL_NOT_AVAILABLE` |
| 데이터 월 수 < 3 | `coldStart[c].variableToIncome × 기준 수입`(2.2) | `USER_DATA_TOO_SHORT` |
| 데이터 월 수 < 2 또는 `IncomeBasis.NONE` | 예측하지 않음 | `FEATURES_UNAVAILABLE` |

아티팩트가 없을 때의 결과는 **현행 평균 기반과 같은 값이 아니다.** seasonal·dow만 1이 될 뿐
MAD 클리핑·Theil–Sen 추세·지수평활은 그대로 돌기 때문에 "최근 3개월 단순 평균"과 다른 수를 낸다.
11.1의 기준선 비교에서 두 값을 구분해 기록한다.

군집이 없으면 `coldStart`도 `"ALL"` 칸을 쓴다.

폴백 사유는 응답과 저장 행에 남긴다. 굳힌 결과가 어느 경로로 나왔는지 구분하지 못하면 재계산
판단이 불가능하다. 아티팩트 문제로 기동이 막히면 안 된다(`ClusterModelArtifactLoader`와 동일).

## 5. 스키마 — `V45__create_spending_forecast.sql`

`cashflow_forecast` 관례를 따른다. 금액은 전부 `BIGINT` 원 단위 정수다.

```
spending_forecast
  forecast_id BIGINT PK AI
  user_id BIGINT NOT NULL FK -> user
  base_month DATE NOT NULL            -- 해당 월 1일로 정규화
  horizon_months INT NOT NULL         -- CHECK > 0
  status VARCHAR(30) NOT NULL         -- CHECK IN (PENDING,RUNNING,COMPLETED,FAILED)
  model_version VARCHAR(50) NULL
  fallback_reason VARCHAR(50) NULL
  request_hash CHAR(64) NULL
  source_fingerprint CHAR(64) NULL    -- 700 지문을 길이 접두 직렬화 후 SHA-256 (아래 주석)
  created_at DATETIME NOT NULL
  completed_at DATETIME NULL
  KEY (user_id, base_month, created_at)
  KEY (user_id, request_hash)

spending_forecast_month
  forecast_month_id BIGINT PK AI
  forecast_id BIGINT NOT NULL FK
  target_month DATE NOT NULL
  predicted_variable BIGINT NOT NULL   -- CHECK >= 0
  predicted_total BIGINT NOT NULL      -- 확정 고정비 + predicted_variable
  lower_bound BIGINT NOT NULL
  upper_bound BIGINT NOT NULL
  UNIQUE (forecast_id, target_month)

spending_forecast_category
  forecast_category_id BIGINT PK AI
  forecast_month_id BIGINT NOT NULL FK
  category_id BIGINT NOT NULL FK -> transaction_category
  predicted_amount BIGINT NOT NULL     -- CHECK >= 0
  UNIQUE (forecast_month_id, category_id)
```

`readSourceFingerprint()`는 해시가 아니라 레코드(cnt, amountSum, lastCreatedAt, lastUpdatedAt)를
돌려주고, pattern은 그것을 `feature_summary_json` 안에 넣어 `.equals()`로 비교한다. 여기서는
컬럼 하나로 인덱스를 태우려는 것이므로 `CashflowEventKeys`와 같은 **길이 접두 인코딩으로
직렬화한 뒤 SHA-256**을 건다. 구분자만 쓰면 값 안의 구분자로 서로 다른 입력이 같은 문자열이 된다.

카테고리를 JSON 컬럼이 아니라 별도 테이블로 두는 이유는 카테고리별 집계·정렬 조회가 들어올 때
JSON 함수로 풀면 인덱스를 타지 못하기 때문이다. `pattern_analysis`의 `*_json` 컬럼은 **조회 시
재계산하지 않기 위한 스냅샷**이라 성격이 다르다.

## 6. Cashflow 연동

### 6.0 선행 조건 — 입금이 없는 예측에 출금만 더하면 안 된다

2.2 때문에 현재 cashflow 예측 곡선은 **현재 잔액에서 출금만 빼 나가는 단조 감소선**이다. 급여
입금이 한 번도 들어오지 않는다.

여기에 변동 소비(출금)를 더하면 거의 모든 사용자가 horizon 안에서 부족에 걸린다. 6.3·6.4에서
알림과 효율 점수를 제외하기로 했으므로 그 둘은 막히지만, **`GET /risks` 화면과
`minimum_balance`·`risk_date`는 그대로 망가진다.**

**결정: 급여 INFLOW 이벤트 소스를 먼저 구현한다.** `AccountScheduledEventSource`는 이미 확장점으로
열려 있고 시그니처가 그대로 맞는다.

```java
List<AccountEvent> read(Long userId, LocalDate start, LocalDate end);
record AccountEvent(Long userId, LocalDate date, String type, String name, Direction direction,
        long amount, String sourceType, String sourceId, String occurrence) { }
```

`financial_profile`의 `payday`와 `monthly_income`으로 월 급여 입금을 만들어 `@Component`로
등록하면 컬렉터가 `List<AccountScheduledEventSource>` 주입으로 받아 간다. 컬렉터·계산기·저장은
손대지 않는다. 인터페이스 주석대로 **소유권을 조회 조건으로 검증**하고 `sourceType`/`sourceId`/
`occurrence`에 민감번호를 넣지 않는다(예: `SALARY` / `financial_profile` / `yyyy-MM`).

이 작업은 시계열 모델과 무관하게 **단독으로도 예측 정확도를 올린다.** 12절 MR 6이며 MR 7의
선행이다.

### 6.1 합성 이벤트로 주입한다

`CashflowDailyBalanceCalculator`의 Javadoc이 이미 이 구멍을 명시한다.

> **제한**: 기획안이 함께 제시한 요일별 평균 변동지출은 반영하지 않는다. (…) 확정 예정 이벤트만
> 반영하므로 예상잔액은 실제보다 낙관적이다.

계산기를 고치지 않는다. V̂를 일별로 쪼개 **`CashflowEvent`로 만들어 넣는다.** 그러면 입금 선반영
순서, `uk_cashflow_event_forecast_dedupe` 중복 차단, 부족 사건 수집이 전부 공짜로 따라온다.
별도 계산 경로를 만들면 이 세 가지가 두 곳에 생긴다.

**단, `CashflowEventCollector.collect()` 안에 넣으면 안 된다.** `UpcomingExpenseService:82`가 같은
`collect()`를 불러 리포트의 "다가오는 지출"을 만든다. 그 목록은 카드 청구·대출 상환·정기결제·적금
납입처럼 **확정된 지출**을 보여 주는 화면이다. 여기에 예측 소비가 섞이면 사용자는 오지 않을
지출을 확정 청구로 읽는다.

주입은 `CashflowForecastRunner`가 수집 결과를 받은 **뒤에** 한다. `collect()`에 플래그를 더하는
방식은 기본값을 잘못 잡으면 같은 사고가 나므로, 확정 이벤트를 만드는 곳과 예측 이벤트를 붙이는
곳을 아예 분리한다.

- 일별 금액 = V̂(월) × dow[c][요일] / (그 달 요일 계수 합)
- `sourceType = "PREDICTED_VARIABLE"`, `sourceId = modelVersion`, `occurrence = yyyy-MM-dd`
- `dedupe_key = CashflowEventKeys.key(userId, sourceType, sourceId, occurrence)`
- `amount > 0` CHECK 때문에 **0원으로 떨어지는 날은 이벤트를 만들지 않는다**
- 금액은 원 단위 정수다. 일별로 내림한 뒤 남는 잔돈은 **그 달의 마지막 생성일 하나에 몰아준다.**
  이 규칙이 없으면 일별 합이 `predicted_variable`과 어긋나 월 예측과 일별 잔액이 서로 다른
  소비를 말하게 된다

### 6.2 캐시 무효화 — 구멍이 하나 있다

`CashflowRequestHash.of(userId, period, calculationVersion)`는 계산 버전을 포함하므로
`CashflowCalculationVersion.CURRENT`를 v1 → v2로 올리면 기존 캐시가 자동으로 갈라진다.
(`CURRENT`에는 이미 540 TODO가 달려 있다.)

**그러나 지문에 원천 데이터가 들어 있지 않다.** 확정 이벤트만 쓰던 동안에는 방어가 됐지만,
거래 이력에 의존하는 V̂가 들어가면 거래가 바뀌어도 같은 기준일·기간·버전이면 낡은 예측이
그대로 재사용된다. `CashflowRequestHash`에 `readSourceFingerprint()` 값을 추가한다.

지문의 창은 **예측 기간(미래)이 아니라 추론 입력 창(기준일 직전 12개월)**이다.
`readSourceFingerprint(userId, startDate, endDate)`는 과거 구간을 받으므로 여기에 미래 구간을
넘기면 항상 빈 지문이 나와 아무것도 무효화하지 못한다.

### 6.3 알림 — 정책 결정이 필요하다

`NotificationEventService.cashflowRisk()`가 최신 COMPLETED 예측의 `cashflow_risk`를 읽어 알림을
만든다. 예측 출금이 들어가면 부족 사건이 늘고 **알림이 같이 늘어난다.** 지금까지 "잔액 부족"
알림은 확정 이벤트 기반이었다.

**결정: 기록하되 알림 대상에서 제외한다.** 예측으로 만든 경고를 확정 경고와 같은 무게로 보내면
알림 신뢰도가 떨어진다.

구현은 `cashflow_risk`에 **예측 기반 여부 컬럼을 추가**하고 `JdbcNotificationEventReader`의
`cashflowRisks()` 쿼리에서 걸러낸다. 6.4의 효율 점수도 같은 컬럼을 쓴다. 조회 API
(`GET /risks`)에는 그대로 내보내되 응답에 구분 값을 실어, 사용자가 "예상 소비 기준"임을 알 수
있게 한다.

### 6.4 월간 리포트 효율 점수도 같이 움직인다

`EfficiencyScoreCalculator:78-99`는 최신 COMPLETED `CashflowForecast`와 그 예측의 `cashflow_risk`
심각도 목록을 읽어 `EfficiencyScorePolicy.cashflow(...)`에 넣는다. 예측 출금이 위험을 늘리면
**효율 점수가 내려간다.**

6.3에서 알림만 막는 것으로는 부족하다. 계산기는 알림이 아니라 `cashflow_risk` **행을 직접** 읽는다.

**결정: 효율 점수에서도 제외한다.** 6.3이 추가하는 예측 기반 여부 컬럼을 여기서도 쓴다.
`EfficiencyScorePolicy.cashflow(boolean, boolean, List<String> riskSeverities)`는 심각도 문자열
목록만 받으므로, **호출 전에 `EfficiencyScoreCalculator`에서 예측 기반 행을 걸러낸다.** 시그니처를
넓히면 정책 클래스가 cashflow의 저장 구조를 알게 되므로 걸러내는 쪽이 경계가 깨끗하다.

그 결과 효율 점수는 MR 7 배포 전후로 **바뀌지 않는다.** READY 월간 리포트는 갱신 API가 없는
불변 스냅샷이라(699) 점수가 바뀌면 배포 지점에서 월별 점프가 생기는데, 애초에 입력이 안 바뀌면
그 문제가 발생하지 않는다. 과거 리포트를 소급해 다시 만들 필요도 없다(7절 결정과 일관).

## 7. 목표 단축 — rev.1 계획의 오류 정정

rev.1 문서는 "예측 속도 = 예측 수입 − 예측 지출"이라고 썼다. **이대로 하면 안 된다.**

`GoalSavingPaceReader.monthlyPace()`는 사용자 전체가 아니라 **목표에 연결된 계좌**만 본다.
연결 예·적금의 PAID 납입 + 연결 입출금 계좌의 순현금흐름이며, 한 계좌를 여러 목표에 담았을 때는
`shareOf()`로 (기여액 / 그 계좌의 총 할당액) 몫만 가져간다(703). 사용자 레벨 예측치를 그대로
넣으면 계좌 스코프와 몫 계산이 둘 다 무너지고, 694에서 잡았던 "연결한 잔액 전체를 한 달 저축으로
세는" 부류의 버그가 다시 생긴다.

### 전제: 달성일 식이 일 단위여야 한다

`GoalForecastFormula.expectedDate()`는 **일 단위로 계산한다**(658). 축은 달력 개월
`t(k) = 기준일.plusMonths(k)`이고, 정수 개월은 그 축 위의 날짜, 나머지 금액은
`(t(whole), t(whole+1)]` 구간의 실제 일수에 비례해 올림한다.

이 전제가 이 절 전체를 지탱한다. 658 이전의 `기준일 + ceil(남은금액 / 속도)개월`은 월 단위
올림이라 속도가 몇 %P 움직여도 달성일이 한 달 단위로만 변해 "변화 없음"으로 나왔다. 아래 배율은
`clamp(0.5, 1.5)` 안에서 움직이므로 대부분의 달에 속도 변화가 크지 않고, **월 단위 올림 식이었다면
계절성 효과가 화면에서 전부 사라진다.** 달성일 식을 월 단위로 되돌리는 변경이 들어오면 이 기능은
같이 죽는다.

### 올바른 형태: 스코프는 그대로, 배율만 씌운다

```java
// 새 메서드. 기존 monthlyPace()의 의미는 건드리지 않는다.
long predictedMonthlyPace(userId, allocation, today) {
    long base = monthlyPace(userId, allocation, today);   // 계좌 스코프·몫 계산 그대로
    double factor = clamp(predictedSavingCapacity / recentSavingCapacity, 0.5, 1.5);
    return Math.max(0L, Math.round(base * factor));
}
```

- `recentSavingCapacity` = 최근 3개 완료 월 평균 (수입 − 소비). `monthlyPace`의 창과 같은 3개월
- `predictedSavingCapacity` = 기준 수입(2.2) − (확정 고정비 + V̂)
- 분모가 0 이하면 factor = 1 (배율을 정의할 수 없다)
- clamp(0.5, 1.5)가 안전장치다. 이게 없으면 계절지수가 큰 달에 달성일이 몇 년씩 튄다

**이것은 가정이다.** 분자·분모는 **사용자 레벨** 저축 여력이고 `base`는 **계좌 스코프** 값이다.
배율을 씌운다는 것은 "사용자 전체의 저축 여력이 x% 변하면 이 목표 계좌로 가는 돈도 x% 변한다"를
가정한다는 뜻이다. 계좌별 월 순현금흐름을 따로 예측하면 가정을 없앨 수 있지만 계좌 단위 월
집계가 없어 모델과 쿼리가 같이 커진다. 가정을 택한 이유와 함께 구현 문서와 API 응답 주석에 적는다.

단축일은 새로 만들지 않는다. `GoalForecastFormula.expectedDate()`에 두 속도를 각각 넣어 차이를
구하고, "목표일 안에 들어온 경우에만 노출"하는 `GeneratorSupport`의 기존 규칙을 그대로 쓴다.

### 호출부는 3개다 (rev.2 정정)

rev.2는 "6개 API를 동시에 바꾼다"고 썼는데, **직접 호출부와 하류 소비자를 섞어 센 것이다.**
`paceReader.monthlyPace(...)`를 실제로 부르는 곳은 셋뿐이다.

| # | 호출부 | 영향 |
| --- | --- | --- |
| 1 | `GoalCalculationService:367` | Goal 510 예상 달성일·필요 저축액 |
| 2 | `GoalHistoryRecorder:63` | `goal_history.forecast_date` (511) |
| 3 | `SimulationBaselineReader:120` | 시뮬레이션 기준선 |

진단·추천·코치는 pace reader를 부르지 않는다. `DiagnosisEvaluator`가 `goalSnapshot`을 evidence
JSON에 굳히고, 추천 생성기(`GeneratorSupport.SnapshotSavingGoal`)는 **그 저장된 JSON**을 읽는다.
코치는 시뮬레이션 결과 문장을 받는다. 따라서 이전 대상은 3개이고 나머지는 따라온다.

1. `predictedMonthlyPace()` 추가 + 단위 테스트 (호출부 없음, 동작 변화 0)
2. 신규 예측 API에서만 사용
3. 호출부 1 → 3 → 2 순서로 이전. 1에서 회귀 테스트가 가장 많이 깨진다

각 단계에서 factor = 1을 강제하면 이전 동작과 **한 날짜도 다르지 않아야 한다.** 이것을 이전
단계마다 테스트로 고정한다.

### 굳힌 스냅샷과 기준이 섞인다 — 별도 처리가 필요하다

속도 정의가 바뀌면 **이미 저장된 "변경 전" 값들이 새 정의로 계산한 "현재" 값과 비교된다.**
셋 다 실제로 그렇게 동작한다.

| 저장물 | 비교 상대 | 증상 |
| --- | --- | --- |
| `improvement_action.before_snapshot`의 `goalForecastDate` | `ImprovementQueryService.goalImpact()`가 `goalCalculations.getForecast()`로 구하는 현재값 | 개선 행동을 하나도 안 했는데 목표일이 앞당겨지거나 밀린 것으로 보인다 |
| 진단 evidence의 `goalSnapshot` | 추천 생성기가 그 위에서 계산하는 개선 후 날짜 | `advancedDays`가 두 정의의 차이만큼 틀어진다 |
| `goal_history.forecast_date` | 이력 그래프의 이전 행들 | 정의 변경 지점에서 꺾인다 |

8.4의 파생 리셋은 **seed 데이터 변경**을 위한 것이고, 이건 **속도 정의 변경**에서 따로 생기는
문제다. 같은 리셋으로 덮이지 않는다.

**결정: 굳힌 스냅샷만 재촬영하고 과거 값은 그대로 둔다.**

- `improvement_action` / 진단 evidence — 새 정의 적용 시점에 **측정 가능 상태의 before 스냅샷을
  현재 기준으로 다시 찍는다.** 그 시점까지의 효과는 0이 되지만 이후 측정이 정확해진다.
  거짓 효과를 그대로 보여 주는 것보다 낫다
- `goal_history` — 소급 재계산하지 **않는다.** 기록 시점 값이라는 계약이 있고(511), 과거 행을
  고치면 이력의 의미가 사라진다
- 월간 리포트 — 다시 만들지 않는다. 6.4의 결정으로 효율 점수 입력이 바뀌지 않으므로 소급할
  이유가 없다

정의 변경일은 `backend/docs/`의 구현 문서에 남긴다. 목표 이력 그래프가 그 지점에서 꺾이는 것은
**의도된 동작**이며, 기록 시점 값이라는 511 계약과 일관된다.

이 처리는 MR 9에 포함되어야 하며, 빠지면 "개선 안 했는데 3개월 단축"이 시연에 그대로 나온다.

## 8. 데이터 변경

### 8.1 기간 확장

`2026-03-01 ~ 2026-08-31` 6개월(사용자당 약 84건, 약 336,000행) →
`2024-09-01 ~ 2026-08-31` 24개월(**약 1,340,000행**). 계절성 학습에 최소 2주기가 필요하므로
24개월이 하한이다.

### 8.2 기간만 늘리면 모델이 학습할 것이 없다

`financial_profile.monthly_income`은 **시간축이 없는 스칼라 한 값**이고, 492 생성 규칙은 이 값의
고정 비율(성향별 variable 20~70%, saving 3~30%)로 월별 금액을 만든다. 기간만 늘리면 24개월이
완전히 평탄해진다. 추세는 0, 계절지수는 전부 1.0으로 학습되고 시연 화면의 단축일이 전부 0이 된다.

생성 규칙에 다음을 넣는다. 전부 (순번, 월번호, 슬롯)의 결정적 함수이며 `RAND()`를 쓰지 않는다
(기존 seed 규약).

| 성분 | 규칙 |
| --- | --- |
| 소득 성장 | income × (1 + g)^(경과월/12), g는 성향별 0.02~0.05 |
| 월 계절성 | 1월 1.15, 2월 1.10, 5월 1.05, 7·8월 1.20, 12월 1.25, 그 외 0.92~1.00 (기하평균 1로 정규화) |
| 카테고리별 패턴 | CULTURE·TAXI 7·8월, SHOPPING·BEAUTY 12월, FOOD·CAFE·GROCERY 평탄 |

**TRAVEL은 쓸 수 없다.** 492의 변동 슬롯 표가 만드는 카테고리는 FOOD·CAFE·GROCERY·
PUBLIC_TRANSPORT·TAXI·SHOPPING·BEAUTY·CULTURE·MEDICAL·ETC 열 개뿐이고 **TRAVEL은 4,000명
데이터에 아예 없다**(U0003 스토리 seed의 5월 아웃라이어만 예외). 여름 성수기를 TRAVEL로 표현하려면
슬롯 표에 카테고리를 새로 넣어야 하는데, TRAVEL은 `DISCRETIONARY_CATEGORY_CODES`에 속하므로
**4,000명의 `discretionaryShare`가 통째로 움직이고 그것은 K-Means 특징이다**(8.5).
기존 카테고리 안에서 계절성을 주는 편이 파급이 작다.

**카테고리마다 다른 패턴을 주는 것이 핵심이다.** 전 카테고리가 같은 배율로 움직이면 4.1의
category[c][code][month] 계수가 월과 무관해져 카테고리 배분이 학습되지 않는다.

계좌 `created_at`(2026-09-07)보다 이른 거래가 생기는 것은 현행 seed에도 이미 있는 상태이므로
새로 생기는 문제가 아니다.

### 8.3 조회 기간 상한은 건드리지 않는다

- `PatternProperties.MAX_LOOKBACK_MONTHS = 12`
- `QueryPeriodPolicy.MAX_PERIOD_MONTHS = 12`

학습은 오프라인 스크립트가 DB를 직접 읽으므로 상한과 무관하다. 런타임 추론은 계절지수를
아티팩트에서 받고 사용자별로는 level·trend만 추정하므로 12개월 입력이면 충분하다(4.2).
상수를 올리면 Spending·Pattern·Report 전 API의 쿼리 비용과 응답 크기가 같이 올라간다.

### 8.4 재적재 순서와 파생 리셋

`backend/database/seeds/README.md`의 18단계 체인을 갱신한다. 거래(6번)를 다시 만들면
8·11·14·15·17번이 연쇄로 다시 실행돼야 하고 **18번 신용점수(680)는 반드시 마지막**이다.

파생 결과는 699 패턴을 그대로 확장한다.

- 월간 리포트 — READY 스냅샷은 갱신 API가 없으므로 **삭제 후 재조회**
- pattern / cashflow forecast / diagnosis — 이력 보존 + `forceRefresh`로 새 행 (삭제하면 추천의
  외래키가 깨진다)
- 대상이 U0003 하나에서 **U0001~U0004 + 4,000명 pattern 집계**로 넓어진다

### 8.5 seed를 바꾸면 K-Means부터 다시 학습해야 한다

배포된 `spending-kmeans-v2.json`은 **`window: 2026-03 ~ 2026-08`, 6개월**로 학습됐고 특징은
`spendingToIncomeRatio`, `savingRate`, `fixedExpenseShare`, `discretionaryShare`,
`spendingVolatility` 다섯이다. 표준화 계수(`standardization.means`/`stds`)와 중심점이 **그때의
분포에 맞춰져 있다.**

24개월 확장과 8.2의 성장률·계절성은 이 다섯 특징을 모두 움직인다. 특히 `spendingVolatility`는
월 소비 변동계수라 **계절성을 넣는 순간 거의 모든 사용자에서 올라간다.** 낡은 중심점으로 배정하면
군집이 틀어지고, ts 아티팩트는 그 군집 위에 얹히므로(4.1 `trainedAgainst`) 계절지수까지 엉뚱한
집단의 값을 쓴다.

**따라서 순서는 seed → K-Means 재학습 → ts 학습이다.** rev.9까지의 MR 표에는 K-Means 재학습이
없었다. 12절 MR 4에 포함한다.

재학습 시 정할 것: 학습 창을 현행 6개월로 둘지 24개월로 늘릴지. **6개월 유지를 제안한다** —
`PatternProperties.DEFAULT_LOOKBACK_MONTHS`가 6이고 군집은 "최근 성향"을 보는 지표이므로,
창을 늘리면 오래된 행동이 현재 군집을 끌어당긴다. 시계열 계절성은 ts 아티팩트가 24개월로
따로 학습한다.

## 9. API

기존 `ApiResponse` / `PageResponse` / `ErrorCode` / `AuthenticatedUserIdResolver` 계약을 재사용한다.

기존 컨트롤러는 전부 `/api/v1/<도메인>` 기준이고 cashflow는 `/api/v1/cashflow` + `/forecasts`,
`/forecasts/latest`다. 같은 형태로 맞춘다. (rev.3까지 적었던 `/forecasts/spending`은 `/api/v1`
접두도 없고 존재하지 않는 기준 경로였다.)

| Method | Path | 비고 |
| --- | --- | --- |
| POST | `/api/v1/spending-forecasts` | 실행. `horizonMonths` 기본 3, 최대 12. 같은 `request_hash`면 재사용, `forceRefresh`로 강제 |
| GET | `/api/v1/spending-forecasts/latest` | 최신 COMPLETED 1건 + 월별·카테고리별 |

복합어 기준 경로는 `/api/v1/deposit-savings` 전례를 따른다.
소유권 조건을 모든 조회·변경에 포함하고, 타인 예측은 `RESOURCE_NOT_FOUND`로 처리한다.

### 9.1 동시성 — 기존 장치를 그대로 복제한다

중복 실행 방지는 `request_hash` 비교만으로 되지 않는다. 같은 사용자의 POST가 동시에 들어오면 둘 다
"재사용할 행 없음"을 보고 각자 새 행을 만든다.

- **사용자 행 잠금** — `PatternUserLock`(= `DiagnosisUserLock`)과 같은 `PESSIMISTIC_WRITE`로 접수와
  완료 순서를 직렬화한다. 계산 동안에는 잠금을 잡지 않는다
- **진행 중 만료** — `CashflowForecastRunner.IN_PROGRESS_TIMEOUT`(10분)과 같은 장치가 필요하다.
  계산 도중 프로세스가 내려가면 헤더가 `PENDING`으로 남아 `forceRefresh`로도 다시 돌릴 수 없다.
  실제 소요 시간을 잰 뒤 값을 정한다
- **기준 시각** — 17개 도메인이 전부 `*Clock`(`Asia/Seoul`, 테스트에서 고정 시각 주입)을 갖고 있다.
  `SpendingForecastClock`을 같은 형태로 둔다. `LocalDate.now()`를 직접 부르면 월 경계 테스트를 쓸 수 없다

## 10. 설정

`app.forecast.*` (`PatternProperties` 관례). `model-location` 기본값
`classpath:ml/spending-ts-v1.json`, `min-user-months`, `default-horizon-months`,
`max-horizon-months`, `damping`, `alpha`. 아티팩트 값이 있으면 아티팩트가 우선하고 설정은 폴백
기본값이다.

`app.forecast.enabled`(기본 true)를 둔다. 운영에서 예측이 이상하게 나올 때 배포 없이 끌 수 있어야
한다. `false`면 신규 API는 `FEATURES_UNAVAILABLE`을 내고, cashflow는 합성 이벤트를 만들지 않으며
(`CURRENT`도 v1로 되돌아가야 지문이 갈린다), 목표 속도는 factor = 1로 고정된다. 이 세 곳이 한
스위치에 묶여 있지 않으면 일부만 꺼져 앞뒤가 다른 숫자가 나온다.

## 11. 검증

### 11.1 모델이 실제로 나은지 먼저 본다

마지막 3개월을 홀드아웃으로 빼고 학습해 MAPE·MAE를 측정하고, **기준선 둘과 비교한다.**

| 비교 대상 | 의미 |
| --- | --- |
| 최근 3개월 단순 평균 | 현행 동작 |
| 이 모델에서 seasonal·dow만 1로 둔 값 | 계절성이 실제로 기여하는 몫 (4.4가 말하는 그 값) |

두 번째 기준선이 없으면 개선이 계절지수 덕인지 로버스트 추정 덕인지 구분되지 않는다. 계절지수가
기여하지 않으면 적용하지 않는 것이 옳은 결론이다. 수치는 `backend/docs/`의 구현 문서에 남긴다.

**배포하는 아티팩트는 24개월 전체로 다시 학습한다.** 홀드아웃 모델은 평가용이며, 평가 수치는
"학습에 쓰지 않은 3개월"에 대한 것이고 배포 모델의 정확도가 아니다. 이 구분을 문서에 적지 않으면
나중에 같은 수치가 운영 성능으로 인용된다.

### 11.2 테스트

- factor = 1, seasonal = 1 고정 시 기존 값과 **완전 일치** (7절 이전 단계마다)
- `spending_type IS NULL`인 지출이 `V`에 들어가고 `predicted_total`이 `spending`과 맞는지 (2.1)
- `D(h)`가 h에 대해 단조 증가하고 `φ/(1−φ)`로 수렴하는지 (4.2-5)
- 일별 이벤트 금액 합 = `predicted_variable` (6.1 잔돈 규칙)
- 개선 행동이 없을 때 `goalImpact`의 단축일이 0인지 — 스냅샷 재촬영 후 (7절)
- 결측 월(`transactionCount = 0`)이 0으로 취급되지 않는지 (3.1)
- 이상치 클리핑이 U0003 5월 여행을 걸러내는지
- 폴백 4종 각각의 `fallback_reason`
- 인증 실패, 소유권 격리, 빈 결과, 경계값(`horizonMonths` 0·13), 이력 0~2개월
- 같은 사용자의 동시 POST가 행을 하나만 만드는지, `PENDING` 만료 후 재실행되는지 (9.1)
- **리포트 "다가오는 지출"에 예측 이벤트가 섞이지 않는지** (6.1) — `UpcomingExpenseService` 경유
- 예측 기반 위험이 늘어도 `EfficiencyScorePolicy.cashflow(…, List<String> riskSeverities)`가
  구분하는지 (6.4) — 호출 전에 걸러낼지 시그니처를 넓힐지 함께 정한다
- K-Means `modelVersion`이 `trainedAgainst`와 다를 때 `"ALL"`로 내려가는지 (4.1)
- `app.forecast.enabled = false`일 때 API·cashflow·목표 속도가 **함께** 꺼지는지 (10절)
- 시뮬레이션 **실사용 7종은 화면까지**, 미사용 3종은 테스트 통과로 확인 (2.4).
  `/combined`가 미사용 3종을 시나리오로 받을 수 있으므로 복합 경로는 실사용 취급한다
- 급여 INFLOW가 생긴 뒤 일별 잔액이 급여일에 올라가는지, `payday`·`monthly_income`이 NULL인
  사용자에게는 이벤트를 만들지 않는지 (6.0)
- `V45`의 CHECK·UNIQUE·FK는 H2로 검증되지 않는다 — `*MySqlIntegrationTest` 경로로 따로 확인

## 12. MR 10개 (이슈 706, 브랜치 하나)

이 저장소는 **이슈 하나에 브랜치 하나, 그 브랜치에서 MR을 여러 번** 낸다. 확인한 이력이다.

| 이슈 | 브랜치 | MR 수 |
| --- | --- | --- |
| 589 | `S15P21D105-589-AI-coach` | 10 |
| 693 | `S15P21D105-693-load-test` | 5 |
| 703 | `S15P21D105-703-be` | 4 |

`S15P21D105-706-db`도 같은 방식으로 쓴다. MR 하나를 develop에 병합한 뒤 같은 브랜치를 최신
develop 위로 rebase하고 다음 MR을 잇는다(679가 678 위로 rebase한 것과 같다).

**브랜치가 하나이므로 MR은 병렬이 아니라 순차다.** rev.8까지는 MR 2와 3의 선행을 둘 다 "1"로
적어 병렬처럼 보였는데, 한 브랜치에서는 성립하지 않는다. 아래 순서가 곧 의존 관계다.

| MR | 범위 | 비고 |
| --- | --- | --- |
| 1 | 이 설계 문서 (`Docs`) | 703이 계획 문서를 같은 이슈 첫 MR로 낸 것과 같다 |
| 2 | seed 생성 규칙 개정(24개월·성장률·카테고리별 계절성) + README 체인 | 착수 전 재적재 시간 측정 |
| 3 | `V45` + 엔티티·Repository + 사용자×월×카테고리 쿼리(2.1 NULL 규칙) | |
| 4 | **K-Means 재학습(8.5)** + ts 학습 스크립트·아티팩트 + 로더·폴백 + 홀드아웃 평가 | 순서는 seed → K-Means → ts |
| 5 | 월별 지출 예측 API + 사용자 락·진행 중 만료(9.1) + `SpendingForecastClock` + `enabled` 스위치 | |
| 6 | **급여 INFLOW 이벤트 소스 구현**(`AccountScheduledEventSource`, 6.0) | 7번의 선행. 단독으로도 예측 정확도가 올라간다 |
| 7 | cashflow 합성 이벤트(`Runner`에 주입, 6.1) + `CURRENT` v2 + 지문 보강 + `V46` 예측 구분 컬럼(6.3·6.4) | 6 없이는 위험이 폭증한다 |
| 8 | `predictedMonthlyPace()` 추가 (호출부 없음, 동작 변화 0) | |
| 9 | 호출부 3개 이전 + **굳힌 스냅샷 재촬영**(7절) | 회귀가 여기 몰린다 |
| 10 | 파생 리셋 seed + 검증 seed | 9 이후여야 리셋을 두 번 돌지 않는다 |

### 12.1 브랜치 이름이 `-db`인 점

**결정(2026-09-23): 10개 MR 전부를 이 브랜치에서 낸다.** `703-be`가 그랬듯 이름은 범위 제한이
아니다. 축소안(cashflow 제외, seed 12개월, 계절성 포기)은 모두 검토 후 채택하지 않았다.

## 13. 미결 사항

1. ~~`-db` 브랜치의 범위~~ — **닫힘(2026-09-23)**: 10개 MR 전부를 이 브랜치에서 낸다.
2. **`predicted_total`을 응답에 실을지** (2절) — 스케줄 없는 고정비는 최근 3개월 `fixedSpending`
   중앙값 반복으로 채우기로 했으나(결정), 그 합계를 서버가 낼지 V̂만 내고 화면에서 조립할지는
   프론트 표시 설계와 함께 정한다. MR 5 착수 전까지
3. **프론트엔드** — 예측 표시는 `frontend/` 변경이 필요하다. 기본 작업 범위 밖이라 별도 보고한다

## 14. 남은 위험

1. **회귀 범위** — 직접 호출부는 3개지만(7절) 표시 숫자가 바뀌는 API는 그보다 많다(MR 9). 고정
   기대값을 가진 기존 테스트가 전부 재검토 대상이고, factor = 1 일치 테스트가 유일한 안전망이다.
   굳힌 스냅샷 재촬영을 빠뜨리면 "개선 안 했는데 단축"이 시연에 나온다.
2. **재적재 시간** — 1.34M행 결정적 생성의 소요 시간은 **측정하지 않았다.** MR 2 착수 시 먼저 잰다.
   `IN_PROGRESS_TIMEOUT`(9.1) 값도 이 측정에 달려 있다.
3. **시연 계약 충돌** — 678·679가 굳힌 U0003 8월 계약(income 3,012,400 / spending 2,559,221 /
   saving 400,000 / fixed 1,565,221)을 24개월 확장이 깨면 699 검증 seed의 선행 검사가 거부한다.
   확장 규칙은 2026-08을 **불변점으로 고정**하고 과거로만 뻗어야 한다.
4. **계절지수의 시연 위험** — 12월 배율 1.25를 학습하면 12월 시연에서 "소비가 늘 것"이라는 예측이
   나온다. 의도한 동작이지만 시연 시나리오와 맞는지 기획 확인이 필요하다.
5. **H2 한계** — 5절 스키마와 신규 월별 카테고리 쿼리(`YEAR()`·`MONTH()` GROUP BY)는 MySQL에서
   따로 본다.
6. **두 아티팩트의 결합** — ts 모델은 K-Means 군집에 얹혀 있다. seed가 바뀌면 K-Means부터
   다시 학습해야 하고(8.5), 이후에도 한쪽만 재학습하면 4.1의 버전 검사가 `"ALL"`로 떨어뜨려
   **조용히 계절성이 사라진다.** 예외가 아니라 품질 저하로 나타나므로 폴백 사유 분포를
   모니터링 대상으로 둔다.
7. **급여 INFLOW 구현 범위** — `payday`나 `monthly_income`이 NULL인 사용자는 급여 이벤트를
   만들 수 없다. 그 사용자들의 예측 곡선은 지금과 같이 단조 감소로 남고, 변동 소비를 더하면
   위험이 늘어난다. MR 6에서 그 집단의 비율을 먼저 재고 처리를 정한다.
8. **고정비 펼침의 불확실성** — 2절 기본안(스케줄 없는 항목은 최근 3개월 중앙값 반복)은 12개월
   앞에서 실제와 크게 벌어질 수 있다. `predicted_total`의 오차가 V̂의 오차보다 클 수 있으며,
   11.1의 평가는 V̂만 재므로 이 부분은 측정되지 않는다.
