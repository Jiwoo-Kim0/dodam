import type { ClusterRankBand } from '@/types/discoveryApi.types';

/**
 * 백분율로 적는 특징. 비중·비율이라 0.44 → 44%가 자연스럽다.
 *
 * <p>`spendingVolatility`는 여기 없다 — 비율이 아니라서 서버 설명도 "소비 변동성이 0.05로"처럼
 * 원값을 그대로 쓴다. 100을 곱하면 같은 화면의 문제 설명과 숫자가 어긋난다.
 * 모르는 특징이 새로 생겨도 원값으로 두는 쪽이 안전하다.
 */
const PERCENT_FEATURES = new Set([
  'spendingToIncomeRatio',
  'savingRate',
  'fixedExpenseShare',
  'discretionaryShare',
]);

function roundForDisplay(value: number, digits: number) {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

/**
 * 군집 특징 값 표기. 특징에 따라 백분율과 원값으로 갈린다.
 *
 * <p>백분율은 10% 이상이면 정수, 미만이면 소수 한 자리로 끊는다(0.44 → 44%, 0.08 → 8%).
 * 원값은 소수 셋째 자리까지 둔다 — 변동성은 0.05처럼 작아서 둘째 자리로 끊으면 기준 범위의
 * 양 끝이 같은 값으로 뭉개진다(0.011·0.014 → `0.01~0.01`). 끝자리 0은 숫자 변환이 알아서 뗀다.
 */
export function formatClusterValue(feature: string, value: number) {
  if (!PERCENT_FEATURES.has(feature)) return `${roundForDisplay(value, 3)}`;

  const percent = value * 100;
  return `${roundForDisplay(percent, Math.abs(percent) >= 10 ? 0 : 1)}%`;
}

/** 기준 범위는 `12~31%`처럼 단위를 한 번만 붙인다. 원값 특징은 붙일 단위가 없다. */
export function formatClusterRange(feature: string, low: number, high: number) {
  const highText = formatClusterValue(feature, high);
  return `${formatClusterValue(feature, low).replace('%', '')}~${highText}`;
}

const RANK_BAND_LABELS: Record<ClusterRankBand, string> = {
  TOP_10: '상위',
  UPPER: '상위',
  MIDDLE: '중간',
  LOWER: '하위',
  BOTTOM_10: '하위',
};

/**
 * "상위 8%"처럼 적는다. `percentileInCluster`는 군집 안에서의 백분위(0~100)이며
 * 상위 구간이면 100에서 빼야 "상위 몇 %"가 된다.
 *
 * <p>중간 구간은 순위를 말하지 않는다 — 이탈로 잡히지 않은 자리라 굳이 등수를 붙일 이유가 없다.
 * 서버가 모르는 구간 값을 보내면 아무것도 그리지 않는다.
 */
export function clusterRankBandLabel(
  rankBand: ClusterRankBand,
  percentileInCluster: number,
): string | null {
  const prefix = RANK_BAND_LABELS[rankBand];
  if (prefix === undefined || rankBand === 'MIDDLE') return null;

  const isTop = rankBand === 'TOP_10' || rankBand === 'UPPER';
  const percent = Math.round(isTop ? 100 - percentileInCluster : percentileInCluster);
  return `${prefix} ${Math.max(1, percent)}%`;
}
