"""소비 유형 군집 모델(`spending-kmeans-v1`) 학습 스크립트.

MySQL의 거래 집계에서 사용자별 비율 특징 5종을 만들고(Java `PatternFeatureCalculator.clusterVector`와 같은 정의), 표준화 후
scikit-learn KMeans(k-means++)로 학습해 모델 아티팩트 JSON을 만든다. Spring `ClusterModelArtifactLoader`가 이 JSON을 읽어 배정에 쓴다.

    python train_cluster_model.py --db-url jdbc:mysql://127.0.0.1:3307/dodam_516_test --db-user root --db-password root \
        --reference-date 2026-09-14 --k-range 2 6 --output ../../src/main/resources/ml/spending-kmeans-v1.json

- 입력은 익명 집계값(사용자·월별 합계)만 읽고 거래 원문·식별정보는 읽지 않는다(기획안 14-6). 사용자 ID는 표본 수 계산에만 쓴다.
- `--k`를 주면 그 k로 학습하고, `--k-range MIN MAX`면 실루엣 점수가 가장 높은 k를 고른다(동점이면 작은 k).
- 아티팩트에는 모델 버전·학습 시각·기간·표본 수·표준화 파라미터·중심·군집별 기준 범위(p5~p95)·라벨·평가 지표가 들어간다.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import os
import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlparse

import numpy as np
import pymysql
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pattern_features import (  # noqa: E402
    DISCRETIONARY_CATEGORY_CODES,
    FEATURE_KEYS,
    FEATURES,
    LOAN_REPAYMENT_CATEGORY_CODE,
    MonthlyRow,
    Window,
    cluster_vector,
    label_clusters,
    round4,
)

MODEL_VERSION = "spending-kmeans-v1"
DEFAULT_RANDOM_STATE = 20260914

# Java JdbcPatternFeatureReader.POPULATION_MONTHLY_SQL과 같은 집계.
IS_SPENDING = "(entry.transaction_type = 'EXPENSE' AND (entry.spending_type IS NULL OR entry.spending_type <> 'NON_SPENDING'))"
POPULATION_MONTHLY_SQL = f"""
SELECT entry.user_id AS uid, YEAR(entry.transaction_date) AS y, MONTH(entry.transaction_date) AS m,
       COALESCE(SUM(CASE WHEN entry.transaction_type = 'INCOME' THEN entry.amount ELSE 0 END), 0) AS income,
       COALESCE(SUM(CASE WHEN {IS_SPENDING} THEN entry.amount ELSE 0 END), 0) AS spending,
       COALESCE(SUM(CASE WHEN {IS_SPENDING} AND entry.spending_type = 'FIXED' THEN entry.amount ELSE 0 END), 0) AS fixed_spending,
       COALESCE(SUM(CASE WHEN {IS_SPENDING} AND entry.spending_type = 'VARIABLE' THEN entry.amount ELSE 0 END), 0) AS variable_spending,
       COALESCE(SUM(CASE WHEN {IS_SPENDING} AND category.code IN %(discretionary)s THEN entry.amount ELSE 0 END), 0) AS discretionary,
       COALESCE(SUM(CASE WHEN entry.transaction_type = 'EXPENSE' AND entry.spending_type = 'NON_SPENDING' THEN entry.amount ELSE 0 END), 0)
           AS saving_transfer,
       COALESCE(SUM(CASE WHEN {IS_SPENDING} AND category.code = %(loan)s THEN entry.amount ELSE 0 END), 0) AS loan_repayment,
       COUNT(*) AS cnt
FROM `transaction` entry
JOIN `user` owner ON owner.user_id = entry.user_id AND owner.deleted_at IS NULL
LEFT JOIN transaction_category category ON category.category_id = entry.category_id
WHERE entry.hidden = FALSE
  AND entry.transaction_date BETWEEN %(start)s AND %(end)s
