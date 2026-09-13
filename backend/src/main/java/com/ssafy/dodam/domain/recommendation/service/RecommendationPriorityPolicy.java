package com.ssafy.dodam.domain.recommendation.service;

import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 기획안 5-4 우선순위 기준을 결정적 정수 점수로 만든다. 군집 이탈 가중치 α는 군집 미연동이라 0이다.
 *
 * <table>
 *   <tr><th>기준</th><th>점수</th></tr>
 *   <tr><td>금융 위험도</td><td>원본 문제 severity: CRITICAL 40 / HIGH 30 / MEDIUM 20 / LOW 10</td></tr>
 *   <tr><td>예상 개선 효과</td><td>max(monthlyCostSaving, contributionAmount)를 10만 원 단위로 올림, 최대 30</td></tr>
 *   <tr><td>목표 기여도</td><td>advancedDays 30일당 5점, 최대 20</td></tr>
 *   <tr><td>시급성</td><td>CASHFLOW 부족일 − 기준일이 14일 이내 20, 30일 이내 10 (이미 지난 날짜도 14일 이내로 본다)</td></tr>
 *   <tr><td>실행 난이도</td><td>SPENDING·SAVING 행동은 즉시 가능 +5</td></tr>
 * </table>
 *
 * <p>{@code priority}는 한 생성 요청 안에서 점수 내림차순 순위(1부터)다. 동점은 원본 문제 issueId 오름차순. 점수 자체는 저장 컬럼이
 * 없어 생성 응답에만 노출한다. 서로 다른 진단의 priority는 비교하지 않는다.
 */
public final class RecommendationPriorityPolicy {
    static final long EFFECT_UNIT = 100_000L;
    static final int EFFECT_MAX = 30;
    static final int GOAL_DAYS_UNIT = 30;
    static final int GOAL_POINT_PER_UNIT = 5;
    static final int GOAL_MAX = 20;
    static final int URGENT_DAYS = 14;
    static final int SOON_DAYS = 30;

    private RecommendationPriorityPolicy() {
    }

    public record Scored(RecommendationCandidate candidate, int score, int priority) { }

    public static List<Scored> rank(RecommendationInput input, List<RecommendationCandidate> candidates, LocalDate baseDate) {
        Map<Long, RecommendationInput.Issue> issues = input.issues().stream()
                .collect(Collectors.toMap(RecommendationInput.Issue::issueId, Function.identity()));
        record Entry(RecommendationCandidate candidate, int score, long issueOrder) { }
        List<Entry> entries = new ArrayList<>();
        for (RecommendationCandidate candidate : candidates) {
            RecommendationInput.Issue issue = candidate.sourceIssueId() == null ? null : issues.get(candidate.sourceIssueId());
            entries.add(new Entry(candidate, score(candidate, issue, baseDate),
                    candidate.sourceIssueId() == null ? Long.MAX_VALUE : candidate.sourceIssueId()));
        }
        entries.sort(Comparator.comparingInt(Entry::score).reversed().thenComparingLong(Entry::issueOrder)
                .thenComparing(e -> e.candidate().candidateKey()));
        List<Scored> ranked = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ranked.add(new Scored(entries.get(i).candidate(), entries.get(i).score(), i + 1));
        }
        return ranked;
    }

    static int score(RecommendationCandidate candidate, RecommendationInput.Issue issue, LocalDate baseDate) {
        int score = severityPoints(issue == null ? null : issue.severity());
        Recommendation.Effect effect = candidate.effect();
        long amount = Math.max(effect.monthlyCostSaving() == null ? 0 : effect.monthlyCostSaving(),
                effect.contributionAmount() == null ? 0 : effect.contributionAmount());
        score += (int) Math.min(EFFECT_MAX, (amount + EFFECT_UNIT - 1) / EFFECT_UNIT);
        if (effect.advancedDays() != null && effect.advancedDays() > 0) {
            score += Math.min(GOAL_MAX, effect.advancedDays() / GOAL_DAYS_UNIT * GOAL_POINT_PER_UNIT);
        }
        if ("CASHFLOW".equals(candidate.domain()) && issue != null) {
            LocalDate riskDate = dateOf(issue);
            if (riskDate != null) {
                long days = ChronoUnit.DAYS.between(baseDate, riskDate);
                score += days <= URGENT_DAYS ? 20 : days <= SOON_DAYS ? 10 : 0;
            }
        }
        if ("SPENDING".equals(candidate.domain()) || "SAVING".equals(candidate.domain())) {
            score += 5;
        }
        return score;
    }

    static int severityPoints(IssueSeverity severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity) {
            case CRITICAL -> 40;
            case HIGH -> 30;
            case MEDIUM -> 20;
            case LOW -> 10;
        };
    }

    private static LocalDate dateOf(RecommendationInput.Issue issue) {
        var node = issue.currentValue() == null ? null : issue.currentValue().get("riskDate");
        try {
            return node == null || node.isNull() ? null : LocalDate.parse(node.asText());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }
}
