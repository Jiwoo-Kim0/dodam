import { useCallback, useEffect, useRef, useState } from 'react';

import { assetApi, goalApi, isApiError, ledgerApi, safeboxApi } from '@/api';
import type { SafeboxAccountResponse, SafeboxAllocationResponse } from '@/api/safeboxApi';
import type { AccountApiResponse } from '@/types/assetApi.types';
import type { DailySpendingApiResponse } from '@/types/ledgerApi.types';
import type { GoalDetail, GoalHistoryPoint } from '@/types/goal.types';
import type {
  GoalAccountApiResponse,
  GoalHistoryApiResponse,
  GoalProgressApiResponse,
} from '@/types/goalApi.types';

interface GoalDetailData {
  goal: GoalDetail;
  linkedAccounts: GoalAccountApiResponse[];
  availableAccounts: AccountApiResponse[];
  safeboxAccount: SafeboxAccountResponse | null;
  safeboxGoal: SafeboxAllocationResponse | null;
}

async function loadSafeboxAccount() {
  try {
    return await safeboxApi.getAccount();
  } catch (error) {
    if (isApiError(error) && error.status === 404) return null;
    throw error;
  }
}

function formatDateLabel(date: string) {
  return `${date.replaceAll('-', '.')}까지`;
}

function getSavingAccent(daysDifference: number | null) {
  if (daysDifference === null) return '달성일을 계산 중이에요';
  if (daysDifference > 0) return `${daysDifference}일 늦어요`;
  if (daysDifference < 0) return `${Math.abs(daysDifference)}일 빨라요`;
  return '목표일에 맞춰요';
}

function toUtcTime(date: string) {
  const [year, month, day] = date.split('-').map(Number);
  return Date.UTC(year, month - 1, day);
}

function toHistoryLabel(date: string) {
  const [, month, day] = date.split('-').map(Number);
  return `${month}.${day}`;
}

function mapSavingHistory(
  history: readonly GoalHistoryApiResponse[],
  progress: GoalProgressApiResponse,
): GoalHistoryPoint[] {
  const latestByDate = new Map<string, GoalHistoryApiResponse>();
  history.forEach((item) => {
    if (!latestByDate.has(item.recordedDate)) {
      latestByDate.set(item.recordedDate, item);
    }
  });

  const startTime = toUtcTime(progress.startDate);
  const targetTime = toUtcTime(progress.targetDate);
  const totalDuration = Math.max(targetTime - startTime, 1);

  return [...latestByDate.values()]
    .sort((left, right) => left.recordedDate.localeCompare(right.recordedDate))
    .slice(-6)
    .map((item) => {
      const elapsed = Math.min(
        Math.max(toUtcTime(item.recordedDate) - startTime, 0),
        totalDuration,
      );

      return {
        label: toHistoryLabel(item.recordedDate),
        actualAmount: item.currentAmount,
        plannedAmount: Math.round(
          progress.targetAmount * (elapsed / totalDuration),
        ),
      };
    });
}

function mapSpendingHistory(
  daily: DailySpendingApiResponse,
  monthlyBudget: number,
  daysInMonth: number,
): GoalHistoryPoint[] {
  let cumulative = 0;

  return daily.days.reduce<GoalHistoryPoint[]>((points, day, index) => {
    cumulative += day.totalSpending;
    const dayOfMonth = Number(day.date.slice(-2));
    const isLast = index === daily.days.length - 1;
    const isWeekBoundary = dayOfMonth % 7 === 0;

    if (!isWeekBoundary && !isLast) return points;

    const week = Math.ceil(dayOfMonth / 7);
    points.push({
      label: isLast ? '현재' : `${week}주차`,
      tooltipLabel: isLast ? '현재까지' : `${week}주차 누적`,
      actualAmount: cumulative,
      plannedAmount: Math.round(monthlyBudget * (dayOfMonth / daysInMonth)),
    });
    return points;
  }, []);
}

async function loadGoalHistory(goalId: number) {
  try {
    return (await goalApi.getHistory(goalId, { size: 100 })).content;
  } catch {
    return [];
  }
}

