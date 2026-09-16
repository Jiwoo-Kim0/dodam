package com.ssafy.dodam.domain.report.service;

import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalHistory;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.repository.GoalHistoryRepository;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.report.dto.MonthlyGoalImpactResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월간 목표변화를 만든다. 값은 {@code goal_history}에서만 읽는다 — 목표 잔액·예상 달성일은 기록된 시점 값이라야
 * 과거 달을 재현할 수 있다. 오늘의 잔액으로 지난달 리포트를 만들면 그 달의 변화가 아니라 오늘 상태가 나온다.
 *
 * <p>모으기 목표만 담는다. 줄이기 목표에는 "모인 금액"과 "예상 달성일" 개념이 없다.
 * 삭제된 목표는 제외하되 종료(달성·취소)된 목표는 남긴다 — 그 달에 달성한 목표가 리포트에서 사라지면 안 된다.
 */
@Component
@Transactional(readOnly = true)
public class MonthlyGoalImpactAssembler {

    static final String NO_HISTORY = "NO_HISTORY";
    static final String NO_HISTORY_BEFORE_PERIOD = "NO_HISTORY_BEFORE_PERIOD";
    private static final int RATIO_SCALE = 4;

    private final GoalRepository goals;
    private final SavingGoalRepository savingGoals;
    private final GoalHistoryRepository histories;

    public MonthlyGoalImpactAssembler(GoalRepository goals, SavingGoalRepository savingGoals,
            GoalHistoryRepository histories) {
        this.goals = goals;
        this.savingGoals = savingGoals;
        this.histories = histories;
    }

    public MonthlyGoalImpactResponse assemble(Long userId, YearMonth month) {
        List<MonthlyGoalImpactResponse.Goal> results = new ArrayList<>();
        for (Goal goal : goals.findAllByUserIdAndDeletedAtIsNull(userId)) {
            if (goal.getType() != GoalType.SAVING) {
                continue;
            }
            results.add(assemble(goal, month));
        }
        results.sort(Comparator.comparing(MonthlyGoalImpactResponse.Goal::goalId));
        return new MonthlyGoalImpactResponse(month.toString(), results);
    }

    private MonthlyGoalImpactResponse.Goal assemble(Goal goal, YearMonth month) {
        Optional<GoalHistory> start = histories
                .findTopByGoalIdAndRecordedDateLessThanEqualOrderByRecordedDateDescIdDesc(
                        goal.getId(), month.atDay(1).minusDays(1));
        Optional<GoalHistory> end = histories
                .findTopByGoalIdAndRecordedDateLessThanEqualOrderByRecordedDateDescIdDesc(
                        goal.getId(), month.atEndOfMonth());
        if (end.isEmpty()) {
            return unavailable(goal, NO_HISTORY);
        }
        if (start.isEmpty()) {
            // 그 달 안에 목표가 만들어졌다. 시작값이 없으므로 "얼마나 늘었는지"는 말할 수 없다.
            // 끝값과 예상일은 관측된 사실이라 그대로 내려 준다.
            return new MonthlyGoalImpactResponse.Goal(goal.getId(), goal.getName(), null,
                    end.get().getCurrentAmount(), null, progressRatio(goal, end.get()),
                    null, end.get().getForecastDate(), null, NO_HISTORY_BEFORE_PERIOD);
        }
        Long advancedDays = advancedDays(start.get().getForecastDate(), end.get().getForecastDate());
        return new MonthlyGoalImpactResponse.Goal(goal.getId(), goal.getName(),
                start.get().getCurrentAmount(), end.get().getCurrentAmount(),
                end.get().getCurrentAmount() - start.get().getCurrentAmount(),
                progressRatio(goal, end.get()), start.get().getForecastDate(), end.get().getForecastDate(),
                advancedDays, null);
    }

    private MonthlyGoalImpactResponse.Goal unavailable(Goal goal, String reason) {
        return new MonthlyGoalImpactResponse.Goal(goal.getId(), goal.getName(), null, null, null, null,
                null, null, null, reason);
    }

    /** 목표 금액 대비 그 달 마지막 모인 금액. 목표 하위행이 없거나 목표 금액이 0이면 null이다. */
    private Double progressRatio(Goal goal, GoalHistory end) {
        return savingGoals.findById(goal.getId())
                .map(savingGoal -> savingGoal.getTargetAmount())
                .filter(target -> target > 0)
                .map(target -> BigDecimal.valueOf(end.getCurrentAmount())
                        .divide(BigDecimal.valueOf(target), RATIO_SCALE, RoundingMode.HALF_UP).doubleValue())
                .orElse(null);
    }

    /** 양수 = 그 달 동안 달성일이 앞당겨졌다. 두 예상일이 모두 있어야 값이 생긴다. */
    private static Long advancedDays(LocalDate atStart, LocalDate atEnd) {
        if (atStart == null || atEnd == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(atEnd, atStart);
    }
}
