"""소비 유형 군집 모델 평가·실험 스크립트 (발표용 지표·표·그림).

seed 사용자(`seed.kmeans.uNNNN@dodam.invalid`)는 순번 규칙 `(NNNN-1) % 4`로 성향 4종을 만들었고 라벨 누출을 막기 위해 DB에는 저장하지 않았다
(`backend/database/seeds/README.md`). 이 스크립트는 **평가 시점에만** 이메일 순번으로 라벨을 복원해 비지도 학습이 설계된 성향을 얼마나 되찾았는지
(ARI·NMI·purity·혼동행렬) 계산한다. seed가 아닌 사용자는 정답이 없으므로 실루엣 같은 내부 지표에만 들어간다.

실험(모두 같은 집계·특징 정의, 결과는 CSV·Markdown·PNG):
  1. k 탐색           : k=MIN..MAX 실루엣·inertia·ARI·purity
  2. 특징 ablation    : 특징 5종 중 하나씩 뺐을 때(그리고 전체) 실루엣·ARI
  3. 전처리 ablation  : 표준화 유무 × 클리핑 유무
  4. 재학습 안정성    : random_state를 바꿔 여러 번 학습, 실행 간 ARI 평균·최소
  5. 최종 구성 혼동행렬: 군집 라벨 × 설계 성향
  6. (--artifact)     : 배포 아티팩트의 배정 결과 지표, 신뢰도 분포, 노출 조건별 fallback 비율

    python evaluate_cluster_model.py --db-url jdbc:mysql://127.0.0.1:3307/dodam_516_test --db-user root --db-password root \
        --reference-date 2026-09-14 --k-range 2 6 --artifact ../../src/main/resources/ml/spending-kmeans-v1.json --out-dir reports/2026-09-14

주의: seed는 성향별 소비 패턴을 뚜렷하게 만든 합성 데이터라 지표가 높게 나오는 것이 당연하다. 발표에서는 "합성 데이터 기준"을 명시한다.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import re
import sys
from pathlib import Path

import numpy as np
from sklearn.metrics import adjusted_rand_score, normalized_mutual_info_score, silhouette_score

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pattern_features import FEATURE_KEYS, MonthlyRow, Window, cluster_vector, label_clusters  # noqa: E402
from train_cluster_model import DEFAULT_RANDOM_STATE, connect, fit, read_population, standardize  # noqa: E402

SEED_EMAIL = re.compile(r"^seed\.kmeans\.u(\d{4})@dodam\.invalid$")
# seeds/README.md: MOD(순번-1, 4) → 0 BALANCED, 1 SPENDING_FOCUSED, 2 SAVING_FOCUSED, 3 VOLATILE
SEED_TYPES = ("BALANCED", "SPENDING_FOCUSED", "SAVING_FOCUSED", "VOLATILE")


# ---------- 정답 라벨·지표 ----------

def seed_type_of(email: str | None) -> str | None:
    """seed 이메일 순번으로 설계 성향을 복원한다. seed가 아니면 None."""
    if not email:
        return None
    match = SEED_EMAIL.match(email.strip().lower())
    if not match:
        return None
    return SEED_TYPES[(int(match.group(1)) - 1) % 4]


def purity(true_labels: list[str], assignments: np.ndarray) -> float:
    """군집마다 가장 많은 성향의 수를 합해 전체로 나눈다. 정답이 있는 표본만 센다."""
    if len(true_labels) == 0:
        return float("nan")
    total = 0
    for c in set(assignments.tolist()):
        members = [t for t, a in zip(true_labels, assignments) if a == c]
        if members:
            total += max(members.count(t) for t in set(members))
    return total / len(true_labels)


def confusion(true_labels: list[str], assignments: np.ndarray, cluster_labels: list[str]) -> tuple[list[str], list[str], list[list[int]]]:
    """행 = 군집 라벨(번호 순), 열 = 설계 성향(SEED_TYPES 순). 값 = 사용자 수."""
    rows = list(cluster_labels)
    cols = list(SEED_TYPES)
    matrix = [[0] * len(cols) for _ in rows]
    for t, a in zip(true_labels, assignments):
        matrix[int(a)][cols.index(t)] += 1
    return rows, cols, matrix


def external_metrics(true_labels: list[str], assignments: np.ndarray) -> dict:
    if len(true_labels) < 2:
        return {"ari": None, "nmi": None, "purity": None, "labeledSamples": len(true_labels)}
    return {
        "ari": round(float(adjusted_rand_score(true_labels, assignments)), 4),
        "nmi": round(float(normalized_mutual_info_score(true_labels, assignments)), 4),
        "purity": round(purity(true_labels, assignments), 4),
        "labeledSamples": len(true_labels),
    }


def internal_metrics(points: np.ndarray, assignments: np.ndarray, inertia: float | None = None) -> dict:
    result = {"inertia": None if inertia is None else round(float(inertia), 4)}
    if len(set(assignments.tolist())) >= 2 and len(points) > len(set(assignments.tolist())):
        result["silhouette"] = round(float(silhouette_score(points, assignments)), 4)
    else:
        result["silhouette"] = None
    return result


# ---------- 데이터 ----------

def read_emails(connection, user_ids: list[int]) -> dict[int, str]:
    if not user_ids:
        return {}
    with connection.cursor() as cursor:
        cursor.execute("SELECT user_id, email FROM `user` WHERE user_id IN %(ids)s", {"ids": user_ids})
        return {int(r["user_id"]): r["email"] for r in cursor.fetchall()}


def build_dataset(by_user: dict[int, list[MonthlyRow]], emails: dict[int, str], window: Window, min_months: int,
                  clip_values: bool = True) -> tuple[np.ndarray, list[str | None]]:
    """특징 행렬과 같은 순서의 설계 성향(seed가 아니면 None)."""
    vectors, truths = [], []
    for user_id, rows in by_user.items():
        vector = cluster_vector(rows, window, min_months, clip_values=clip_values)
        if vector is None:
            continue
        vectors.append(vector)
        truths.append(seed_type_of(emails.get(user_id)))
    return np.array(vectors, dtype=float).reshape(-1, len(FEATURE_KEYS)), truths


def labeled(truths: list[str | None], assignments: np.ndarray) -> tuple[list[str], np.ndarray]:
    mask = np.array([t is not None for t in truths], dtype=bool)
    return [t for t in truths if t is not None], assignments[mask]


def prepare(matrix: np.ndarray, standardized: bool) -> np.ndarray:
    return standardize(matrix)[0] if standardized else matrix.copy()


# ---------- 실험 ----------

def experiment_k_sweep(points: np.ndarray, truths, k_min: int, k_max: int, random_state: int, n_init: int) -> list[dict]:
    rows = []
    for k in range(max(2, k_min), min(k_max, len(points) - 1) + 1):
        model = fit(points, k, random_state, n_init)
        t, a = labeled(truths, model.labels_)
        rows.append({"k": k, **internal_metrics(points, model.labels_, model.inertia_), **external_metrics(t, a)})
    return rows


def experiment_feature_ablation(matrix: np.ndarray, truths, k: int, random_state: int, n_init: int) -> list[dict]:
    rows = []
    configs = [("all", list(range(len(FEATURE_KEYS))))] + [
        (f"-{key}", [d for d in range(len(FEATURE_KEYS)) if d != i]) for i, key in enumerate(FEATURE_KEYS)]
    for name, dims in configs:
        points = prepare(matrix[:, dims], standardized=True)
        model = fit(points, k, random_state, n_init)
        t, a = labeled(truths, model.labels_)
        rows.append({"features": name, "dimensions": len(dims), **internal_metrics(points, model.labels_, model.inertia_),
                     **external_metrics(t, a)})
    return rows


def experiment_preprocessing(by_user, emails, window, min_months, k, random_state, n_init) -> list[dict]:
    rows = []
    for clip_values in (True, False):
        matrix, truths = build_dataset(by_user, emails, window, min_months, clip_values=clip_values)
        if len(matrix) <= k:
            continue
        for standardized in (True, False):
            points = prepare(matrix, standardized)
            model = fit(points, k, random_state, n_init)
            t, a = labeled(truths, model.labels_)
            rows.append({"clipping": clip_values, "standardization": standardized,
                         **internal_metrics(points, model.labels_, model.inertia_), **external_metrics(t, a)})
    return rows


def experiment_stability(points: np.ndarray, k: int, base_seed: int, runs: int, n_init: int) -> dict:
    assignments = [fit(points, k, base_seed + i, n_init).labels_ for i in range(runs)]
    pairwise = [float(adjusted_rand_score(assignments[i], assignments[j]))
                for i in range(runs) for j in range(i + 1, runs)]
    if not pairwise:
        return {"runs": runs, "meanPairwiseAri": None, "minPairwiseAri": None}
    return {"runs": runs, "meanPairwiseAri": round(float(np.mean(pairwise)), 4), "minPairwiseAri": round(min(pairwise), 4)}


def evaluate_artifact(path: Path, matrix: np.ndarray, truths, min_confidence: float, min_cluster_sample: int) -> dict:
    """배포 아티팩트의 표준화·중심으로 배정했을 때의 지표. Spring ClusterModel.assign과 같은 계산."""
    artifact = json.loads(path.read_text(encoding="utf-8"))
    if list(artifact["features"]) != list(FEATURE_KEYS):
        raise SystemExit("아티팩트 특징 순서가 평가 스크립트와 다릅니다.")
    means = np.array(artifact["standardization"]["means"], dtype=float)
    stds = np.array(artifact["standardization"]["stds"], dtype=float)
    stds = np.where(stds > 0, stds, 1.0)
    centroids = np.array(artifact["centroids"], dtype=float)
    points = (matrix - means) / stds
    distances = np.sqrt(((points[:, None, :] - centroids[None, :, :]) ** 2).sum(axis=2))
    order = np.sort(distances, axis=1)
    assignments = distances.argmin(axis=1)
    if centroids.shape[0] >= 2:
        second = order[:, 1]
        confidence = np.where(second > 0, (second - order[:, 0]) / np.where(second > 0, second, 1.0), 1.0)
        confidence = np.clip(confidence, 0.0, 1.0)
    else:
        confidence = np.ones(len(points))
    cluster_labels = [c["label"] for c in artifact["clusters"]]
    sample_sizes = {c["clusterId"]: c["sampleSize"] for c in artifact["clusters"]}
    t, a = labeled(truths, assignments)
    small_cluster = np.array([sample_sizes.get(int(c), 0) < min_cluster_sample for c in assignments])
    low_confidence = confidence < min_confidence
    rows, cols, cm = confusion(t, a, cluster_labels)
    return {
        "modelVersion": artifact["modelVersion"], "trainedAt": artifact.get("trainedAt"), "trainedBy": artifact.get("trainedBy"),
        "populationSize": artifact.get("populationSize"), "clusterCount": len(cluster_labels), "evaluatedSamples": int(len(points)),
        **internal_metrics(points, assignments), **external_metrics(t, a),
        "confidence": {"mean": round(float(confidence.mean()), 4), "p10": round(float(np.percentile(confidence, 10)), 4),
                       "median": round(float(np.median(confidence)), 4), "min": round(float(confidence.min()), 4)},
        "fallback": {"minConfidence": min_confidence, "lowConfidenceShare": round(float(low_confidence.mean()), 4),
                     "minClusterSampleSize": min_cluster_sample, "smallClusterShare": round(float(small_cluster.mean()), 4),
                     "exposedShare": round(float((~low_confidence & ~small_cluster).mean()), 4)},
        "assignmentShare": {cluster_labels[c]: round(float((assignments == c).mean()), 4) for c in range(len(cluster_labels))},
        "confusion": {"rows": rows, "cols": cols, "matrix": cm},
    }


# ---------- 출력 ----------

def write_csv(path: Path, rows: list[dict]) -> None:
    if not rows:
        return
    keys = list(rows[0].keys())
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=keys)
        writer.writeheader()
        writer.writerows(rows)


def md_table(rows: list[dict]) -> str:
    if not rows:
        return "_(없음)_\n"
    keys = list(rows[0].keys())
    lines = ["| " + " | ".join(keys) + " |", "| " + " | ".join("---" for _ in keys) + " |"]
    for row in rows:
        lines.append("| " + " | ".join("" if row.get(k) is None else str(row.get(k)) for k in keys) + " |")
    return "\n".join(lines) + "\n"


def md_confusion(rows: list[str], cols: list[str], matrix: list[list[int]]) -> str:
    lines = ["| 군집 \\ 설계 성향 | " + " | ".join(cols) + " | 합 |", "| --- | " + " | ".join("---" for _ in cols) + " | --- |"]
    for label, values in zip(rows, matrix):
        lines.append(f"| {label} | " + " | ".join(str(v) for v in values) + f" | {sum(values)} |")
    return "\n".join(lines) + "\n"


def try_plots(out_dir: Path, k_rows: list[dict], final_confusion: dict | None) -> list[str]:
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from matplotlib import font_manager
    except ImportError:
        return []
    # 군집 라벨이 한글이라 한글 글꼴이 있으면 쓴다(Windows 맑은 고딕, macOS AppleGothic, Linux 나눔고딕). 없으면 기본 글꼴로 그린다.
    available = {f.name for f in font_manager.fontManager.ttflist}
    for candidate in ("Malgun Gothic", "AppleGothic", "NanumGothic", "Noto Sans CJK KR", "Noto Sans KR"):
        if candidate in available:
            plt.rcParams["font.family"] = candidate
            break
    plt.rcParams["axes.unicode_minus"] = False
    written = []
    if k_rows:
        ks = [r["k"] for r in k_rows]
        fig, ax1 = plt.subplots(figsize=(6, 3.5))
        ax1.plot(ks, [r["silhouette"] or 0 for r in k_rows], marker="o", label="silhouette")
        if any(r.get("ari") is not None for r in k_rows):
            ax1.plot(ks, [r["ari"] or 0 for r in k_rows], marker="s", label="ARI (seed)")
        ax1.set_xlabel("k"); ax1.set_ylabel("score"); ax1.set_ylim(0, 1.05); ax1.grid(alpha=0.3); ax1.legend()
        ax1.set_title("k sweep")
        fig.tight_layout(); fig.savefig(out_dir / "k_sweep.png", dpi=150); plt.close(fig)
        written.append("k_sweep.png")
    if final_confusion:
        rows, cols, matrix = final_confusion["rows"], final_confusion["cols"], np.array(final_confusion["matrix"])
        fig, ax = plt.subplots(figsize=(6, 3.5))
        image = ax.imshow(matrix, cmap="Blues")
        ax.set_xticks(range(len(cols))); ax.set_xticklabels(cols, rotation=30, ha="right", fontsize=8)
        ax.set_yticks(range(len(rows))); ax.set_yticklabels(rows, fontsize=8)
        for i in range(len(rows)):
            for j in range(len(cols)):
                ax.text(j, i, str(matrix[i, j]), ha="center", va="center", fontsize=8,
                        color="white" if matrix[i, j] > matrix.max() / 2 else "black")
        ax.set_title("cluster vs designed type"); fig.colorbar(image, ax=ax, fraction=0.046)
        fig.tight_layout(); fig.savefig(out_dir / "confusion.png", dpi=150); plt.close(fig)
        written.append("confusion.png")
    return written


# ---------- main ----------

def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--db-url", default=None); parser.add_argument("--db-user", default=None); parser.add_argument("--db-password", default=None)
    parser.add_argument("--reference-date", type=dt.date.fromisoformat, default=dt.date.today())
    parser.add_argument("--lookback-months", type=int, default=6)
    parser.add_argument("--min-user-months", type=int, default=3)
    parser.add_argument("--k", type=int, default=None, help="최종 구성 k. 없으면 k 탐색에서 실루엣 최대 k")
    parser.add_argument("--k-range", type=int, nargs=2, metavar=("MIN", "MAX"), default=[2, 6])
    parser.add_argument("--random-state", type=int, default=DEFAULT_RANDOM_STATE)
    parser.add_argument("--n-init", type=int, default=10)
    parser.add_argument("--stability-runs", type=int, default=5)
    parser.add_argument("--artifact", type=Path, default=None, help="배포 아티팩트 JSON. 주면 그 모델의 배정 지표도 계산")
    parser.add_argument("--min-confidence", type=float, default=0.2, help="Spring app.pattern.min-confidence")
    parser.add_argument("--min-cluster-sample-size", type=int, default=10, help="Spring app.pattern.min-cluster-sample-size")
    parser.add_argument("--out-dir", type=Path, default=None, help="기본 backend/ml/pattern/reports/<날짜시각>")
    args = parser.parse_args(argv)
    import os
    args.db_url = args.db_url or os.environ.get("DB_URL")
    args.db_user = args.db_user or os.environ.get("DB_USERNAME")
    args.db_password = args.db_password if args.db_password is not None else os.environ.get("DB_PASSWORD")
    if not args.db_url:
        parser.error("--db-url 또는 DB_URL이 필요합니다.")
    return args


def main(argv=None) -> int:
    args = parse_args(argv)
    window = Window.ending_before(args.reference_date, args.lookback_months)
    out_dir = args.out_dir or (Path(__file__).resolve().parent / "reports" / dt.datetime.now().strftime("%Y%m%d-%H%M%S"))
    out_dir.mkdir(parents=True, exist_ok=True)

    with connect(args.db_url, args.db_user, args.db_password) as connection:
        by_user = read_population(connection, window)
        emails = read_emails(connection, list(by_user.keys()))
    matrix, truths = build_dataset(by_user, emails, window, args.min_user_months)
    n_labeled = sum(t is not None for t in truths)
    print(f"[eval] window {window.label(window.start)}~{window.label(window.end)}, samples={len(matrix)}, seed-labeled={n_labeled}")
    if len(matrix) < 3:
        raise SystemExit("평가할 표본이 3개 미만입니다.")
    points = prepare(matrix, standardized=True)

    k_rows = experiment_k_sweep(points, truths, args.k_range[0], args.k_range[1], args.random_state, args.n_init)
    write_csv(out_dir / "k_sweep.csv", k_rows)
    if args.k is not None:
        k = args.k
    else:
        candidates = [r for r in k_rows if r["silhouette"] is not None]
        k = max(candidates, key=lambda r: (r["silhouette"], -r["k"]))["k"] if candidates else min(4, len(points) - 1)
    print(f"[eval] final k={k}")

    ablation_rows = experiment_feature_ablation(matrix, truths, k, args.random_state, args.n_init)
    write_csv(out_dir / "feature_ablation.csv", ablation_rows)
    preprocessing_rows = experiment_preprocessing(by_user, emails, window, args.min_user_months, k, args.random_state, args.n_init)
    write_csv(out_dir / "preprocessing_ablation.csv", preprocessing_rows)
    stability = experiment_stability(points, k, args.random_state, args.stability_runs, args.n_init)

    final = fit(points, k, args.random_state, args.n_init)
    raw_centroids = final.cluster_centers_ * standardize(matrix)[2] + standardize(matrix)[1]
    final_labels = label_clusters(raw_centroids.tolist())
    t, a = labeled(truths, final.labels_)
    final_metrics = {"k": k, **internal_metrics(points, final.labels_, final.inertia_), **external_metrics(t, a)}
    rows, cols, cm = confusion(t, a, final_labels)
    with (out_dir / "confusion.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle); writer.writerow(["cluster"] + cols)
        for label, values in zip(rows, cm):
            writer.writerow([label] + values)

    artifact_result = None
    if args.artifact:
        artifact_result = evaluate_artifact(args.artifact, matrix, truths, args.min_confidence, args.min_cluster_sample_size)
        print(f"[eval] artifact {artifact_result['modelVersion']} ari={artifact_result['ari']} purity={artifact_result['purity']} "
              f"exposed={artifact_result['fallback']['exposedShare']}")

    summary = {
        "generatedAt": dt.datetime.now().replace(microsecond=0).isoformat(),
        "window": {"start": window.label(window.start), "end": window.label(window.end), "months": window.months},
        "samples": int(len(matrix)), "seedLabeledSamples": n_labeled, "minUserMonths": args.min_user_months,
        "kSweep": k_rows, "featureAblation": ablation_rows, "preprocessingAblation": preprocessing_rows,
        "stability": stability, "final": {**final_metrics, "labels": final_labels, "confusion": {"rows": rows, "cols": cols, "matrix": cm}},
        "artifact": artifact_result,
        "caveat": "seed는 성향별 소비 패턴을 뚜렷하게 만든 합성 데이터라 지표가 높게 나온다. 발표에서는 합성 데이터 기준임을 명시한다.",
    }
    (out_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    plots = try_plots(out_dir, k_rows, summary["final"]["confusion"])
    report = [
        f"# 소비 유형 군집 모델 평가 ({summary['generatedAt']})", "",
        f"- 기간 {summary['window']['start']} ~ {summary['window']['end']} ({summary['window']['months']}개월), 표본 {summary['samples']}명, "
        f"seed 정답 라벨 {n_labeled}명, min-user-months {args.min_user_months}",
        f"- 최종 k = {k}, 실루엣 {final_metrics['silhouette']}, ARI {final_metrics['ari']}, NMI {final_metrics['nmi']}, purity {final_metrics['purity']}",
        f"- 재학습 안정성(random_state {args.stability_runs}회): 실행 간 ARI 평균 {stability['meanPairwiseAri']}, 최소 {stability['minPairwiseAri']}",
        f"- {summary['caveat']}", "",
        "## 1. k 탐색", "", md_table(k_rows),
        "## 2. 특징 ablation (하나씩 제외, 표준화 O)", "", md_table(ablation_rows),
        "## 3. 전처리 ablation", "", md_table(preprocessing_rows),
        "## 4. 최종 구성 혼동행렬 (라벨은 중심값 순위 정책)", "", md_confusion(rows, cols, cm),
    ]
    if artifact_result:
        ar = artifact_result
        report += [
            f"## 5. 배포 아티팩트 `{ar['modelVersion']}` ({ar['trainedBy']}, {ar['trainedAt']})", "",
            f"- 학습 표본 {ar['populationSize']}, 평가 표본 {ar['evaluatedSamples']}, 실루엣 {ar['silhouette']}, ARI {ar['ari']}, purity {ar['purity']}",
            f"- 신뢰도 평균 {ar['confidence']['mean']}, 중앙 {ar['confidence']['median']}, p10 {ar['confidence']['p10']}, 최소 {ar['confidence']['min']}",
            f"- 노출 조건: 신뢰도 < {ar['fallback']['minConfidence']} 비율 {ar['fallback']['lowConfidenceShare']}, "
            f"표본 < {ar['fallback']['minClusterSampleSize']} 군집 배정 비율 {ar['fallback']['smallClusterShare']}, 군집 노출 비율 {ar['fallback']['exposedShare']}",
            f"- 군집별 배정 비율: {ar['assignmentShare']}", "",
            md_confusion(ar["confusion"]["rows"], ar["confusion"]["cols"], ar["confusion"]["matrix"]),
        ]
    if plots:
        report += ["## 그림", ""] + [f"![{p}]({p})" for p in plots] + [""]
    (out_dir / "report.md").write_text("\n".join(report), encoding="utf-8")
    print(f"[eval] report written: {out_dir / 'report.md'} (+ summary.json, csv, {', '.join(plots) if plots else 'no plots'})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
