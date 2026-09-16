package com.ssafy.dodam.domain.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader.CategorySpendingRow;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 627 카테고리 소비 증가 규칙의 순수 계산. 기준월 2026-09 → 최근 2026-06~08, 이전 2026-03~05. */
class SpendingIncreaseRuleTest {
    private static final YearMonth BASELINE_START = YearMonth.of(2026, 3);
    private static final YearMonth RECENT_START = YearMonth.of(2026, 6);
    private static final YearMonth RECENT_END = YearMonth.of(2026, 8);
    private final ObjectMapper json = new ObjectMapper();
    private final SpendingIncreaseRule rule = new SpendingIncreaseRule(Mockito.mock(JdbcSpendingReader.class), json);

    private static CategorySpendingRow row(YearMonth month, Long categoryId, String code, long total) {
        return new CategorySpendingRow(month, categoryId, code, code == null ? null : code + "명", total, 1);
    }

    private static SpendingIncreaseRule.Result evaluate(List<CategorySpendingRow> rows) {
        return SpendingIncreaseRule.evaluate(rows, BASELINE_START, RECENT_START, RECENT_END);
    }

    @Test
    void increaseAboveThresholdBecomesItemWithConsecutiveMonthsAndSeverity() {
        List<CategorySpendingRow> rows = new ArrayList<>();
        // FOOD: 이전 3개월 100,000씩(평균 100,000), 최근 120,000 / 140,000 / 160,000(평균 140,000) → +40,000, 40.0%, 3개월 연속 증가
        for (int month = 3; month <= 5; month++) {
            rows.add(row(YearMonth.of(2026, month), 1L, "FOOD", 100_000L));
        }
        rows.add(row(YearMonth.of(2026, 6), 1L, "FOOD", 120_000L));
        rows.add(row(YearMonth.of(2026, 7), 1L, "FOOD", 140_000L));
        rows.add(row(YearMonth.of(2026, 8), 1L, "FOOD", 160_000L));
        // CAFE: 이전 평균 50,000 → 최근 평균 100,000(+100%)이지만 최근 달이 전달보다 줄어 연속 증가 0, 심각도 HIGH
        for (int month = 3; month <= 5; month++) {
            rows.add(row(YearMonth.of(2026, month), 2L, "CAFE", 50_000L));
        }
        rows.add(row(YearMonth.of(2026, 6), 2L, "CAFE", 90_000L));
        rows.add(row(YearMonth.of(2026, 7), 2L, "CAFE", 120_000L));
        rows.add(row(YearMonth.of(2026, 8), 2L, "CAFE", 90_000L));
        // 기간 밖(진행 중인 달)과 미분류는 무시한다
        rows.add(row(YearMonth.of(2026, 9), 1L, "FOOD", 5_000_000L));
        rows.add(row(YearMonth.of(2026, 8), null, null, 9_000_000L));

        SpendingIncreaseRule.Result result = evaluate(rows);

        assertThat(result.recentPeriod()).isEqualTo(new SpendingIncreaseRule.Period("2026-06", "2026-08", 3));
        assertThat(result.baselinePeriod()).isEqualTo(new SpendingIncreaseRule.Period("2026-03", "2026-05", 3));
        assertThat(result.evaluatedCategories()).isEqualTo(2);
        assertThat(result.items()).extracting(SpendingIncreaseRule.Item::categoryCode).containsExactly("CAFE", "FOOD");
        SpendingIncreaseRule.Item food = result.items().get(1);
        assertThat(food.monthlyAverage()).isEqualTo(140_000L);
        assertThat(food.previousMonthlyAverage()).isEqualTo(100_000L);
        assertThat(food.increaseAmount()).isEqualTo(40_000L);
        assertThat(food.increaseRatePercent()).isEqualTo(40.0);
        assertThat(food.consecutiveMonths()).isEqualTo(3);
        assertThat(food.severity()).isEqualTo(IssueSeverity.MEDIUM);
        assertThat(food.monthly()).hasSize(6).extracting(SpendingIncreaseRule.MonthAmount::amount)
                .containsExactly(100_000L, 100_000L, 100_000L, 120_000L, 140_000L, 160_000L);
        SpendingIncreaseRule.Item cafe = result.items().get(0);
        assertThat(cafe.increaseRatePercent()).isEqualTo(100.0);
        assertThat(cafe.consecutiveMonths()).isZero();
        assertThat(cafe.severity()).isEqualTo(IssueSeverity.HIGH);

        Diagnosis diagnosis = Diagnosis.start(77L, null, LocalDateTime.of(2026, 9, 13, 12, 0));
        DiagnosisIssue issue = rule.toIssue(diagnosis, result, food);
        assertThat(issue.getDomain()).isEqualTo("SPENDING");
        assertThat(issue.getType()).isEqualTo("CATEGORY_SPENDING_INCREASE");
        assertThat(issue.getTitle()).isEqualTo("FOOD명 소비가 3개월째 늘고 있어요");
        // 설명은 화면 한 줄에 들어가야 해서 비율 하나만 남겼다(S15P21D105-604).
        assertThat(issue.getDescription()).isEqualTo("최근 3개월 평균보다 40.0% 높아요.");
        // 줄인 것은 표시 문구뿐이고 수치는 JSON에 그대로 남는다 — 화면이 필요하면 여기서 읽는다
        assertThat(issue.getCurrentValue().get("monthlyAverage").asLong()).isEqualTo(140_000L);
        assertThat(issue.getCurrentValue().get("increaseAmount").asLong()).isEqualTo(40_000L);
        assertThat(issue.getCurrentValue().get("categoryCode").asText()).isEqualTo("FOOD");
        assertThat(issue.getCurrentValue().get("consecutiveMonths").asInt()).isEqualTo(3);
        assertThat(issue.getBenchmarkValue().get("previousMonthlyAverage").asLong()).isEqualTo(100_000L);
        assertThat(issue.getBenchmarkValue().get("thresholdRatePercent").asDouble()).isEqualTo(20.0);
        assertThat(issue.getEvidence().get("targetId").asLong()).isEqualTo(1L);
        assertThat(issue.getEvidence().get("monthly")).hasSize(6);
        assertThat(rule.toIssue(diagnosis, result, cafe).getTitle()).isEqualTo("CAFE명 소비가 늘었어요");
    }

