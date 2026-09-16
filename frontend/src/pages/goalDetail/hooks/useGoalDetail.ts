import { useCallback, useEffect, useState } from 'react';

import { assetApi, goalApi, ledgerApi } from '@/api';
import type { AccountApiResponse } from '@/types/assetApi.types';
import type { GoalAccountApiResponse } from '@/types/goalApi.types';
import type { GoalDetail } from '@/types/goal.types';

interface GoalDetailData {
  goal: GoalDetail;
  linkedAccounts: GoalAccountApiResponse[];
  availableAccounts: AccountApiResponse[];
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

export function useGoalDetail(goalIdValue: string) {
  const [data, setData] = useState<GoalDetailData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async (refreshing = false) => {
    const goalId = Number(goalIdValue);
    if (!Number.isSafeInteger(goalId) || goalId <= 0) {
      setError(new Error('올바르지 않은 목표입니다.'));
      setIsLoading(false);
      return;
    }

    if (refreshing) setIsRefreshing(true);
    else setIsLoading(true);
    setError(null);

    try {
      const goalResponse = await goalApi.getGoal(goalId);

      if (goalResponse.type === 'SAVING') {
        const [progress, requiredSaving, forecast, risk, accounts, availableAccounts] =
          await Promise.all([
            goalApi.getProgress(goalId),
            goalApi.getRequiredSaving(goalId),
            goalApi.getForecast(goalId),
            goalApi.getRisk(goalId),
            goalApi.getGoalAccounts(goalId, { size: 100 }),
            assetApi.getAccounts(),
          ]);
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
            history: [],
            linkedAccountIds: accounts.accounts.map(({ accountId }) => String(accountId)),
          },
          linkedAccounts: accounts.accounts,
          availableAccounts: availableAccounts.filter(
            ({ accountType, status }) =>
              accountType !== 'MINUS' && status !== 'CLOSED',
          ),
        });
        return;
      }

      const [progress, risk, categories] = await Promise.all([
        goalApi.getSpendingProgress(goalId),
        goalApi.getRisk(goalId),
        ledgerApi.getCategorySpending(),
      ]);

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
          history: [],
          categories: categories.categories.map((category) => ({
            id: category.code.toLowerCase(),
            label: category.name,
            amount: category.totalSpending,
          })),
        },
        linkedAccounts: [],
        availableAccounts: [],
      });
    } catch (loadError) {
      setData(null);
      setError(
        loadError instanceof Error
          ? loadError
          : new Error('목표를 불러오지 못했어요.'),
      );
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [goalIdValue]);

  useEffect(() => {
    const timeout = setTimeout(() => void load(), 0);

    return () => clearTimeout(timeout);
  }, [load]);

  return {
    data,
    error,
    isLoading,
    isRefreshing,
    refetch: () => load(true),
  };
}
