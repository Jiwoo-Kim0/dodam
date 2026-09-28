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
  /** 군집 이탈이 근거에 섞였는지. 목록에서 "또래 비교" 배지를 띄울지 정한다. */
  hasClusterEvidence: boolean;
}

/**
 * 641 이전 완료본에는 `detectionSource`가 없다. 없으면 규칙 진단으로 보고 배지를 띄우지 않는다.
 * `clusterContext`까지 있어야 상세 카드를 그릴 수 있어서 둘을 함께 본다.
 */
export function hasClusterEvidence(issue: DiagnosisIssueApiResponse) {
  return (
    (issue.detectionSource === 'CLUSTER' || issue.detectionSource === 'RULE_AND_CLUSTER') &&
    Boolean(issue.clusterContext)
  );
}

const DOMAIN_LABELS: Record<string, string> = {
  CASHFLOW: '현금흐름',
  GOAL: '목표',
  LOAN: '대출',
  SAVING: '저축',
  SPENDING: '소비',
};

export function discoveryDomainLabel(domain: string) {
  return DOMAIN_LABELS[domain] ?? domain;
}

const DOMAIN_ICONS: Record<string, ImageSourcePropType> = {
  CASHFLOW: require('@/assets/icons/common/wallet.png'),
  GOAL: require('@/assets/icons/common/light.png'),
  LOAN: require('@/assets/icons/common/wallet.png'),
  // 저축 단독 문제(641 SAVING/CLUSTER_SAVING_RATE_LOW)다. 리포트 저축률과 같은 그림을 쓴다.
  SAVING: require('@/assets/icons/common/report_piggy.png'),
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

/**
 * 발견에 올리지 않는 도메인. 목표 위험(GOAL/*_GOAL_RISK)은 목표 화면이 같은 판정을 이미 보여주고
 * 있어 비효율 발견으로는 중복이고, 제목이 `목표 달성 계획 점검` 고정 문구라 목표가 여럿이면
 * 같은 줄이 여러 개 쌓인다.
 *
 * <p>서버는 계속 이 문제를 만들고 추천(INCREASE_MONTHLY_SAVING·REDUCE_SPENDING)도 붙인다.
 * 화면에서만 걸러내므로 진단의 `issueCount`에는 여전히 포함된다 — 목록 개수는 실제로 보여주는
 * 발견 수를 따로 세야 한다.
 */
export function isHiddenFromDiscovery(issue: DiagnosisIssueApiResponse) {
  return issue.domain === 'GOAL';
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
    hasClusterEvidence: hasClusterEvidence(issue),
  };
}

/**
 * 진단 문제를 그대로 하나씩 발견으로 옮긴다. 카테고리별 소비 증가 문제도 묶지 않고
 * 카테고리마다 한 줄씩 세운다 — 어느 카테고리가 늘었는지 목록에서 바로 보이게 한다.
 */
/**
 * 화면에 실제로 뜬 발견만으로 요약 수치를 만든다.
 *
 * <p>서버 요약(`recommendations/summary`의 `discovery`)을 쓰지 않는 이유는 그 값이 진단의 모든 문제를
 * 세기 때문이다. 목표 위험을 화면에서 거르는 지금은 목록과 어긋난다 — 특히 `expectedGoalAdvanceDays`는
 * `advancedDays`가 채워진 추천에서만 나오는데 그 값을 만드는 생성기가 목표 쪽뿐이라, 목표를 빼면
 * 서버 값은 통째로 목록에 없는 항목의 것이 된다.
 *
 * <p>집계 규칙은 서버와 같게 맞췄다 — 금액은 문제별 최댓값의 합(같은 문제의 대안 추천은 서로 합산하지
 * 않는다), 단축일은 최댓값. 다만 서버가 "열린 추천"만 보는 것과 달리 여기서는 상태를 가리지 않는다.
 */
export function discoverySummary(insights: readonly DiscoveryInsight[]) {
  let annualSavingAmount: number | null = null;
  let goalAdvanceDays: number | null = null;
  for (const insight of insights) {
    if (insight.annualSavingAmount !== null) {
      annualSavingAmount = (annualSavingAmount ?? 0) + insight.annualSavingAmount;
    }
    if (insight.goalAccelerationDays !== null) {
      goalAdvanceDays = Math.max(goalAdvanceDays ?? 0, insight.goalAccelerationDays);
    }
  }
  return { annualSavingAmount, goalAdvanceDays };
}

export function toDiscoveryInsights(
  issues: readonly DiagnosisIssueApiResponse[],
  recommendations: readonly RecommendationApiResponse[],
) {
  return issues
    .filter((issue) => !isHiddenFromDiscovery(issue))
    .map((issue) => toDiscoveryInsight(issue, recommendations, 0));
}
