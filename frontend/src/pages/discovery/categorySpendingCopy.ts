import type {
  DiagnosisIssueApiResponse,
  RecommendationApiResponse,
} from '@/types/discoveryApi.types';
import { formatNumber, formatRoundedAmount } from '@/utils/currency';

function nonNegativeNumber(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
    ? value
    : null;
}

function roundedThousand(value: number) {
  return formatNumber(Math.round(value / 1000) * 1000);
}

/** 생성 시점의 진단 스냅샷만 읽어 카드 문구를 만든다. 현재 거래를 재계산하지 않는다. */
export function categorySpendingCopy(
  issue: DiagnosisIssueApiResponse,
  recommendation: RecommendationApiResponse,
) {
  const current = issue.currentValue;
  const rawName = current?.categoryName;
  const categoryName =
    typeof rawName === 'string' && rawName.trim()
      ? rawName.trim()
      : /^(.+?) 소비가/.exec(issue.title)?.[1] ?? '이 항목';
  const title = categoryName.endsWith('소비')
    ? `${categoryName} 줄이기`
    : `${categoryName} 소비 줄이기`;

  const increase =
    nonNegativeNumber(current?.increaseAmount) ??
    nonNegativeNumber(recommendation.effect.monthlyCostSaving);
  if (increase !== null) {
    return {
      title,
      description: `이전 평균보다 약 ${roundedThousand(increase)}원 증가했어요.`,
    };
  }
  return { title, description: '이전 평균보다 소비가 증가했어요.' };
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
