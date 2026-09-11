import type { GoalDetail } from '@/types/goal.types';

export const MOCK_GOAL_DETAILS: readonly GoalDetail[] = [
  {
    id: 'saving-emergency-fund',
    type: 'saving',
    title: '비상금 모으기',
    dueDateLabel: '2027.01.01까지',
    dDay: 159,
    currentAmount: 500000,
    targetAmount: 3000000,
    characterImage: require('@/assets/characters/dori/dori_saving_home.png'),
    statusAccentText: '35일 늦어요',
    statusDescription: '목표일에 맞추려면\n매달 134,000원 더 모아야 해요.',
    expectedAchievementDate: '2027.02.05',
    monthlyRequiredAmount: 604000,
    history: [
      { label: '1개월', actualAmount: 80000, plannedAmount: 20000 },
      { label: '2개월', actualAmount: 110000, plannedAmount: 20000 },
      { label: '3개월', actualAmount: 110000, plannedAmount: 20000 },
      { label: '4개월', actualAmount: 190000, plannedAmount: 40000 },
      { label: '현재', actualAmount: 500000, plannedAmount: 60000 },
    ],
    linkedAccountIds: ['goal-account-shinhan', 'goal-account-nonghyup'],
  },
  {
    id: 'reducing-spending',
    type: 'reducing',
    title: '지출 줄이기',
    dueDateLabel: '2027.01.01까지',
    dDay: 13,
    currentAmount: 148000,
    targetAmount: 300000,
    characterImage: require('@/assets/characters/tori/tori_reducing_home.png'),
    statusAccentText: '60,000원',
    statusDescription: '앞으로 소비를 조금만 더 줄이면\n목표를 달성할 수 있어요.',
    expectedSavingAmount: 240000,
    dailyBudget: 8000,
    history: [
      { label: '1주차', actualAmount: 70000, plannedAmount: 40000 },
      { label: '2주차', actualAmount: 90000, plannedAmount: 60000 },
      { label: '3주차', actualAmount: 110000, plannedAmount: 90000 },
      { label: '현재', actualAmount: 148000, plannedAmount: 180000 },
    ],
    categories: [
      { id: 'shopping', label: '쇼핑', amount: 67000 },
      { id: 'coffee', label: '커피', amount: 45000 },
      { id: 'transportation', label: '교통', amount: 32000 },
      { id: 'leisure', label: '여가', amount: 4000 },
    ],
  },
];

export function findGoalDetail(goalId: string) {
  return MOCK_GOAL_DETAILS.find(({ id }) => id === goalId);
}
