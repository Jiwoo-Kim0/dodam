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

/**
 * 알림 1건이 이어지는 화면. 원천 규약은 backend/docs/notification.md를 따른다.
 *
 * 경로 상수를 여기서 참조하지 않고 종류만 돌려준다 — utils가 라우팅을 모르게 두고,
 * 바로가기 버튼 노출 여부와 실제 이동이 같은 분기 하나를 쓰게 하려는 것이다.
 * 둘이 갈라지면 버튼은 보이는데 눌러도 아무 일이 없는 상태가 된다.
 */
export type NotificationDestination =
  | { kind: 'dailyBriefing' }
  | { kind: 'report' }
  | { kind: 'goalDetail'; goalId: string }
  /** 추천 ID로 원천 진단 문제를 한 번 더 조회해야 상세 화면을 열 수 있다. */
  | { kind: 'recommendation'; recommendationId: string }
  | { kind: 'discoveryInsights' }
  | { kind: 'assets' };

export function getNotificationDestination(
  notification: NotificationApiResponse,
): NotificationDestination | null {
  const { sourceId, sourceType, type } = notification;

  if (type === 'DAILY_BRIEFING') return { kind: 'dailyBriefing' };
  if (type === 'MONTHLY_REPORT' || sourceType === 'REPORT') {
    return { kind: 'report' };
  }

  switch (sourceType) {
    case 'GOAL':
      return sourceId ? { kind: 'goalDetail', goalId: sourceId } : null;
    case 'RECOMMENDATION':
      // 원천 ID가 없으면 특정할 대상이 없으므로 발견 목록까지만 안내한다.
      return sourceId
        ? { kind: 'recommendation', recommendationId: sourceId }
        : { kind: 'discoveryInsights' };
    case 'DIAGNOSIS':
    case 'PATTERN_ANALYSIS':
    case 'SIMULATION':
      // 발견 탭 첫 화면은 진단 소개 랜딩이라 알림 목적지로 쓸 수 없다.
      return { kind: 'discoveryInsights' };
    case 'CASHFLOW_FORECAST':
      return { kind: 'report' };
    case 'ACCOUNT':
    case 'MINUS_ACCOUNT':
    case 'CARD':
    case 'INSTALLMENT':
    case 'LOAN':
    case 'DEPOSIT_SAVING':
    case 'SUBSCRIPTION':
      return { kind: 'assets' };
    default:
      return null;
  }
}

export function hasNotificationDestination(
  notification: NotificationApiResponse,
): boolean {
  return getNotificationDestination(notification) !== null;
}
