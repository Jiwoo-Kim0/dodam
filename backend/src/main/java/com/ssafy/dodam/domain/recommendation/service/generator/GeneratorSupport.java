package com.ssafy.dodam.domain.recommendation.service.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.goal.service.GoalForecastFormula;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 생성기 공통: 진단 스냅샷·문제 JSON에서 값을 꺼내고 목표 예상 달성일을 계산한다.
 *
 * <p>예상 달성일 식은 {@code GoalCalculationService.forecast()}와 같은 {@link GoalForecastFormula}다(남은 금액 ≤ 0 → 기준일,
 * 속도 ≤ 0 → null, 그 외 기준일 축의 일 단위 날짜, 658). Goal 서비스를 호출하지 않는 이유는 그 경로가 현재 잔액으로 재계산하며
 * 목표 잔액·이력을 갱신하기 때문이다. 추천은 진단 시점 스냅샷만으로 계산해야 같은 진단에서 같은 결과가 나온다.
 *
 * <p>기준일은 진단 스냅샷의 {@code asOf}(= 진단 접수 시각, 위험 판정에 쓴 날)다. 없으면 완료 시각의 날짜다.
 */
final class GeneratorSupport {
    private GeneratorSupport() {
    }

    static Long longOf(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? null : value.asLong();
    }

    static String textOf(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    static LocalDate dateOf(JsonNode node, String field) {
        String text = textOf(node, field);
        try {
            return text == null ? null : LocalDate.parse(text);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    static LocalDate baseDate(RecommendationInput input) {
        String asOf = textOf(input.inputSnapshot(), "asOf");
        if (asOf != null) {
            try {
                return LocalDateTime.parse(asOf).toLocalDate();
            } catch (java.time.format.DateTimeParseException ignored) {
                // 형식이 다르면 완료 시각으로 떨어진다
            }
        }
        return input.completedAt().toLocalDate();
    }

    static LocalDate expectedDate(long remaining, long monthlyPace, LocalDate base) {
        return GoalForecastFormula.expectedDate(remaining, monthlyPace, base);
    }

    /** {@code current − improved}(일). 앞당겨지면 양수. 한쪽이 null이면 null. */
    static Integer advancedDays(LocalDate current, LocalDate improved) {
        if (current == null || improved == null) {
            return null;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(improved, current));
    }

    /** 개선 예상일이 목표일 안에 들어온 경우에만 사용자에게 약속할 수 있는 단축 일수를 반환한다. */
    static Integer achievableAdvancedDays(LocalDate current, LocalDate improved, LocalDate target) {
        if (target != null && (improved == null || improved.isAfter(target))) {
            return null;
        }
        return advancedDays(current, improved);
    }

    static String won(long amount) {
        return String.format("%,d원", amount);
    }

    /**
     * 진단 스냅샷 {@code goals[]}에서 날짜 효과를 계산할 모으기 목표 하나(696).
     *
     * <p>546이 저장한 {@code forecast}(남은 금액·현재 저축속도)가 있는 SAVING 목표만 대상이다. 생성 요청에 goalId가 있으면 그 목표만
     * 보고, 없으면 FE 발견 상세가 고르는 규칙과 같이 <b>가장 많이 밀린 목표</b>({@code daysDifference} 최대, 없으면 뒤로, 같으면 goalId
     * 작은 것)를 고른다. 남은 금액이 0이거나 forecast가 없는 목표(v3 이전 스냅샷)는 건너뛴다. 조건에 맞는 목표가 없으면 empty.
     *
     * @param goalId       목표 id
     * @param name         목표 이름(없으면 null)
     * @param remaining    남은 금액(> 0)
     * @param monthlyPace  현재 저축속도(0이면 현재 속도로는 달성 불가)
     * @param targetDate   목표일. 이전 스냅샷에 없으면 null
     */
    record SnapshotSavingGoal(long goalId, String name, long remaining, long monthlyPace, LocalDate targetDate) {
    }

    static java.util.Optional<SnapshotSavingGoal> savingGoalForForecast(RecommendationInput input) {
        JsonNode snapshot = input.inputSnapshot();
        if (snapshot == null || !snapshot.path("goals").isArray()) {
            return java.util.Optional.empty();
        }
        SnapshotSavingGoal chosen = null;
        Long chosenDelay = null;
        for (JsonNode goal : snapshot.get("goals")) {
            Long goalId = longOf(goal, "goalId");
            if (goalId == null || !"SAVING".equals(textOf(goal, "type"))) {
                continue;
            }
            if (input.goalId() != null && !input.goalId().equals(goalId)) {
                continue;
            }
            JsonNode forecast = goal.get("forecast");
            Long remaining = longOf(forecast, "remainingAmount");
            Long pace = longOf(forecast, "averageMonthlySaving");
            if (remaining == null || pace == null || remaining <= 0) {
                continue;
            }
            Long delay = longOf(forecast, "daysDifference");
            SnapshotSavingGoal candidate = new SnapshotSavingGoal(goalId, textOf(goal.get("goalSnapshot"), "name"),
                    remaining, Math.max(0L, pace), dateOf(forecast, "targetDate"));
            if (chosen == null || moreDelayed(delay, chosenDelay) || (java.util.Objects.equals(delay, chosenDelay)
                    && goalId < chosen.goalId())) {
                chosen = candidate;
                chosenDelay = delay;
            }
        }
        return java.util.Optional.ofNullable(chosen);
    }

    /** 지연 일수가 있는 목표가 없는 목표보다 앞, 둘 다 있으면 큰 쪽이 앞. */
    private static boolean moreDelayed(Long delay, Long current) {
        if (delay == null) {
            return false;
        }
        return current == null || delay > current;
    }
}
