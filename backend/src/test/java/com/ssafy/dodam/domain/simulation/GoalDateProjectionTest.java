package com.ssafy.dodam.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.service.GoalAchievementProjection;
import com.ssafy.dodam.domain.simulation.service.GoalDateProjection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** 585 §10.5 날짜별 목표 투영. 검산표의 목표 사례를 그대로 옮겼다. */
class GoalDateProjectionTest {
    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_14 = LocalDate.of(2026, 1, 14);

    private static NavigableMap<LocalDate, Long> deltas(Object... pairs) {
        NavigableMap<LocalDate, Long> map = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((LocalDate) pairs[i], ((Number) pairs[i + 1]).longValue());
        }
        return map;
    }

    @Test
    void unchangedDeltasPreserveBaselineDateExactly() {
        LocalDate before = GoalAchievementProjection.expectedDate(30_000, 10_000, JAN_1);
        assertThat(GoalDateProjection.project(30_000, 10_000, JAN_1, before, deltas())).isEqualTo(before);
        assertThat(GoalDateProjection.project(30_000, 10_000, JAN_1, before,
                deltas(LocalDate.of(2026, 2, 1), 0L, LocalDate.of(2026, 3, 1), 0L))).isEqualTo(before);
        assertThat(GoalDateProjection.project(500, 0, JAN_1, null, deltas(LocalDate.of(2026, 2, 1), 0L))).isNull();
        assertThat(GoalDateProjection.project(0, 0, JAN_1, JAN_1, deltas())).isEqualTo(JAN_1);
    }

    @Test
    void smallSavingsKeepMonthCountAndLargerOnesShortenIt() {
        // 남은 30,000·속도 10,000, 2/1·3/1에 3,000씩: 17,000 → 4,000 → 3개월(기존과 동일)
        NavigableMap<LocalDate, Long> saving = deltas(LocalDate.of(2026, 2, 1), 3_000L, LocalDate.of(2026, 3, 1), 3_000L);
        assertThat(GoalDateProjection.project(30_000, 10_000, JAN_1, JAN_1.plusMonths(3), saving)).isEqualTo(JAN_1.plusMonths(3));
        // 남은 25,000이면 12,000 → -1,000 → 2개월(기존 3개월에서 단축)
        assertThat(GoalDateProjection.project(25_000, 10_000, JAN_1, JAN_1.plusMonths(3), saving)).isEqualTo(JAN_1.plusMonths(2));
        // 음수(상환 증가)는 0 하한: 속도 10,000, -15,000이면 그 달 0
        assertThat(GoalDateProjection.project(20_000, 10_000, JAN_1, JAN_1.plusMonths(2),
                deltas(LocalDate.of(2026, 2, 1), -15_000L))).isEqualTo(JAN_1.plusMonths(3));
    }

    @Test
    void savingsAreNeverUsedBeforeTheyOccur() {
        // 기준 1/14, remaining 5,000·속도 0, 2/28 절감 5,000 → 2/14 미달성, 3/14 달성
        assertThat(GoalDateProjection.project(5_000, 0, JAN_14, null, deltas(LocalDate.of(2026, 2, 28), 5_000L)))
                .isEqualTo(LocalDate.of(2026, 3, 14));
        // 당월 미래 절감(1/28)은 첫 투영일 2/14에 반영, 1/14로 소급 금지
        assertThat(GoalDateProjection.project(5_000, 0, JAN_14, null, deltas(LocalDate.of(2026, 1, 28), 5_000L)))
                .isEqualTo(LocalDate.of(2026, 2, 14));
        // 속도 0이고 절감 합이 부족하면 null
        assertThat(GoalDateProjection.project(5_000, 0, JAN_14, null, deltas(LocalDate.of(2026, 1, 28), 4_999L))).isNull();
        // 말일 복원: 1/31 기준 → 2/28 → 3/31
        LocalDate jan31 = LocalDate.of(2026, 1, 31);
        assertThat(GoalDateProjection.project(20_000, 10_000, jan31, jan31.plusMonths(2),
                deltas(LocalDate.of(2026, 2, 28), 1L, LocalDate.of(2026, 3, 31), 1L))).isEqualTo(LocalDate.of(2026, 3, 31));
        // 마지막 사건 뒤 기존 속도로 이어 계산 (base.plusMonths(k + n))
        // 55,000: 2/28에 15,000 → 40,000 남음 → 4개월 더 = 5개월(기존 6개월)
        assertThat(GoalDateProjection.project(55_000, 10_000, jan31, jan31.plusMonths(6),
                deltas(LocalDate.of(2026, 2, 28), 5_000L))).isEqualTo(jan31.plusMonths(5));
    }

    @Test
    void allocationIsProportionalToRemainingAndFixedAtBase() {
        List<SimulationBaseline.SavingGoal> goals = List.of(
                new SimulationBaseline.SavingGoal(2L, "B", 0, 0, 500L, 0L, null, null, List.of()),
                new SimulationBaseline.SavingGoal(1L, "A", 0, 0, 1_000L, 0L, null, null, List.of()),
                new SimulationBaseline.SavingGoal(3L, "C", 0, 0, 0L, 0L, null, null, List.of()));
        Map<Long, NavigableMap<LocalDate, Long>> allocated = GoalDateProjection.allocate(goals,
                deltas(LocalDate.of(2026, 2, 1), 100L, LocalDate.of(2026, 3, 1), -7L));
        // A 1,000 : B 500 → 66 : 34 (마지막 목표 B가 나머지), 달성 목표 C는 배분 없음
        assertThat(allocated.get(1L)).containsExactly(Map.entry(LocalDate.of(2026, 2, 1), 66L), Map.entry(LocalDate.of(2026, 3, 1), -5L));
        assertThat(allocated.get(2L)).containsExactly(Map.entry(LocalDate.of(2026, 2, 1), 34L), Map.entry(LocalDate.of(2026, 3, 1), -2L));
        assertThat(allocated.get(3L)).isEmpty();
        assertThat(GoalDateProjection.allocate(List.of(goals.get(2)), deltas(LocalDate.of(2026, 2, 1), 100L)).get(3L)).isEmpty();
    }
}
