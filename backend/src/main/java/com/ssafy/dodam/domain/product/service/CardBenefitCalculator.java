package com.ssafy.dodam.domain.product.service;

import com.ssafy.dodam.domain.product.dto.response.CardBenefitResponse;
import com.ssafy.dodam.domain.product.model.MonthlySpendingProfile;
import com.ssafy.dodam.domain.product.model.SpendingSegment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 보유 카드 분석과 상품 추천이 공유하는 혜택 적용·한도·실적 계산 정책. */
@Component
public class CardBenefitCalculator {

    private static final Set<String> GENERAL_CATEGORIES = Set.of("ALL", "DOMESTIC");

    private final CardBenefitCategoryMapper categoryMapper;

    public CardBenefitCalculator(CardBenefitCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public Calculation calculate(
            List<CardBenefitResponse> benefits,
            MonthlySpendingProfile profile,
            long performanceSpending,
            Long baselinePerformance,
            Long maxBenefitLimit
    ) {
        Map<Integer, Long> calculableSpending = new LinkedHashMap<>();
        Map<Integer, Long> qualitativeSpending = new LinkedHashMap<>();
        long matchedSpending = 0;

        for (SpendingSegment segment : profile.segments()) {
            Set<String> applicable = categoryMapper.applicableBenefitCategories(segment);
            int bestCalculableIndex = findBestCalculableBenefit(benefits, applicable);
            boolean matched = bestCalculableIndex >= 0;
            if (bestCalculableIndex >= 0) {
                calculableSpending.merge(bestCalculableIndex, segment.amount(), Long::sum);
            }

            for (int index = 0; index < benefits.size(); index++) {
                CardBenefitResponse benefit = benefits.get(index);
                if (isApplicable(benefit, applicable) && !isCalculable(benefit)) {
                    qualitativeSpending.merge(index, segment.amount(), Long::sum);
                    matched = true;
                }
            }
            if (matched) {
                matchedSpending += segment.amount();
            }
        }

        List<BenefitMatch> matches = new ArrayList<>();
        long potentialBenefit = 0;
        for (Map.Entry<Integer, Long> entry : calculableSpending.entrySet()) {
            CardBenefitResponse benefit = benefits.get(entry.getKey());
            long expectedBenefit = calculateBenefit(entry.getValue(), benefit);
            potentialBenefit += expectedBenefit;
            matches.add(new BenefitMatch(benefit, entry.getValue(), expectedBenefit, true));
        }
        for (Map.Entry<Integer, Long> entry : qualitativeSpending.entrySet()) {
            matches.add(new BenefitMatch(benefits.get(entry.getKey()), entry.getValue(), 0, false));
        }

        if (maxBenefitLimit != null && maxBenefitLimit > 0) {
            potentialBenefit = Math.min(potentialBenefit, maxBenefitLimit);
        }

        long baseline = normalizedBaseline(baselinePerformance);
        boolean performanceMet = performanceSpending >= baseline;
        return new Calculation(
                matchedSpending,
                potentialBenefit,
                performanceMet ? potentialBenefit : 0,
                performanceMet,
                Math.max(baseline - performanceSpending, 0),
                matches
        );
    }

    private int findBestCalculableBenefit(
            List<CardBenefitResponse> benefits,
            Set<String> applicable
    ) {
        int bestIndex = -1;
        for (int index = 0; index < benefits.size(); index++) {
            CardBenefitResponse candidate = benefits.get(index);
            if (!isApplicable(candidate, applicable) || !isCalculable(candidate)) {
                continue;
            }
            if (bestIndex < 0 || isBetter(candidate, benefits.get(bestIndex))) {
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private boolean isBetter(CardBenefitResponse candidate, CardBenefitResponse current) {
        int rateComparison = candidate.discountRate().compareTo(current.discountRate());
        if (rateComparison != 0) {
            return rateComparison > 0;
        }
        boolean candidateGeneral = GENERAL_CATEGORIES.contains(candidate.category());
        boolean currentGeneral = GENERAL_CATEGORIES.contains(current.category());
        return currentGeneral && !candidateGeneral;
    }

    private boolean isApplicable(CardBenefitResponse benefit, Set<String> applicable) {
        return benefit.category() != null && applicable.contains(benefit.category());
    }

    private boolean isCalculable(CardBenefitResponse benefit) {
        return benefit.discountRate() != null && benefit.discountRate().signum() > 0;
    }

    private long calculateBenefit(long spendingAmount, CardBenefitResponse benefit) {
        long calculated = BigDecimal.valueOf(spendingAmount)
                .multiply(benefit.discountRate())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .longValue();
        if (benefit.limitAmount() != null && benefit.limitAmount() > 0) {
            return Math.min(calculated, benefit.limitAmount());
        }
        return calculated;
    }

    private static long normalizedBaseline(Long baselinePerformance) {
        return baselinePerformance == null || baselinePerformance <= 0 ? 0 : baselinePerformance;
    }

    public record Calculation(
            long matchedSpending,
            long potentialBenefit,
            long estimatedBenefit,
            boolean performanceMet,
            long performanceShortfall,
            List<BenefitMatch> matches
    ) {

        public Calculation {
            matches = List.copyOf(matches);
        }
    }

    public record BenefitMatch(
            CardBenefitResponse benefit,
            long spendingAmount,
            long expectedBenefit,
            boolean calculable
    ) {
    }
}
