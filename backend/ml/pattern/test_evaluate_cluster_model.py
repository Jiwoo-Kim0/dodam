"""평가 스크립트의 DB 없는 부분(라벨 복원·purity·혼동행렬·아티팩트 배정)을 검증한다."""

import json
import tempfile
import unittest
from pathlib import Path

import numpy as np

from evaluate_cluster_model import confusion, evaluate_artifact, external_metrics, purity, seed_type_of
from pattern_features import FEATURE_KEYS


class SeedTypeTest(unittest.TestCase):
    def test_seed_email_maps_to_designed_type_by_sequence(self):
        self.assertEqual(seed_type_of("seed.kmeans.u0001@dodam.invalid"), "BALANCED")
        self.assertEqual(seed_type_of("seed.kmeans.u0002@dodam.invalid"), "SPENDING_FOCUSED")
        self.assertEqual(seed_type_of("seed.kmeans.u0003@dodam.invalid"), "SAVING_FOCUSED")
        self.assertEqual(seed_type_of("seed.kmeans.u0004@dodam.invalid"), "VOLATILE")
        self.assertEqual(seed_type_of("seed.kmeans.u4000@dodam.invalid"), "VOLATILE")
        self.assertEqual(seed_type_of("SEED.KMEANS.U0005@DODAM.INVALID"), "BALANCED")

    def test_non_seed_users_have_no_label(self):
        self.assertIsNone(seed_type_of("pattern516-1@example.com"))
        self.assertIsNone(seed_type_of(None))
        self.assertIsNone(seed_type_of("seed.kmeans.u12@dodam.invalid"))


class MetricsTest(unittest.TestCase):
    def test_purity_and_confusion(self):
        truths = ["A", "A", "A", "B", "B", "C"]
        assignments = np.array([0, 0, 1, 1, 1, 2])
        # 군집0: A,A → 2 / 군집1: A,B,B → 2 / 군집2: C → 1 → 5/6
        self.assertAlmostEqual(purity(truths, assignments), 5 / 6)
        rows, cols, matrix = confusion(["BALANCED", "VOLATILE", "VOLATILE"], np.array([1, 0, 0]), ["x", "y"])
        self.assertEqual(rows, ["x", "y"])
        self.assertEqual(matrix[0][cols.index("VOLATILE")], 2)
        self.assertEqual(matrix[1][cols.index("BALANCED")], 1)

    def test_external_metrics_perfect_recovery(self):
        metrics = external_metrics(["A", "A", "B", "B"], np.array([1, 1, 0, 0]))
        self.assertEqual(metrics["ari"], 1.0)
        self.assertEqual(metrics["purity"], 1.0)
        self.assertEqual(external_metrics(["A"], np.array([0]))["ari"], None)


class ArtifactEvaluationTest(unittest.TestCase):
    def test_artifact_assignment_confidence_and_fallback_shares(self):
        artifact = {
            "modelVersion": "spending-kmeans-v1", "trainedAt": "2026-09-14T11:00:00", "trainedBy": "python-sklearn-test",
            "populationSize": 40, "features": list(FEATURE_KEYS),
            "standardization": {"means": [0.7, 0.15, 0.4, 0.4, 0.05], "stds": [0.2, 0.15, 0.1, 0.2, 0.05]},
            "centroids": [[1.0, -1.0, -0.5, 1.0, 0.5], [-1.0, 1.0, 0.5, -1.0, -0.5]],
            "clusters": [{"clusterId": 0, "label": "소비 집중형", "sampleSize": 20}, {"clusterId": 1, "label": "저축 중심형", "sampleSize": 5}],
        }
        matrix = np.array([
            [0.9, 0.0, 0.35, 0.6, 0.08],   # 소비 집중형 근처
            [0.5, 0.3333, 0.4667, 0.2667, 0.0],  # 저축 중심형 근처
            [0.7, 0.15, 0.4, 0.4, 0.05],   # 두 중심의 정확히 가운데 → 신뢰도 0
        ])
        truths = ["SPENDING_FOCUSED", "SAVING_FOCUSED", None]
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "artifact.json"
            path.write_text(json.dumps(artifact), encoding="utf-8")
            result = evaluate_artifact(path, matrix, truths, min_confidence=0.2, min_cluster_sample=10)

        self.assertEqual(result["evaluatedSamples"], 3)
        self.assertEqual(result["labeledSamples"], 2)
        self.assertEqual(result["ari"], 1.0)
        self.assertEqual(result["confidence"]["min"], 0.0)
        self.assertAlmostEqual(result["fallback"]["lowConfidenceShare"], 1 / 3, places=4)
        self.assertAlmostEqual(result["fallback"]["smallClusterShare"], 1 / 3, places=3)  # 저축 중심형 표본 5 < 10 → 1명
        self.assertEqual(result["confusion"]["rows"], ["소비 집중형", "저축 중심형"])


if __name__ == "__main__":
    unittest.main()
