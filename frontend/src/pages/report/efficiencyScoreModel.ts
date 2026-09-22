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
 * 항목 라벨.
 *
 * 오른쪽에 붙는 숫자가 실제 비율이 아니라 0~100점의 관리 점수라, 라벨에 "점수"를 넣어
 * 또래 비교 화면의 비율(`6개월 평균 소비율` 등)과 섞이지 않게 한다(698).
 * 모르는 키가 오면 서버 표기를 그대로 쓴다.
 */
const DIMENSION_LABELS: Record<string, string> = {
  SPENDING: '지출 관리 점수',
  SAVING: '저축 관리 점수',
  FIXED_EXPENSE: '고정지출 관리 점수',
  CREDIT: '신용 관리 점수',
  CASHFLOW: '현금흐름 관리 점수',
  INEFFICIENCY: '비효율 관리 점수',
};

/**
 * 기준 달을 설명 앞에 붙일 항목. 이 세 가지만 그 달의 수입·소비로 계산한 비율을 말한다.
 * 신용은 시점 등급이고, 현금흐름·비효율은 서버 문장이 "가장 최근 … 기준으로"를 이미 달고 나와
 * 달을 덧붙이면 기간이 두 번 적히거나 틀린 말이 된다.
 */
const MONTH_SCOPED_KEYS: ReadonlySet<string> = new Set(['SPENDING', 'SAVING', 'FIXED_EXPENSE']);

/**
 * 서버 요약의 "관리" 중복을 지운다.
 *
 * <p>서버 라벨이 `지출 관리`·`신용 관리`·`비효율 관리`인데 요약 템플릿이 뒤에 " 관리가 필요해요"를
 * 다시 붙여 "지출 관리 관리가 필요해요"가 된다(`EfficiencyScorePolicy` LABELS·summary).
 *
 * <p>서버가 만드는 요약은 네 가지뿐이고, `관리 관리가`는 이 버그에서만 나온다. 정상 문장인
 * `저축 관리가 필요해요`·`신용 관리를 조금 더 챙겨 보세요`는 패턴에 걸리지 않는다.
 * BE가 고치면 이 치환은 아무것도 바꾸지 않는다.
 */
function withoutDuplicatedManagement(summary: string): string {
  return summary.replace(/관리 관리가/g, '관리가');
}

/** `2026-08` → `2026년 8월`. 형식이 다르면 null이라 화면이 기준 달을 감춘다. */
function toPeriodLabel(period: string | null | undefined): string | null {
  if (!period) return null;

  const [year, month] = period.split('-');
  if (year === undefined || month === undefined) return null;

  const monthNumber = Number(month);
  if (!Number.isInteger(monthNumber)) return null;

  return `${year}년 ${monthNumber}월`;
}

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
  periodLabel: null,
  dimensions: [],
  unavailableMessage: '금융 효율 점수를 불러오지 못했어요',
};

/**
 * 서버 설명에 기준 달을 붙인다. 계산하지 못한 항목의 문장은 "이 달 수입이 없어…"처럼
 * 이미 그 달을 가리키고 있어 그대로 둔다.
 */
function toDescription(
  dimension: EfficiencyDimensionApiResponse,
  monthLabel: string | null,
): string {
  if (monthLabel === null) return dimension.description;
  if (dimension.score === null) return dimension.description;
  if (!MONTH_SCOPED_KEYS.has(dimension.key)) return dimension.description;

  return `${monthLabel} ${dimension.description}`;
}

function toDimension(
  dimension: EfficiencyDimensionApiResponse,
  monthLabel: string | null,
): ReportScoreDimension {
  return {
    key: dimension.key,
    label: DIMENSION_LABELS[dimension.key] ?? dimension.label,
    score: dimension.score,
    status: DIMENSION_STATUSES[dimension.status] ?? 'unavailable',
    description: toDescription(dimension, monthLabel),
  };
}

/**
 * @param period 서버 월간 리포트의 `period`(yyyy-MM). 기준 달 표기와 설명 접두어에 쓴다.
 */
export function toEfficiencyScore(
  efficiency: EfficiencyScoreApiResponse | null,
  period?: string | null,
): ReportEfficiencyScore {
  if (efficiency === null) return EFFICIENCY_SCORE_UNAVAILABLE;

  const periodLabel = toPeriodLabel(period);
  // 설명 앞에는 `8월`만 붙인다. 연도까지 넣으면 한 줄짜리 문장이 두 줄로 넘어간다.
  const monthLabel = periodLabel === null ? null : `${periodLabel.split(' ')[1] ?? ''}`.trim() || null;

  return {
    score: efficiency.score,
    maxScore: efficiency.maxScore,
    previousMonthDifference: efficiency.previousMonthDifference,
    summary: withoutDuplicatedManagement(efficiency.summary),
    periodLabel,
    dimensions: efficiency.dimensions.map((dimension) => toDimension(dimension, monthLabel)),
    unavailableMessage:
      efficiency.score === null ? '점수를 계산할 거래가 아직 부족해요' : null,
  };
}
