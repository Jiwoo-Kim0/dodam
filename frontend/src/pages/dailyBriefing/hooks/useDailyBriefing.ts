import { useCallback, useRef, useState } from 'react';

import { cashflowApi, goalApi, isApiError, ledgerApi } from '@/api';
import type { TransactionApiResponse } from '@/types/ledgerApi.types';
import { formatCurrency } from '@/utils/currency';
import { shiftDateKey, toDateKey } from '@/utils/date';

import { buildBriefingInsights, buildBriefingStreaks, mapBriefingTransactions } from '../dailyBriefingModel';
import type { DailyBriefingData, DailyBriefingGoalImpact } from '../dailyBriefing.types';

async function loadYesterdayTransactions(date: string): Promise<TransactionApiResponse[]> {
  const transactions: TransactionApiResponse[] = [];
  let page = 0;
  while (true) {
    const response = await ledgerApi.getTransactions({
      startDate: date,
      endDate: date,
      hidden: false,
      page,
      size: 100,
    });
    transactions.push(...response.content);
    if (response.last) return transactions;
    page += 1;
  }
}

async function loadGoalImpacts(): Promise<DailyBriefingGoalImpact[]> {
  const goals = await goalApi.getGoals({ status: 'ACTIVE', size: 100 });
  const spendingGoal = goals.content.find((goal) => goal.type === 'SPENDING');
  const savingGoal = goals.content.find((goal) => goal.type === 'SAVING');
  const [spending, saving] = await Promise.allSettled([
    spendingGoal ? goalApi.getSpendingProgress(spendingGoal.goalId) : Promise.resolve(null),
    savingGoal ? goalApi.getForecast(savingGoal.goalId) : Promise.resolve(null),
  ]);
  const impacts: DailyBriefingGoalImpact[] = [];

  if (spending.status === 'fulfilled' && spending.value) {
    impacts.push({
      id: 'saving',
      label: '이번 달 예상 절약',
      value: formatCurrency(spending.value.expectedSavingAmount),
      tone: 'neutral',
      icon: require('@/assets/icons/common/money.png'),
    });
  }
  if (saving.status === 'fulfilled' && saving.value) {
    impacts.push({
      id: 'date',
      label: '예상 목표 달성일',
      value: saving.value.expectedAchievementDate?.replaceAll('-', '.') ?? '계산 전',
      tone: 'neutral',
      icon: require('@/assets/icons/common/calendar.png'),
    });
    if (saving.value.daysDifference !== null) {
      const days = saving.value.daysDifference;
      impacts.push({
        id: 'difference',
        label: '목표 예정일 대비',
        value: days < 0 ? `${Math.abs(days)}일 빠름` : days > 0 ? `${days}일 늦음` : '예정대로',
        tone: days < 0 ? 'good' : days > 0 ? 'bad' : 'neutral',
        icon: require('@/assets/icons/common/clock.png'),
      });
    }
  }
  if (impacts.length === 0 && (spending.status === 'rejected' || saving.status === 'rejected')) {
    throw new Error('목표 현황을 불러오지 못했어요.');
  }
  return impacts;
}

async function loadUpcomingEvent(today: string) {
  try {
    const response = await cashflowApi.getEvents({
      startDate: today,
      endDate: shiftDateKey(today, 14),
    });
    const event = response.events
      .filter((item) => item.direction === 'OUTFLOW' && item.eventDate >= today)
      .sort((left, right) => left.eventDate.localeCompare(right.eventDate))[0];
    if (!event) {
      return { title: '예정된 출금이 없어요', description: '앞으로 2주간 확인된 출금 일정이 없어요.' };
    }
    const days = Math.round(
      (new Date(`${event.eventDate}T00:00:00`).getTime() - new Date(`${today}T00:00:00`).getTime()) / 86_400_000,
    );
    return {
      title: `${days === 0 ? '오늘' : `${days}일 뒤`} ${event.name} ${formatCurrency(event.amount)} 출금 예정`,
      description: '예정 내역을 확인하고 잔액을 준비해 주세요.',
    };
  } catch (error) {
    if (isApiError(error) && error.status === 404) {
      return { title: '현금흐름 예측 전이에요', description: '금융 데이터가 연결되면 예정 출금을 확인할 수 있어요.' };
    }
    throw error;
  }
}

