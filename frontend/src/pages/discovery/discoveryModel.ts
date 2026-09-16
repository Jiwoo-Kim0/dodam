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

export function isCategorySpendingIssue(issue: DiagnosisIssueApiResponse) {
  return issue.domain === 'SPENDING' && issue.type === 'CATEGORY_SPENDING_INCREASE';
}

/** 묶어서 보여주는 카테고리 추천 수. 목록의 개수·금액과 상세의 카드 수가 이 값을 함께 따른다. */
export const CATEGORY_SPENDING_DISPLAY_LIMIT = 3;

const bySaving = (a: RecommendationApiResponse, b: RecommendationApiResponse) =>
  (b.effect.monthlyCostSaving ?? -1) - (a.effect.monthlyCostSaving ?? -1) ||
  a.priority - b.priority;

/** 같은 진단의 카테고리별 소비 추천을 문제당 하나씩, 절감액이 큰 순으로 모은다. */
function categorySpendingRecommendations(
  recommendations: readonly RecommendationApiResponse[],
  issues: readonly DiagnosisIssueApiResponse[],
  diagnosisId: number,
) {
  const categoryIssueIds = new Set(
    issues
      .filter(
        (issue) => issue.diagnosisId === diagnosisId && isCategorySpendingIssue(issue),
      )
      .map((issue) => issue.issueId),
  );
  const seenIssues = new Set<number>();
  return recommendations
    .filter(
      (item) =>
        item.diagnosisId === diagnosisId &&
        item.type === 'REDUCE_CATEGORY_SPENDING' &&
        item.sourceIssueId !== null &&
        categoryIssueIds.has(item.sourceIssueId),
    )
    .sort(bySaving)
    .filter((item) => {
      if (item.sourceIssueId === null || seenIssues.has(item.sourceIssueId)) return false;
      seenIssues.add(item.sourceIssueId);
      return true;
    });
}

/** 소비 증가 상세에서는 같은 진단의 카테고리별 추천을 함께 보여준다. */
export function recommendationsForDetail(
  recommendations: readonly RecommendationApiResponse[],
  issues: readonly DiagnosisIssueApiResponse[],
  selectedIssue: DiagnosisIssueApiResponse,
) {
  if (!isCategorySpendingIssue(selectedIssue)) {
    return recommendationsForIssue(recommendations, selectedIssue);
  }

  // 묶이지 않은 문제로 직접 들어올 수도 있다. 선택한 문제는 잘리지 않게 앞에 세운다.
  const categoryRecommendations = categorySpendingRecommendations(
    recommendations,
    [...issues, selectedIssue],
    selectedIssue.diagnosisId,
  );
  const selected = categoryRecommendations.find(
    (item) => item.sourceIssueId === selectedIssue.issueId,
  );
  const others = categoryRecommendations.filter((item) => item !== selected);
  return [...(selected ? [selected] : []), ...others]
    .slice(0, CATEGORY_SPENDING_DISPLAY_LIMIT)
    .sort(bySaving);
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

/** 카테고리별 소비 증가 문제는 목록에서 하나의 발견으로 묶는다. */
export function toDiscoveryInsights(
  issues: readonly DiagnosisIssueApiResponse[],
  recommendations: readonly RecommendationApiResponse[],
) {
  const categoryIssues = issues.filter(isCategorySpendingIssue);
  const otherInsights = issues
    .filter((issue) => !categoryIssues.includes(issue))
    .map((issue) => toDiscoveryInsight(issue, recommendations, 0));
  const ungrouped = () => [
    ...otherInsights,
    ...categoryIssues.map((issue) => toDiscoveryInsight(issue, recommendations, 0)),
  ];

  if (categoryIssues.length <= 1) return ungrouped();

  // 상세와 같은 선별을 써야 목록의 개수·금액이 상세 화면과 어긋나지 않는다.
  const shown = categorySpendingRecommendations(
    recommendations,
    categoryIssues,
    categoryIssues[0].diagnosisId,
  ).slice(0, CATEGORY_SPENDING_DISPLAY_LIMIT);
  if (shown.length <= 1) return ungrouped();

  // 상세로 이동할 대표 문제는 상세에서 첫 카드로 서는 추천의 출처다.
  const representativeIssue =
    categoryIssues.find((issue) => issue.issueId === shown[0].sourceIssueId) ??
    categoryIssues[0];
  // 카테고리끼리는 서로 대안이 아니라 각각 줄일 수 있는 항목이라 합산한다.
  const annualSavingAmount = shown.reduce<number | null>((total, item) => {
    const amount = item.effect.annualCostSaving;
    return amount === null ? total : (total ?? 0) + amount;
  }, null);
  const groupTitle = `소비가 늘어난 항목 ${shown.length}개`;

  return [
    ...otherInsights,
    {
      ...toDiscoveryInsight(representativeIssue, recommendations, 0),
      title: groupTitle,
      detailedTitle: groupTitle,
      description: '카테고리별 소비 변화와 줄일 수 있는 방법을 확인해 보세요.',
      annualSavingAmount,
    },
  ];
}
