import type { MonthlyReport } from '@/types/report.types';

/**
 * 월간 리포트 화면의 개발용 Mock 데이터이다.
 * 수치와 문구는 UI 디자인의 리포트 화면을 기준으로 한다.
 * 리포트 API(`/api/v1/reports/monthly`) 연결 시 이 데이터를 대체한다.
 */
export const MONTHLY_REPORT: MonthlyReport = {
  yearMonth: '2026-08',
  monthLabel: '8월',
  userName: '김싸피',
  efficiencyScore: {
    score: 77,
    maxScore: 100,
    previousMonthDifference: 5,
    percentile: 32,
    summary: '고정지출 관리와 저축 습관이 좋아지고 있어요',
    // 도넛 구성 비율은 디자인의 점수 그래프를 기준으로 한다.
    composition: [
      { id: 'percentile', tone: 'medium', ratio: 32 },
      { id: 'improved', tone: 'light', ratio: 22 },
      { id: 'maintained', tone: 'strong', ratio: 46 },
    ],
  },
  patterns: [
    {
      id: 'saving-habit',
      status: 'good',
      title: '월급 이후 저축 습관이 좋아요',
      description: '월급일 이후 3일 만에 평균 42만원을 저축하고 있어요',
      icon: require('@/assets/icons/common/report_piggy.png'),
    },
    {
      id: 'weekend-spending',
      status: 'check',
      title: '주말에 소비가 몰려 있어요',
      description: '외식비의 61%가 금, 토, 일에 발생했어요',
      icon: require('@/assets/icons/common/report_bag.png'),
    },
    {
      id: 'subscription-increase',
      status: 'check',
      title: '정기결제 금액이 늘어나고 있어요',
      description: '지난달보다 정기결제가 18,900원 증가했어요',
      icon: require('@/assets/icons/common/report_coins.png'),
    },
  ],
  patternCoachingMessage: '주말 외식비를 조금만 줄여도 한 달에 약 5만원을 아낄 수 있어요!',
  patternCoachingDisclaimer: null,
  patternCharacterImage: require('@/assets/characters/dori/dori_memo.png'),
  nextMonthForecast: {
    description: '최근 3개월의 소비 흐름과 다음 달 예정된 고정지출을 기준으로 예측했어요',
    expectedExpense: 1720000,
    differenceFromThisMonth: 84000,
    breakdown: [
      { id: 'fixed', label: '고정지출', amount: 620000, tone: 'fixed' },
      { id: 'living', label: '생활비', amount: 780000, tone: 'living' },
      { id: 'card', label: '카드 결제 예정', amount: 320000, tone: 'card' },
    ],
  },
  keyMetrics: [
    {
      id: 'saving-rate',
      label: '저축률',
      value: 28,
      format: 'percent',
      changeLabel: '지난달 대비 +4%p',
      tone: 'accent',
      icon: require('@/assets/icons/common/report_pig.png'),
      iconScale: 1.05,
    },
    {
      id: 'fixed-expense-ratio',
      label: '고정지출 비중',
      value: 32,
      format: 'percent',
      changeLabel: '지난달 대비 +2%p',
      tone: 'plain',
      icon: require('@/assets/icons/common/report_home.png'),
      iconScale: 0.95,
    },
    {
      id: 'card-usage',
      label: '카드 사용액',
      value: 320000,
      format: 'currency',
      changeLabel: '지난달 대비 -12%',
      tone: 'accent',
      icon: require('@/assets/icons/common/report_card.png'),
      iconScale: 1.4,
    },
    {
      id: 'loan-balance',
      label: '대출 잔액',
      value: 6000000,
      format: 'currency',
      changeLabel: '변동 없음',
      tone: 'plain',
      icon: require('@/assets/icons/common/report_money.png'),
      iconScale: 0.85,
    },
  ],
  // 8월 지출은 홈·가계부 Mock의 이번 달 지출(2,265,000원)과 같은 값을 쓴다.
  monthlySpending: [
    { yearMonth: '2026-03', monthLabel: '3월', amount: 1580000 },
    { yearMonth: '2026-04', monthLabel: '4월', amount: 1650000 },
    { yearMonth: '2026-05', monthLabel: '5월', amount: 1480000 },
    { yearMonth: '2026-06', monthLabel: '6월', amount: 1720000 },
    { yearMonth: '2026-07', monthLabel: '7월', amount: 1505000 },
    { yearMonth: '2026-08', monthLabel: '8월', amount: 2265000 },
  ],
};
