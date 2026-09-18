import type {
  PeerAnalysisPeriodApiResponse,
  PeerComparisonApiResponse,
  PeerFeatureApiResponse,
  PeerFeatureDirection,
  PeerGroupApiResponse,
  PeerUnavailableReason,
} from '@/types/patternApi.types';
import type { ReportPeerComparison, ReportPeerFeature } from '@/types/report.types';

/**
 * 오각형 축 라벨. 서버 `displayName`은 `소득 대비 소비율`처럼 길어 축 라벨로 쓰면 잘리므로
 * 특징 키마다 짧은 표기를 둔다. 모르는 키가 오면 서버 표기를 그대로 쓴다.
 */
const FEATURE_AXIS_LABELS: Record<string, string> = {
  spendingToIncomeRatio: '소비율',
  savingRate: '저축률',
  fixedExpenseShare: '고정지출',
  discretionaryShare: '재량소비',
  spendingVolatility: '변동성',
};

/**
 * 특징별 방향. 서버가 `features[].direction`으로 내려 주며 이 표는 값이 빠졌을 때만 쓰는 대비책이다.
 * 방향을 모른 채 백분위를 그대로 그리면 축마다 좋고 나쁨이 뒤섞여 그래프가 의미를 잃는다.
 */
const FALLBACK_DIRECTIONS: Record<string, PeerFeatureDirection> = {
  spendingToIncomeRatio: 'LOWER_IS_BETTER',
  savingRate: 'HIGHER_IS_BETTER',
  fixedExpenseShare: 'LOWER_IS_BETTER',
  discretionaryShare: 'LOWER_IS_BETTER',
  spendingVolatility: 'LOWER_IS_BETTER',
};

const AGE_BAND_LABELS: Record<string, string> = {
  '10S': '10대',
  '20S': '20대',
  '30S': '30대',
  '40S': '40대',
  '50S': '50대',
  '60S_PLUS': '60대 이상',
};

/**
 * 비교할 수 없는 사유별 안내 문구. 서버는 사유 코드만 주고 문장은 주지 않는다.
 * 사유별 안내 기준은 BE 회신(`backend/docs/pattern.md` 635 FE 회신)을 따른다.
 *
 * `PEER_GROUP_TOO_SMALL`은 값이 함께 오는 경우가 많아 대부분 여기까지 오지 않는다.
 */
const UNAVAILABLE_MESSAGES: Record<PeerUnavailableReason, string> = {
  DEMOGRAPHICS_MISSING: '생년월일과 성별을 등록하면 또래와 비교할 수 있어요',
  PEER_GROUP_TOO_SMALL: '아직 비교할 또래가 충분히 모이지 않았어요',
  MODEL_NOT_SUPPORTED: '지금은 또래 비교를 준비하고 있어요',
  CLUSTER_NOT_ASSIGNED: '소비 유형을 나누려면 3개월 이상의 거래가 필요해요',
  NOT_COMPUTED: '분석을 다시 실행하면 또래 비교를 확인할 수 있어요',
};

const DEFAULT_UNAVAILABLE_MESSAGE = '아직 또래 비교 결과가 없어요';

/** 완료된 분석이 없어 404가 온 경우다. */
export const PEER_COMPARISON_NOT_ANALYZED: ReportPeerComparison = {
  status: 'unavailable',
  message: '거래 데이터가 쌓이면 소비 유형과 또래 비교를 확인할 수 있어요',
};

function toPercentText(ratio: number | null) {
  if (ratio === null) return null;
  return `${Math.round(ratio * 100)}%`;
}

function toFeature(feature: PeerFeatureApiResponse): ReportPeerFeature {
  const direction = feature.direction ?? FALLBACK_DIRECTIONS[feature.feature] ?? 'HIGHER_IS_BETTER';

  return {
    key: feature.feature,
    label: FEATURE_AXIS_LABELS[feature.feature] ?? feature.displayName,
    axisValue:
      direction === 'LOWER_IS_BETTER'
        ? 100 - feature.percentileInPeer
        : feature.percentileInPeer,
    percentile: feature.percentileInPeer,
    userValue: feature.userValue,
    peerP25: feature.peerP25,
    peerMedian: feature.peerMedian,
    peerP75: feature.peerP75,
    userValueText: `${Math.round(feature.userValue * 100)}%`,
    peerMedianText: toPercentText(feature.peerMedian),
  };
}

/** `2026년 3월 ~ 8월 소비 기준`. 같은 해면 뒤쪽 연도는 생략한다. */
function toAnalysisPeriodLabel(period: PeerAnalysisPeriodApiResponse | undefined) {
  if (period === undefined) return null;

  const [startYear, startMonth] = period.startMonth.split('-');
  const [endYear, endMonth] = period.endMonth.split('-');
  if (startYear === undefined || startMonth === undefined) return null;
  if (endYear === undefined || endMonth === undefined) return null;

  const start = `${startYear}년 ${Number(startMonth)}월`;
  const end = startYear === endYear ? `${Number(endMonth)}월` : `${endYear}년 ${Number(endMonth)}월`;
  return `${start} ~ ${end} 소비 기준`;
}

/** 사용자에게 보여 줄 비교 집단 표기다. 서버가 실제로 사용한 축을 그대로 따른다. */
function toGroupLabel(peerGroup: PeerGroupApiResponse) {
  const ageBand = peerGroup.ageBand === null ? null : AGE_BAND_LABELS[peerGroup.ageBand] ?? null;
  const gender = peerGroup.gender === null ? null : peerGroup.gender === 'FEMALE' ? '여성' : '남성';

  switch (peerGroup.dimension) {
    case 'AGE_BAND':
      return ageBand ?? '전체 사용자';
    case 'GENDER':
      return gender ?? '전체 사용자';
    case 'AGE_BAND_GENDER':
      return [ageBand, gender].filter((part) => part !== null).join(' ') || '전체 사용자';
    case 'CLUSTER_AGE_BAND':
      return ageBand === null ? '같은 소비 유형' : `${ageBand} 같은 소비 유형`;
    case 'ALL':
    default:
      return '전체 사용자';
  }
}

/**
 * 또래 비교 응답을 카드 모델로 바꾼다.
 *
 * 표본이 부족해 전체 모집단까지 넓힌 경우 서버는 값과 `PEER_GROUP_TOO_SMALL`을 함께 준다. 이때는
 * 비교 불가가 아니라 넓어진 집단으로 표시해야 하므로, 사유 코드가 아니라 실제 값이 있는지로 판단한다.
 */
export function toPeerComparison(
  response: PeerComparisonApiResponse,
): ReportPeerComparison {
  const { features, peerGroup } = response;

  if (peerGroup === null || features.length === 0) {
    return {
      status: 'unavailable',
      message: response.unavailableReason
        ? UNAVAILABLE_MESSAGES[response.unavailableReason] ?? DEFAULT_UNAVAILABLE_MESSAGE
        : DEFAULT_UNAVAILABLE_MESSAGE,
    };
  }

  return {
    status: 'available',
    clusterLabel: response.clusterShare.find((share) => share.mine)?.clusterLabel ?? null,
    groupLabel: toGroupLabel(peerGroup),
    sampleSize: peerGroup.sampleSize,
    isFallback: peerGroup.fallbackFrom !== null,
    features: features.map(toFeature),
    analysisPeriodLabel: toAnalysisPeriodLabel(response.analysisPeriod),
    insights: response.insights.map((insight) => insight.message),
  };
}
