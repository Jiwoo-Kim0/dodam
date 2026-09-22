import type {
  PeerAnalysisPeriodApiResponse,
  PeerComparisonApiResponse,
  PeerFeatureApiResponse,
  PeerFeatureDirection,
  PeerGroupApiResponse,
  PeerRankBand,
  PeerUnavailableReason,
} from '@/types/patternApi.types';
import type {
  ReportPeerBadge,
  ReportPeerComparison,
  ReportPeerFeature,
} from '@/types/report.types';
import { formatFeatureValue } from '@/utils/patternFeature';

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
 * 상세 화면 라벨 규칙. `{N}`은 서버가 준 분석 개월 수로 채운다.
 *
 * 금융 효율 점수 화면은 한 달치 점수를 보여 주는데 여기는 여러 달 평균이라, 라벨에 기간을
 * 넣지 않으면 두 화면의 `소비율`이 같은 값처럼 읽힌다(698). 개월 수는 하드코딩하지 않고
 * `analysisPeriod.months`를 그대로 쓴다.
 */
const FEATURE_DETAIL_LABEL_TEMPLATES: Record<string, string> = {
  spendingToIncomeRatio: '{N}개월 평균 소비율',
  savingRate: '{N}개월 평균 저축률',
  fixedExpenseShare: '{N}개월 고정지출 비중',
  discretionaryShare: '{N}개월 평균 재량소비 비중',
  spendingVolatility: '{N}개월 소비 변동성',
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

/**
 * 좋고 나쁨을 말하지 않는 특징(BE 670 회신).
 *
 * <p>서버 `direction`은 시뮬레이션·진단 규칙이 쓰므로 그대로 두고, 뱃지 색만 중립으로 둔다.
 * 고정지출·재량소비는 많다고 낭비라 볼 수 없어 방향만 알려 준다(641 군집 비교 원칙).
 */
const NEUTRAL_TONE_FEATURES = new Set(['fixedExpenseShare', 'discretionaryShare']);

/** 판정할 수 있는 구간. 서버가 구간을 늘리면 그 값은 판정 없이 지나간다. */
const KNOWN_RANK_BANDS = new Set<string>(['TOP_10', 'UPPER', 'MIDDLE', 'LOWER', 'BOTTOM_10']);

/**
 * 또래와 견준 한 줄 판정.
 *
 * <p>경계는 서버 `rankBand`를 그대로 쓴다(TOP_10 ≥ 90 / UPPER ≥ 75 / MIDDLE / LOWER < 25 /
 * BOTTOM_10 < 10). FE가 임계값을 따로 들면 서버가 기준을 바꿔도 따라가지 못한다.
 * 좋고 나쁨은 서버 `direction`이 정하며, 여기서 새 기준을 만들지 않는다.
 */
function toBadge(
  feature: string,
  rankBand: PeerRankBand,
  direction: PeerFeatureDirection,
): ReportPeerBadge | null {
  if (rankBand === 'MIDDLE') return { label: '비슷해요', tone: 'neutral' };
  // 서버가 모르는 구간을 보내면 판정하지 않는다. 기본값으로 떨어뜨리면 없는 판정을 지어내게 된다.
  if (!KNOWN_RANK_BANDS.has(rankBand)) return null;

  const isHigh = rankBand === 'TOP_10' || rankBand === 'UPPER';
  // 상·하위 10%까지 간 경우만 단정해 말하고, 그 안쪽은 '조금'을 붙인다.
  const isExtreme = rankBand === 'TOP_10' || rankBand === 'BOTTOM_10';
  const directionLabel = isHigh ? '높아요' : '낮아요';
  const label = isExtreme ? directionLabel : `조금 ${directionLabel}`;

  if (NEUTRAL_TONE_FEATURES.has(feature)) return { label, tone: 'neutral' };

  const isBetter = isHigh === (direction === 'HIGHER_IS_BETTER');
  return isBetter ? { label: '좋아요', tone: 'good' } : { label, tone: 'watch' };
}

function toFeature(feature: PeerFeatureApiResponse, months: number | null): ReportPeerFeature {
  const direction = feature.direction ?? FALLBACK_DIRECTIONS[feature.feature] ?? 'HIGHER_IS_BETTER';
  const axisLabel = FEATURE_AXIS_LABELS[feature.feature] ?? feature.displayName;
  const detailTemplate = FEATURE_DETAIL_LABEL_TEMPLATES[feature.feature];

  return {
    badge: toBadge(feature.feature, feature.rankBand, direction),
    key: feature.feature,
    axisLabel,
    // 개월 수를 모르면 기간 없이 축 라벨을 그대로 쓴다. 지어낸 기간을 적지 않는다.
    detailLabel:
      months === null || detailTemplate === undefined
        ? axisLabel
        : detailTemplate.replace('{N}', String(months)),
    /*
     * 낮을수록 좋은 특징만 백분위를 뒤집는다. 뒤집지 않으면 소비율이 높은 사람의 축이
     * 바깥으로 뻗어 "좋아 보이는" 그래프가 된다.
     */
    healthPercentile:
      direction === 'LOWER_IS_BETTER'
        ? 100 - feature.percentileInPeer
        : feature.percentileInPeer,
    peerPercentile: feature.percentileInPeer,
    rawRatio: feature.userValue,
    peerP25Ratio: feature.peerP25,
    peerMedianRatio: feature.peerMedian,
    peerP75Ratio: feature.peerP75,
    rawRatioText: formatFeatureValue(feature.feature, feature.userValue),
    peerMedianRatioText:
      feature.peerMedian === null
        ? null
        : formatFeatureValue(feature.feature, feature.peerMedian),
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
    features: features.map((feature) => toFeature(feature, response.analysisPeriod?.months ?? null)),
    analysisPeriodLabel: toAnalysisPeriodLabel(response.analysisPeriod),
    insights: response.insights.map((insight) => insight.message),
  };
}
