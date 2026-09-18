import type { ImageSourcePropType } from 'react-native';

/** 금융 효율 점수 도넛의 구성 색상 단계 */
export type ReportScoreTone = 'strong' | 'medium' | 'light';

export interface ReportScoreSegment {
  id: string;
  tone: ReportScoreTone;
  /** 도넛 전체(100) 기준 비율 */
  ratio: number;
}

export interface ReportEfficiencyScore {
  score: number;
  maxScore: number;
  summary: string;
  composition: readonly ReportScoreSegment[];
}

export type ReportPatternStatus = 'good' | 'check';

export interface ReportFinancePattern {
  id: string;
  status: ReportPatternStatus;
  title: string;
  description: string;
  icon: ImageSourcePropType;
}

export type ReportForecastTone = 'fixed' | 'living' | 'card';

export interface ReportForecastBreakdown {
  id: string;
  label: string;
  amount: number;
  tone: ReportForecastTone;
}

export interface ReportNextMonthForecast {
  description: string;
  expectedExpense: number;
  /** 이번 달 대비 증감액이며 양수면 더 많이 쓸 것으로 예상한다. */
  differenceFromThisMonth: number;
  breakdown: readonly ReportForecastBreakdown[];
}

export type ReportMetricFormat = 'percent' | 'currency';
/** 값이 늘었는지 줄었는지. `none`은 변화가 없거나 비교할 지난달 값이 없는 상태다. */
export type ReportMetricChangeDirection = 'up' | 'down' | 'none';
/**
 * 변화가 사용자에게 좋은 방향인지. 지표마다 좋은 방향이 달라서(저축률은 오르면 좋고 카드 사용액은
 * 오르면 나쁘다) 증감 부호만으로 색을 정하지 않는다.
 */
export type ReportMetricChangeTone = 'good' | 'bad' | 'neutral';

export interface ReportMetricChange {
  direction: ReportMetricChangeDirection;
  /** `up`·`down`이면 화살표 뒤에 붙는 값, `none`이면 문구 전체다. */
  label: string;
  tone: ReportMetricChangeTone;
}

export interface ReportKeyMetric {
  id: string;
  label: string;
  value: number;
  format: ReportMetricFormat;
  change: ReportMetricChange;
  icon: ImageSourcePropType;
  /** 원본 이미지의 투명 여백을 보정하는 시각 배율 */
  iconScale: number;
}

export interface ReportMonthlySpending {
  /** YYYY-MM */
  yearMonth: string;
  monthLabel: string;
  amount: number;
}

export interface MonthlyReport {
  /** YYYY-MM */
  yearMonth: string;
  monthLabel: string;
  userName: string;
  efficiencyScore: ReportEfficiencyScore;
  patterns: readonly ReportFinancePattern[];
  patternCoachingMessage: string;
  patternCoachingDisclaimer: string | null;
  patternCharacterImage: ImageSourcePropType;
  nextMonthForecast: ReportNextMonthForecast;
  keyMetrics: readonly ReportKeyMetric[];
  /** 리포트 기준 달을 마지막으로 하는 최근 6개월 지출이다. */
  monthlySpending: readonly ReportMonthlySpending[];
}
