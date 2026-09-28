import type { ClusterRankBand } from '@/types/discoveryApi.types';
import { formatFeatureRange, formatFeatureValue } from '@/utils/patternFeature';

/**
 * 군집 특징 값·범위 표기는 또래 비교 화면과 같은 규칙을 쓴다(BE 670 회신).
 * 한 사용자가 두 화면을 오갈 때 같은 특징이 다른 숫자로 보이면 안 된다.
 */
export const formatClusterValue = formatFeatureValue;
export const formatClusterRange = formatFeatureRange;

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

/**
 * 분석 기준 기간 표기. 서버는 `2026-03~2026-08`처럼 기계용 문자열로 준다.
 *
 * <p>같은 해면 뒤쪽 연도를 생략해 또래 비교 화면(`peerComparisonModel`)과 같은 모양으로 읽힌다.
 * 형식이 다르면 서버 값을 그대로 둔다 — 기간을 못 읽느니 원문이 낫다.
 */
export function formatClusterWindow(window: string) {
  const [start, end] = window.split('~');
  const [startYear, startMonth] = start?.split('-') ?? [];
  const [endYear, endMonth] = end?.split('-') ?? [];
  if (!startYear || !startMonth || !endYear || !endMonth) return window;

  const startLabel = `${startYear}년 ${Number(startMonth)}월`;
  const endLabel =
    startYear === endYear ? `${Number(endMonth)}월` : `${endYear}년 ${Number(endMonth)}월`;
  return `${startLabel}~${endLabel}`;
}
