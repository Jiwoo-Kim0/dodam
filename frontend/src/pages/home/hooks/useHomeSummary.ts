import { useCallback, useEffect, useRef, useState } from 'react';

import { assetApi, cashflowApi, goalApi, isApiError, ledgerApi } from '@/api';
import type { AssetOverview } from '@/types/asset.types';
import type { HomeGoal } from '@/types/goal.types';
import type { GoalApiResponse } from '@/types/goalApi.types';
import { mapOwnedAssetOverview } from '@/utils/assetOverview';
import { formatCurrency } from '@/utils/currency';
import { shiftDateKey, toDateKey } from '@/utils/date';

export interface HomeBriefing {
  yesterdaySpentAmount: number;
  message: string;
}

export interface HomeSpendingCategory {
  rank: number;
  label: string;
  amount: number;
  changePercent: number;
  direction: 'up' | 'down';
}

interface HomeSummaryData {
  monthLabel: string;
  monthlyExpense: number | null;
  assetOverview: AssetOverview | null;
  goals: HomeGoal[];
  briefing: HomeBriefing | null;
  topSpending: HomeSpendingCategory[];
}

function monthInfo(offsetFromCurrent: number) {
  const today = new Date();
  const date = new Date(today.getFullYear(), today.getMonth() + offsetFromCurrent, 1);
  const month = date.getMonth() + 1;
  return {
    monthLabel: `${month}월`,
    yearMonth: `${date.getFullYear()}-${String(month).padStart(2, '0')}`,
  };
}

function mapSavingGoal(goal: GoalApiResponse): HomeGoal {
  const progress = Math.round(goal.progressRatePercent ?? 0);
  return {
    id: String(goal.goalId),
    type: 'saving',
    title: goal.name,
    dueDateLabel: goal.targetDate ? `${goal.targetDate.replaceAll('-', '.')}까지` : '기한 없음',
    dDay: Math.max(0, goal.daysRemaining ?? 0),
    currentAmount: goal.currentAmount,
    targetAmount: goal.targetAmount ?? 0,
    message: progress >= 100 ? '목표를 달성했어요!' : `현재 목표의 ${progress}%를 모았어요.`,
    characterImage: require('@/assets/characters/dori/dori_saving_home.png'),
  };
}

async function mapSpendingGoal(goal: GoalApiResponse): Promise<HomeGoal> {
  const progress = await goalApi.getSpendingProgress(goal.goalId);
  const isExceeded = progress.remainingBudget < 0;
  return {
    id: String(goal.goalId),
    type: 'reducing',
    title: goal.name,
    dueDateLabel: `${Number(progress.yearMonth.slice(5))}월 소비 목표`,
    dDay: progress.daysLeft,
    currentAmount: progress.spent,
    targetAmount: progress.monthlyBudget,
    message: isExceeded
      ? `목표보다 ${formatCurrency(Math.abs(progress.remainingBudget))} 더 썼어요.`
      : `이번 달 ${formatCurrency(progress.remainingBudget)} 더 쓸 수 있어요.`,
    characterImage: require('@/assets/characters/tori/tori_reducing_home.png'),
  };
}

async function loadGoals() {
  const response = await goalApi.getGoals({ status: 'ACTIVE', size: 20 });
  return Promise.all(
    response.content.map((goal) =>
      goal.type === 'SPENDING' ? mapSpendingGoal(goal) : mapSavingGoal(goal),
    ),
  );
}

async function loadOptionalCashflowEvents(startDate: string, endDate: string) {
  try {
    return await cashflowApi.getEvents({ startDate, endDate });
  } catch (error) {
    if (isApiError(error) && error.status === 404) return null;
    throw error;
  }
}

