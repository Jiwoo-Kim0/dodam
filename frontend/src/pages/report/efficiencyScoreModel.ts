import type {
  EfficiencyDimensionApiResponse,
  EfficiencyDimensionStatus,
  EfficiencyScoreApiResponse,
} from '@/types/reportApi.types';
import type {
  ReportEfficiencyScore,
  ReportScoreDimension,
  ReportScoreDimensionStatus,
} from '@/types/report.types';

/**
 * 육각형 축 라벨. 서버 `label`이 `고정지출`, `비효율 관리`처럼 길이가 제각각이라 축에서 잘리지 않게
 * 짧은 표기를 둔다. 모르는 키가 오면 서버 표기를 그대로 쓴다.
 */
const DIMENSION_AXIS_LABELS: Record<string, string> = {
  SPENDING: '지출',
  SAVING: '저축',
  FIXED_EXPENSE: '고정지출',
  CREDIT: '신용',
  CASHFLOW: '현금흐름',
  INEFFICIENCY: '비효율',
};

const DIMENSION_STATUSES: Record<EfficiencyDimensionStatus, ReportScoreDimensionStatus> = {
  GOOD: 'good',
  NORMAL: 'normal',
  CHECK: 'check',
  UNAVAILABLE: 'unavailable',
};

/** 월간 리포트를 받지 못했을 때다. 점수를 FE에서 만들지 않고 없는 상태로 둔다. */
export const EFFICIENCY_SCORE_UNAVAILABLE: ReportEfficiencyScore = {
  score: null,
  maxScore: 100,
  previousMonthDifference: null,
  summary: '',
  dimensions: [],
  unavailableMessage: '금융 효율 점수를 불러오지 못했어요',
};

function toDimension(dimension: EfficiencyDimensionApiResponse): ReportScoreDimension {
  return {
    key: dimension.key,
    label: DIMENSION_AXIS_LABELS[dimension.key] ?? dimension.label,
    score: dimension.score,
    status: DIMENSION_STATUSES[dimension.status] ?? 'unavailable',
    description: dimension.description,
  };
}

export function toEfficiencyScore(
  efficiency: EfficiencyScoreApiResponse | null,
): ReportEfficiencyScore {
  if (efficiency === null) return EFFICIENCY_SCORE_UNAVAILABLE;

  return {
    score: efficiency.score,
    maxScore: efficiency.maxScore,
    previousMonthDifference: efficiency.previousMonthDifference,
    summary: efficiency.summary,
    dimensions: efficiency.dimensions.map(toDimension),
    unavailableMessage:
      efficiency.score === null ? '점수를 계산할 거래가 아직 부족해요' : null,
  };
}
