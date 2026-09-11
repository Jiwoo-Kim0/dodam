package com.ssafy.dodam.domain.goal.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 예상 달성일(GET /goals/{goalId}/forecast). 모으기 목표 전용. 기획안 「현재 저축속도 기준 예상 목표 달성일」.
 *
 * @param baseDate                 예측 기준일(기본 오늘)
 * @param averageMonthlySaving     현재 저축속도(currentAmount / 경과 개월)
 * @param monthsToAchieve          남은 금액 / 저축속도(올림). 이미 달성했으면 0, 속도가 0이면 null
 * @param expectedAchievementDate  baseDate + monthsToAchieve. 속도가 0이면 null(현재 속도로는 달성 불가)
 * @param daysDifference           expectedAchievementDate − targetDate. 음수면 목표일보다 빨리 달성, 양수면 늦음. 예상일이 없으면 null
 * @param onSchedule               예상일이 목표일 이하인지. 예상일이 없으면 false
 * @param projection               baseDate 다음 달부터 `months`개월의 예상 모인 금액(목표 금액 상한)
 */
public record GoalForecastResponse(
        Long goalId,
        long targetAmount,
        long currentAmount,
        long remainingAmount,
        LocalDate baseDate,
        LocalDate targetDate,
        long averageMonthlySaving,
        Integer monthsToAchieve,
        LocalDate expectedAchievementDate,
        Long daysDifference,
        boolean onSchedule,
        List<Point> projection
) {

    public GoalForecastResponse {
        projection = List.copyOf(projection);
    }

    /**
     * @param achieved 이 달에 목표 금액에 도달하는지(그 달 포함 이후 모두 true)
     */
    public record Point(String yearMonth, long projectedAmount, boolean achieved) {
    }
}
