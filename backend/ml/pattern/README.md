# 소비 유형 군집 모델 학습 (`spending-kmeans-v2`)

API 3 Preference(S15P21D105-518)의 군집 모델을 **Python(scikit-learn)으로 학습**하고, Spring은 그 결과(아티팩트 JSON)를 읽어 **배정·근거 생성만** 한다.
런타임 Python 서비스는 없다. 계약 전체는 [`backend/docs/pattern.md`](../../docs/pattern.md) "군집 메타데이터·Fallback".

```
MySQL(transaction 집계) ─▶ train_cluster_model.py ─▶ spending-kmeans-v1.json ─▶ Spring ClusterModelArtifactLoader ─▶ 배정·clusterProfile
                                                            (backend/src/main/resources/ml/)
```

## 파일

| 파일 | 내용 |
| --- | --- |
| `pattern_features.py` | 특징 정의(기간·비율 특징 5종·클리핑·라벨 정책). Java `PatternFeatureCalculator.clusterVector`·`ClusterLabelPolicy`와 같은 계산 |
| `train_cluster_model.py` | 집계 조회 → 표준화 → k 선택(실루엣) → KMeans 학습 → 아티팩트 JSON |
| `evaluate_cluster_model.py` | 발표용 평가·실험: seed 순번으로 성향 라벨 복원 → ARI·NMI·purity·혼동행렬, k 탐색, 특징/전처리 ablation, 재학습 안정성, 배포 아티팩트 배정 지표·신뢰도·fallback 비율. CSV·Markdown·PNG 출력 |
| `test_pattern_features.py` | Java 단위 테스트와 같은 입력·기대값으로 두 구현의 일치를 확인 |
| `test_evaluate_cluster_model.py` | 라벨 복원·purity·혼동행렬·아티팩트 배정 계산 검증(DB 불필요) |
| `requirements.txt` | numpy, scikit-learn, PyMySQL (그림은 matplotlib이 있을 때만) |

## 실행

```bash
pip install -r backend/ml/pattern/requirements.txt
python -m unittest discover -s backend/ml/pattern -p "test_*.py"      # Java와의 특징 정의 일치 확인

python backend/ml/pattern/train_cluster_model.py \
  --db-url jdbc:mysql://127.0.0.1:3306/dodam --db-user dodam --db-password '...' \
  --reference-date 2026-09-14 --lookback-months 6 --min-user-months 3 \
  --k-range 2 6 \
  --output backend/src/main/resources/ml/spending-kmeans-v1.json
```

- `--db-url`·`--db-user`·`--db-password`는 백엔드와 같은 환경변수 `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`를 기본값으로 읽는다. 읽기 권한만 필요하다.
- `--reference-date`·`--lookback-months`·`--min-user-months`는 Spring `app.pattern.*`와 같은 값을 써야 사용자 특징과 군집 기준 범위가 같은 기간·조건으로 계산된다.
- `--k`(고정) 또는 `--k-range MIN MAX`(실루엣 최대, 동점이면 작은 k). 기본 2~6.
  **2026-09-14 seed 4,000명에서는 실루엣이 k=3·4·6 사이 0.005 차이로 k=6을 골랐지만 설계 성향 대비 ARI는 k=4만 1.0**이라 배포 아티팩트는
  `--k 4`로 고정했다(`backend/docs/pattern.md` "모델 평가"). 실루엣은 진단용으로 보고, k는 평가 스크립트의 외부 지표와 함께 정한다.
- `--export-features features.csv`는 사용자 식별 없이 특징 벡터만 남긴다(분포 확인·k 탐색용). 레포에 커밋하지 않는다.
- `--dry-run`은 요약만 출력한다.

## 평가 (발표 자료용)

```bash
python backend/ml/pattern/evaluate_cluster_model.py \
  --db-url jdbc:mysql://127.0.0.1:3306/dodam --db-user dodam --db-password '...' \
  --reference-date 2026-09-14 --k-range 2 6 --stability-runs 5 \
  --artifact backend/src/main/resources/ml/spending-kmeans-v1.json \
  --out-dir backend/ml/pattern/reports/2026-09-14
```

- **정답 라벨**: seed 사용자(`seed.kmeans.uNNNN@dodam.invalid`)의 설계 성향을 `(NNNN-1) % 4`로 복원한다(0 BALANCED, 1 SPENDING_FOCUSED, 2 SAVING_FOCUSED, 3 VOLATILE).
  DB에는 저장되지 않은 값이며 평가에만 쓴다. seed가 아닌 사용자는 실루엣 등 내부 지표에만 들어간다.
- **산출물**(`reports/<날짜>/`, git 제외): `report.md`(발표에 붙일 표), `summary.json`, `k_sweep.csv`, `feature_ablation.csv`, `preprocessing_ablation.csv`,
  `confusion.csv`, `k_sweep.png`, `confusion.png`.
- **표 내용**: k별 실루엣·inertia·ARI·purity / 특징 하나씩 제외 시 지표 변화 / 표준화·클리핑 유무 / random_state 5회 재학습 간 ARI / 최종 혼동행렬 /
  아티팩트 배정의 신뢰도 분포와 노출 조건(신뢰도 하한·군집 표본 하한)별 fallback 비율.
