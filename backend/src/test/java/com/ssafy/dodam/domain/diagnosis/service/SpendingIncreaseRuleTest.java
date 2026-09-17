package com.ssafy.dodam.domain.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader.CategorySpendingRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 627 카테고리 소비 증가 규칙의 순수 계산. 631 기준: 기준월 2026-09 → 최근 = 2026-08(지난달 1개월), 기준 = 2026-05~07(직전 3개월 평균).
 * 진행 중인 9월은 넣지 않는다.
 */
class SpendingIncreaseRuleTest {
    private static final YearMonth BASELINE_START = YearMonth.of(2026, 5);
    private static final YearMonth RECENT_START = YearMonth.of(2026, 8);
    private static final YearMonth RECENT_END = YearMonth.of(2026, 8);
    private final ObjectMapper json = new ObjectMapper();
    private final JdbcSpendingReader reader = Mockito.mock(JdbcSpendingReader.class);
    private final SpendingIncreaseRule rule = new SpendingIncreaseRule(reader, json);

    private static CategorySpendingRow row(YearMonth month, Long categoryId, String code, long total) {
        return new CategorySpendingRow(month, categoryId, code, code == null ? null : code + "명", total, 1);
    }

    private static SpendingIncreaseRule.Result evaluate(List<CategorySpendingRow> rows) {
        return SpendingIncreaseRule.evaluate(rows, BASELINE_START, RECENT_START, RECENT_END);
    }

    @Test
    void lastMonthAboveThreeMonthAverageBecomesItemWithConsecutiveMonthsAndSeverity() {
        List<CategorySpendingRow> rows = new ArrayList<>();
        // FOOD: 5~7월 100,000 / 120,000 / 140,000(평균 120,000), 8월 160,000 → +40,000, 33.3%, 3개월 연속 증가, MEDIUM
        rows.add(row(YearMonth.of(2026, 5), 1L, "FOOD", 100_000L));
        rows.add(row(YearMonth.of(2026, 6), 1L, "FOOD", 120_000L));
        rows.add(row(YearMonth.of(2026, 7), 1L, "FOOD", 140_000L));
        rows.add(row(YearMonth.of(2026, 8), 1L, "FOOD", 160_000L));
        // CAFE: 5~7월 50,000씩(평균 50,000), 8월 120,000 → +70,000, 140.0%, HIGH
        for (int month = 5; month <= 7; month++) {
            rows.add(row(YearMonth.of(2026, month), 2L, "CAFE", 50_000L));
        }
        rows.add(row(YearMonth.of(2026, 8), 2L, "CAFE", 120_000L));
        // 기간 밖(진행 중인 9월, 기준 기간 이전 4월)과 미분류는 무시한다
        rows.add(row(YearMonth.of(2026, 9), 1L, "FOOD", 5_000_000L));
        rows.add(row(YearMonth.of(2026, 4), 1L, "FOOD", 9_000_000L));
        rows.add(row(YearMonth.of(2026, 8), null, null, 9_000_000L));

        SpendingIncreaseRule.Result result = evaluate(rows);

        assertThat(result.recentPeriod()).isEqualTo(new SpendingIncreaseRule.Period("2026-08", "2026-08", 1));
        assertThat(result.baselinePeriod()).isEqualTo(new SpendingIncreaseRule.Period("2026-05", "2026-07", 3));
        assertThat(result.evaluatedCategories()).isEqualTo(2);
        assertThat(result.items()).extracting(SpendingIncreaseRule.Item::categoryCode).containsExactly("CAFE", "FOOD");

        SpendingIncreaseRule.Item food = result.items().get(1);
        assertThat(food.monthlyAverage()).isEqualTo(160_000L);
        assertThat(food.previousMonthlyAverage()).isEqualTo(120_000L);
        assertThat(food.increaseAmount()).isEqualTo(40_000L);
        assertThat(food.increaseRatePercent()).isEqualTo(33.3);
        assertThat(food.consecutiveMonths()).isEqualTo(3);
        assertThat(food.severity()).isEqualTo(IssueSeverity.MEDIUM);
        assertThat(food.monthly()).hasSize(4).extracting(SpendingIncreaseRule.MonthAmount::amount)
                .containsExactly(100_000L, 120_000L, 140_000L, 160_000L);
        SpendingIncreaseRule.Item cafe = result.items().get(0);
        assertThat(cafe.increaseRatePercent()).isEqualTo(140.0);
        assertThat(cafe.consecutiveMonths()).isEqualTo(1);
        assertThat(cafe.severity()).isEqualTo(IssueSeverity.HIGH);

        Diagnosis diagnosis = Diagnosis.start(77L, null, LocalDateTime.of(2026, 9, 13, 12, 0));
        DiagnosisIssue issue = rule.toIssue(diagnosis, result, food);
        assertThat(issue.getDomain()).isEqualTo("SPENDING");
        assertThat(issue.getType()).isEqualTo("CATEGORY_SPENDING_INCREASE");
        assertThat(issue.getTitle()).isEqualTo("FOOD명 소비가 3개월째 늘고 있어요");
        // 설명은 화면 한 줄에 들어가야 해서 비율 하나만 남겼다(S15P21D105-604). 631부터 비교 대상이 문구에 드러난다.
        assertThat(issue.getDescription()).isEqualTo("지난달 소비가 직전 3개월 평균보다 33.3% 높아요.");
        // JSON 키는 627(v5)과 같다. monthlyAverage = 최근 1개월 평균 = 지난달 금액이며 lastMonthAmount를 같은 값으로 함께 싣는다.
        assertThat(issue.getCurrentValue().get("monthlyAverage").asLong()).isEqualTo(160_000L);
        assertThat(issue.getCurrentValue().get("lastMonthAmount").asLong()).isEqualTo(160_000L);
        assertThat(issue.getCurrentValue().get("increaseAmount").asLong()).isEqualTo(40_000L);
        assertThat(issue.getCurrentValue().get("categoryCode").asText()).isEqualTo("FOOD");
        assertThat(issue.getCurrentValue().get("consecutiveMonths").asInt()).isEqualTo(3);
        assertThat(issue.getCurrentValue().at("/recentPeriod/months").asInt()).isEqualTo(1);
        assertThat(issue.getCurrentValue().at("/recentPeriod/startMonth").asText()).isEqualTo("2026-08");
        assertThat(issue.getBenchmarkValue().get("previousMonthlyAverage").asLong()).isEqualTo(120_000L);
        assertThat(issue.getBenchmarkValue().get("thresholdRatePercent").asDouble()).isEqualTo(20.0);
        assertThat(issue.getBenchmarkValue().get("minimumIncrease").asLong()).isEqualTo(10_000L);
        assertThat(issue.getBenchmarkValue().at("/baselinePeriod/months").asInt()).isEqualTo(3);
        assertThat(issue.getBenchmarkValue().at("/baselinePeriod/startMonth").asText()).isEqualTo("2026-05");
        assertThat(issue.getBenchmarkValue().at("/baselinePeriod/endMonth").asText()).isEqualTo("2026-07");
        assertThat(issue.getEvidence().get("targetId").asLong()).isEqualTo(1L);
        assertThat(issue.getEvidence().get("monthly")).hasSize(4);
        assertThat(rule.toIssue(diagnosis, result, cafe).getTitle()).isEqualTo("CAFE명 소비가 늘었어요");
    }

