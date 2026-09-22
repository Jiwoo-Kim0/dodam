import type { ImageSourcePropType } from 'react-native';

import type {
  ReportMetricsApiResponse,
  ReportPatternApiResponse,
  ReportPatternStatusApiResponse,
} from '@/types/reportApi.types';
import type { ReportFinancePattern, ReportPatternStatus } from '@/types/report.types';

/**
 * 패턴 아이콘. 서버는 아이콘을 내리지 않고 `key`만 준다(BE 697 §7-3).
 * 모르는 키가 새로 생기면 저금통을 쓴다 — 카드가 통째로 비는 것보다 낫다.
 */
const PATTERN_ICONS: Record<string, ImageSourcePropType> = {
  SAVING_RATE: require('@/assets/icons/common/report_piggy.png'),
  SPENDING_INCREASE: require('@/assets/icons/common/report_bag.png'),
  CASHFLOW_RISK: require('@/assets/icons/common/report_coins.png'),
};

const DEFAULT_PATTERN_ICON: ImageSourcePropType = require('@/assets/icons/common/report_piggy.png');

const STATUS_MAP: Record<ReportPatternStatusApiResponse, ReportPatternStatus> = {
  GOOD: 'good',
  CHECK: 'check',
  UNAVAILABLE: 'unavailable',
};

function toPattern(pattern: ReportPatternApiResponse): ReportFinancePattern {
  return {
    id: pattern.key,
    // 모르는 상태가 오면 좋다고 단정하지 않는다. 판단할 자료가 없다고 본다.
    status: STATUS_MAP[pattern.status] ?? 'unavailable',
    title: pattern.title,
    description: pattern.description,
    evidencePeriod: pattern.evidencePeriod,
    icon: PATTERN_ICONS[pattern.key] ?? DEFAULT_PATTERN_ICON,
  };
}

/**
 * 서버가 판정한 금융 생활 패턴을 카드 모델로 바꾼다.
 *
 * <p>판정·문구는 모두 서버가 만든다(BE 697). FE가 임계값을 들고 있으면 서버가 기준을 바꿔도
 * 따라가지 못하고, 발견 화면과 다른 판정을 내놓게 된다.
 *
 * <p>697 이전에 굳은 리포트에는 `metrics`가 없다. 이때는 카드를 비운다 — 자료가 없는 것을
 * 지어내어 "위험 없음"으로 보여 주면 안 된다.
 */
export function toFinancePatterns(
  metrics: ReportMetricsApiResponse | null | undefined,
): readonly ReportFinancePattern[] {
  if (!metrics) return [];

  return metrics.patterns.map(toPattern);
}
