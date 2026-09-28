"""소비 유형 군집의 특징 정의. Java `PatternFeatureCalculator.clusterVector`·`ClusterLabelPolicy`와 **같은 계산**이어야 한다.

Java 쪽 정의를 바꾸면 이 파일과 `test_pattern_features.py`의 기대값도 함께 바꾼다. 두 구현이 어긋나면 사용자 값과 군집 기준 범위를
다른 잣대로 비교하게 된다.
"""

from __future__ import annotations

import datetime as dt
import math
from dataclasses import dataclass
from typing import Iterable, Sequence

# Java ClusterFeature 순서. (키, 표시 이름, 상한)
FEATURES: tuple[tuple[str, str, float], ...] = (
    ("spendingToIncomeRatio", "소득 대비 소비율", 3.0),
    ("savingRate", "저축률", 2.0),
    ("fixedExpenseShare", "고정지출 비중", 1.0),
    ("discretionaryShare", "재량소비 비중", 1.0),
    ("spendingVolatility", "소비 변동성", 2.0),
)
FEATURE_KEYS: tuple[str, ...] = tuple(key for key, _, _ in FEATURES)

# Java ClusterLabelPolicy.PRIORITY
LABEL_PRIORITY: tuple[tuple[str, str], ...] = (
    ("savingRate", "저축 중심형"),
    ("spendingVolatility", "소비 변동형"),
    ("spendingToIncomeRatio", "소비 집중형"),
    ("discretionaryShare", "재량소비 집중형"),
    ("fixedExpenseShare", "고정지출 집중형"),
)
BALANCED_LABEL = "균형형"

# Java JdbcPatternFeatureReader.DISCRETIONARY_CATEGORY_CODES / LOAN_REPAYMENT_CATEGORY_CODE
DISCRETIONARY_CATEGORY_CODES = ("FOOD", "CAFE", "SHOPPING", "BEAUTY", "CULTURE", "TRAVEL", "TAXI")
LOAN_REPAYMENT_CATEGORY_CODE = "LOAN_REPAYMENT"


@dataclass(frozen=True)
class Window:
    """기준일이 속한 달을 뺀 완료된 달 `months`개 (Java PatternWindow.endingBefore)."""

    start: tuple[int, int]  # (year, month)
    end: tuple[int, int]

    @staticmethod
    def ending_before(reference: dt.date, months: int) -> "Window":
        if months < 1:
            raise ValueError("months must be >= 1")
        end_year, end_month = _minus_months(reference.year, reference.month, 1)
        start_year, start_month = _minus_months(end_year, end_month, months - 1)
        return Window((start_year, start_month), (end_year, end_month))

    @property
    def start_date(self) -> dt.date:
        return dt.date(self.start[0], self.start[1], 1)

    @property
    def end_date(self) -> dt.date:
        year, month = self.end
        next_year, next_month = (year + 1, 1) if month == 12 else (year, month + 1)
        return dt.date(next_year, next_month, 1) - dt.timedelta(days=1)

    @property
    def months(self) -> int:
        return (self.end[0] - self.start[0]) * 12 + (self.end[1] - self.start[1]) + 1

    def label(self, ym: tuple[int, int]) -> str:
        return f"{ym[0]:04d}-{ym[1]:02d}"


def _minus_months(year: int, month: int, delta: int) -> tuple[int, int]:
    index = year * 12 + (month - 1) - delta
    return index // 12, index % 12 + 1


@dataclass(frozen=True)
class MonthlyRow:
    """Java JdbcPatternFeatureReader.MonthlyPatternRow와 같은 열."""

    user_id: int
    year: int
    month: int
    income: int
    spending: int
    fixed_spending: int
    variable_spending: int
    discretionary: int
    saving_transfer: int
    loan_repayment: int
    transaction_count: int


def clip(value: float, cap: float) -> float:
    if math.isnan(value) or math.isinf(value):
        return 0.0
    return max(0.0, min(cap, value))


def coefficient_of_variation(values: Sequence[float]) -> float | None:
    """모표준편차 / 평균. 표본 2개 미만이거나 평균 ≤ 0이면 None (Java Stats.coefficientOfVariation)."""
    if len(values) < 2:
        return None
    mean = sum(values) / len(values)
    if mean <= 0:
        return None
    variance = sum((v - mean) ** 2 for v in values) / len(values)
    return math.sqrt(variance) / mean


def cluster_vector(rows: Iterable[MonthlyRow], window: Window, min_months: int, clip_values: bool = True) -> list[float] | None:
    """한 사용자의 군집 특징 벡터 (Java PatternFeatureCalculator.clusterVector).

    기간 밖 행은 버리고, 거래가 있는 달이 `min_months` 미만이거나 소득·소비가 0이면 None.
    평균은 거래가 있는 달로 나누고 Java와 같이 반올림(round half up)한다.
    `clip_values=False`는 평가 스크립트의 클리핑 유무 비교(ablation)용이며 Java와는 다른 값이다.
    """
    in_window = [
        r for r in rows
        if window.start <= (r.year, r.month) <= window.end and r.transaction_count > 0
    ]
    n = len(in_window)
    if n < min_months:
        return None
    income_sum = sum(r.income for r in in_window)
    if income_sum <= 0:
        return None
    spending_sum = sum(r.spending for r in in_window)
    if spending_sum <= 0:
        return None
    fixed_sum = sum(r.fixed_spending for r in in_window)
    discretionary_sum = sum(r.discretionary for r in in_window)
    saving_sum = sum(r.saving_transfer for r in in_window)

    avg_income = _round_half_up(income_sum / n)
    avg_spending = _round_half_up(spending_sum / n)
    avg_saving = _round_half_up(saving_sum / n)
    volatility = coefficient_of_variation([float(r.spending) for r in in_window])

    caps = {key: (cap if clip_values else math.inf) for key, _, cap in FEATURES}
    return [
        clip(avg_spending / avg_income, caps["spendingToIncomeRatio"]),
        clip(avg_saving / avg_income, caps["savingRate"]),
        clip(fixed_sum / spending_sum, caps["fixedExpenseShare"]),
        clip(discretionary_sum / spending_sum, caps["discretionaryShare"]),
        clip(0.0 if volatility is None else volatility, caps["spendingVolatility"]),
    ]


def _round_half_up(value: float) -> int:
    """Java Math.round: 양수에서 .5는 올림."""
    return int(math.floor(value + 0.5))


def label_clusters(raw_centroids: Sequence[Sequence[float]]) -> list[str]:
    """Java ClusterLabelPolicy.label. 우선순위 순으로 아직 이름 없는 군집 중 중심값이 가장 큰 군집에 라벨을 준다."""
    k = len(raw_centroids)
    labels: list[str | None] = [None] * k
    named: set[int] = set()
    for feature_key, label in LABEL_PRIORITY:
        if k - len(named) <= 1:
            break
        dim = FEATURE_KEYS.index(feature_key)
        best, best_value = -1, -math.inf
        for c in range(k):
            if c in named:
                continue
            value = raw_centroids[c][dim]
            if value > best_value:
                best, best_value = c, value
        labels[best] = label
        named.add(best)
    result: list[str] = []
    balanced_used = False
    extra = 1
    for c in range(k):
        if labels[c] is not None:
            result.append(labels[c])  # type: ignore[arg-type]
        elif not balanced_used:
            result.append(BALANCED_LABEL)
            balanced_used = True
        else:
            result.append(f"소비 유형 {extra}")
            extra += 1
    return result


def round4(value: float) -> float:
    """Java Stats.round4 (Math.round(v * 10000) / 10000)."""
    return _round_half_up(value * 10_000.0) / 10_000.0
