import type { HomeGoal } from '@/types/goal.types';

export const HOME_GOALS: readonly HomeGoal[] = [
  {
    id: 'saving-emergency-fund',
    type: 'saving',
    title: '비상금 모으기',
    dueDateLabel: '2027.01.01까지',
    dDay: 159,
    currentAmount: 500000,
    targetAmount: 3000000,
    message: '지금 속도라면 목표보다\n35일 늦게 달성해요.',
    characterImage: require('@/assets/characters/dori/dori_saving_home.png'),
  },
  {
    id: 'reducing-spending',
    type: 'reducing',
    title: '지출 줄이기',
    dueDateLabel: '2027.01.01까지',
    dDay: 13,
    currentAmount: 148000,
    targetAmount: 300000,
    message: '작은 지출도 모이면\n큰 돈이 돼요!',
    characterImage: require('@/assets/characters/tori/tori_reducing_home.png'),
  },
];

export const HOME_BRIEFING = {
  yesterdaySpentAmount: 38500,
  message: '3일 뒤 적금 500,000원이 출금돼요.',
};

export interface SpendingCategory {
  rank: number;
  label: string;
  amount: number;
  changePercent: number;
  direction: 'up' | 'down';
}

export const HOME_TOP_SPENDING: readonly SpendingCategory[] = [
  { rank: 1, label: '식비', amount: 420000, changePercent: 8, direction: 'down' },
  { rank: 2, label: '쇼핑', amount: 280000, changePercent: 12, direction: 'down' },
  { rank: 3, label: '교통', amount: 165000, changePercent: 10, direction: 'up' },
];
