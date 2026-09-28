import type {
  DiagnosisIssueApiResponse,
  RecommendationApiResponse,
} from '@/types/discoveryApi.types';
import { formatRoundedAmount } from '@/utils/currency';

function nonNegativeNumber(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
    ? value
    : null;
}

function periodRange(value: unknown) {
  if (typeof value !== 'object' || value === null) return null;
  if (!('startMonth' in value) || !('endMonth' in value) || !('months' in value)) {
    return null;
  }
  const { startMonth, endMonth, months } = value;
  const yearMonthPattern = /^\d{4}-(0[1-9]|1[0-2])$/;
  return typeof startMonth === 'string' &&
    typeof endMonth === 'string' &&
    yearMonthPattern.test(startMonth) &&
    yearMonthPattern.test(endMonth) &&
    startMonth <= endMonth &&
    typeof months === 'number' &&
    Number.isInteger(months) &&
    months > 0
    ? { startMonth, endMonth, months }
    : null;
}

/**
 * 진단이 본 최근 기간의 이름. 631(v6)부터는 마지막 완료 월 한 달이라 "8월"이 되고,
 * 627(v5)까지 만들어진 진단은 3개월 평균이라 "최근 3개월"이 된다. 화면 어디서나 이 말을 써야
 * 제목과 카드가 서로 다른 기간을 가리키지 않는다.
 */
export function recentPeriodLabel(period: { endMonth: string; months: number }) {
  return period.months === 1
    ? `${Number(period.endMonth.slice(-2))}월`
    : `최근 ${period.months}개월`;
}

function recentValueLabel(period: ReturnType<typeof periodRange>) {
  if (!period) return '최근 월평균';
  return period.months === 1
    ? `${recentPeriodLabel(period)} 소비`
    : `${recentPeriodLabel(period)} 월평균`;
}

function categoryName(issue: DiagnosisIssueApiResponse) {
  const rawName = issue.currentValue?.categoryName;
  return typeof rawName === 'string' && rawName.trim()
    ? rawName.trim()
    : /^(.+?) 소비가/.exec(issue.title)?.[1] ?? '이 카테고리';
}

/** 진단 응답의 기간 길이와 서버 계산값을 그대로 사용해 이전·새 규칙을 모두 표시한다. */
export function categorySpendingDiagnosisComparison(
  issue: DiagnosisIssueApiResponse,
) {
  const evidence = categorySpendingEvidence(issue);
  const recentPeriod = periodRange(issue.currentValue?.recentPeriod);
  const baselinePeriod = periodRange(issue.benchmarkValue?.baselinePeriod);
  const increaseAmount = nonNegativeNumber(issue.currentValue?.increaseAmount);
  const increaseRatePercent = nonNegativeNumber(
    issue.currentValue?.increaseRatePercent,
  );
  if (
    !evidence ||
    !recentPeriod ||
    !baselinePeriod ||
    increaseAmount === null ||
    increaseRatePercent === null
  ) {
    return null;
  }

  return {
    categoryName: categoryName(issue),
    recentAmount: evidence.recentAmount,
    previousAverage: evidence.previousAverage,
    increaseAmount,
    increaseRatePercent,
    recentPeriod,
    baselinePeriod,
  };
}

function categorySpendingEvidence(issue: DiagnosisIssueApiResponse) {
  const recentAmount = nonNegativeNumber(issue.currentValue?.monthlyAverage);
  const previousAverage = nonNegativeNumber(
    issue.benchmarkValue?.previousMonthlyAverage,
  );
  const recentPeriod = periodRange(issue.currentValue?.recentPeriod);
  const baselinePeriod = periodRange(issue.benchmarkValue?.baselinePeriod);
  return recentAmount !== null && previousAverage !== null
    ? {
        recentAmount,
        previousAverage,
        recentLabel: recentValueLabel(recentPeriod),
        baselineLabel: baselinePeriod
          ? `이전 ${baselinePeriod.months}개월 평균`
          : '이전 월평균',
      }
    : null;
}

/** 화면에 표시된 추천의 연간 예상 절약액만 합산한다. */
export function categorySpendingSummary(
  recommendations: readonly RecommendationApiResponse[],
) {
  const savings = recommendations
    .map((item) => nonNegativeNumber(item.effect.annualCostSaving))
    .filter((amount): amount is number => amount !== null);
  if (savings.length === 0) return null;

  return {
    amount: formatRoundedAmount(savings.reduce((sum, amount) => sum + amount, 0)),
    isComplete: savings.length === recommendations.length,
  };
}
