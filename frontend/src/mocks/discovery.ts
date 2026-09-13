import type { ImageSourcePropType } from 'react-native';

export interface DiscoveryInsight {
  id: string;
  rank: number;
  category: string;
  title: string;
  detailedTitle: string;
  description: string;
  annualSavingAmount: number;
  icon: ImageSourcePropType;
}

export const DISCOVERY_INSIGHTS: readonly DiscoveryInsight[] = [
  {
    id: 'delivery-dining',
    rank: 1,
    category: '소비',
    title: '배달 · 외식이 3개월째 늘고 있어요',
    detailedTitle: '배달 · 외식이 3개월째 늘고 있어요',
    description: '최근 3개월 평균보다 42% 높아요',
    annualSavingAmount: 420000,
    icon: require('@/assets/icons/common/coffee.png'),
  },
  {
    id: 'credit-loan-rate',
    rank: 2,
    category: '대출',
    title: '첫 신용대출 금리가 높아요',
    detailedTitle: '첫 신용대출 금리가 1.5%p 높아요',
    description: '비교하면 더 낮은 금리로 갈아탈 수 있어요',
    annualSavingAmount: 270000,
    icon: require('@/assets/icons/common/wallet.png'),
  },
  {
    id: 'unused-subscription',
    rank: 3,
    category: '구독',
    title: '두 달 넘게 안 쓴 구독이 있어요',
    detailedTitle: '두 달 넘게 안 쓴 구독 3건이 결제 중',
    description: '3건이 계속 결제되고 있어요',
    annualSavingAmount: 232000,
    icon: require('@/assets/icons/common/calendar.png'),
  },
  {
    id: 'idle-funds',
    rank: 4,
    category: '유휴자금',
    title: '320만원이 낮은 금리 통장에 있어요',
    detailedTitle: '320만원이 4개월째 연 0.1% 통장에 있어요',
    description: '4개월째 그대로 머물고 있어요',
    annualSavingAmount: 62000,
    icon: require('@/assets/icons/common/light.png'),
  },
] as const;

export const DISCOVERY_SUMMARY = {
  userName: '김싸피',
  totalAnnualSavingAmount: DISCOVERY_INSIGHTS.reduce(
    (total, insight) => total + insight.annualSavingAmount,
    0,
  ),
  goalAccelerationDays: 41,
  analysisPeriodLabel: '8월 1일 ~ 8월 31일',
} as const;
