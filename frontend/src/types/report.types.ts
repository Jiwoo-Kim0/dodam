import type { ImageSourcePropType } from 'react-native';

/** 금융 효율 점수 도넛의 구성 색상 단계 */
export type ReportScoreDimensionStatus = 'good' | 'normal' | 'check' | 'unavailable';

export interface ReportScoreDimension {
  key: string;
  /** 육각형 축 라벨이다. */
  label: string;
  /** 0~100. 계산할 수 없으면 null이며 0점과 구분한다. */
  score: number | null;
  status: ReportScoreDimensionStatus;
  /** 숫자 근거가 담긴 서버 문장이다. */
  description: string;
}

export interface ReportEfficiencyScore {
  /** 계산된 항목이 너무 적으면 서버가 전체 점수를 주지 않는다. */
  score: number | null;
  maxScore: number;
  /** 전월 대비 점수 차. 비교할 수 없으면 null이다. */
  previousMonthDifference: number | null;
  summary: string;
  /** 항목별 점수. 비어 있으면 육각형을 그리지 않는다. */
  dimensions: readonly ReportScoreDimension[];
  /** 점수를 계산할 수 없을 때 보여 줄 안내다. 점수가 있으면 null이다. */
  unavailableMessage: string | null;
}

export type ReportPatternStatus = 'good' | 'check';

export interface ReportFinancePattern {
  id: string;
  status: ReportPatternStatus;
  title: string;
  description: string;
  icon: ImageSourcePropType;
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

export interface ReportSpendingCategory {
  id: string;
  name: string;
  amount: number;
  /** 리포트 달 총지출 대비 비중(%)이다. 총지출이 0이면 0이다. */
  sharePercent: number;
  icon: ImageSourcePropType;
}

export interface ReportPastSpending {
  /** 리포트 기준 달을 마지막으로 하는 최근 6개월 지출이다. */
  months: readonly ReportMonthlySpending[];
  /** 서버가 계산한 기간 월평균이다. 같은 값을 FE에서 다시 평균 내지 않는다. */
  monthlyAverage: number;
  latestAmount: number;
  /** 월평균 대비 증감액이며 양수면 평균보다 많이 썼다. */
  differenceFromAverage: number;
  /** 리포트 달 지출 상위 카테고리다. 거래가 없으면 빈 배열이다. */
  topCategories: readonly ReportSpendingCategory[];
}

export interface ReportPeerFeature {
  key: string;
  /** 오각형 축에 들어갈 짧은 라벨이다. */
  label: string;
  /**
   * 오각형에 그릴 값(0~100)이다. 낮을수록 좋은 특징은 백분위를 뒤집어 담아, 어느 축이든
   * **바깥쪽일수록 좋은 상태**가 되게 맞춘다. 또래 중앙값은 뒤집어도 50이라 기준선은 그대로다.
   */
  axisValue: number;
  /** 서버가 준 원래 백분위(0~100)다. 값이 클수록 큰 쪽이며 좋고 나쁨과는 무관하다. */
  percentile: number;
  /** 원본 비율 값이다. 또래 분포와 내 위치를 같은 축에 놓는 데 쓴다. */
  userValue: number;
  peerP25: number | null;
  peerMedian: number | null;
  peerP75: number | null;
  /** 사용자 값을 퍼센트로 표기한 문자열이다. */
  userValueText: string;
  /** 또래 중앙값을 퍼센트로 표기한 문자열이다. 서버가 주지 않으면 null이다. */
  peerMedianText: string | null;
}

/**
 * 또래 비교 카드 상태. 서버가 값을 줄 수 없는 사유가 여러 가지라 화면에서 구분해 보여 준다.
 * `available`이어도 표본 부족으로 전체 사용자까지 넓힌 경우가 있어 실제 비교 집단을 함께 싣는다.
 */
export type ReportPeerComparison =
  | {
      status: 'available';
      /** 배정된 소비 유형 이름. 군집 구성비가 비어 있으면 null이다. */
      clusterLabel: string | null;
      /** `20대 여성`처럼 실제로 비교한 집단 표기다. */
      groupLabel: string;
      sampleSize: number;
      /** 표본이 부족해 더 넓은 집단으로 비교한 경우 true다. */
      isFallback: boolean;
      features: readonly ReportPeerFeature[];
      /** `2026년 3월 ~ 8월 소비 기준`처럼 분석에 사용한 기간 표기다. 서버가 주지 않으면 null이다. */
      analysisPeriodLabel: string | null;
      /** 서버가 만든 문장이다. 비어 있을 수 있다. */
      insights: readonly string[];
    }
  | {
      status: 'unavailable';
      /** 사용자에게 보여 줄 안내 문구다. */
      message: string;
    };

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
  /** 또래 비교 조회 자체가 실패하면 null이며, 이때 카드를 렌더링하지 않는다. */
  peerComparison: ReportPeerComparison | null;
  keyMetrics: readonly ReportKeyMetric[];
  pastSpending: ReportPastSpending;
}
