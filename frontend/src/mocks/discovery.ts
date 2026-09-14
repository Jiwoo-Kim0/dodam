import type { ImageSourcePropType } from 'react-native';

export interface DiscoveryInsight {
  id: string;
  rank: number;
  category: string;
  title: string;
  detailedTitle: string;
  description: string;
  annualSavingAmount: number;
  goalAccelerationDays: number;
  evidence: string;
  recommendationSummary: string;
  recommendations: readonly DiscoveryRecommendation[];
  resultNotice: string;
  icon: ImageSourcePropType;
}

export interface DiscoveryRecommendation {
  title: string;
  detail: string;
  recommended?: boolean;
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
    goalAccelerationDays: 35,
    evidence: '최근 3개월 동안 배달·외식비가 월평균 35,000원씩 늘고 있어요.',
    recommendationSummary: '평소 소비 흐름을 유지하면서도 자주 새는 지출부터 줄일 수 있어요.',
    recommendations: [
      {
        title: '주 2회 배달 없는 날',
        detail: '한 달에 약 24만원을 아낄 수 있어요',
        recommended: true,
      },
      {
        title: '배달 멤버십 정리',
        detail: '이용 빈도가 낮은 멤버십부터 해지해요',
      },
      {
        title: '외식 예산 알림 설정',
        detail: '월 35만원을 넘기기 전에 알려드려요',
      },
    ],
    resultNotice: '최근 소비 패턴을 기준으로 계산했어요. 무리하게 줄이기보다 주 2회부터 시작해보세요.',
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
    goalAccelerationDays: 21,
    evidence: '비슷한 조건의 대출보다 적용 금리가 약 1.5%p 높아요.',
    recommendationSummary: '중도상환수수료와 한도를 함께 비교하면 더 유리한 조건을 찾을 수 있어요.',
    recommendations: [
      {
        title: '낮은 금리 대출로 갈아타기',
        detail: '예상 금리 연 3.8% · 한도와 수수료 확인 필요',
        recommended: true,
      },
      {
        title: '금리인하요구권 신청',
        detail: '소득이나 신용점수가 올랐다면 신청해보세요',
      },
      {
        title: '일부 금액 먼저 상환',
        detail: '여유 자금 범위에서 원금을 줄여요',
      },
    ],
    resultNotice: '실제 금리와 절감액은 심사 결과 및 중도상환수수료에 따라 달라질 수 있어요.',
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
    goalAccelerationDays: 18,
    evidence: '최근 두 달 동안 사용 기록이 없는 구독 3건에서 결제가 이어지고 있어요.',
    recommendationSummary: '사용하지 않는 구독만 정리해도 생활의 불편 없이 고정지출을 줄일 수 있어요.',
    recommendations: [
      {
        title: '미사용 구독 3건 해지',
        detail: '다음 결제일 전에 한 번에 정리해요',
        recommended: true,
      },
      {
        title: '가족 공유 요금제로 변경',
        detail: '계속 쓸 서비스는 함께 이용해요',
      },
      {
        title: '결제 전 알림 받기',
        detail: '자동 결제 3일 전에 다시 확인해요',
      },
    ],
    resultNotice: '업무나 학습에 필요한 서비스인지 확인한 뒤 해지하는 것을 권해요.',
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
    goalAccelerationDays: 9,
    evidence: '최근 소비를 보면 240만원은 당장 쓰지 않아도 될 돈으로 보여요.',
    recommendationSummary: '생활비와 비상금을 남겨두고 여유 자금만 옮기면 이자를 더 받을 수 있어요.',
    recommendations: [
      {
        title: '파킹통장 · 연 2.9%',
        detail: '하루만 넣어도 이자가 붙고 언제든 출금',
        recommended: true,
      },
      {
        title: '6개월 정기예금 · 연 3.4%',
        detail: '240만원 기준 예상 이자 40,800원 · 중도해지 0.5%',
      },
      {
        title: '청년도약계좌 납입 늘리기',
        detail: '월 40만원에서 70만원으로 늘려요',
      },
    ],
    resultNotice: '비상금 80만원은 통장에 그대로 두는 걸 권해요. 최근 지출 변동도 함께 고려했어요.',
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