GROUP BY entry.user_id, YEAR(entry.transaction_date), MONTH(entry.transaction_date)
ORDER BY uid, y, m
"""


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--db-url", default=os.environ.get("DB_URL"),
                        help="jdbc:mysql://host:port/db 또는 mysql://host:port/db (기본: 환경변수 DB_URL)")
    parser.add_argument("--db-user", default=os.environ.get("DB_USERNAME"), help="기본: 환경변수 DB_USERNAME")
    parser.add_argument("--db-password", default=os.environ.get("DB_PASSWORD"), help="기본: 환경변수 DB_PASSWORD")
    parser.add_argument("--reference-date", type=dt.date.fromisoformat, default=dt.date.today(),
                        help="기준일(YYYY-MM-DD). 이 달을 뺀 완료된 달이 학습 기간이다. 기본: 오늘")
    parser.add_argument("--lookback-months", type=int, default=6, help="학습 기간 개월 수. Spring app.pattern.lookback-months와 맞춘다")
    parser.add_argument("--min-user-months", type=int, default=3, help="표본에 넣을 최소 개월 수. app.pattern.min-user-months")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--k", type=int, help="고정 군집 수")
    group.add_argument("--k-range", type=int, nargs=2, metavar=("MIN", "MAX"), default=None,
                       help="실루엣 점수로 고를 k 범위. 기본 2 6")
    parser.add_argument("--random-state", type=int, default=DEFAULT_RANDOM_STATE)
    parser.add_argument("--n-init", type=int, default=10, help="KMeans 초기화 반복 수")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent / "spending-kmeans-v1.json",
                        help="아티팩트 JSON 경로. 배포용은 backend/src/main/resources/ml/spending-kmeans-v1.json")
    parser.add_argument("--export-features", type=Path, default=None,
                        help="사용자별 특징 벡터를 CSV로 남긴다(user_id 없이 순번만). 검증·탐색용")
    parser.add_argument("--dry-run", action="store_true", help="파일을 쓰지 않고 요약만 출력")
    args = parser.parse_args(argv)
    if not args.db_url:
        parser.error("--db-url 또는 DB_URL이 필요합니다.")
    if args.k is None and args.k_range is None:
        args.k_range = [2, 6]
    return args


def connect(db_url: str, user: str | None, password: str | None) -> pymysql.Connection:
    url = re.sub(r"^jdbc:", "", db_url)
    parsed = urlparse(url)
    if parsed.scheme != "mysql":
        raise SystemExit(f"지원하지 않는 DB URL: {db_url}")
    return pymysql.connect(
        host=parsed.hostname or "127.0.0.1",
        port=parsed.port or 3306,
        user=user or (unquote(parsed.username) if parsed.username else "root"),
        password=password if password is not None else (unquote(parsed.password) if parsed.password else ""),
        database=parsed.path.lstrip("/"),
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


def read_population(connection: pymysql.Connection, window: Window) -> dict[int, list[MonthlyRow]]:
    with connection.cursor() as cursor:
        cursor.execute(POPULATION_MONTHLY_SQL, {
            "discretionary": DISCRETIONARY_CATEGORY_CODES,
            "loan": LOAN_REPAYMENT_CATEGORY_CODE,
            "start": window.start_date,
            "end": window.end_date,
        })
        by_user: dict[int, list[MonthlyRow]] = {}
        for r in cursor.fetchall():
            by_user.setdefault(int(r["uid"]), []).append(MonthlyRow(
                int(r["uid"]), int(r["y"]), int(r["m"]), int(r["income"]), int(r["spending"]), int(r["fixed_spending"]),
                int(r["variable_spending"]), int(r["discretionary"]), int(r["saving_transfer"]), int(r["loan_repayment"]),
                int(r["cnt"])))
        return by_user


def build_matrix(by_user: dict[int, list[MonthlyRow]], window: Window, min_months: int) -> np.ndarray:
    vectors = [v for rows in by_user.values() if (v := cluster_vector(rows, window, min_months)) is not None]
    return np.array(vectors, dtype=float).reshape(-1, len(FEATURE_KEYS))


def standardize(matrix: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Java KMeansClusterer와 같은 z-score(모표준편차, 0이면 1)."""
    means = matrix.mean(axis=0)
    stds = matrix.std(axis=0, ddof=0)
    stds = np.where(stds > 0, stds, 1.0)
    return (matrix - means) / stds, means, stds


def fit(points: np.ndarray, k: int, random_state: int, n_init: int) -> KMeans:
    return KMeans(n_clusters=k, init="k-means++", n_init=n_init, random_state=random_state).fit(points)


def select_k(points: np.ndarray, k_min: int, k_max: int, random_state: int, n_init: int) -> tuple[int, dict[int, float]]:
    scores: dict[int, float] = {}
    upper = min(k_max, len(points) - 1)
    for k in range(max(2, k_min), upper + 1):
        model = fit(points, k, random_state, n_init)
        if len(set(model.labels_)) < 2:
            continue
        scores[k] = float(silhouette_score(points, model.labels_))
    if not scores:
        raise SystemExit("실루엣을 계산할 수 있는 k가 없습니다(표본이 너무 적음).")
    best = max(sorted(scores), key=lambda k: (scores[k], -k))
    return best, scores


