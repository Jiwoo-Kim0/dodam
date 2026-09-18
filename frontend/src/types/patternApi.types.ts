/**
 * 사용자 패턴 분석(K-Means 소비 유형 군집) API 응답 타입이다.
 *
 * 특징 값(`userValue`, `peerP25`, `peerMedian`, `peerP75`)은 퍼센트가 아니라 **비율**이다.
 * 소득 대비 소비율은 최대 3.0, 저축률은 최대 2.0, 나머지는 최대 1.0으로 잘린 값이 내려온다.
 * `percentileInPeer`만 0~100 척도라 축마다 스케일이 다른 그래프에 함께 쓸 수 있다.
 */

/** 요청할 수 있는 비교 축. `ALL`은 요청할 수 없고 표본 부족 시 응답에만 나온다. */
export type PeerDimension =
  | 'AGE_BAND'
  | 'GENDER'
  | 'AGE_BAND_GENDER'
  | 'CLUSTER_AGE_BAND';

export type PeerGroupDimension = PeerDimension | 'ALL';

export type PeerRankBand = 'TOP_10' | 'UPPER' | 'MIDDLE' | 'LOWER' | 'BOTTOM_10';

/**
 * 값이 클수록 좋은 특징인지. 저축률만 `HIGHER_IS_BETTER`이고 나머지는 `LOWER_IS_BETTER`다.
 * 백분위는 방향과 무관하게 "값이 큰 쪽"이 높으므로, 그래프를 바깥쪽이 좋은 형태로 그리려면
 * `LOWER_IS_BETTER` 축의 백분위를 뒤집어야 한다.
 */
export type PeerFeatureDirection = 'HIGHER_IS_BETTER' | 'LOWER_IS_BETTER';

/**
 * 비교 값이 없거나 제한된 이유.
 *
 * - `DEMOGRAPHICS_MISSING`: 생년월일 또는 성별이 없다
 * - `PEER_GROUP_TOO_SMALL`: 요청 집단의 표본이 부족하다. 전체 모집단까지 넓혀 값을 준 경우에도 붙는다
 * - `MODEL_NOT_SUPPORTED`: 분위 격자가 없는 구버전 모델이다
 * - `CLUSTER_NOT_ASSIGNED`: 군집이 배정되지 않은 규칙 기반 분석이다
 * - `NOT_COMPUTED`: 동년배 비교 도입 이전 분석이라 값이 저장되지 않았다
 */
export type PeerUnavailableReason =
  | 'DEMOGRAPHICS_MISSING'
  | 'PEER_GROUP_TOO_SMALL'
  | 'MODEL_NOT_SUPPORTED'
  | 'CLUSTER_NOT_ASSIGNED'
  | 'NOT_COMPUTED';

export interface PeerGroupApiResponse {
  /** 실제로 비교에 사용한 축이다. 요청한 축과 다를 수 있다. */
  dimension: PeerGroupDimension;
  /** `10S` ~ `60S_PLUS`. 축에 나이대가 없으면 null이다. */
  ageBand: string | null;
  gender: 'MALE' | 'FEMALE' | null;
  clusterId: number | null;
  sampleSize: number;
  /** 표본 부족으로 넓히기 전 원래 요청 축이다. 넓히지 않았으면 null이다. */
  fallbackFrom: PeerDimension | null;
}

export interface PeerFeatureApiResponse {
  /** `spendingToIncomeRatio`, `savingRate` 등 군집 입력 특징 키다. */
  feature: string;
  displayName: string;
  /** 비율이다. 퍼센트로 보여 주려면 100을 곱한다. */
  userValue: number;
  peerP25: number | null;
  peerMedian: number | null;
  peerP75: number | null;
  /** 집단 안에서 사용자 값의 백분위(0~100)다. 방향과 무관하게 값이 클수록 높다. */
  percentileInPeer: number;
  rankBand: PeerRankBand;
  direction?: PeerFeatureDirection;
}

/** 분석에 사용한 완료 월 구간이다. */
export interface PeerAnalysisPeriodApiResponse {
  /** yyyy-MM */
  startMonth: string;
  /** yyyy-MM */
  endMonth: string;
  months: number;
}

export interface PeerClusterShareApiResponse {
  clusterId: number;
  clusterLabel: string;
  /** 이 집단에서 해당 군집이 차지하는 비율(0~1)이다. */
  peerRatio: number;
  mine: boolean;
}

/** 서버가 만든 한국어 문장이다. FE에서 문구를 다시 만들지 않는다. */
export interface PatternInsightApiResponse {
  code: string;
  feature?: string;
  clusterId?: number;
  percentile?: number;
  ratio?: number;
  message: string;
}

export interface PeerComparisonApiResponse {
  analysisId: number | null;
  /** 분석 실행 시각이자 이 비교의 기준 시점이다. 비교 불가 응답에도 들어온다. */
  analyzedAt?: string;
  analysisPeriod?: PeerAnalysisPeriodApiResponse;
  modelVersion: string | null;
  /** 비교할 수 없으면 null이다. */
  peerGroup: PeerGroupApiResponse | null;
  /** 비교할 수 없으면 빈 배열이다. */
  features: PeerFeatureApiResponse[];
  clusterShare: PeerClusterShareApiResponse[];
  insights: PatternInsightApiResponse[];
  /** 값이 정상일 때는 응답에 아예 없다. */
  unavailableReason?: PeerUnavailableReason;
}

export interface AnalyzePatternApiResponse {
  /** 오늘 완료된 분석을 재사용했으면 true다. 응답의 `analysis`는 리포트에서 쓰지 않아 타입을 두지 않는다. */
  cached: boolean;
}
