import type { ImageSourcePropType } from 'react-native';

import type {
  DiagnosisIssueApiResponse,
  RecommendationApiResponse,
} from '@/types/discoveryApi.types';

export interface DiscoveryInsight {
  id: string;
  rank: number;
  category: string;
  title: string;
  detailedTitle: string;
  description: string;
  annualSavingAmount: number | null;
  goalAccelerationDays: number | null;
  icon: ImageSourcePropType;
}

const DOMAIN_LABELS: Record<string, string> = {
  CASHFLOW: '현금흐름',
  GOAL: '목표',
  LOAN: '대출',
  SPENDING: '소비',
};

export function discoveryDomainLabel(domain: string) {
  return DOMAIN_LABELS[domain] ?? domain;
}

const DOMAIN_ICONS: Record<string, ImageSourcePropType> = {
  CASHFLOW: require('@/assets/icons/common/wallet.png'),
  GOAL: require('@/assets/icons/common/light.png'),
  LOAN: require('@/assets/icons/common/wallet.png'),
  SPENDING: require('@/assets/icons/common/coffee.png'),
};

export function issueRouteId(diagnosisId: number, issueId: number) {
  return `${diagnosisId}-${issueId}`;
}

export function parseIssueRouteId(value?: string) {
  const match = /^(\d+)-(\d+)$/.exec(value ?? '');
  if (!match) return null;
  const diagnosisId = Number(match[1]);
  const issueId = Number(match[2]);
  if (!Number.isSafeInteger(diagnosisId) || !Number.isSafeInteger(issueId)) return null;
  if (diagnosisId < 1 || issueId < 1) return null;
  return { diagnosisId, issueId };
}

export function recommendationsForIssue(
  recommendations: readonly RecommendationApiResponse[],
  issue: DiagnosisIssueApiResponse,
) {
  return recommendations
    .filter(
      (item) =>
        item.diagnosisId === issue.diagnosisId &&
        item.sourceIssueId === issue.issueId,
    )
    .sort((a, b) => a.priority - b.priority);
}

export function toDiscoveryInsight(
  issue: DiagnosisIssueApiResponse,
  recommendations: readonly RecommendationApiResponse[],
  rank: number,
): DiscoveryInsight {
  const matching = recommendationsForIssue(recommendations, issue);
  // 대안 추천은 서로 합산하지 않는다. 가장 큰 단일 예상 효과만 표시한다.
  const annualSavingAmount = matching.reduce<number | null>((largest, item) => {
    const amount = item.effect.annualCostSaving;
    return amount === null ? largest : Math.max(largest ?? 0, amount);
  }, null);
  const goalAccelerationDays = matching.reduce<number | null>((largest, item) => {
    const days = item.effect.advancedDays;
    return days === null ? largest : Math.max(largest ?? 0, days);
  }, null);

  return {
    id: issueRouteId(issue.diagnosisId, issue.issueId),
    rank,
    category: discoveryDomainLabel(issue.domain),
    title: issue.title,
    detailedTitle: issue.title,
    description: issue.description,
    annualSavingAmount,
    goalAccelerationDays,
    icon: DOMAIN_ICONS[issue.domain] ?? DOMAIN_ICONS.GOAL,
  };
}
