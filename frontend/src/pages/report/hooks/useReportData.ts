import { useCallback, useRef, useState } from 'react';

import {
  assetApi,
  cashflowApi,
  coachApi,
  isApiError,
  ledgerApi,
  patternApi,
  reportApi,
  userApi,
} from '@/api';
import type {
  MonthlyReport,
  ReportFinancePattern,
  ReportMetricChange,
  ReportPeerComparison,
} from '@/types/report.types';
import { getTransactionCategoryIcon } from '@/constants/transactionCategoryIcons';
import { formatCurrency } from '@/utils/currency';

import { toEfficiencyScore } from '../efficiencyScoreModel';
import {
  PEER_COMPARISON_NOT_ANALYZED,
  toPeerComparison,
} from '../peerComparisonModel';

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

const NO_CHANGE: ReportMetricChange = {
  direction: 'none',
  label: '지난달과 같아요',
  tone: 'neutral',
};

/**
 * 전월 대비 증감을 지표 타일의 화살표 표시로 바꾼다. 색은 증감 부호가 아니라 `goodWhen`으로 정한다.
 * 저축률은 올라야 좋고 고정지출 비중·카드 사용액은 내려야 좋아서, 같은 `+`라도 의미가 반대다.
 */
function metricChange(
  difference: number,
  goodWhen: 'up' | 'down',
  format: (value: number) => string,
): ReportMetricChange {
  if (difference === 0) return NO_CHANGE;

  const direction = difference > 0 ? 'up' : 'down';
  return {
    direction,
    label: format(Math.abs(difference)),
    tone: direction === goodWhen ? 'good' : 'bad',
  };
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

/**
 * 또래 비교를 조회한다. 서버에 스케줄러가 없어 분석은 `analyze` 호출로만 만들어지므로 먼저 실행하고,
 * 오늘 완료된 결과가 있으면 서버가 그대로 재사용한다. 분석 자체가 실패해도 조회는 시도한다.
 *
 * 이 카드 하나 때문에 리포트 전체가 비지 않도록 실패는 null로 흡수한다.
 */
async function loadPeerComparison(): Promise<ReportPeerComparison | null> {
  try {
    await patternApi.analyze();
  } catch {
    // 이미 완료된 분석이 있으면 조회는 성공할 수 있어 여기서 멈추지 않는다.
  }

  try {
    return toPeerComparison(await patternApi.getPeerComparison());
  } catch (error) {
    if (isApiError(error) && error.status === 404) return PEER_COMPARISON_NOT_ANALYZED;
    return null;
  }
}

async function fetchReport(): Promise<MonthlyReport> {
  const reportMonth = monthAt(-1);
  const previousMonth = monthAt(-2);
  const trendStart = monthAt(-6);
  const anomalyStart = monthAt(-3);

  const [
    user,
    trend,
    currentRatio,
    previousRatio,
    currentFixed,
    previousFixed,
    currentPayments,
    previousPayments,
    loans,
    cashflowRisks,
    anomalies,
    coachSummary,
    peerComparison,
    monthlyReport,
  ] = await Promise.all([
    userApi.getMe(),
    ledgerApi.getCategoryTrend(trendStart.yearMonth, reportMonth.yearMonth),
    ledgerApi.getSavingRatio(reportMonth.yearMonth),
    ledgerApi.getSavingRatio(previousMonth.yearMonth),
    ledgerApi.getFixedSpending(reportMonth.yearMonth, reportMonth.yearMonth),
    ledgerApi.getFixedSpending(previousMonth.yearMonth, previousMonth.yearMonth),
    ledgerApi.getPaymentMethodSpending(reportMonth.yearMonth),
    ledgerApi.getPaymentMethodSpending(previousMonth.yearMonth),
    assetApi.getLoanSummary(),
    loadOptionalResource(cashflowApi.getRisks()),
    ledgerApi.getSpendingAnomalies(anomalyStart.yearMonth, reportMonth.yearMonth),
    loadCoachSummary(),
    loadPeerComparison(),
    loadOptionalResource(reportApi.getMonthly(reportMonth.yearMonth)),
  ]);

  const risks = cashflowRisks?.risks ?? [];
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

  // 카드 사용액은 서버 집계(카드 식별자 또는 결제수단 CARD)를 쓴다. 결제수단 이름을 FE에서 문자열로
  // 거르던 방식은 이름이 예상과 다르면 조용히 빠져서, 월간 리포트를 못 받은 경우에만 대비책으로 남긴다.
  const currentCard = monthlyReport?.cardSpending ?? cardSpending(currentPayments.paymentMethods);
  const cardDifference = monthlyReport === null
    ? currentCard - cardSpending(previousPayments.paymentMethods)
    : monthlyReport.cardSpendingDifferenceFromPreviousMonth;
  const latestTrendMonth = trend.months.at(-1) ?? null;
  const latestMonthSpending = latestTrendMonth?.totalSpending ?? 0;
  const savingRate = Math.round(currentRatio.savingRatioPercent ?? 0);
  const previousSavingRate = Math.round(previousRatio.savingRatioPercent ?? 0);
  const fixedRate = Math.round(currentFixed.shareOfSpendingPercent);
  const previousFixedRate = Math.round(previousFixed.shareOfSpendingPercent);

  return {
    yearMonth: reportMonth.yearMonth,
    monthLabel: reportMonth.label,
    userName: user.name,
    efficiencyScore: toEfficiencyScore(monthlyReport?.efficiency ?? null),
    patterns,
    patternCoachingMessage:
      coachSummary?.message.trim() ||
      (anomaly
        ? `${anomaly.name} 소비를 지난 기간 수준으로 줄이면 한 달에 약 ${formatCurrency(anomaly.increaseAmount)}을 아낄 수 있어요!`
        : '지금의 소비 흐름을 유지하면서 저축률을 조금씩 높여보세요!'),
    patternCoachingDisclaimer: coachSummary?.disclaimer ?? null,
    patternCharacterImage: require('@/assets/characters/dori/dori_memo.png'),
    peerComparison,
    keyMetrics: [
      {
        id: 'saving-rate', label: '저축률', value: savingRate, format: 'percent',
        change: metricChange(savingRate - previousSavingRate, 'up', (value) => `${value}%p`),
        icon: require('@/assets/icons/common/report_pig.png'), iconScale: 1.05,
      },
      {
        id: 'fixed-expense-ratio', label: '고정지출 비중', value: fixedRate, format: 'percent',
        change: metricChange(fixedRate - previousFixedRate, 'down', (value) => `${value}%p`),
        icon: require('@/assets/icons/common/report_home.png'), iconScale: 0.95,
      },
      {
        id: 'card-usage', label: '카드 사용액', value: currentCard, format: 'currency',
        change: cardDifference === null
          ? { direction: 'none', label: '비교할 지난달 기록이 없어요', tone: 'neutral' }
          : metricChange(cardDifference, 'down', formatCurrency),
        icon: require('@/assets/icons/common/report_card.png'), iconScale: 1.4,
      },
      {
        id: 'loan-balance', label: '대출 잔액', value: loans.totalBalance, format: 'currency',
        // 대출 잔액은 전월 값을 받아오지 않아 증감을 계산할 수 없다.
        change: { direction: 'none', label: '현재 잔액', tone: 'neutral' },
        icon: require('@/assets/icons/common/report_money.png'), iconScale: 0.85,
      },
    ],
    pastSpending: {
      months: trend.months.map((month) => ({
        yearMonth: month.yearMonth,
        monthLabel: `${Number(month.yearMonth.slice(5))}월`,
        amount: month.totalSpending,
      })),
      monthlyAverage: trend.monthlyAverage,
      latestAmount: latestMonthSpending,
      differenceFromAverage: latestMonthSpending - trend.monthlyAverage,
      // 서버가 카테고리를 내림차순으로 주므로 상위 세 개만 자른다.
      topCategories: (latestTrendMonth?.categories ?? [])
        .filter((category) => category.totalSpending > 0)
        .slice(0, 3)
        .map((category) => ({
          id: `${category.categoryId ?? category.code}`,
          name: category.name,
          amount: category.totalSpending,
          sharePercent:
            latestMonthSpending === 0
              ? 0
              : Math.round((category.totalSpending / latestMonthSpending) * 100),
          icon: getTransactionCategoryIcon(category.code),
        })),
    },
  };
}

export function useReportData() {
  const [report, setReport] = useState<MonthlyReport | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const requestIdRef = useRef(0);
  const loadInFlightRef = useRef(false);

  const load = useCallback(async (refreshing = false) => {
    if (loadInFlightRef.current) return;

    loadInFlightRef.current = true;
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
        setError(
          isApiError(loadError) && loadError.status === 404
            ? null
            : loadError instanceof Error
              ? loadError
              : new Error('리포트를 불러오지 못했어요.'),
        );
        setReport(null);
      }
    } finally {
      if (requestId === requestIdRef.current) {
        loadInFlightRef.current = false;
        setIsLoading(false);
        setIsRefreshing(false);
      }
    }
  }, []);

  return {
    report,
    error,
    isLoading,
    isRefreshing,
    refetch: useCallback(() => load(true), [load]),
    reload: useCallback(() => load(), [load]),
    cancel: useCallback(() => {
      requestIdRef.current += 1;
      loadInFlightRef.current = false;
    }, []),
  };
}
