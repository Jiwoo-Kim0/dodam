package com.ssafy.dodam.domain.improvement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 전후 비교 기간 정책. 수락한 달과 진행 중인 달을 양쪽에서 빼는 규칙이 여기서 깨지면 "개선 효과"가 착시가 된다. */
class ImprovementMeasurementPolicyTest {

    private final ImprovementMeasurementPolicy policy = new ImprovementMeasurementPolicy();

    @Test
    void beforeIsTheThreeCompletedMonthsBeforeAcceptance() {
        var before = policy.before(LocalDateTime.of(2026, 9, 16, 12, 0));

        // 수락한 9월은 전·후가 섞여 있어 어느 쪽에도 넣지 않는다
        assertThat(before.startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(before.endDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(before.months()).isEqualTo(3);
    }

    @Test
    void afterOpensOnlyWhenAWholeMonthHasCompleted() {
        LocalDateTime accepted = LocalDateTime.of(2026, 9, 16, 12, 0);

        // 10월이 진행 중인 동안에는 비교할 완료 월이 없다
        assertThat(policy.after(accepted, LocalDate.of(2026, 10, 31))).isNull();
        assertThat(policy.afterStartDate(accepted)).isEqualTo(LocalDate.of(2026, 10, 1));

        var oneMonth = policy.after(accepted, LocalDate.of(2026, 11, 1));
        assertThat(oneMonth.startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(oneMonth.endDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(oneMonth.months()).isEqualTo(1);
    }

    @Test
    void afterGrowsToThreeMonthsAndStopsThere() {
        LocalDateTime accepted = LocalDateTime.of(2026, 9, 16, 12, 0);

        assertThat(policy.after(accepted, LocalDate.of(2026, 12, 15)).months()).isEqualTo(2);
        var full = policy.after(accepted, LocalDate.of(2027, 1, 15));
        assertThat(full.months()).isEqualTo(3);
        assertThat(full.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));

        // 한참 지난 뒤에 봐도 창은 넓어지지 않는다. 그 뒤의 변화는 이 행동의 결과라고 말할 수 없다.
        var muchLater = policy.after(accepted, LocalDate.of(2027, 6, 1));
        assertThat(muchLater.months()).isEqualTo(3);
        assertThat(muchLater.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }
}