    @Test
    void smallOrLowRateIncreasesAndMissingBaselineAreNotItems() {
        List<CategorySpendingRow> rows = new ArrayList<>();
        // 증가율 20% 초과지만 월 9,000원 증가 → 소액 보정으로 제외
        for (int month = 3; month <= 5; month++) {
            rows.add(row(YearMonth.of(2026, month), 1L, "SMALL", 30_000L));
        }
        for (int month = 6; month <= 8; month++) {
            rows.add(row(YearMonth.of(2026, month), 1L, "SMALL", 39_000L));
        }
        // 정확히 20.0% 증가 → 임계값은 초과여야 하므로 제외
        for (int month = 3; month <= 5; month++) {
            rows.add(row(YearMonth.of(2026, month), 2L, "EDGE", 100_000L));
        }
        for (int month = 6; month <= 8; month++) {
            rows.add(row(YearMonth.of(2026, month), 2L, "EDGE", 120_000L));
        }
        // 이전 기간 소비가 없는 새 카테고리는 판정 대상이 아니다
        rows.add(row(YearMonth.of(2026, 8), 3L, "NEW", 900_000L));

        SpendingIncreaseRule.Result result = evaluate(rows);

        assertThat(result.evaluatedCategories()).isEqualTo(2);
        assertThat(result.items()).isEmpty();
        assertThat(evaluate(List.of()).evaluatedCategories()).isZero();
    }
}
