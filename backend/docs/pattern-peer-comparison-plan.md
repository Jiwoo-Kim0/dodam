# 패턴 분석 — 군집 내 위치·동년배(peer) 비교 설계안 (S15P21D105-632)

작성 2026-09-17, Jira S15P21D105-632 "k-means 클러스터 결과 Report 개선". 구현은 §6 순서로 이 티켓에서 진행한다. 629(User 생년월일·성별·신용등급, V39)로 들어온 인구통계 값을 K-Means 군집 결과 위에 얹어
"같은 나이대 안에서 내 위치", "과소비 집단 중에서도 특히 많이 쓰는가"를 전달하기 위한 API·모델·저장 변경을 정리한다.
현재 구현 기준은 [pattern.md](pattern.md)(516~518), 인구통계 컬럼은 [user-profile.md](user-profile.md)다.

## 1. 원칙 — 나이·성별은 특징이 아니라 비교 축

- **K-Means 입력 벡터에 넣지 않는다.** 벡터는 소비 성향 5종(`spendingToIncomeRatio`, `savingRate`, `fixedExpenseShare`, `discretionaryShare`,
  `spendingVolatility`)으로 유지한다. 나이를 넣으면 소비 성향이 아니라 나이로 군집이 갈리고, seed 4,000명·기존 회원은 값이 NULL이라 학습 표본에서
  빠지거나 대치값으로 군집을 왜곡한다.
- 대신 **군집 배정 뒤** 두 종류의 위치를 계산한다.
  1. 군집 내 위치: 배정된 군집 표본 안에서 내 특징 값의 분위(percentile). "소비 집중형 중 상위 8%".
  2. 동년배 위치: 나이대(·성별) 집단 안에서 내 특징 값의 분위와, 그 집단의 군집 구성비. "20대 여성 중 소비 비율 상위 12%", "20대의 31%가 소비 집중형".
- 지금 아티팩트는 군집별 p5·p95·평균·표준편차만 있어 **범위 안에 있는 사용자는 전부 `deviation = 0`** 으로 구분이 안 된다. 분위 격자를 추가하는 것이
  핵심 변경이다.

## 2. API 변경

### 2-1. `GET /patterns/me/spending` — `clusterProfile` 확장 (기존 응답에 필드 추가, 하위 호환)

| 위치 | 추가 필드 | 의미 |
| --- | --- | --- |
| `features[]` | `percentileInCluster` (0~100), `rankBand` | 배정 군집 표본 안에서 사용자 값의 분위. `rankBand`는 `TOP_10` / `UPPER`(75~90) / `MIDDLE`(25~75) / `LOWER`(10~25) / `BOTTOM_10` |
| `clusterProfile` | `positionSummary { feature, percentileInCluster, rankBand }` | 문장 생성의 근거가 되는 대표 특징 1개. 군집 라벨이 소비 집중형이면 `spendingToIncomeRatio`, 저축 중심형이면 `savingRate` 등 라벨별 대표 특징 표를 둔다 |
| `clusterProfile` | `insights[]` | §2-3 공통 형식. 군집 내 위치만으로 만드는 문장(예: `CLUSTER_TOP_DECILE_SPENDING`) |

기존 `deviation`·`deviationScore`(범위 이탈)는 그대로 둔다. 이탈은 "군집 기준 범위 밖", 분위는 "범위 안 순위"로 역할이 다르다.

### 2-2. 신규 `GET /patterns/me/peer-comparison?dimension=`

| 항목 | 내용 |
| --- | --- |
| 인증 | 필요. 본인만 |
| `dimension` | `AGE_BAND` / `GENDER` / `AGE_BAND_GENDER`(기본) / `CLUSTER_AGE_BAND`(내 군집 안에서 같은 나이대) |
| 전제 | 최신 COMPLETED 패턴 분석이 있어야 한다(없으면 `GET /patterns/me`와 같은 404 `PATTERN_ANALYSIS_NOT_FOUND`) |
| 응답 | 아래 예시 |

```json
{
  "analysisId": 123,
  "modelVersion": "spending-kmeans-v2",
  "peerGroup": {
    "dimension": "AGE_BAND_GENDER", "ageBand": "20S", "gender": "FEMALE",
    "sampleSize": 412, "fallbackFrom": null
  },
  "features": [
    { "feature": "spendingToIncomeRatio", "displayName": "소득 대비 소비 비율", "userValue": 0.81,
      "peerP25": 0.52, "peerMedian": 0.63, "peerP75": 0.74,
      "percentileInPeer": 88, "rankBand": "UPPER" }
  ],
  "clusterShare": [
    { "clusterId": 2, "clusterLabel": "소비 집중형", "peerRatio": 0.31, "isMine": true },
    { "clusterId": 1, "clusterLabel": "저축 중심형", "peerRatio": 0.24, "isMine": false }
  ],
  "insights": [
    { "code": "PEER_TOP_DECILE_SPENDING", "feature": "spendingToIncomeRatio", "percentile": 88,
      "message": "같은 20대 여성 중 소득 대비 소비 비율이 상위 12%입니다." },
    { "code": "PEER_CLUSTER_SHARE", "clusterId": 2, "ratio": 0.31,
      "message": "20대 여성 10명 중 3명이 소비 집중형이고, 당신도 그중 한 명입니다." }
  ],
  "unavailableReason": null
}
```