def build_artifact(matrix: np.ndarray, points: np.ndarray, means: np.ndarray, stds: np.ndarray, model: KMeans,
                   window: Window, evaluation: dict, args: argparse.Namespace) -> dict:
    k = model.n_clusters
    centroids_std = model.cluster_centers_
    centroids_raw = centroids_std * stds + means
    labels = label_clusters(centroids_raw.tolist())
    clusters = []
    for c in range(k):
        members = matrix[model.labels_ == c]
        stats = {}
        for d, (key, _, _) in enumerate(FEATURES):
            column = members[:, d] if len(members) else np.array([0.0])
            stats[key] = {
                "centroid": round4(float(centroids_raw[c][d])),
                "min": round4(float(column.min())),
                "max": round4(float(column.max())),
                "p5": round4(float(np.percentile(column, 5))),
                "p95": round4(float(np.percentile(column, 95))),
                "mean": round4(float(column.mean())),
                "std": round4(float(column.std(ddof=0))),
            }
        clusters.append({"clusterId": c, "label": labels[c], "sampleSize": int(len(members)), "stats": stats})
    import sklearn  # noqa: PLC0415

    return {
        "modelVersion": MODEL_VERSION,
        "algorithm": "kmeans",
        "trainedAt": dt.datetime.now().replace(microsecond=0).isoformat(),
        "trainedBy": f"python-sklearn-{sklearn.__version__}",
        "window": {"start": window.label(window.start), "end": window.label(window.end), "months": window.months},
        "referenceDate": args.reference_date.isoformat(),
        "minUserMonths": args.min_user_months,
        "features": list(FEATURE_KEYS),
        "populationSize": int(len(matrix)),
        "clusterCount": k,
        "evaluation": evaluation,
        "standardization": {"means": [float(v) for v in means], "stds": [float(v) for v in stds]},
        "centroids": [[float(v) for v in row] for row in centroids_std],
        "clusters": clusters,
    }


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    window = Window.ending_before(args.reference_date, args.lookback_months)
    print(f"[train] window {window.label(window.start)} ~ {window.label(window.end)} ({window.months} months), "
          f"min-user-months={args.min_user_months}")

    with connect(args.db_url, args.db_user, args.db_password) as connection:
        by_user = read_population(connection, window)
    matrix = build_matrix(by_user, window, args.min_user_months)
    print(f"[train] users with rows={len(by_user)}, usable samples={len(matrix)}")
    if len(matrix) < 2:
        raise SystemExit("학습할 표본이 2개 미만입니다.")

    if args.export_features:
        with open(args.export_features, "w", newline="", encoding="utf-8") as handle:
            writer = csv.writer(handle)
            writer.writerow(FEATURE_KEYS)
            writer.writerows(matrix.tolist())
        print(f"[train] features exported: {args.export_features}")

    points, means, stds = standardize(matrix)
    evaluation: dict = {"random_state": args.random_state, "n_init": args.n_init}
    if args.k is not None:
        k = args.k
        if k < 1 or k > len(points):
            raise SystemExit("k는 1 이상, 표본 수 이하여야 합니다.")
        evaluation["kSelection"] = "fixed"
    else:
        k, scores = select_k(points, args.k_range[0], args.k_range[1], args.random_state, args.n_init)
        evaluation["kSelection"] = "silhouette"
        evaluation["silhouetteByK"] = {str(kk): round(score, 4) for kk, score in sorted(scores.items())}
        print("[train] silhouette by k: " + ", ".join(f"k={kk}: {score:.4f}" for kk, score in sorted(scores.items())))
    model = fit(points, k, args.random_state, args.n_init)
    evaluation["inertia"] = round(float(model.inertia_), 4)
    if k >= 2 and len(set(model.labels_)) >= 2:
        evaluation["silhouette"] = round(float(silhouette_score(points, model.labels_)), 4)

    artifact = build_artifact(matrix, points, means, stds, model, window, evaluation, args)
    for cluster in artifact["clusters"]:
        print(f"[train] cluster {cluster['clusterId']} {cluster['label']}: n={cluster['sampleSize']}, "
              + ", ".join(f"{key}={cluster['stats'][key]['centroid']}" for key in FEATURE_KEYS))
    print(f"[train] k={k}, silhouette={evaluation.get('silhouette')}, inertia={evaluation['inertia']}")

    if args.dry_run:
        print("[train] dry-run: 파일을 쓰지 않았습니다.")
        return 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with open(args.output, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(artifact, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(f"[train] artifact written: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
