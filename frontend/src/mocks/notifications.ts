import type { ImageSourcePropType } from 'react-native';

export interface NotificationItem {
  id: string;
  title: string;
  description: string;
  timeLabel: string;
  icon: ImageSourcePropType;
}

export const NOTIFICATIONS: readonly NotificationItem[] = [
  {
    id: 'saving-maturity',
    title: '적금 만기가 46일 남았어요',
    description: '만기 후 갈아탈 상품을 미리 봐볼까요?',
    timeLabel: '1일 전',
    icon: require('@/assets/icons/alarm/check_something.png'),
  },
  {
    id: 'goal-progress',
    title: '이번 달 모으기 목표를 채웠어요',
    description: '9월에만 30만원을 모았어요',
    timeLabel: '2일 전',
    icon: require('@/assets/icons/alarm/money_grow.png'),
  },
  {
    id: 'spending-alert',
    title: '어제 카페 지출이 많았어요',
    description: '하루 평균보다 8,200원 더 썼어요',
    timeLabel: '3일 전',
    icon: require('@/assets/icons/alarm/spend_money.png'),
  },
  {
    id: 'new-discovery',
    title: '새로 발견한 내용이 2개 있어요',
    description: '구독만 정리해도 연 84,000원 아껴요',
    timeLabel: '5일 전',
    icon: require('@/assets/icons/alarm/new_thing.png'),
  },
  {
    id: 'monthly-report',
    title: '8월 금융 리포트가 도착했어요',
    description: '효율 점수가 지난달보다 8점 올랐어요',
    timeLabel: '1주 전',
    icon: require('@/assets/icons/alarm/something_better.png'),
  },
];
