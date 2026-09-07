package com.ssafy.dodam.domain.product.service;

import com.ssafy.dodam.domain.product.dto.response.CardBenefitResponse;
import com.ssafy.dodam.domain.product.dto.response.CardRecommendationResponse;
import com.ssafy.dodam.domain.product.dto.response.ProductCompanyResponse;
import com.ssafy.dodam.domain.product.dto.response.RecommendedBenefitResponse;
import com.ssafy.dodam.domain.product.dto.response.RecommendedCardResponse;
import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.product.model.MonthlySpendingProfile;
import com.ssafy.dodam.domain.product.model.SpendingSegment;
import com.ssafy.dodam.domain.product.repository.CardRecommendationSpendingReader;
import com.ssafy.dodam.domain.product.repository.FinancialProductRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CardRecommendationService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final Set<String> GENERAL_CATEGORIES = Set.of("ALL", "DOMESTIC");
    private static final Comparator<RecommendedCardResponse> RECOMMENDATION_ORDER = Comparator
            .comparingLong(RecommendedCardResponse::estimatedMonthlyBenefit).reversed()
            .thenComparing(RecommendedCardResponse::benefitCoverageRate, Comparator.reverseOrder())
            .thenComparing(response -> response.matchedBenefits().size(), Comparator.reverseOrder())
            .thenComparingLong(response -> normalizedBaseline(response.baselinePerformance()))
            .thenComparing(RecommendedCardResponse::productId);

    private final FinancialProductRepository financialProductRepository;
    private final CardRecommendationSpendingReader spendingReader;
    private final CardBenefitCategoryMapper categoryMapper;

    public CardRecommendationService(
            FinancialProductRepository financialProductRepository,
            CardRecommendationSpendingReader spendingReader,
            CardBenefitCategoryMapper categoryMapper
    ) {
        this.financialProductRepository = financialProductRepository;
        this.spendingReader = spendingReader;
        this.categoryMapper = categoryMapper;
    }

    public CardRecommendationResponse recommend(Long userId, int topN, String requestedYearMonth) {
        YearMonth yearMonth = parseYearMonth(requestedYearMonth);
        MonthlySpendingProfile profile = spendingReader.read(userId, yearMonth);
        if (profile.totalSpending() <= 0) {
            return new CardRecommendationResponse(yearMonth.toString(), 0, false, List.of());
        }

        List<RecommendedCardResponse> ranked = financialProductRepository
                .findAllByProductTypeOrderByIdAsc(ProductType.CARD)
                .stream()
                .map(product -> calculate(product, profile))
                .sorted(RECOMMENDATION_ORDER)
                .limit(topN)
                .toList();

        List<RecommendedCardResponse> withRanks = new ArrayList<>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            withRanks.add(ranked.get(index).withRank(index + 1));
        }
        return new CardRecommendationResponse(
                yearMonth.toString(),
                profile.totalSpending(),
                true,
                withRanks
        );
    }

    private RecommendedCardResponse calculate(
            FinancialProduct product,
            MonthlySpendingProfile profile
    ) {
        List<CardBenefitResponse> benefits = CardBenefitResponse.from(product.getBenefits());
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

        List<RecommendedBenefitResponse> matches = new ArrayList<>();
        long potentialBenefit = 0;
        for (Map.Entry<Integer, Long> entry : calculableSpending.entrySet()) {
            CardBenefitResponse benefit = benefits.get(entry.getKey());
            long expectedBenefit = calculateBenefit(entry.getValue(), benefit);
            potentialBenefit += expectedBenefit;
            matches.add(toResponse(benefit, entry.getValue(), expectedBenefit, true));
        }
        for (Map.Entry<Integer, Long> entry : qualitativeSpending.entrySet()) {
            CardBenefitResponse benefit = benefits.get(entry.getKey());
            matches.add(toResponse(benefit, entry.getValue(), 0, false));
        }

        if (product.getMaxBenefitLimit() != null && product.getMaxBenefitLimit() > 0) {
            potentialBenefit = Math.min(potentialBenefit, product.getMaxBenefitLimit());
        }

        long baseline = normalizedBaseline(product.getBaselinePerformance());
        boolean performanceReachable = profile.totalSpending() >= baseline;
        long shortfall = Math.max(baseline - profile.totalSpending(), 0);
        long estimatedBenefit = performanceReachable ? potentialBenefit : 0;
        BigDecimal coverageRate = coverageRate(matchedSpending, profile.totalSpending());
        matches.sort(Comparator
                .comparingLong(RecommendedBenefitResponse::expectedBenefit).reversed()
                .thenComparing(RecommendedBenefitResponse::category));

        return new RecommendedCardResponse(
                0,
                product.getId(),
                ProductCompanyResponse.from(product.getCompany()),
                product.getProductName(),
                product.getBaselinePerformance(),
                performanceReachable,
                shortfall,
                estimatedBenefit,
                potentialBenefit,
                product.getMaxBenefitLimit(),
                coverageRate,
                matches,
                recommendationReason(matches, performanceReachable)
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

    private RecommendedBenefitResponse toResponse(
            CardBenefitResponse benefit,
            long spendingAmount,
            long expectedBenefit,
            boolean calculable
    ) {
        return new RecommendedBenefitResponse(
                benefit.category(),
                spendingAmount,
                benefit.discountRate(),
                expectedBenefit,
                calculable,
                benefit.description()
        );
    }

    private BigDecimal coverageRate(long matchedSpending, long totalSpending) {
        if (totalSpending <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(matchedSpending)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalSpending), 2, RoundingMode.HALF_UP);
    }

    private String recommendationReason(
            List<RecommendedBenefitResponse> matches,
            boolean performanceReachable
    ) {
        if (matches.isEmpty()) {
            return "현재 소비 패턴과 직접 일치하는 혜택이 없습니다.";
        }
        String category = matches.get(0).category();
        if (!performanceReachable) {
            return category + " 혜택이 있지만 현재 소비 기준으로 전월 실적이 부족합니다.";
        }
        return category + " 소비에서 높은 혜택을 기대할 수 있습니다.";
    }

    private YearMonth parseYearMonth(String requestedYearMonth) {
        if (requestedYearMonth == null) {
            return YearMonth.now(SERVICE_ZONE);
        }
        try {
            return YearMonth.parse(requestedYearMonth);
        } catch (DateTimeException exception) {
            throw new InvalidRequestException("yearMonth는 YYYY-MM 형식의 유효한 연월이어야 합니다.");
        }
    }

    private static long normalizedBaseline(Long baselinePerformance) {
        return baselinePerformance == null || baselinePerformance <= 0 ? 0 : baselinePerformance;
    }
}