async function fetchBriefing(): Promise<DailyBriefingData> {
  const today = toDateKey(new Date());
  const yesterday = shiftDateKey(today, -1);
  const previousMonthDate = new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1);
  const previousMonth = `${previousMonthDate.getFullYear()}-${String(previousMonthDate.getMonth() + 1).padStart(2, '0')}`;
  const sevenDaysAgo = shiftDateKey(yesterday, -6);
  const [daily, previousSummary, transactions, previousCategories, recentDaily, goals, upcoming] = await Promise.allSettled([
    ledgerApi.getDailySpending({ startDate: yesterday, endDate: yesterday }),
    ledgerApi.getMonthlySummary(previousMonth),
    loadYesterdayTransactions(yesterday),
    ledgerApi.getCategorySpending(previousMonth),
    ledgerApi.getDailySpending({ startDate: sevenDaysAgo, endDate: yesterday }),
    loadGoalImpacts(),
    loadUpcomingEvent(today),
  ]);

  if (daily.status === 'rejected') throw daily.reason;
  const average = previousSummary.status === 'fulfilled' && previousSummary.value.transactionCount > 0
    ? previousSummary.value.dailyAverage : null;
  const transactionList = transactions.status === 'fulfilled'
    ? mapBriefingTransactions(transactions.value) : [];
  const canAnalyze = transactions.status === 'fulfilled' && previousCategories.status === 'fulfilled' &&
    previousSummary.status === 'fulfilled';
  const insights = canAnalyze
    ? buildBriefingInsights(transactions.value, previousCategories.value, previousSummary.value.daysInMonth)
    : [];
  const streaks = recentDaily.status === 'fulfilled'
    ? buildBriefingStreaks(recentDaily.value, average) : [];

  return {
    date: yesterday,
    yesterdaySpentAmount: daily.value.totalSpending,
    previousMonthDailyAverage: average,
    comparisonMessage: previousSummary.status === 'rejected'
      ? '지난달 소비 내역을 불러오지 못했어요.'
      : previousSummary.value.transactionCount === 0
        ? '지난달 소비 기록이 없어 비교할 수 없어요.'
        : null,
    transactions: transactionList,
    transactionsMessage: transactions.status === 'rejected' ? '결제 내역을 불러오지 못했어요.' : null,
    insights,
    insightsMessage: !canAnalyze ? '비교 데이터를 불러오지 못했어요.'
      : average === null ? '지난달 소비 기록이 없어 비교할 수 없어요.'
        : '어제는 지난달 평균과 차이가 없어요.',
    goalImpacts: goals.status === 'fulfilled' ? goals.value : [],
    goalImpactsMessage: goals.status === 'rejected' ? '목표 현황을 불러오지 못했어요.' : '진행 중인 목표가 없어요.',
    upcomingEvent: upcoming.status === 'fulfilled' ? upcoming.value : {
      title: '예정 내역을 불러오지 못했어요', description: '잠시 후 다시 확인해 주세요.',
    },
    streaks,
    streaksMessage: recentDaily.status === 'rejected' ? '최근 소비 기록을 불러오지 못했어요.' : '현재 이어지는 기록이 없어요.',
  };
}

export function useDailyBriefing() {
  const [data, setData] = useState<DailyBriefingData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const requestIdRef = useRef(0);
  const hasLoadedRef = useRef(false);

  const load = useCallback(async (refreshing: boolean) => {
    const requestId = ++requestIdRef.current;
    if (refreshing || hasLoadedRef.current) setIsRefreshing(true);
    else setIsLoading(true);
    setError(null);
    try {
      const next = await fetchBriefing();
      if (requestId === requestIdRef.current) setData(next);
    } catch (loadError) {
      if (requestId === requestIdRef.current) {
        setError(loadError instanceof Error ? loadError : new Error('브리핑을 불러오지 못했어요.'));
        setData(null);
      }
    } finally {
      if (requestId === requestIdRef.current) {
        hasLoadedRef.current = true;
        setIsLoading(false);
        setIsRefreshing(false);
      }
    }
  }, []);

  return {
    data,
    error,
    isLoading,
    isRefreshing,
    reload: useCallback(() => load(false), [load]),
    refetch: useCallback(() => load(true), [load]),
    cancel: useCallback(() => { requestIdRef.current += 1; }, []),
  };
}
