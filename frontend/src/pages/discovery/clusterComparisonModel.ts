import type { ClusterRankBand } from '@/types/discoveryApi.types';

/**
 * 군집 특징 값 표기. 서버가 비중·비율을 0~1 소수로 주므로 백분율로 바꿔 보여 준다.
 *
 * <p>1을 넘는 특징(소득 대비 소비율은 1.2 같은 값이 나온다)도 같은 규칙으로 적는다.
 * 소수 한 자리는 0.44 → 44%처럼 반올림 오차가 눈에 띄지 않는 선에서 끊는다.
 */
export function formatClusterValue(value: number) {
  const percent = value * 100;
  const rounded = Math.abs(percent) >= 10 ? Math.round(percent) : Math.round(percent * 10) / 10;
  return `${rounded}%`;
}

/** 기준 범위는 `12~31%`처럼 단위를 한 번만 붙인다. */
export function formatClusterRange(low: number, high: number) {
  return `${formatClusterValue(low).replace('%', '')}~${formatClusterValue(high)}`;
}

const RANK_BAND_LABELS: Record<ClusterRankBand, string> = {
  TOP_10: '상위',
  TOP_25: '상위',
  MIDDLE: '중간',
  BOTTOM_25: '하위',
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

  const isTop = rankBand === 'TOP_10' || rankBand === 'TOP_25';
  const percent = Math.round(isTop ? 100 - percentileInCluster : percentileInCluster);
  return `${prefix} ${Math.max(1, percent)}%`;
}
