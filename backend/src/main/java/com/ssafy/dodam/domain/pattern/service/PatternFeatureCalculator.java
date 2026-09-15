package com.ssafy.dodam.domain.pattern.service;

import com.ssafy.dodam.domain.pattern.entity.LiquidityLevel;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader.LiquidityRow;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader.MonthlyPatternRow;
import com.ssafy.dodam.domain.pattern.service.PatternFeatures.Averages;
import com.ssafy.dodam.domain.pattern.service.PatternFeatures.IncomeBasis;
import com.ssafy.dodam.domain.pattern.service.PatternFeatures.MonthSummary;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 월별 집계 → 패턴 지표·군집 특징. 순수 계산이라 DB를 보지 않는다.
 *
 * <p>사용자 본인 계산({@link #calculate})과 군집 모집단 계산({@link #clusterVector})이 같은 특징 정의를 쓴다. 정의가 한 곳에
 * 있어야 사용자 값과 군집 기준 범위를 같은 잣대로 비교할 수 있다. 모집단은 금융 기본정보를 읽지 않으므로 소득은 INCOME 거래만 본다.
 */
@Component
public class PatternFeatureCalculator {

    public PatternFeatures calculate(
            PatternWindow window, List<MonthlyPatternRow> rows, LiquidityRow liquidity, Long profileMonthlyIncome
    ) {
        List<MonthSummary> months = fill(window, rows);
        List<MonthSummary> withData = months.stream().filter(m -> m.transactionCount() > 0).toList();
        int n = withData.size();
        int dataPointCount = (int) withData.stream().mapToLong(MonthSummary::transactionCount).sum();

        long incomeSum = sum(withData, MonthSummary::income);
        long spendingSum = sum(withData, MonthSummary::spending);
        long fixedSum = sum(withData, MonthSummary::fixedSpending);
        long variableSum = sum(withData, MonthSummary::variableSpending);
        long discretionarySum = sum(withData, MonthSummary::discretionary);
        long savingSum = sum(withData, MonthSummary::savingTransfer);
        long loanSum = sum(withData, MonthSummary::loanRepayment);

        Averages averages = new Averages(
                average(incomeSum, n), average(spendingSum, n), average(fixedSum, n), average(variableSum, n),
                average(discretionarySum, n), average(savingSum, n), average(loanSum, n));

        IncomeBasis incomeBasis;
        Long monthlyIncome;
        if (n > 0 && incomeSum > 0) {
            incomeBasis = IncomeBasis.TRANSACTIONS;
            monthlyIncome = averages.income();
        } else if (profileMonthlyIncome != null && profileMonthlyIncome > 0) {
            incomeBasis = IncomeBasis.PROFILE;
            monthlyIncome = profileMonthlyIncome;
        } else {
            incomeBasis = IncomeBasis.NONE;
            monthlyIncome = null;
        }

        double[] spendingSeries = withData.stream().mapToDouble(MonthSummary::spending).toArray();
        Double volatility = Stats.coefficientOfVariation(spendingSeries);
        Double regularity = volatility == null ? null : Stats.clamp01(1.0 - volatility);
        Double savingConsistency = n == 0 ? null
                : (double) withData.stream().filter(m -> m.savingTransfer() > 0).count() / n;
        Double fixedRatio = Stats.ratio(fixedSum, spendingSum);
        Double variableRatio = Stats.ratio(variableSum, spendingSum);
        Double discretionaryRatio = Stats.ratio(discretionarySum, spendingSum);
        Double debtBurden = monthlyIncome == null ? null : (double) averages.loanRepayment() / monthlyIncome;
        Double cashflowStability = cashflowStability(withData, incomeBasis, monthlyIncome);

        Long liquidBalance = null;
        Double runwayMonths = null;
        LiquidityLevel liquidityLevel = null;
        if (liquidity != null && liquidity.accountCount() > 0) {
            liquidBalance = liquidity.liquidBalance();
            runwayMonths = averages.spending() > 0 ? (double) liquidBalance / averages.spending() : null;
            liquidityLevel = LiquidityLevel.ofRunway(runwayMonths, liquidBalance);
        }

        double[] clusterVector = clusterVector(monthlyIncome, averages, spendingSum, fixedSum, discretionarySum, volatility)
                .orElse(null);

        return new PatternFeatures(window, months, n, dataPointCount, incomeBasis, monthlyIncome, averages,
                Stats.round4(regularity), Stats.round4(savingConsistency), Stats.round4(fixedRatio), Stats.round4(variableRatio),
                Stats.round4(discretionaryRatio), Stats.round4(debtBurden), Stats.round4(cashflowStability),
                Stats.round4(volatility), liquidBalance, runwayMonths == null ? null : Stats.round4(runwayMonths),
                liquidityLevel, clusterVector);
    }

    /**
     * 군집 모집단 한 사용자의 특징 벡터. 거래가 있는 달이 `minMonths`보다 적거나 소득·소비가 없으면 비어 있다.
     * 모집단은 금융 기본정보를 보지 않으므로 소득은 INCOME 거래 월평균이다.
     */
    public Optional<double[]> clusterVector(PatternWindow window, List<MonthlyPatternRow> rows, int minMonths) {
        List<MonthSummary> withData = fill(window, rows).stream().filter(m -> m.transactionCount() > 0).toList();
        int n = withData.size();
        if (n < minMonths) {
            return Optional.empty();
        }
        long incomeSum = sum(withData, MonthSummary::income);
        if (incomeSum <= 0) {
            return Optional.empty();
        }
        long spendingSum = sum(withData, MonthSummary::spending);
        long fixedSum = sum(withData, MonthSummary::fixedSpending);
        long discretionarySum = sum(withData, MonthSummary::discretionary);
        long savingSum = sum(withData, MonthSummary::savingTransfer);
        Averages averages = new Averages(average(incomeSum, n), average(spendingSum, n), average(fixedSum, n), 0,
                average(discretionarySum, n), average(savingSum, n), 0);
        Double volatility = Stats.coefficientOfVariation(withData.stream().mapToDouble(MonthSummary::spending).toArray());
        return clusterVector(averages.income(), averages, spendingSum, fixedSum, discretionarySum, volatility);
    }

    private static Optional<double[]> clusterVector(
            Long monthlyIncome, Averages averages, long spendingSum, long fixedSum, long discretionarySum, Double volatility
    ) {
        if (monthlyIncome == null || monthlyIncome <= 0 || spendingSum <= 0) {
            return Optional.empty();
        }
        double[] vector = new double[ClusterFeature.dimension()];
        vector[ClusterFeature.SPENDING_TO_INCOME_RATIO.ordinal()] =
                ClusterFeature.SPENDING_TO_INCOME_RATIO.clip((double) averages.spending() / monthlyIncome);
        vector[ClusterFeature.SAVING_RATE.ordinal()] =
                ClusterFeature.SAVING_RATE.clip((double) averages.savingTransfer() / monthlyIncome);
        vector[ClusterFeature.FIXED_EXPENSE_SHARE.ordinal()] =
                ClusterFeature.FIXED_EXPENSE_SHARE.clip((double) fixedSum / spendingSum);
        vector[ClusterFeature.DISCRETIONARY_SHARE.ordinal()] =
                ClusterFeature.DISCRETIONARY_SHARE.clip((double) discretionarySum / spendingSum);
        vector[ClusterFeature.SPENDING_VOLATILITY.ordinal()] =
                ClusterFeature.SPENDING_VOLATILITY.clip(volatility == null ? 0.0 : volatility);
        return Optional.of(vector);
    }

    private static Double cashflowStability(List<MonthSummary> withData, IncomeBasis basis, Long monthlyIncome) {
        if (withData.isEmpty() || basis == IncomeBasis.NONE || monthlyIncome == null) {
            return null;
        }
        long positive = withData.stream()
                .filter(m -> (m.income() > 0 ? m.income() : monthlyIncome) - m.spending() >= 0)
                .count();
        return (double) positive / withData.size();
    }

    /** 기간 안 모든 달을 채운다. 집계 행이 없는 달은 0이다. */
    static List<MonthSummary> fill(PatternWindow window, List<MonthlyPatternRow> rows) {
        Map<YearMonth, MonthlyPatternRow> byMonth = rows.stream()
                .filter(row -> window.contains(row.yearMonth()))
                .collect(Collectors.toMap(MonthlyPatternRow::yearMonth, Function.identity(), (a, b) -> a));
        List<MonthSummary> months = new ArrayList<>();
        for (YearMonth yearMonth : window.yearMonths()) {
            MonthlyPatternRow row = byMonth.get(yearMonth);
            if (row == null) {
                months.add(new MonthSummary(yearMonth, 0, 0, 0, 0, 0, 0, 0, 0));
            } else {
                months.add(new MonthSummary(yearMonth, row.income(), row.spending(), row.fixedSpending(), row.variableSpending(),
                        row.discretionary(), row.savingTransfer(), row.loanRepayment(), row.transactionCount()));
            }
        }
        return months;
    }

    private static long sum(List<MonthSummary> months, java.util.function.ToLongFunction<MonthSummary> field) {
        return months.stream().mapToLong(field).sum();
    }

    private static long average(long sum, int count) {
        return count == 0 ? 0 : Math.round((double) sum / count);
    }
}
