import { useCallback, useEffect, useState } from 'react';

import { goalApi } from '@/api';
import type { HomeGoal } from '@/types/goal.types';
import type { GoalApiResponse } from '@/types/goalApi.types';
import { formatCurrency } from '@/utils/currency';

function mapSavingGoal(goal: GoalApiResponse): HomeGoal {
  const progress = Math.round(goal.progressRatePercent ?? 0);

  return {
    id: String(goal.goalId),
    type: 'saving',
    title: goal.name,
    dueDateLabel: goal.targetDate
      ? `${goal.targetDate.replaceAll('-', '.')}까지`
      : '기한 없음',
    dDay: Math.max(0, goal.daysRemaining ?? 0),
    currentAmount: goal.currentAmount,
    targetAmount: goal.targetAmount ?? 0,
    message:
      progress >= 100
        ? '목표를 달성했어요!'
        : `현재 목표의 ${progress}%를 모았어요.`,
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

export function useOtherGoals(visible: boolean) {
  const [goals, setGoals] = useState<HomeGoal[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const response = await goalApi.getGoals({ status: 'ACTIVE', size: 100 });
      const mappedGoals = await Promise.all(
        response.content.map((goal) =>
          goal.type === 'SPENDING' ? mapSpendingGoal(goal) : mapSavingGoal(goal),
        ),
      );
      setGoals(mappedGoals);
    } catch (loadError) {
      setGoals([]);
      setError(
        loadError instanceof Error
          ? loadError
          : new Error('목표 목록을 불러오지 못했어요.'),
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!visible) return;

    const timeout = setTimeout(() => void load(), 0);
    return () => clearTimeout(timeout);
  }, [load, visible]);

  return { error, goals, isLoading, refetch: load };
}
