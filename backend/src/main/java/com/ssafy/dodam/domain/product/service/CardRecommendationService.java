package com.ssafy.dodam.domain.product.service;

import com.ssafy.dodam.domain.product.dto.response.CardBenefitResponse;
import com.ssafy.dodam.domain.product.dto.response.CardRecommendationResponse;
import com.ssafy.dodam.domain.product.dto.response.ProductCompanyResponse;
import com.ssafy.dodam.domain.product.dto.response.RecommendedBenefitResponse;
import com.ssafy.dodam.domain.product.dto.response.RecommendedCardResponse;
import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.product.model.MonthlySpendingProfile;
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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CardRecommendationService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final Comparator<RecommendedCardResponse> RECOMMENDATION_ORDER = Comparator
            .comparingLong(RecommendedCardResponse::estimatedMonthlyBenefit).reversed()
            .thenComparing(RecommendedCardResponse::benefitCoverageRate, Comparator.reverseOrder())
            .thenComparing(response -> response.matchedBenefits().size(), Comparator.reverseOrder())
            .thenComparingLong(response -> normalizedBaseline(response.baselinePerformance()))
            .thenComparing(RecommendedCardResponse::productId);

    private final FinancialProductRepository financialProductRepository;
    private final CardRecommendationSpendingReader spendingReader;
    private final CardBenefitCalculator benefitCalculator;

    public CardRecommendationService(
            FinancialProductRepository financialProductRepository,
            CardRecommendationSpendingReader spendingReader,
            CardBenefitCalculator benefitCalculator
    ) {
        this.financialProductRepository = financialProductRepository;
        this.spendingReader = spendingReader;
        this.benefitCalculator = benefitCalculator;
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
        CardBenefitCalculator.Calculation calculation = benefitCalculator.calculate(
                benefits,
                profile,
                profile.totalSpending(),
                product.getBaselinePerformance(),
                product.getMaxBenefitLimit()
        );

        List<RecommendedBenefitResponse> matches = new ArrayList<>();
        calculation.matches().forEach(match -> matches.add(toResponse(match)));
        BigDecimal coverageRate = coverageRate(
                calculation.matchedSpending(),
                profile.totalSpending()
        );
        matches.sort(Comparator
                .comparingLong(RecommendedBenefitResponse::expectedBenefit).reversed()
                .thenComparing(RecommendedBenefitResponse::category));

        return new RecommendedCardResponse(
                0,
                product.getId(),
                ProductCompanyResponse.from(product.getCompany()),
                product.getProductName(),
                product.getBaselinePerformance(),
                calculation.performanceMet(),
                calculation.performanceShortfall(),
                calculation.estimatedBenefit(),
                calculation.potentialBenefit(),
                product.getMaxBenefitLimit(),
                coverageRate,
                matches,
                recommendationReason(matches, calculation.performanceMet())
        );
    }

    private RecommendedBenefitResponse toResponse(
            CardBenefitCalculator.BenefitMatch match
    ) {
        return new RecommendedBenefitResponse(
                match.benefit().category(),
                match.spendingAmount(),
                match.benefit().discountRate(),
                match.expectedBenefit(),
                match.calculable(),
                match.benefit().description()
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