- **해석 주의**: seed는 성향별 소비 패턴을 뚜렷하게 만든 합성 데이터라 지표가 높게 나온다. 발표에서는 "합성 데이터 기준"임을 밝히고 방법론(익명 집계 특징,
  자동 fallback, 모델 버전 기록)과 진단·추천 연결을 함께 설명한다.

## 아티팩트 형식

```json
{
  "modelVersion": "spending-kmeans-v1", "algorithm": "kmeans",
  "trainedAt": "2026-09-14T11:20:33", "trainedBy": "python-sklearn-1.9.0",
  "window": {"start": "2026-03", "end": "2026-08", "months": 6}, "referenceDate": "2026-09-14", "minUserMonths": 3,
  "features": ["spendingToIncomeRatio", "savingRate", "fixedExpenseShare", "discretionaryShare", "spendingVolatility"],
  "populationSize": 4000, "clusterCount": 4,
  "evaluation": {"kSelection": "silhouette", "silhouetteByK": {"2": 0.61, "3": 0.72, "4": 0.89, "5": 0.80, "6": 0.74}, "silhouette": 0.89, "inertia": 123.4},
  "standardization": {"means": [...5], "stds": [...5]},
  "centroids": [[...5], [...5], [...5], [...5]],
  "clusters": [{"clusterId": 0, "label": "저축 중심형", "sampleSize": 1000,
                "stats": {"savingRate": {"centroid": 0.33, "min": 0.30, "max": 0.36, "p5": 0.31, "p95": 0.35, "mean": 0.33, "std": 0.01}, "...": {}}}]
}
```

- `standardization`·`centroids`(표준화 공간)로 배정하고, `clusters[].stats`(원 단위 p5~p95·평균·표준편차)로 기준 범위와 범위 이탈을 만든다.
- 라벨은 중심값 순위로 결정되며 Java 정책과 같다. 학습마다 뒤바뀌지 않게 하려면 이 JSON을 커밋해 배포 단위로 고정한다.
- 특징 순서가 `features`와 다르면 Spring 로더가 거부하고 인프로세스 학습 또는 RULE_ONLY로 넘어간다(서버 기동은 막지 않는다).

## 배포

1. 시연·운영 DB(seed 적재 후)에 스크립트를 돌려 `backend/src/main/resources/ml/spending-kmeans-v1.json`을 만든다.
2. 커밋하면 jar에 포함돼 `app.pattern.model-location: classpath:ml/spending-kmeans-v1.json`으로 읽힌다. 파일로 따로 두려면 `file:/opt/dodam/models/spending-kmeans-v1.json`.
3. 운영에서는 `app.pattern.train-in-process: false`로 두어 아티팩트가 없을 때 Java가 임의로 학습하지 않게 한다(RULE_ONLY로 전환, 사유 `MODEL_NOT_AVAILABLE`).
4. 재학습 주기(월 1회 등)·자동화(Jenkins 단계)는 remaining-work 518 절에 남긴 결정 사항이다.

## v2 추가 항목 (S15P21D105-632, 2026-09-17)

`train_cluster_model.py`가 v1 항목에 더해 아래를 쓴다. 군집 수·중심·표준화·라벨 규칙은 v1과 같아 라벨은 안정적이다. Spring `ClusterModelArtifact`는 이 항목이
없는 v1 파일도 그대로 읽는다(격자 없는 모델은 위치 계산이 null).

| 항목 | 형식 | 용도 |
| --- | --- | --- |
| `quantileGrid` | `[0, 5, …, 100]` (21점) | 분위 격자 백분위점. Java `ClusterModel.DEFAULT_QUANTILE_GRID`와 같다 |
| `clusters[].quantiles[feature]` | 격자 길이의 값 배열(비감소) | 군집 안 백분위(`percentileInCluster`) |
| `peerGroups[]` | `{ ageBand, gender, clusterId, sampleSize, quantiles, clusterShare }` — 각 키는 null이면 "그 축으로 나누지 않음" | 동년배 비교. 나이대 × (전체·MALE·FEMALE·군집별), 성별 전체 |
| `population` | `{ sampleSize, quantiles, clusterShare }` | 최종 fallback |
| `demographics` | `{ ageBands, referenceDate, usersWithBirthDate, usersWithGender, peerGroupCount }` | 결측 규모 기록 |

- 나이대는 `--reference-date` 기준 만 나이 10세 단위(`10S`~`60S_PLUS`), Java `AgeBand`와 같은 정의다. `user.birth_date`·`gender`(V39)를 `DEMOGRAPHICS_SQL`로 읽고
  K-Means 입력에는 넣지 않는다. 생년월일이 없는 사용자는 군집 학습에는 들어가고 집단 통계에서만 빠진다.
- 2026-09-17 seed 4,000명(491 보강 후) 결과: 군집 4 × 1,000, 실루엣 0.51(v1과 동일), 집단 34개, 배포 파일 95 KB. 학습 약 5초.
