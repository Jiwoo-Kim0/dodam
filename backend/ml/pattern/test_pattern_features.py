"""Java `PatternFeatureCalculatorTest`·`ClusterLabelPolicyTest`와 같은 입력·기대값. 두 구현의 특징 정의가 같은지 확인한다.

실행: `python -m pytest backend/ml/pattern` (pytest가 없으면 `python -m unittest discover -s backend/ml/pattern`).
"""

import datetime as dt
import unittest

from pattern_features import (
    BALANCED_LABEL,
    MonthlyRow,
    Window,
    cluster_vector,
    label_clusters,
    round4,
)


def row(year, month, income, spending, fixed, variable, discretionary, saving, loan, count, user_id=1):
    return MonthlyRow(user_id, year, month, income, spending, fixed, variable, discretionary, saving, loan, count)


class WindowTest(unittest.TestCase):
    def test_window_excludes_reference_month(self):
        window = Window.ending_before(dt.date(2026, 9, 14), 6)
        self.assertEqual(window.start, (2026, 3))
        self.assertEqual(window.end, (2026, 8))
        self.assertEqual(window.months, 6)
        self.assertEqual(window.start_date, dt.date(2026, 3, 1))
        self.assertEqual(window.end_date, dt.date(2026, 8, 31))

    def test_window_across_year_boundary(self):
        window = Window.ending_before(dt.date(2026, 2, 1), 6)
        self.assertEqual(window.start, (2025, 8))
        self.assertEqual(window.end, (2026, 1))


class ClusterVectorTest(unittest.TestCase):
    window = Window.ending_before(dt.date(2026, 9, 14), 6)

    def test_matches_java_calculator_fixture(self):
        # PatternFeatureCalculatorTest.지표를_정의대로_계산하고_거래_없는_달은_0으로_채운다
        rows = [
            row(2026, 3, 3_000_000, 1_800_000, 1_000_000, 800_000, 400_000, 1_000_000, 300_000, 6),
            row(2026, 4, 3_000_000, 1_800_000, 1_000_000, 800_000, 400_000, 1_000_000, 300_000, 6),
            row(2026, 7, 3_000_000, 1_800_000, 1_000_000, 800_000, 400_000, 0, 300_000, 5),
            row(2026, 8, 3_000_000, 1_800_000, 1_000_000, 800_000, 400_000, 1_000_000, 300_000, 6),
        ]
        vector = cluster_vector(rows, self.window, 3)
        self.assertIsNotNone(vector)
        self.assertEqual([round4(v) for v in vector], [0.6, 0.25, 0.5556, 0.2222, 0.0])

    def test_population_conditions(self):
        # PatternFeatureCalculatorTest.모집단_특징은_개월_수와_소득_조건을_만족할_때만_만든다
        two_months = [
            row(2026, 7, 3_000_000, 1_000_000, 0, 1_000_000, 0, 0, 0, 3),
            row(2026, 8, 3_000_000, 1_000_000, 0, 1_000_000, 0, 0, 0, 3),
        ]
        self.assertIsNone(cluster_vector(two_months, self.window, 3))
        self.assertIsNotNone(cluster_vector(two_months, self.window, 2))
        no_income = [
            row(2026, 6, 0, 1_000_000, 0, 1_000_000, 0, 0, 0, 3),
            row(2026, 7, 0, 1_000_000, 0, 1_000_000, 0, 0, 0, 3),
            row(2026, 8, 0, 1_000_000, 0, 1_000_000, 0, 0, 0, 3),
        ]
        self.assertIsNone(cluster_vector(no_income, self.window, 3))

    def test_volatility_and_out_of_window_rows(self):
        rows = [
            row(2026, 1, 3_000_000, 9_000_000, 0, 9_000_000, 0, 0, 0, 3),  # 기간 밖: 무시
            row(2026, 7, 3_000_000, 1_000_000, 0, 1_000_000, 0, 0, 0, 3),
            row(2026, 8, 3_000_000, 1_500_000, 0, 1_500_000, 0, 0, 0, 3),
        ]
        vector = cluster_vector(rows, self.window, 2)
        self.assertEqual(round4(vector[4]), 0.2)  # std 250,000 / mean 1,250,000
        self.assertEqual(round4(vector[0]), 0.4167)  # 1,250,000 / 3,000,000


class LabelPolicyTest(unittest.TestCase):
    def test_labels_match_java_policy(self):
        # ClusterLabelPolicyTest.중심값이_가장_큰_군집부터_우선순위대로_라벨을_붙이고_마지막은_균형형이다
        centroids = [
            [0.90, 0.00, 0.30, 0.60, 0.10],
            [0.50, 0.33, 0.47, 0.27, 0.02],
            [0.67, 0.17, 0.45, 0.25, 0.05],
            [0.63, 0.07, 0.40, 0.35, 0.37],
        ]
        self.assertEqual(label_clusters(centroids), ["소비 집중형", "저축 중심형", BALANCED_LABEL, "소비 변동형"])

    def test_two_clusters(self):
        self.assertEqual(
            label_clusters([[0.7, 0.05, 0.4, 0.3, 0.1], [0.5, 0.30, 0.4, 0.3, 0.1]]),
            [BALANCED_LABEL, "저축 중심형"],
        )

    def test_more_clusters_than_candidates(self):
        centroids = [[((c * 7 + d * 3) % 11) / 10.0 for d in range(5)] for c in range(8)]
        labels = label_clusters(centroids)
        self.assertEqual(len(labels), 8)
        self.assertEqual(len(set(labels)), 8)
        self.assertIn(BALANCED_LABEL, labels)
        self.assertTrue(any(label.startswith("소비 유형 ") for label in labels))


if __name__ == "__main__":
    unittest.main()
