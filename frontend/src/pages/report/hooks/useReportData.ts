import { useCallback, useEffect, useRef, useState } from 'react';

import {
  assetApi,
  cashflowApi,
  coachApi,
  isApiError,
  ledgerApi,
  userApi,
} from '@/api';
import type { CashflowRiskApiResponse } from '@/types/cashflowApi.types';
import type { SavingRatioApiResponse } from '@/types/ledgerApi.types';
import type { MonthlyReport, ReportFinancePattern } from '@/types/report.types';
import { formatCurrency } from '@/utils/currency';
import { toDateKey } from '@/utils/date';

function monthAt(offset: number) {
  const today = new Date();
  const date = new Date(today.getFullYear(), today.getMonth() + offset, 1);
  const month = date.getMonth() + 1;
  return {
    date,
    label: `${month}월`,
    yearMonth: `${date.getFullYear()}-${String(month).padStart(2, '0')}`,
  };
}

function monthPeriod(date: Date) {
  return {
    startDate: toDateKey(new Date(date.getFullYear(), date.getMonth(), 1)),
    endDate: toDateKey(new Date(date.getFullYear(), date.getMonth() + 1, 0)),
  };
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function riskPenalty(risks: readonly CashflowRiskApiResponse[]) {
  const points = { LOW: 5, MEDIUM: 10, HIGH: 20, CRITICAL: 30 } as const;
  return Math.min(30, risks.reduce((sum, risk) => sum + points[risk.severity], 0));
}

function efficiencyScore(
  ratio: SavingRatioApiResponse,
  fixedRatio: number,
  risks: readonly CashflowRiskApiResponse[],
) {
  const savingScore = ratio.savingRatioPercent === null
    ? 0
    : (clamp(ratio.savingRatioPercent, 0, 50) / 50) * 40;
  const spendingScore = clamp(100 - fixedRatio, 0, 100) * 0.3;
  return Math.round(clamp(savingScore + spendingScore + 30 - riskPenalty(risks), 0, 100));
}

function percentChange(current: number, previous: number) {
  if (previous === 0) return current === 0 ? 0 : 100;
  return Math.round(((current - previous) / previous) * 100);
}

function signed(value: number, suffix = '') {
  return `${value > 0 ? '+' : ''}${value}${suffix}`;
}

function cardSpending(paymentMethods: { paymentMethod: string; totalSpending: number }[]) {
  return paymentMethods
    .filter(({ paymentMethod }) => /CARD|카드/i.test(paymentMethod))
    .reduce((sum, item) => sum + item.totalSpending, 0);
}

async function loadOptionalResource<T>(request: Promise<T>): Promise<T | null> {
  try {
    return await request;
  } catch (error) {
    if (isApiError(error) && error.status === 404) return null;
    throw error;
  }
}

async function loadCoachSummary() {
  try {
    return await coachApi.getFinancialSummary();
  } catch {
    // AI 설명 호출 실패가 다른 금융 API로 계산한 리포트 전체를 가리지 않게 한다.
    return null;
  }
}

async function fetchReport(): Promise<MonthlyReport> {
  const reportMonth = monthAt(-1);
  const previousMonth = monthAt(-2);
  const trendStart = monthAt(-6);
  const nextMonth = monthAt(0);
  const nextPeriod = monthPeriod(nextMonth.date);
  const anomalyStart = monthAt(-3);

  const [
    user,
    currentSummary,
    trend,
    currentRatio,
    previousRatio,
    currentFixed,
    previousFixed,
    currentVariable,
    currentPayments,
    previousPayments,
    loans,
    cashflowEvents,
    cashflowRisks,
    anomalies,
    coachSummary,
  ] = await Promise.all([
    userApi.getMe(),
    ledgerApi.getMonthlySummary(reportMonth.yearMonth),
    ledgerApi.getCategoryTrend(trendStart.yearMonth, reportMonth.yearMonth),
    ledgerApi.getSavingRatio(reportMonth.yearMonth),
    ledgerApi.getSavingRatio(previousMonth.yearMonth),
    ledgerApi.getFixedSpending(reportMonth.yearMonth, reportMonth.yearMonth),
    ledgerApi.getFixedSpending(previousMonth.yearMonth, previousMonth.yearMonth),
    ledgerApi.getVariableSpending(reportMonth.yearMonth, reportMonth.yearMonth),
    ledgerApi.getPaymentMethodSpending(reportMonth.yearMonth),
    ledgerApi.getPaymentMethodSpending(previousMonth.yearMonth),
    assetApi.getLoanSummary(),
    loadOptionalResource(cashflowApi.getEvents(nextPeriod)),
    loadOptionalResource(cashflowApi.getRisks()),
    ledgerApi.getSpendingAnomalies(anomalyStart.yearMonth, reportMonth.yearMonth),
    loadCoachSummary(),
  ]);

  const risks = cashflowRisks?.risks ?? [];
  const score = efficiencyScore(
    currentRatio,
    currentFixed.shareOfSpendingPercent,
    risks,
  );
  const previousScore = efficiencyScore(
    previousRatio,
    previousFixed.shareOfSpendingPercent,
    risks,
  );
  const anomaly = anomalies?.anomalies[0];
  const patterns: ReportFinancePattern[] = [
    {
      id: 'saving-rate',
      status: (currentRatio.savingRatioPercent ?? 0) >= 20 ? 'good' : 'check',
      title: currentRatio.savingRatioPercent === null
        ? '저축률을 계산할 소득 정보가 없어요'
        : `이번 달 저축률은 ${Math.round(currentRatio.savingRatioPercent)}%예요`,
      description: currentRatio.incomeSource === 'NONE'
        ? '금융 기본정보에 월소득을 등록하면 분석할 수 있어요'
        : `수입 ${formatCurrency(currentRatio.income)} 중 ${formatCurrency(Math.max(0, currentRatio.remaining))}이 남았어요`,
      icon: require('@/assets/icons/common/report_piggy.png'),
    },
    anomaly
      ? {
          id: `anomaly-${anomaly.code}`,
          status: 'check',
          title: `${anomaly.name} 소비가 늘고 있어요`,
          description: `이전 기간보다 월평균 ${formatCurrency(anomaly.increaseAmount)} 증가했어요`,
          icon: require('@/assets/icons/common/report_bag.png'),
        }
      : {
          id: 'spending-stable',
          status: 'good',
          title: '급격하게 늘어난 소비가 없어요',
          description: '최근 소비 흐름이 이전 기간과 비슷하게 유지되고 있어요',
          icon: require('@/assets/icons/common/report_bag.png'),
        },
    cashflowRisks === null
      ? {
          id: 'cashflow-unavailable',
          status: 'check',
          title: '현금흐름 예측 데이터가 없어요',
          description: '금융 데이터 연결 후 예정 잔액 위험을 확인할 수 있어요',
          icon: require('@/assets/icons/common/report_coins.png'),
        }
      : risks[0]
      ? {
          id: `cashflow-${risks[0].riskId}`,
          status: 'check',
          title: '예상 잔액을 확인해 주세요',
          description: risks[0].message,
          icon: require('@/assets/icons/common/report_coins.png'),
        }
      : {
          id: 'cashflow-stable',
          status: 'good',
          title: '예정된 현금흐름 위험이 없어요',
          description: '현재 예측 기간에는 잔액 부족 위험이 발견되지 않았어요',
          icon: require('@/assets/icons/common/report_coins.png'),
        },
  ];

  const currentCard = cardSpending(currentPayments.paymentMethods);
  const previousCard = cardSpending(previousPayments.paymentMethods);
  const fixedForecast = currentFixed.monthlyAverage;
  const livingForecast = currentVariable.monthlyAverage;
  const expectedExpense = fixedForecast + livingForecast;
  const savingRate = Math.round(currentRatio.savingRatioPercent ?? 0);
  const previousSavingRate = Math.round(previousRatio.savingRatioPercent ?? 0);
  const fixedRate = Math.round(currentFixed.shareOfSpendingPercent);
  const previousFixedRate = Math.round(previousFixed.shareOfSpendingPercent);

  return {
    yearMonth: reportMonth.yearMonth,
    monthLabel: reportMonth.label,
    userName: user.name,
    efficiencyScore: {
      score,
      maxScore: 100,
      previousMonthDifference: score - previousScore,
      percentile: clamp(100 - score, 1, 99),
      summary: score >= 70
        ? '저축과 지출 흐름을 안정적으로 관리하고 있어요'
        : '고정지출과 저축 흐름을 한 번 더 살펴보세요',
      composition: [
        { id: 'score', tone: 'strong', ratio: score },
        { id: 'remaining', tone: 'light', ratio: 100 - score },
      ],
    },
    patterns,
    patternCoachingMessage:
      coachSummary?.message.trim() ||
      (anomaly
        ? `${anomaly.name} 소비를 지난 기간 수준으로 줄이면 한 달에 약 ${formatCurrency(anomaly.increaseAmount)}을 아낄 수 있어요!`
        : '지금의 소비 흐름을 유지하면서 저축률을 조금씩 높여보세요!'),
    patternCoachingDisclaimer: coachSummary?.disclaimer ?? null,
    patternCharacterImage: require('@/assets/characters/dori/dori_memo.png'),
    nextMonthForecast: {
      description: cashflowEvents
        ? '최근 소비 흐름과 다음 달 예정 금융일정을 기준으로 계산했어요'
        : '최근 소비 흐름을 기준으로 계산했어요',
      expectedExpense,
      differenceFromThisMonth: expectedExpense - currentSummary.totalSpending,
      breakdown: [
        { id: 'fixed', label: '고정지출', amount: fixedForecast, tone: 'fixed' },
        { id: 'living', label: '변동지출', amount: livingForecast, tone: 'living' },
      ],
    },
    keyMetrics: [
      {
        id: 'saving-rate', label: '저축률', value: savingRate, format: 'percent',
        changeLabel: `지난달 대비 ${signed(savingRate - previousSavingRate, '%p')}`,
        tone: 'accent', icon: require('@/assets/icons/common/report_pig.png'), iconScale: 1.05,
      },
      {
        id: 'fixed-expense-ratio', label: '고정지출 비중', value: fixedRate, format: 'percent',
        changeLabel: `지난달 대비 ${signed(fixedRate - previousFixedRate, '%p')}`,
        tone: 'plain', icon: require('@/assets/icons/common/report_home.png'), iconScale: 0.95,
      },
      {
        id: 'card-usage', label: '카드 사용액', value: currentCard, format: 'currency',
        changeLabel: `지난달 대비 ${signed(percentChange(currentCard, previousCard), '%')}`,
        tone: 'accent', icon: require('@/assets/icons/common/report_card.png'), iconScale: 1.4,
      },
      {
        id: 'loan-balance', label: '대출 잔액', value: loans.totalBalance, format: 'currency',
        changeLabel: '현재 대출 잔액', tone: 'plain',
        icon: require('@/assets/icons/common/report_money.png'), iconScale: 0.85,
      },
    ],
    monthlySpending: trend.months.map((month) => ({
      yearMonth: month.yearMonth,
      monthLabel: `${Number(month.yearMonth.slice(5))}월`,
      amount: month.totalSpending,
    })),
  };
}

export function useReportData() {
  const [report, setReport] = useState<MonthlyReport | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const requestIdRef = useRef(0);

  const load = useCallback(async (refreshing = false) => {
    const requestId = ++requestIdRef.current;
    if (refreshing) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    setError(null);
    try {
      const nextReport = await fetchReport();
      if (requestId === requestIdRef.current) setReport(nextReport);
    } catch (loadError) {
      if (requestId === requestIdRef.current) {
        setError(loadError instanceof Error ? loadError : new Error('리포트를 불러오지 못했어요.'));
      }
    } finally {
      if (requestId === requestIdRef.current) {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    }
  }, []);

  useEffect(() => {
    const timeout = setTimeout(() => void load(), 0);
    return () => {
      clearTimeout(timeout);
      requestIdRef.current += 1;
    };
  }, [load]);

  return {
    report,
    error,
    isLoading,
    isRefreshing,
    refetch: useCallback(() => load(true), [load]),
  };
}