- 생년월일이 없으면 `peerGroup = null`, `features`·`clusterShare`·`insights`는 빈 배열, `unavailableReason = DEMOGRAPHICS_MISSING`. `GENDER` 차원인데
  성별만 없으면 같은 처리. 200으로 준다(404 아님) — 화면이 "생년월일을 입력하면 비교를 볼 수 있어요"를 그릴 수 있게.
- 표본 부족은 자동 확장한다. `AGE_BAND_GENDER` → `AGE_BAND` → 전체 모집단(`ALL`) 순이며 실제로 쓴 차원을 `peerGroup.dimension`에, 원래 요청을
  `fallbackFrom`에 적는다. 전체 모집단까지 갔으면 `unavailableReason = PEER_GROUP_TOO_SMALL`과 함께 전체 기준 값만 준다.
- 모델 아티팩트에 집단 분포가 없으면(v1) `unavailableReason = MODEL_NOT_SUPPORTED`. pattern.md의 `MODEL_NOT_AVAILABLE` 계열과 같은 fallback 방식.

### 2-3. `insights[]` 공통 형식

| 필드 | 내용 |
| --- | --- |
| `code` | enum. FE 아이콘·강조 분기와 AI 코치(589) 근거 키로 쓴다. 초기값: `CLUSTER_TOP_DECILE_SPENDING`, `CLUSTER_BOTTOM_DECILE_SAVING`, `PEER_TOP_DECILE_SPENDING`, `PEER_ABOVE_MEDIAN_SPENDING`, `PEER_TOP_QUARTILE_SAVING`, `PEER_CLUSTER_SHARE`, `PEER_VOLATILITY_HIGH` |
| `feature` / `clusterId` | 근거 특징 또는 군집 |
| `percentile` / `ratio` | 숫자 근거. FE가 문장을 다시 만들 때 쓴다 |
| `message` | 서버 템플릿 문장. 한국어 고정, 수치는 반올림 |

문장 규칙과 임계값은 서버에 둔다. 예: "과소비 집단 중에서도 특히 많이" = `clusterLabel = 소비 집중형` **그리고** `percentileInCluster ≥ 90`.
임계값(90/75/25/10)은 `app.pattern.insight.*`로 빼고 기획과 확정한다. 코치가 읽는 데이터 범위([ai-coach.md](ai-coach.md))에 이 코드 목록을 추가한다.

## 3. 모델·아티팩트 변경 (`spending-kmeans-v2`)

`backend/ml/pattern/train_cluster_model.py`가 만드는 JSON에 아래를 추가한다. 군집 수·중심·표준화 파라미터·라벨 규칙은 v1과 같아 라벨 안정성은 유지된다.

| 추가 항목 | 형식 | 용도 |
| --- | --- | --- |
| `clusters[].quantiles[feature]` | 5% 간격 21점(`q0`~`q100`) | 군집 내 분위. 선형 보간으로 `percentileInCluster` 계산 |
| `peerGroups[]` | `{ ageBand, gender(null 허용), sampleSize, quantiles[feature], clusterShare[clusterId → ratio] }` | 동년배 분위·군집 구성비. `gender = null` 행은 나이대 전체 |
| `population.quantiles[feature]`, `population.clusterShare` | 전체 모집단 | 최종 fallback |
| `demographics` | `{ ageBands: ["10S","20S",...,"60S_PLUS"], referenceDate, usersWithBirthDate, usersWithGender }` | 나이대 정의와 결측 규모 기록 |

- 학습 SQL에 `user.birth_date`·`gender`를 조인한다. 나이대는 학습 기준일(`referenceDate`) 기준 만 나이의 10세 단위. 생년월일 없는 사용자는 군집 학습에는
  들어가고 `peerGroups`에서만 빠진다.
- Java `ClusterModel`에 `percentileOf(clusterId, feature, value)`, `peerPercentileOf(groupKey, feature, value)`, `peerClusterShare(groupKey)`를 추가한다.
  격자에서 선형 보간, 격자 밖은 0 또는 100. v1 아티팩트(격자 없음)를 읽으면 `supportsPercentiles() = false`로 §2-2의 `MODEL_NOT_SUPPORTED`가 된다.
