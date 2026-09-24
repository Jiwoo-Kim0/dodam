import type { ImageSourcePropType } from 'react-native';

import type { SpendingForecast } from './forecastApi.types';

/** 금융 효율 점수 도넛의 구성 색상 단계 */
export type ReportScoreDimensionStatus = 'good' | 'normal' | 'check' | 'unavailable';

export interface ReportScoreDimension {
  key: string;
  /** `지출 관리 점수`처럼 이 값이 비율이 아니라 점수라는 것이 드러나는 라벨이다. */
  label: string;
  /** 0~100 관리 점수다. **실제 비율이 아니다.** 계산할 수 없으면 null이며 0점과 구분한다. */
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
  /**
   * `2026년 8월`처럼 점수를 낸 기준 달이다. 서버 `period`에서 만든다.
   * 또래 비교(최근 N개월 평균)와 기간이 달라, 두 화면 값을 직접 비교할 수 없다는 것을 알리는 데 쓴다.
   */
  periodLabel: string | null;
  /** 항목별 점수. */
  dimensions: readonly ReportScoreDimension[];
  /** 점수를 계산할 수 없을 때 보여 줄 안내다. 점수가 있으면 null이다. */
  unavailableMessage: string | null;
}

/**
 * 금융 생활 패턴 판정. `unavailable`은 '위험 없음'이 아니라 '판단할 자료가 없음'이다.
 * 둘을 같은 모양으로 그리면 데이터가 없는 것을 안전하다고 읽게 된다(BE 697).
 */
export type ReportPatternStatus = 'good' | 'check' | 'unavailable';

export interface ReportFinancePattern {
  id: string;
  status: ReportPatternStatus;
  title: string;
  description: string;
  /** 판정이 본 기간. 서버가 주지 않으면 null이다. */
  evidencePeriod: string | null;
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

/** 뱃지 색을 정하는 기준. `neutral`은 또래와 비슷해 좋고 나쁨을 말하지 않는 상태다. */
export type ReportPeerBadgeTone = 'good' | 'watch' | 'neutral';

export interface ReportPeerBadge {
  label: string;
  tone: ReportPeerBadgeTone;
}

/**
 * 또래 비교 한 축. **비율과 백분위는 서로 다른 값이라 이름으로 구분한다(698).**
 * 오각형은 백분위(`healthPercentile`)로 그리고, 상세 화면 막대는 비율(`rawRatio`)로 그린다.
 * 둘을 바꿔 쓰면 "실제 비율"과 "또래 안에서의 위치"가 뒤섞여 값이 안 맞는다고 느끼게 된다.
 */
export interface ReportPeerFeature {
  key: string;
  /** 오각형 축에 들어갈 짧은 라벨이다. 길면 축에서 잘린다. */
  axisLabel: string;
  /** 상세 화면 목록에 쓰는 라벨이다. `6개월 평균 소비율`처럼 분석 기간을 함께 밝힌다. */
  detailLabel: string;
  /**
   * 오각형에 그릴 값(0~100)이다. 비율이 아니라 **또래 안에서의 상대 위치**다.
   * 낮을수록 좋은 특징은 백분위를 뒤집어 담아, 어느 축이든 **바깥쪽일수록 좋은 상태**가
   * 되게 맞춘다. 또래 중앙값은 뒤집어도 50이라 기준선은 그대로다.
   */
  healthPercentile: number;
  /**
   * 또래와 견준 한 줄 판정. 경계는 서버 `rankBand`, 좋고 나쁨은 서버 `direction`이 정하며
   * FE가 기준을 새로 만들지 않는다. 서버가 모르는 구간을 보내면 `null`이다.
   */
  badge: ReportPeerBadge | null;
  /** 서버가 준 원래 백분위(0~100)다. 값이 클수록 큰 쪽이며 좋고 나쁨과는 무관하다. */
  peerPercentile: number;
  /** 실제 비율 값이다(소비율 0.83 = 83%). 또래 분포와 내 위치를 같은 축에 놓는 데 쓴다. */
  rawRatio: number;
  peerP25Ratio: number | null;
  peerMedianRatio: number | null;
  peerP75Ratio: number | null;
  /** 실제 비율을 표기한 문자열이다. */
  rawRatioText: string;
  /** 또래 중앙값 비율을 표기한 문자열이다. 서버가 주지 않으면 null이다. */
  peerMedianRatioText: string | null;
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
  /** 예측 조회가 실패하면 null이며, 이때 카드를 렌더링하지 않는다. */
  spendingForecast: SpendingForecast | null;
  pastSpending: ReportPastSpending;
}
