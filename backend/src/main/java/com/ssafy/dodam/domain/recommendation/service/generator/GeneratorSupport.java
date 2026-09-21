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

    static String won(long amount) {
        return String.format("%,d원", amount);
    }
}
