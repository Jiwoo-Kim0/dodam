package com.ssafy.dodam.domain.pattern.service;

import com.ssafy.dodam.domain.pattern.dto.response.CashflowPatternResponse;
import com.ssafy.dodam.domain.pattern.dto.response.ClusterProfileResponse;
import com.ssafy.dodam.domain.pattern.dto.response.PatternAnalysisResponse;
import com.ssafy.dodam.domain.pattern.dto.response.PatternFeatureSummary;
import com.ssafy.dodam.domain.pattern.dto.response.PriorityProfileResponse;
import com.ssafy.dodam.domain.pattern.dto.response.SpendingPatternResponse;
import com.ssafy.dodam.domain.pattern.entity.PatternAnalysis;
import com.ssafy.dodam.domain.pattern.entity.PatternAnalysisStatus;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader.CategoryPatternRow;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader.MonthlyPatternRow;
import com.ssafy.dodam.domain.pattern.repository.PatternAnalysisRepository;
import com.ssafy.dodam.domain.pattern.service.PatternFeatures.MonthSummary;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 3 조회 4종(S15P21D105-517).
 *
 * <ul>
 *   <li>결과 조회(`/me`)는 저장된 최근 COMPLETED 행만 읽는다(자체 DB).</li>
 *   <li>소비·자금운용 패턴(`/me/spending`, `/me/cashflow`)은 요청 시점 거래로 다시 계산한다(자체 계산). 기간은 `months`(1~12, 기본 6)
 *       개의 완료된 달이다. 기간에 거래가 없으면 409 `PATTERN_INSUFFICIENT_DATA`.</li>
 *   <li>소비 패턴의 `clusterProfile`은 최근 완료 분석의 군집 결과다(months와 무관). 없거나 조건 미충족이면 null.</li>
 *   <li>우선순위 프로필은 저장 분석 → 즉시 계산 → 기본 프로필 순으로 근거를 고른다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PatternQueryService {

    static final String UNCATEGORIZED = "UNCATEGORIZED";

    private final PatternAnalysisRepository analyses;
    private final JdbcPatternFeatureReader reader;
    private final PatternFeatureCalculator calculator;
    private final PatternResponseAssembler assembler;
    private final PriorityProfileCalculator priority;
    private final PatternProperties properties;
    private final PatternClock clock;

    public PatternQueryService(PatternAnalysisRepository analyses, JdbcPatternFeatureReader reader,
            PatternFeatureCalculator calculator, PatternResponseAssembler assembler, PriorityProfileCalculator priority,
            PatternProperties properties, PatternClock clock) {
        this.analyses = analyses;
        this.reader = reader;
        this.calculator = calculator;
        this.assembler = assembler;
        this.priority = priority;
        this.properties = properties;
        this.clock = clock;
    }

    public PatternAnalysisResponse getLatest(Long userId) {
        return assembler.toResponse(latestCompleted(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PATTERN_ANALYSIS_NOT_FOUND)));
    }

    public SpendingPatternResponse getSpendingPattern(Long userId, Integer months) {
        PatternWindow window = window(months);
        PatternFeatures features = liveFeatures(userId, window)
                .orElseThrow(() -> new BusinessException(ErrorCode.PATTERN_INSUFFICIENT_DATA));
        List<MonthSummary> withData = features.months().stream().filter(m -> m.transactionCount() > 0).toList();
        long total = withData.stream().mapToLong(MonthSummary::spending).sum();
        MonthSummary latest = withData.get(withData.size() - 1);
        Long previousAverage = withData.size() < 2 ? null
                : Math.round(withData.subList(0, withData.size() - 1).stream().mapToLong(MonthSummary::spending).average().orElse(0));
        Double changeRate = previousAverage == null || previousAverage == 0 ? null
                : Math.round((latest.spending() - previousAverage) * 1000.0 / previousAverage) / 10.0;

        List<SpendingPatternResponse.Category> categories = reader.readUserCategories(userId, window.startDate(), window.endDate())
                .stream()
                .map(row -> toCategory(row, total))
                .toList();
        Double spendingToIncome = features.monthlyIncome() == null ? null
                : Stats.round4((double) features.averages().spending() / features.monthlyIncome());

        return new SpendingPatternResponse(
                window.start().toString(), window.end().toString(), window.months(), features.monthsWithData(), total,
                features.averages().spending(), features.spendingVolatility(), features.spendingRegularity(),
                features.fixedExpenseRatio(), features.variableExpenseRatio(), features.discretionarySpendingRatio(),
                spendingToIncome, features.incomeBasis().name(), features.monthlyIncome(), latest.spending(), previousAverage,
                changeRate,
                features.months().stream().map(m -> new SpendingPatternResponse.Month(m.yearMonth().toString(), m.spending(),
                        m.fixedSpending(), m.variableSpending(), m.discretionary(), m.transactionCount())).toList(),
                categories,
                latestCompleted(userId).flatMap(assembler::clusterProfile).orElse(null));
    }

    public CashflowPatternResponse getCashflowPattern(Long userId, Integer months) {
        PatternWindow window = window(months);
        PatternFeatures features = liveFeatures(userId, window)
                .orElseThrow(() -> new BusinessException(ErrorCode.PATTERN_INSUFFICIENT_DATA));
        PatternFeatures.Averages a = features.averages();
        Double savingRate = features.monthlyIncome() == null ? null
                : Stats.round4((double) a.savingTransfer() / features.monthlyIncome());
        CashflowPatternResponse.Liquidity liquidity = features.liquidityLevel() == null ? null
                : new CashflowPatternResponse.Liquidity(features.liquidBalance(), features.runwayMonths(),
                        features.liquidityLevel().name());
        return new CashflowPatternResponse(
                window.start().toString(), window.end().toString(), window.months(), features.monthsWithData(),
                features.incomeBasis().name(), features.monthlyIncome(),
                new CashflowPatternResponse.Averages(a.income(), a.spending(), a.savingTransfer(), a.loanRepayment(),
                        a.income() - a.spending()),
                savingRate, features.savingConsistency(), features.cashflowStability(), features.debtBurden(), liquidity,
                features.months().stream().map(m -> new CashflowPatternResponse.Month(m.yearMonth().toString(), m.income(),
                        m.spending(), m.savingTransfer(), m.loanRepayment(), m.net(), m.transactionCount())).toList());
    }

    public PriorityProfileResponse getPriorityProfile(Long userId) {
        Optional<PatternAnalysis> stored = latestCompleted(userId);
        if (stored.isPresent()) {
            PatternAnalysis analysis = stored.get();
            PatternFeatureSummary summary = assembler.featureSummary(analysis).orElse(null);
            ClusterProfileResponse cluster = assembler.clusterProfile(analysis).orElse(null);
            Double spendingToIncome = summary == null || summary.monthlyIncome() == null || summary.monthlyIncome() == 0 ? null
                    : (double) summary.averages().spending() / summary.monthlyIncome();
            Double savingRate = summary == null || summary.monthlyIncome() == null || summary.monthlyIncome() == 0 ? null
                    : (double) summary.averages().savingTransfer() / summary.monthlyIncome();
            return priority.calculate("PATTERN_ANALYSIS", analysis.getId(), analysis.getAnalyzedAt(),
                    new PriorityProfileCalculator.Inputs(
                            toDouble(analysis.getDebtBurden()), analysis.getLiquidityLevel(),
                            toDouble(analysis.getDiscretionarySpendingRatio()), spendingToIncome, savingRate,
                            toDouble(analysis.getSavingConsistency()), toDouble(analysis.getFixedExpenseRatio()),
                            cluster == null ? null : cluster.confidence(), cluster == null ? null : cluster.deviationScore()));
        }
        PatternWindow window = window(null);
        Optional<PatternFeatures> live = liveFeatures(userId, window);
        if (live.isEmpty()) {
            return priority.defaults(clock.now());
        }
        PatternFeatures f = live.get();
        Double spendingToIncome = f.monthlyIncome() == null ? null : (double) f.averages().spending() / f.monthlyIncome();
        Double savingRate = f.monthlyIncome() == null ? null : (double) f.averages().savingTransfer() / f.monthlyIncome();
        return priority.calculate("LIVE_CALCULATION", null, clock.now(), new PriorityProfileCalculator.Inputs(
                f.debtBurden(), f.liquidityLevel(), f.discretionarySpendingRatio(), spendingToIncome, savingRate,
                f.savingConsistency(), f.fixedExpenseRatio(), null, null));
    }

    private Optional<PatternAnalysis> latestCompleted(Long userId) {
        return analyses.findFirstByUserIdAndStatusOrderByAnalyzedAtDescIdDesc(userId, PatternAnalysisStatus.COMPLETED);
    }

    private Optional<PatternFeatures> liveFeatures(Long userId, PatternWindow window) {
        List<MonthlyPatternRow> rows = reader.readUserMonthly(userId, window.startDate(), window.endDate());
        if (rows.stream().noneMatch(row -> row.transactionCount() > 0)) {
            return Optional.empty();
        }
        PatternFeatures features = calculator.calculate(window, rows, reader.readLiquidity(userId),
                reader.readProfileMonthlyIncome(userId));
        return features.hasData() ? Optional.of(features) : Optional.empty();
    }

    private PatternWindow window(Integer months) {
        return PatternWindow.endingBefore(clock.today(), PatternWindow.resolveMonths(months, properties.lookbackMonths()));
    }

    private static SpendingPatternResponse.Category toCategory(CategoryPatternRow row, long total) {
        double share = total == 0 ? 0.0 : Math.round(row.total() * 1000.0 / total) / 10.0;
        return new SpendingPatternResponse.Category(
                row.code() == null ? UNCATEGORIZED : row.code(), row.name() == null ? "미분류" : row.name(),
                row.total(), row.count(), share);
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
