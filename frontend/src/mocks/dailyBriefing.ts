import type { ImageSourcePropType } from 'react-native';

export interface DailyBriefingTransaction {
  id: string;
  time: string;
  merchant: string;
  category: string;
  amount: number;
  icon: ImageSourcePropType;
  iconScale: number;
}

export interface DailyBriefingInsight {
  id: string;
  type: 'decrease' | 'increase';
  label: string;
  title: string;
  description: string;
  icon: ImageSourcePropType;
}

export interface DailyBriefingGoalImpact {
  id: string;
  label: string;
  value: string;
  icon: ImageSourcePropType;
}

export const DAILY_BRIEFING_TRANSACTIONS: readonly DailyBriefingTransaction[] = [
  {
    id: 'cu',
    time: '08 : 36',
    merchant: 'CU 편의점',
    category: '생활 · 마트',
    amount: 4500,
    icon: require('@/assets/icons/payments/cu 1.png'),
    iconScale: 1.2,
  },
  {
    id: 'restaurant',
    time: '12 : 08',
    merchant: '기사식당',
    category: '외식',
    amount: 8300,
    icon: require('@/assets/icons/payments/meal.png'),
    iconScale: 1,
  },
  {
    id: 'starbucks',
    time: '13 : 10',
    merchant: '스타벅스',
    category: '카페',
    amount: 4900,
    icon: require('@/assets/icons/payments/starbucks 1.png'),
    iconScale: 1.2,
  },
  {
    id: 'emart',
    time: '18 : 43',
    merchant: '이마트',
    category: '생활 · 마트',
    amount: 5600,
    icon: require('@/assets/icons/payments/emart 1.png'),
    iconScale: 1.2,
  },
  {
    id: 'taxi',
    time: '19 : 30',
    merchant: '개인택시',
    category: '교통',
    amount: 7600,
    icon: require('@/assets/icons/payments/sol_taxi.png'),
    iconScale: 1,
  },
  {
    id: 'daiso',
    time: '20 : 15',
    merchant: '다이소',
    category: '생활 · 마트',
    amount: 7600,
    icon: require('@/assets/icons/payments/daiso 1.png'),
    iconScale: 1.2,
  },
];

export const DAILY_BRIEFING_INSIGHTS: readonly DailyBriefingInsight[] = [
  {
    id: 'delivery',
    type: 'decrease',
    label: '소비 감소',
    title: '배달비를 쓰지 않았어요',
    description: '최근 평균보다 12,000원 적게 썼어요',
    icon: require('@/assets/icons/category/delivery_icon.png'),
  },
  {
    id: 'mart',
    type: 'increase',
    label: '소비 증가',
    title: '마트지출이 평소보다 늘었어요',
    description: '최근 평균보다 6,500원 많아요',
    icon: require('@/assets/icons/category/Mart_icon.png'),
  },
];

export const DAILY_BRIEFING_GOAL_IMPACTS: readonly DailyBriefingGoalImpact[] = [
  {
    id: 'saving',
    label: '이번달 누적 절약',
    value: '84,500원',
    icon: require('@/assets/icons/common/money.png'),
  },
  {
    id: 'date',
    label: '예상 목표 달성일',
    value: '2027.01.24',
    icon: require('@/assets/icons/common/calendar.png'),
  },
  {
    id: 'change',
    label: '지난주보다',
    value: '5일 빨라졌어요',
    icon: require('@/assets/icons/common/clock.png'),
  },
];

export const DAILY_BRIEFING = {
  yesterdaySpentAmount: DAILY_BRIEFING_TRANSACTIONS.reduce(
    (total, transaction) => total + transaction.amount,
    0,
  ),
  previousMonthDailyAverage: 52000,
  upcomingEvent: {
    title: '3일 뒤 적금 500,000원이 출금돼요',
    description: '예상 잔액으로 문제없이 납부 가능해요',
  },
  streaks: [
    { id: 'no-delivery', label: '배달 없는 날', value: '🔥 연속 4일' },
    { id: 'under-budget', label: '예산 안넘긴 날', value: '🔥 연속 2일' },
  ],
} as const;
