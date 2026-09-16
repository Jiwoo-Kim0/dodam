import type { ImageSourcePropType } from 'react-native';

import type { NotificationApiResponse } from '@/types/notificationApi.types';

const MINUTE_IN_MS = 60 * 1000;
const HOUR_IN_MS = 60 * MINUTE_IN_MS;
const DAY_IN_MS = 24 * HOUR_IN_MS;

const notificationIcons = {
  briefing: require('@/assets/icons/alarm/brifing.png'),
  danger: require('@/assets/icons/alarm/danger.png'),
  info: require('@/assets/icons/alarm/info.png'),
  pay: require('@/assets/icons/alarm/pay.png'),
  recommendation: require('@/assets/icons/alarm/recommand.png'),
  report: require('@/assets/icons/alarm/report.png'),
  result: require('@/assets/icons/alarm/result.png'),
  target: require('@/assets/icons/alarm/target.png'),
} satisfies Record<string, ImageSourcePropType>;

export function getNotificationIcon(type: string): ImageSourcePropType {
  switch (type) {
    case 'GOAL_PROGRESS':
    case 'GOAL_ACHIEVED':
      return notificationIcons.target;
    case 'RECOMMENDATION':
      return notificationIcons.recommendation;
    case 'IMPROVEMENT_RESULT':
      return notificationIcons.result;
    case 'MONTHLY_REPORT':
      return notificationIcons.report;
    case 'PAYMENT_DUE':
    case 'REPAYMENT_DUE':
      return notificationIcons.pay;
    case 'OVERDUE_RISK':
    case 'CASHFLOW_RISK':
      return notificationIcons.danger;
    case 'DAILY_BRIEFING':
      return notificationIcons.briefing;
    case 'SYSTEM':
    default:
      return notificationIcons.info;
  }
}

export function getNotificationCategoryLabel(type: string): string {
  switch (type) {
    case 'PAYMENT_DUE':
    case 'REPAYMENT_DUE':
      return '결제·상환';
    case 'OVERDUE_RISK':
    case 'CASHFLOW_RISK':
      return '위험 안내';
    case 'GOAL_PROGRESS':
    case 'GOAL_ACHIEVED':
      return '목표';
    case 'RECOMMENDATION':
      return '추천';
    case 'IMPROVEMENT_RESULT':
      return '개선 결과';
    case 'MONTHLY_REPORT':
      return '월간 리포트';
    case 'DAILY_BRIEFING':
      return '오늘의 브리핑';
    case 'SYSTEM':
    default:
      return '안내';
  }
}

export function formatNotificationTime(value: string, now = new Date()): string {
  const createdAt = new Date(value);
  const difference = now.getTime() - createdAt.getTime();

  if (Number.isNaN(createdAt.getTime())) return '';
  if (difference < MINUTE_IN_MS) return '방금 전';
  if (difference < HOUR_IN_MS) return `${Math.floor(difference / MINUTE_IN_MS)}분 전`;
  if (difference < DAY_IN_MS) return `${Math.floor(difference / HOUR_IN_MS)}시간 전`;
  if (difference < DAY_IN_MS * 7) return `${Math.floor(difference / DAY_IN_MS)}일 전`;

  const includeYear = createdAt.getFullYear() !== now.getFullYear();
  return new Intl.DateTimeFormat('ko-KR', {
    ...(includeYear ? { year: 'numeric' as const } : {}),
    month: 'long',
    day: 'numeric',
  }).format(createdAt);
}

export function formatNotificationDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date);
}

export function hasNotificationDestination(
  notification: NotificationApiResponse,
): boolean {
  if (notification.type === 'DAILY_BRIEFING' || notification.type === 'MONTHLY_REPORT') {
    return true;
  }

  switch (notification.sourceType) {
    case 'ACCOUNT':
    case 'MINUS_ACCOUNT':
    case 'CARD':
    case 'INSTALLMENT':
    case 'LOAN':
    case 'DEPOSIT_SAVING':
    case 'SUBSCRIPTION':
    case 'DIAGNOSIS':
    case 'RECOMMENDATION':
    case 'CASHFLOW_FORECAST':
    case 'SIMULATION':
    case 'PATTERN_ANALYSIS':
    case 'REPORT':
      return true;
    case 'GOAL':
      return notification.sourceId !== null;
    default:
      return false;
  }
}