- `ClusterModelArtifactLoader`는 `quantiles`·`peerGroups`가 없어도 v1을 그대로 읽어야 한다(필드 선택).
- 인프로세스 Java 학습(`train-in-process`, local 전용)도 같은 격자를 만들도록 `KMeansClusterer.Result`에 분위 계산을 추가한다. 그렇지 않으면 local과 prod
  응답 형태가 갈린다.

## 4. 저장 (V40 예정)

`pattern_analysis`에 `peer_stats_json` JSON 컬럼을 추가한다. 분석 완료 시점에 §2-2 응답 본문(`peerGroup`·`features`·`clusterShare`)을 차원별로 굳혀 저장한다.

- 이유는 `cluster_stats_json`과 같다. 조회 때 재계산하지 않아 모델을 다시 학습해도 과거 분석 결과가 바뀌지 않고, AI 코치가 금융망·모델 재호출 없이 읽는다.
- 나이대는 **분석 시점 나이**로 굳힌다. 생일이 지나 나이대가 바뀌면 다음 분석(`forceRefresh` 또는 월 재분석)에서 갱신된다.
- `percentileInCluster`는 `cluster_stats_json`의 `features[]`에 함께 저장한다(형식 확장, 기존 행은 필드 없음 → null).
- BE-A 2차 구간 다음 번호는 V40이다([마이그레이션 README](../src/main/resources/db/migration/README.md)). ERD `PatternAnalysis 추가 컬럼` 절을 먼저 고친다.

## 5. 표본·통계 기준 (서비스 초기값, 팀 확정 필요)

| 항목 | 초기값 | 근거 |
| --- | --- | --- |
| 나이대 | 10세 단위, `10S`~`60S_PLUS` | seed 배분과 화면 문구("20대") 일치 |
| 동년배 최소 표본 | `AGE_BAND` 30명, `AGE_BAND_GENDER` 30명, `CLUSTER_AGE_BAND` 10명 | 미달 시 상위 차원으로 확장. 5명짜리 집단의 "상위 10%"는 의미가 없다 |
| 분위 격자 | 5% 간격 21점 | 아티팩트 크기(군집 4 × 특징 5 × 21 + 집단 ≤ 14 × 5 × 21) 수 KB 수준 |
| `rankBand` 경계 | 90 / 75 / 25 / 10 | 기획 문구 "상위 10%"에 맞춤 |
| 문장 임계값 | TOP_DECILE ≥ 90, ABOVE_MEDIAN ≥ 50 | `app.pattern.insight.*` |

개인 식별 위험은 분포(분위)만 공개하므로 없다. 집단 최소 표본은 통계 신뢰성 때문이다.

## 6. 선행 조건과 순서

1. **seed 보강 (491)** — seed 4,000명에 생년월일·성별이 없다. 지금 학습하면 `peerGroups`가 비어 `DEMOGRAPHICS_MISSING`만 나온다. 코호트(BALANCED /
   SPENDING_FOCUSED / SAVING_FOCUSED / VOLATILE)별로 나이대·성별을 배분해 `DODAM_USER_ACCOUNT_FINANCIAL_PROFILE_SEED.sql`에 넣고 서버에 재적재한다.
   시연 계정 U0001~U0004도 채운다(629 남은 작업과 같은 항목).
2. 학습 스크립트·아티팩트 v2 (§3) — 로컬 MySQL에서 학습해 커밋. 평가 결과는 pattern.md §모델 평가에 기록.
3. `ClusterModel` 분위 계산 + v1 하위 호환 로더.
4. `clusterProfile` 확장 (§2-1) — 기존 FE 호출을 깨지 않는 추가 필드.
5. V40 `peer_stats_json` + 분석 완료 시 저장.
6. `GET /patterns/me/peer-comparison` (§2-2) + `insights` 템플릿.
7. AI 코치(589) 근거 섹션에 `insights` 코드 연결, FE 화면 연동(현재 FE는 패턴 API 5개를 하나도 호출하지 않는다 — [fe-be-api-integration-status.md](../../docs/fe-be-api-integration-status.md) §5).

## 7. 검토한 대안

- **나이대별로 K-Means를 따로 학습**: 나이대 × 성별 14개 모델은 표본이 쪼개져 군집이 불안정하고, 라벨이 집단마다 달라져 "소비 집중형" 같은 공통 언어를 잃는다. 채택하지 않음.
- **나이·성별을 특징 벡터에 추가**: §1의 이유로 채택하지 않음.
- **신용등급(A~E)을 비교 축에 추가**: 같은 방식으로 가능하지만 seed 회원은 금융망 회원이 아니라 등급이 없다. 실회원 등급이 쌓인 뒤 재검토.
- **조회 시점 실시간 계산(저장 안 함)**: 구현은 단순하지만 모델 재학습 시 과거 분석 결과가 바뀌고 코치가 매번 모델을 읽어야 한다. `cluster_stats_json`과 일관되게 저장 방식을 택했다.