    @Test
    void lastMonthLowerThanPreviousMonthButAboveAverageIsStillAnItemWithZeroStreak() {
        List<CategorySpendingRow> rows = new ArrayList<>();
        // 5월 50,000, 6월 60,000, 7월 130,000(평균 80,000), 8월 110,000 → 7월보다 줄었지만 평균보다 +30,000(37.5%)
        rows.add(row(YearMonth.of(2026, 5), 1L, "SHOP", 50_000L));
        rows.add(row(YearMonth.of(2026, 6), 1L, "SHOP", 60_000L));
        rows.add(row(YearMonth.of(2026, 7), 1L, "SHOP", 130_000L));
        rows.add(row(YearMonth.of(2026, 8), 1L, "SHOP", 110_000L));

        SpendingIncreaseRule.Result result = evaluate(rows);

        assertThat(result.items()).hasSize(1);
        SpendingIncreaseRule.Item shop = result.items().get(0);
        assertThat(shop.increaseAmount()).isEqualTo(30_000L);
        assertThat(shop.increaseRatePercent()).isEqualTo(37.5);
        assertThat(shop.consecutiveMonths()).isZero();
        assertThat(shop.severity()).isEqualTo(IssueSeverity.MEDIUM);
        assertThat(rule.toIssue(Diagnosis.start(1L, null, LocalDateTime.of(2026, 9, 1, 9, 0)), result, shop).getTitle())
                .isEqualTo("SHOP명 소비가 늘었어요");
    }