export function useGoalDetail(goalIdValue: string) {
  const [data, setData] = useState<GoalDetailData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const requestIdRef = useRef(0);

  const load = useCallback(async (refreshing = false) => {
    const requestId = ++requestIdRef.current;
    const goalId = Number(goalIdValue);
    if (!Number.isSafeInteger(goalId) || goalId <= 0) {
      setData(null);
      setError(new Error('올바르지 않은 목표입니다.'));
      setIsLoading(false);
      setIsRefreshing(false);
      return;
    }

    if (refreshing) setIsRefreshing(true);
    else {
      setData(null);
      setIsLoading(true);
    }
    setError(null);

    try {
      const goalResponse = await goalApi.getGoal(goalId);

      if (goalResponse.type === 'SAVING') {
        const [
          progress,
          requiredSaving,
          forecast,
          risk,
          accounts,
          availableAccounts,
          history,
          safeboxAccount,
        ] = await Promise.all([
          goalApi.getProgress(goalId),
          goalApi.getRequiredSaving(goalId),
          goalApi.getForecast(goalId),
          goalApi.getRisk(goalId),
          goalApi.getGoalAccounts(goalId, { size: 100 }),
          assetApi.getAccounts(),
          loadGoalHistory(goalId),
          loadSafeboxAccount(),
        ]);
        const hasLegacyDemandAccount = accounts.accounts.some((account) =>
          account.accountType === 'DEMAND_DEPOSIT' &&
          account.accountId !== safeboxAccount?.accountId,
        );
        const safeboxGoal = safeboxAccount && !hasLegacyDemandAccount
          ? await safeboxApi.getGoal(goalId)
          : null;
        if (requestId !== requestIdRef.current) return;
        const monthlyShortfall = Math.max(requiredSaving.monthlyShortfall, 0);

        setData({
          goal: {
            id: String(goalResponse.goalId),
            type: 'saving',
            title: goalResponse.name,
            dueDateLabel: formatDateLabel(progress.targetDate),
            dDay: Math.max(progress.daysRemaining, 0),
            currentAmount: progress.currentAmount,
            targetAmount: progress.targetAmount,
            characterImage: require('@/assets/characters/dori/dori_saving_home.png'),
            statusAccentText: getSavingAccent(forecast.daysDifference),
            statusDescription:
              risk.riskLevel === 'NONE'
                ? '현재 계획대로 목표를 달성하고 있어요.'
                : monthlyShortfall > 0
                  ? `목표일에 맞추려면\n매달 ${monthlyShortfall.toLocaleString('ko-KR')}원 더 모아야 해요.`
                  : '현재 저축 흐름을 꾸준히 유지해 주세요.',
            expectedAchievementDate:
              forecast.expectedAchievementDate?.replaceAll('-', '.') ?? '계산 불가',
            monthlyRequiredAmount: requiredSaving.requiredMonthlySaving,
            history: mapSavingHistory(history, progress),
            linkedAccountIds: accounts.accounts.map(({ accountId }) => String(accountId)),
          },
          linkedAccounts: accounts.accounts,
          availableAccounts: availableAccounts.filter(
            ({ accountType, status }) =>
              accountType !== 'MINUS' && status !== 'CLOSED',
          ),
          safeboxAccount,
          safeboxGoal,
        });
        return;
      }

      const progressRequest = goalApi.getSpendingProgress(goalId);
      const [progress, risk, categories, daily] = await Promise.all([
        progressRequest,
        goalApi.getRisk(goalId),
        ledgerApi.getCategorySpending(),
        progressRequest.then((spendingProgress) =>
          ledgerApi.getDailySpending({
            startDate: `${spendingProgress.yearMonth}-01`,
            endDate: spendingProgress.asOfDate,
          }),
        ),
      ]);
      if (requestId !== requestIdRef.current) return;

      setData({
        goal: {
          id: String(goalResponse.goalId),
          type: 'reducing',
          title: goalResponse.name,
          dueDateLabel: `${progress.yearMonth.replace('-', '.')}월 목표`,
          dDay: progress.daysLeft,
          currentAmount: progress.spent,
          targetAmount: progress.monthlyBudget,
          characterImage: require('@/assets/characters/tori/tori_reducing_home.png'),
          statusAccentText: `${Math.max(progress.remainingBudget, 0).toLocaleString('ko-KR')}원`,
          statusDescription:
            risk.riskLevel === 'HIGH'
              ? '이번 달 목표 금액을 초과했어요.\n남은 소비 계획을 확인해 주세요.'
              : '앞으로 소비를 조금만 더 줄이면\n목표를 달성할 수 있어요.',
          expectedSavingAmount: progress.expectedSavingAmount,
          dailyBudget: progress.dailyBudgetRemaining ?? 0,
          history: mapSpendingHistory(
            daily,
            progress.monthlyBudget,
            progress.daysInMonth,
          ),
          categories: categories.categories.map((category) => ({
            id: category.code.toLowerCase(),
            label: category.name,
            amount: category.totalSpending,
          })),
        },
        linkedAccounts: [],
        availableAccounts: [],
        safeboxAccount: null,
        safeboxGoal: null,
      });
    } catch (loadError) {
      if (requestId !== requestIdRef.current) return;
      setData(null);
      setError(
        loadError instanceof Error
          ? loadError
          : new Error('목표를 불러오지 못했어요.'),
      );
    } finally {
      if (requestId === requestIdRef.current) {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    }
  }, [goalIdValue]);

  useEffect(() => {
    const timeout = setTimeout(() => void load(), 0);

    return () => {
      clearTimeout(timeout);
      requestIdRef.current += 1;
    };
  }, [load]);

  const refetch = useCallback(() => load(true), [load]);

  return {
    data,
    error,
    isLoading,
    isRefreshing,
    refetch,
  };
}
