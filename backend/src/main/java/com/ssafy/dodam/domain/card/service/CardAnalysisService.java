package com.ssafy.dodam.domain.card.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.card.dto.response.CardBenefitUsageResponse;
import com.ssafy.dodam.domain.card.dto.response.CardBenefitsResponse;
import com.ssafy.dodam.domain.card.dto.response.CardCategorySummaryResponse;
import com.ssafy.dodam.domain.card.dto.response.CardMonthlySummaryResponse;
import com.ssafy.dodam.domain.card.dto.response.CardPaymentBurdenResponse;
import com.ssafy.dodam.domain.card.dto.response.CardPaymentSchedulesResponse;
import com.ssafy.dodam.domain.card.dto.response.LowUsageCardsResponse;
import com.ssafy.dodam.domain.card.entity.Card;
import com.ssafy.dodam.domain.card.repository.CardAnalyticsReader;
import com.ssafy.dodam.domain.card.repository.CardBenefitReader;
import com.ssafy.dodam.domain.card.repository.CardBillingStatementReader;
import com.ssafy.dodam.domain.card.repository.CardCategoryAggregateRow;
import com.ssafy.dodam.domain.card.repository.CardMonthlyAggregateRow;
import com.ssafy.dodam.domain.card.repository.CardPaymentScheduleRow;
import com.ssafy.dodam.domain.card.repository.CardRepository;
import com.ssafy.dodam.domain.product.dto.response.CardBenefitResponse;
import com.ssafy.dodam.domain.product.model.MonthlySpendingProfile;
import com.ssafy.dodam.domain.product.service.CardBenefitCalculator;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.PaymentSchedulePeriodPolicy;
import com.ssafy.dodam.global.policy.QueryPeriod;
import com.ssafy.dodam.global.policy.YearMonthPolicy;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class CardAnalysisService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final CardRepository cardRepository;
    private final CardAnalyticsReader analyticsReader;
    private final CardBillingStatementReader billingStatementReader;
    private final CardBenefitReader benefitReader;
    private final FinancialProfileRepository financialProfileRepository;
    private final SensitiveNumberHasher sensitiveNumberHasher;
    private final YearMonthPolicy yearMonthPolicy;
    private final PaymentSchedulePeriodPolicy paymentSchedulePeriodPolicy;
    private final CardBenefitCalculator benefitCalculator;

    public CardAnalysisService(
            CardRepository cardRepository,
            CardAnalyticsReader analyticsReader,
            CardBillingStatementReader billingStatementReader,
            CardBenefitReader benefitReader,
            FinancialProfileRepository financialProfileRepository,
            SensitiveNumberHasher sensitiveNumberHasher,
            YearMonthPolicy yearMonthPolicy,
            PaymentSchedulePeriodPolicy paymentSchedulePeriodPolicy,
            CardBenefitCalculator benefitCalculator
    ) {
        this.cardRepository = cardRepository;
        this.analyticsReader = analyticsReader;
        this.billingStatementReader = billingStatementReader;
        this.benefitReader = benefitReader;
        this.financialProfileRepository = financialProfileRepository;
        this.sensitiveNumberHasher = sensitiveNumberHasher;
        this.yearMonthPolicy = yearMonthPolicy;
        this.paymentSchedulePeriodPolicy = paymentSchedulePeriodPolicy;
        this.benefitCalculator = benefitCalculator;
    }

    public CardMonthlySummaryResponse getMonthlySummary(
            Long userId,
            String cardNo,
            String requestedYearMonth
    ) {
        Card card = findCard(userId, cardNo);
        YearMonth yearMonth = yearMonthPolicy.resolveHistorical(requestedYearMonth);
        CardMonthlyAggregateRow aggregate = analyticsReader.readMonthly(
                userId,
                card.getId(),
                yearMonth
        );
        return new CardMonthlySummaryResponse(
                card.getCardNoMasked(),
                yearMonth.toString(),
                aggregate.totalAmount(),
                aggregate.transactionCount()
        );
    }

    public CardCategorySummaryResponse getCategories(
            Long userId,
            String cardNo,
            String requestedYearMonth
    ) {
        Card card = findCard(userId, cardNo);
        YearMonth yearMonth = yearMonthPolicy.resolveHistorical(requestedYearMonth);
        List<CardCategoryAggregateRow> rows = analyticsReader.readCategories(
                userId,
                card.getId(),
                yearMonth
        );
        long totalAmount = rows.stream().mapToLong(CardCategoryAggregateRow::amount).sum();
        List<CardCategorySummaryResponse.Category> categories = rows.stream()
                .map(row -> new CardCategorySummaryResponse.Category(
                        row.categoryCode(),
                        row.categoryName(),
                        row.amount(),
                        row.transactionCount(),
                        percentage(row.amount(), totalAmount)
                ))
                .toList();
        return new CardCategorySummaryResponse(
                card.getCardNoMasked(),
                yearMonth.toString(),
                totalAmount,
                categories
        );
    }

    public LowUsageCardsResponse getLowUsageCards(
            Long userId,
            int lookbackMonths,
            long thresholdAmount
    ) {
        LocalDate endDate = LocalDate.now(SERVICE_ZONE);
        LocalDate startDate = endDate.minusMonths(lookbackMonths).plusDays(1);
        List<LowUsageCardsResponse.Card> cards = analyticsReader.readLowUsageCards(
                userId,
                startDate,
                endDate,
                thresholdAmount
        ).stream().map(row -> new LowUsageCardsResponse.Card(
                row.cardNoMasked(),
                row.cardName(),
                row.issuerName(),
                row.annualFee(),
                row.totalUsageAmount(),
                row.transactionCount()
        )).toList();
        return new LowUsageCardsResponse(
                lookbackMonths,
                thresholdAmount,
                startDate,
                endDate,
                cards
        );
    }

    public CardPaymentSchedulesResponse getPaymentSchedules(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        QueryPeriod period = paymentSchedulePeriodPolicy.resolve(startDate, endDate);
        List<CardPaymentScheduleRow> rows = billingStatementReader.readScheduled(userId, period);
        List<CardPaymentSchedulesResponse.Schedule> schedules = rows.stream()
                .map(row -> new CardPaymentSchedulesResponse.Schedule(
                        row.cardNoMasked(),
                        row.cardName(),
                        row.billingMonth(),
                        row.billingDate(),
                        row.scheduledAmount(),
                        row.status()
                ))
                .toList();
        long totalAmount = rows.stream().mapToLong(CardPaymentScheduleRow::scheduledAmount).sum();
        return new CardPaymentSchedulesResponse(
                period.startDate(),
                period.endDate(),
                totalAmount,
                schedules
        );
    }

    public CardPaymentBurdenResponse getPaymentBurden(Long userId, String requestedYearMonth) {
        YearMonth yearMonth = yearMonthPolicy.resolveBillingMonth(requestedYearMonth);
        long totalBillingAmount = billingStatementReader.sumBillingAmount(userId, yearMonth);
        Long monthlyIncome = financialProfileRepository.findById(userId)
                .map(profile -> profile.getMonthlyIncome())
                .orElse(null);
        BigDecimal burdenRate = monthlyIncome == null || monthlyIncome == 0
                ? null
                : percentage(totalBillingAmount, monthlyIncome);
        return new CardPaymentBurdenResponse(
                yearMonth.toString(),
                totalBillingAmount,
                monthlyIncome,
                burdenRate
        );
    }

    public CardBenefitsResponse getBenefits(Long userId, String cardNo, String category) {
        Card card = findCard(userId, cardNo);
        JsonNode benefitData = benefitReader.readBenefits(card.getFinancialProductId()).orElse(null);
        if (benefitData == null) {
            return new CardBenefitsResponse(card.getCardNoMasked(), false, List.of());
        }
        String normalizedCategory = normalize(category);
        List<CardBenefitResponse> benefits = CardBenefitResponse.from(benefitData).stream()
                .filter(benefit -> normalizedCategory == null
                        || normalizedCategory.equals(normalize(benefit.category())))
                .toList();
        return new CardBenefitsResponse(card.getCardNoMasked(), true, benefits);
    }

    public CardBenefitUsageResponse getBenefitUsage(
            Long userId,
            String cardNo,
            String requestedYearMonth
    ) {
        Card card = findCard(userId, cardNo);
        YearMonth yearMonth = yearMonthPolicy.resolveHistorical(requestedYearMonth);
        MonthlySpendingProfile profile = analyticsReader.readSpendingProfile(
                userId,
                card.getId(),
                yearMonth
        );
        MonthlySpendingProfile performanceProfile = analyticsReader.readSpendingProfile(
                userId,
                card.getId(),
                yearMonth.minusMonths(1)
        );
        JsonNode benefitData = benefitReader.readBenefits(card.getFinancialProductId()).orElse(null);
        if (benefitData == null) {
            return new CardBenefitUsageResponse(
                    card.getCardNoMasked(), yearMonth.toString(), false,
                    profile.totalSpending(), null, null, card.getBaselinePerformance(),
                    null, null, null, card.getMaxBenefitLimit(), null
            );
        }

        CardBenefitCalculator.Calculation calculation = benefitCalculator.calculate(
                CardBenefitResponse.from(benefitData),
                profile,
                performanceProfile.totalSpending(),
                card.getBaselinePerformance(),
                card.getMaxBenefitLimit()
        );
        BigDecimal limitUsageRate = card.getMaxBenefitLimit() == null
                || card.getMaxBenefitLimit() <= 0
                ? null
                : percentage(calculation.estimatedBenefit(), card.getMaxBenefitLimit());
        return new CardBenefitUsageResponse(
                card.getCardNoMasked(),
                yearMonth.toString(),
                true,
                profile.totalSpending(),
                calculation.matchedSpending(),
                percentage(calculation.matchedSpending(), profile.totalSpending()),
                card.getBaselinePerformance(),
                calculation.performanceMet(),
                calculation.performanceShortfall(),
                calculation.estimatedBenefit(),
                card.getMaxBenefitLimit(),
                limitUsageRate
        );
    }

    private Card findCard(Long userId, String cardNo) {
        return cardRepository.findByUserIdAndCardNoHash(
                userId,
                sensitiveNumberHasher.hash(cardNo)
        ).orElseThrow(() -> new ResourceNotFoundException("카드를 찾을 수 없습니다."));
    }

    private static BigDecimal percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }
}