    @Test
    void smallOrLowRateIncreasesAndMissingBaselineAreNotItems() {
        List<CategorySpendingRow> rows = new ArrayList<>();
        // 증가율 20% 초과지만 월 9,000원 증가 → 소액 보정으로 제외
        for (int month = 5; month <= 7; month++) {
            rows.add(row(YearMonth.of(2026, month), 1L, "SMALL", 30_000L));
        }
        rows.add(row(YearMonth.of(2026, 8), 1L, "SMALL", 39_000L));
        // 정확히 20.0% 증가 → 임계값은 초과여야 하므로 제외
        for (int month = 5; month <= 7; month++) {
            rows.add(row(YearMonth.of(2026, month), 2L, "EDGE", 100_000L));
        }
        rows.add(row(YearMonth.of(2026, 8), 2L, "EDGE", 120_000L));
        // 기준 기간 소비가 0인 새 카테고리는 판정 대상이 아니다(기준 평균 0원)
        rows.add(row(YearMonth.of(2026, 8), 3L, "NEW", 900_000L));
        // 지난달 소비가 없는 카테고리는 감소라 제외
        for (int month = 5; month <= 7; month++) {
            rows.add(row(YearMonth.of(2026, month), 4L, "GONE", 100_000L));
        }

        SpendingIncreaseRule.Result result = evaluate(rows);

        assertThat(result.evaluatedCategories()).isEqualTo(3);
        assertThat(result.items()).isEmpty();
        assertThat(evaluate(List.of()).evaluatedCategories()).isZero();
    }

    @Test
    void missingBaselineMonthsCountAsZeroAndDiluteTheAverage() {
        // 기준 3개월 중 7월만 90,000 → 평균 30,000(5·6월은 0원). 8월 100,000 → +70,000, 233.3%, HIGH.
        // 거래가 없는 달을 빼고 평균을 내지 않는다는 결정(diagnosis-api.md 631 절)을 고정한다.
        List<CategorySpendingRow> rows = List.of(
                row(YearMonth.of(2026, 7), 1L, "GAME", 90_000L),
                row(YearMonth.of(2026, 8), 1L, "GAME", 100_000L));

        SpendingIncreaseRule.Result result = evaluate(rows);

        assertThat(result.items()).hasSize(1);
        SpendingIncreaseRule.Item game = result.items().get(0);
        assertThat(game.previousMonthlyAverage()).isEqualTo(30_000L);
        assertThat(game.increaseAmount()).isEqualTo(70_000L);
        assertThat(game.increaseRatePercent()).isEqualTo(233.3);
        assertThat(game.severity()).isEqualTo(IssueSeverity.HIGH);
        assertThat(game.monthly()).extracting(SpendingIncreaseRule.MonthAmount::amount).containsExactly(0L, 0L, 90_000L, 100_000L);
        // 0 → 0 은 "증가"가 아니므로 연속 증가는 7→8, 6(0)→7 두 달
        assertThat(game.consecutiveMonths()).isEqualTo(2);
    }

    @Test
    void yearBoundaryPeriodsAreDerivedFromBaseDateAndReadAsOneRange() {
        // 2026-01 진단 → 최근 2025-12, 기준 2025-09~11. 읽는 구간은 2025-09-01 ~ 2025-12-31 한 번.
        given(reader.readByMonthAndCategory(anyLong(), eq(LocalDate.of(2025, 9, 1)), eq(LocalDate.of(2025, 12, 31)))).willReturn(List.of(
                row(YearMonth.of(2025, 9), 1L, "GIFT", 40_000L),
                row(YearMonth.of(2025, 10), 1L, "GIFT", 40_000L),
                row(YearMonth.of(2025, 11), 1L, "GIFT", 40_000L),
                row(YearMonth.of(2025, 12), 1L, "GIFT", 200_000L)));

        SpendingIncreaseRule.Result result = rule.evaluate(9L, LocalDate.of(2026, 1, 15));

        verify(reader).readByMonthAndCategory(9L, LocalDate.of(2025, 9, 1), LocalDate.of(2025, 12, 31));
        assertThat(result.recentPeriod()).isEqualTo(new SpendingIncreaseRule.Period("2025-12", "2025-12", 1));
        assertThat(result.baselinePeriod()).isEqualTo(new SpendingIncreaseRule.Period("2025-09", "2025-11", 3));
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).increaseAmount()).isEqualTo(160_000L);
        assertThat(result.items().get(0).increaseRatePercent()).isEqualTo(400.0);
    }
}
