package com.ssafy.dodam.domain.report.service;

import com.ssafy.dodam.domain.improvement.entity.ImprovementAction;
import com.ssafy.dodam.domain.improvement.repository.ImprovementActionRepository;
import com.ssafy.dodam.domain.report.dto.MonthlyImprovementsResponse;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월간 개선행동 요약을 만든다. API 19의 행동을 리포트 화면이 쓰는 필드만 줄여 담는다.
 *
 * <p>월 배정 기준이 항목마다 다르다(API 19 월별 추이와 같은 규칙). 하나로 합치면 같은 행동이 여러 달에
 * 중복 집계된다. {@code monthlySaving}은 그 달에 <b>측정된</b> 실측값만 더하며 예상치를 섞지 않는다.
 */
@Component
@Transactional(readOnly = true)
public class MonthlyImprovementsAssembler {

    private final ImprovementActionRepository actions;

    public MonthlyImprovementsAssembler(ImprovementActionRepository actions) {
        this.actions = actions;
    }

    public MonthlyImprovementsResponse assemble(Long userId, YearMonth month) {
        long completed = 0L;
        long started = 0L;
        long canceled = 0L;
        long monthlySaving = 0L;
        List<MonthlyImprovementsResponse.Action> items = new ArrayList<>();
        for (ImprovementAction action : actions.findAllByUserId(userId)) {
            boolean touched = false;
            if (inMonth(action.getCompletedAt(), month)) {
                completed++;
                touched = true;
            }
            if (inMonth(action.getStartedAt(), month)) {
                started++;
                touched = true;
            }
            if (inMonth(action.getCanceledAt(), month)) {
                canceled++;
                touched = true;
            }
            if (inMonth(action.getMeasuredAt(), month)) {
                monthlySaving += action.getMonthlyCostSaving() == null ? 0L : action.getMonthlyCostSaving();
                touched = true;
            }
            if (touched) {
                items.add(new MonthlyImprovementsResponse.Action(action.getActionId(), action.getTitle(),
                        action.getStatus().name(), action.getCompletedAt(), action.getMonthlyCostSaving()));
            }
        }
        items.sort(Comparator.comparing(MonthlyImprovementsResponse.Action::actionId));
        return new MonthlyImprovementsResponse(month.toString(), completed, started, canceled, monthlySaving, items);
    }

    private static boolean inMonth(LocalDateTime at, YearMonth month) {
        return at != null && YearMonth.from(at).equals(month);
    }
}
