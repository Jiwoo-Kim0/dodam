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
  previousMonthDifference: number;
  /** 상위 N% 표기에 사용한다. */
  percentile: number;
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
export type ReportMetricTone = 'accent' | 'plain';

export interface ReportKeyMetric {
  id: string;
  label: string;
  value: number;
  format: ReportMetricFormat;
  changeLabel: string;
  tone: ReportMetricTone;
  icon: ImageSourcePropType;
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
  patternCharacterImage: ImageSourcePropType;
  nextMonthForecast: ReportNextMonthForecast;
  keyMetrics: readonly ReportKeyMetric[];
  /** 리포트 기준 달을 마지막으로 하는 최근 6개월 지출이다. */
  monthlySpending: readonly ReportMonthlySpending[];
}
