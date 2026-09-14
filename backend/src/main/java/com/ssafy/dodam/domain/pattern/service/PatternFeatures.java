package com.ssafy.dodam.domain.pattern.service;

import com.ssafy.dodam.domain.pattern.entity.LiquidityLevel;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 한 사용자의 패턴 계산 결과. {@link PatternFeatureCalculator}가 만들고 실행(516)·조회(517)·군집(518)이 함께 쓴다.
 *
 * <p>평균값은 **거래가 있는 달**로 나눈다. 6개월 창에 3개월만 데이터가 있는 사용자를 6으로 나누면 실제보다 절반으로 보인다.
 * 어느 달이 비었는지는 {@code months}의 `transactionCount = 0`으로 드러난다.
 *
 * @param window            분석 기간
 * @param months            기간 안 모든 달(오래된 순, 거래 없는 달은 0)
 * @param monthsWithData    거래가 1건 이상 있는 달 수
 * @param dataPointCount    기간 안 거래 건수(숨긴 거래 제외)
 * @param incomeBasis       월 소득의 출처
 * @param monthlyIncome     월 소득(TRANSACTIONS: INCOME 거래 월평균, PROFILE: 금융 기본정보). NONE이면 null
 * @param spendingRegularity 1 − 월 소비 변동계수(0~1). 달이 2개 미만이면 null
 * @param savingConsistency  저축성 지출이 있는 달 비율(0~1)
 * @param fixedExpenseRatio  고정지출 / 소비. 소비가 0이면 null
 * @param variableExpenseRatio 변동지출 / 소비
 * @param discretionarySpendingRatio 재량소비 / 소비
 * @param debtBurden         월평균 대출상환 / 월 소득. 소득을 모르면 null
 * @param cashflowStability  (소득 − 소비) ≥ 0인 달 비율. 소득을 모르면 null
 * @param spendingVolatility 월 소비 변동계수. 달이 2개 미만이거나 평균이 0이면 null
 * @param liquidBalance      입출금 활성 계좌 잔액 합. 계좌 정보가 없으면 null
 * @param runwayMonths       liquidBalance / 월평균 소비. 계산 불가면 null
 * @param liquidityLevel     유동성 수준. 계좌 정보가 없으면 null
 * @param clusterVector      군집 입력 특징(순서는 {@link ClusterFeature}). 소득을 모르거나 소비가 없으면 null
 */
public record PatternFeatures(
        PatternWindow window,
        List<MonthSummary> months,
        int monthsWithData,
        int dataPointCount,
        IncomeBasis incomeBasis,
        Long monthlyIncome,
        Averages averages,
        Double spendingRegularity,
        Double savingConsistency,
        Double fixedExpenseRatio,
        Double variableExpenseRatio,
        Double discretionarySpendingRatio,
        Double debtBurden,
        Double cashflowStability,
        Double spendingVolatility,
        Long liquidBalance,
        Double runwayMonths,
        LiquidityLevel liquidityLevel,
        double[] clusterVector
) {

    public PatternFeatures {
        months = List.copyOf(months);
        clusterVector = clusterVector == null ? null : clusterVector.clone();
    }

    public boolean hasData() {
        return monthsWithData > 0;
    }

    public boolean hasClusterVector() {
        return clusterVector != null;
    }

    /** 특징 키 → 값. 응답·저장 JSON용. */
    public Map<ClusterFeature, Double> clusterFeatureValues() {
        Map<ClusterFeature, Double> values = new EnumMap<>(ClusterFeature.class);
        if (clusterVector == null) {
            return values;
        }
        List<ClusterFeature> features = ClusterFeature.ordered();
        for (int i = 0; i < features.size(); i++) {
            values.put(features.get(i), Stats.round4(clusterVector[i]));
        }
        return values;
    }

    public enum IncomeBasis {
        /** 기간 INCOME 거래 월평균 */
        TRANSACTIONS,
        /** 금융 기본정보 `financial_profile.monthly_income` */
        PROFILE,
        /** 둘 다 없음. 소득 대비 지표는 null */
        NONE
    }

    /** 한 달 집계(원). */
    public record MonthSummary(
            YearMonth yearMonth,
            long income,
            long spending,
            long fixedSpending,
            long variableSpending,
            long discretionary,
            long savingTransfer,
            long loanRepayment,
            long transactionCount
    ) {
        public long net() {
            return income - spending;
        }
    }

    /** 거래가 있는 달 기준 월평균(원, 반올림). */
    public record Averages(
            long income,
            long spending,
            long fixedSpending,
            long variableSpending,
            long discretionary,
            long savingTransfer,
            long loanRepayment
    ) {
    }
}