async function loadBriefing(): Promise<HomeBriefing> {
  const today = toDateKey(new Date());
  const yesterday = shiftDateKey(today, -1);
  const [daily, eventsResult] = await Promise.all([
    ledgerApi.getDailySpending({ startDate: yesterday, endDate: yesterday }),
    loadOptionalCashflowEvents(today, shiftDateKey(today, 14)),
  ]);
  const nextOutflow = eventsResult?.events
    .filter((event) => event.direction === 'OUTFLOW' && event.eventDate >= today)
    .sort((left, right) => left.eventDate.localeCompare(right.eventDate))[0];
  let message = eventsResult
    ? '앞으로 2주간 예정된 출금이 없어요.'
    : '현금흐름 예측 데이터가 아직 없어요.';

  if (nextOutflow) {
    const days = Math.max(
      0,
      Math.round(
        (new Date(`${nextOutflow.eventDate}T00:00:00`).getTime() -
          new Date(`${today}T00:00:00`).getTime()) /
          86_400_000,
      ),
    );
    message = `${days === 0 ? '오늘' : `${days}일 뒤`} ${nextOutflow.name} ${formatCurrency(nextOutflow.amount)}이 출금돼요.`;
  }

  return { yesterdaySpentAmount: daily.totalSpending, message };
}

async function loadTopSpending(yearMonth: string, previousYearMonth: string) {
  const [current, previous] = await Promise.all([
    ledgerApi.getCategorySpending(yearMonth),
    ledgerApi.getCategorySpending(previousYearMonth),
  ]);
  const previousByCode = new Map(
    previous.categories.map((category) => [category.code, category.totalSpending]),
  );

  return current.categories.slice(0, 3).map((category, index) => {
    const previousAmount = previousByCode.get(category.code) ?? 0;
    const difference = category.totalSpending - previousAmount;
    const changePercent = previousAmount === 0
      ? category.totalSpending === 0 ? 0 : 100
      : Math.round((Math.abs(difference) / previousAmount) * 100);
    return {
      rank: index + 1,
      label: category.name,
      amount: category.totalSpending,
      changePercent,
      direction: difference > 0 ? ('up' as const) : ('down' as const),
    };
  });
}

export function useHomeSummary() {
  const latestCompletedMonth = monthInfo(-1);
  const [data, setData] = useState<HomeSummaryData>({
    monthLabel: latestCompletedMonth.monthLabel,
    monthlyExpense: null,
    assetOverview: null,
    goals: [],
    briefing: null,
    topSpending: [],
  });
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const requestIdRef = useRef(0);

  const loadSummary = useCallback(async (refreshing = false) => {
    const requestId = ++requestIdRef.current;
    if (refreshing) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    setError(null);

    try {
      const month = monthInfo(-1);
      const previousMonth = monthInfo(-2);
      const results = await Promise.allSettled([
        ledgerApi.getMonthlySummary(month.yearMonth),
        assetApi.getAccountSummary(),
        loadGoals(),
        loadBriefing(),
        loadTopSpending(month.yearMonth, previousMonth.yearMonth),
      ]);
      if (requestId !== requestIdRef.current) return;

      const [monthlyResult, assetResult, goalsResult, briefingResult, topResult] = results;
      const failed = results.find((result) => result.status === 'rejected');
      setData({
        monthLabel: month.monthLabel,
        monthlyExpense: monthlyResult.status === 'fulfilled' ? monthlyResult.value.totalSpending : null,
        assetOverview: assetResult.status === 'fulfilled'
          ? mapOwnedAssetOverview(assetResult.value)
          : null,
        goals: goalsResult.status === 'fulfilled' ? goalsResult.value : [],
        briefing: briefingResult.status === 'fulfilled' ? briefingResult.value : null,
        topSpending: topResult.status === 'fulfilled' ? topResult.value : [],
      });
      setError(
        failed?.status === 'rejected'
          ? failed.reason instanceof Error
            ? failed.reason
            : new Error('홈 요약 일부를 불러오지 못했어요.')
          : null,
      );
    } finally {
      if (requestId === requestIdRef.current) {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    }
  }, []);

  useEffect(() => {
    const timeout = setTimeout(() => void loadSummary(), 0);
    return () => {
      clearTimeout(timeout);
      requestIdRef.current += 1;
    };
  }, [loadSummary]);

  return {
    ...data,
    error,
    isLoading,
    isRefreshing,
    refetch: useCallback(() => loadSummary(true), [loadSummary]),
  };
}
