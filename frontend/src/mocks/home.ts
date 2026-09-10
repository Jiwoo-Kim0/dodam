import type { ImageSourcePropType } from 'react-native';

import type { AssetComposition } from '@/types/asset.types';

export type GoalType = 'saving' | 'reducing';

export interface HomeGoal {
  id: string;
  type: GoalType;
  title: string;
  dueDateLabel: string;
  dDay: number;
  currentAmount: number;
  targetAmount: number;
  message: string;
  characterImage: ImageSourcePropType;
}

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
    dDay: 120,
    currentAmount: 120000,
    targetAmount: 300000,
    message: '작은 지출도 모이면\n큰 돈이 돼요!',
    characterImage: require('@/assets/characters/tori/tori_reducing_home.png'),
  },
];

export const HOME_SUMMARY = {
  monthLabel: '8월',
  monthlyExpense: 2265000,
  totalAsset: 8795830,
  assetChangeFromLastMonth: 234000,
  assetComposition: [
    { label: '예·적금', amount: 6200000, tone: 'strong' },
    { label: '입출금', amount: 1595830, tone: 'medium' },
    { label: '대출', amount: 1000000, tone: 'light' },
  ] as const satisfies readonly AssetComposition[],
};

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
