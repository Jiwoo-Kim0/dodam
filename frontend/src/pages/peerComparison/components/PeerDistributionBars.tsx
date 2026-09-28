import { useMemo, useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import type { ReportPeerBadge, ReportPeerFeature } from '@/types/report.types';

import { styles } from './PeerDistributionBars.styles';

type PeerDistributionBarsProps = {
  features: readonly ReportPeerFeature[];
};

/** 뱃지 색 조회표. 스타일 키를 문자열로 조립하면 이름을 바꿀 때 놓치기 쉽다. */
const BADGE_STYLES = {
  good: styles.goodBadge,
  watch: styles.watchBadge,
  neutral: styles.neutralBadge,
};

const BADGE_TEXT_STYLES = {
  good: styles.goodBadgeText,
  watch: styles.watchBadgeText,
  neutral: styles.neutralBadgeText,
};

/** 값이 축 끝에 딱 붙지 않도록 남기는 여유다. */
const DOMAIN_HEADROOM = 1.25;

type BarRow = {
  key: string;
  label: string;
  badge: ReportPeerBadge | null;
  userValueText: string;
  /** 0~100 퍼센트 위치. 트랙 왼쪽 끝이 0이다. */
  userPosition: number;
  /** 또래 25~75분위 구간. 값이 없으면 null이다. */
  peerBand: { left: number; width: number } | null;
  medianPosition: number | null;
};

function toRow(feature: ReportPeerFeature): BarRow {
  // 축마다 값의 크기가 달라(소비율은 1을 넘고 저축률은 0.2대) 행마다 자기 도메인을 쓴다.
  const candidates = [feature.rawRatio, feature.peerP75Ratio ?? 0, feature.peerMedianRatio ?? 0];
  const domain = Math.max(...candidates) * DOMAIN_HEADROOM;
  const toPosition = (value: number) =>
    domain === 0 ? 0 : Math.min(100, Math.max(0, (value / domain) * 100));

  const p25 = feature.peerP25Ratio;
  const p75 = feature.peerP75Ratio;

  return {
    key: feature.key,
    label: feature.detailLabel,
    badge: feature.badge,
    userValueText: feature.rawRatioText,
    userPosition: toPosition(feature.rawRatio),
    peerBand:
      p25 === null || p75 === null
        ? null
        : { left: toPosition(p25), width: Math.max(2, toPosition(p75) - toPosition(p25)) },
    medianPosition: feature.peerMedianRatio === null ? null : toPosition(feature.peerMedianRatio),
  };
}

export function PeerDistributionBars({ features }: PeerDistributionBarsProps) {
  const rows = useMemo(() => features.map(toRow), [features]);
  // 한 번에 하나만 띄운다. 다섯 개가 동시에 떠 있으면 도로 지저분해진다.
  const [openKey, setOpenKey] = useState<string | null>(null);

  return (
    // 점이 아닌 곳을 누르면 수치가 닫힌다. 점 Pressable이 터치를 먼저 잡아 여기까지 오지 않는다.
    <Pressable onPress={() => setOpenKey(null)} style={styles.list}>
      {rows.map((row) => (
        <View key={row.key} style={styles.row}>
          <View style={styles.rowHeader}>
            <Text style={styles.rowLabel}>{row.label}</Text>
            {/* 좋고 나쁨은 서버 `direction`이 정한 것이다. 여기서는 그 결과만 적는다. */}
            {row.badge === null ? null : (
              <View style={[styles.badge, BADGE_STYLES[row.badge.tone]]}>
                <Text style={[styles.badgeText, BADGE_TEXT_STYLES[row.badge.tone]]}>
                  {row.badge.label}
                </Text>
              </View>
            )}
          </View>

          <View style={styles.track}>
            {row.peerBand === null ? null : (
              <View
                style={[
                  styles.peerBand,
                  { left: `${row.peerBand.left}%`, width: `${row.peerBand.width}%` },
                ]}
              />
            )}
            {row.medianPosition === null ? null : (
              <View style={[styles.medianTick, { left: `${row.medianPosition}%` }]} />
            )}
            <Pressable
              accessibilityLabel={`${row.label} 내 수치 ${row.userValueText}`}
              accessibilityRole="button"
              /* 점이 12px이라 그대로 두면 누르기 어렵다. */
              hitSlop={12}
              onPress={() => setOpenKey((key) => (key === row.key ? null : row.key))}
              style={[styles.userDot, { left: `${row.userPosition}%` }]}
            />

            {openKey === row.key ? (
              <View style={[styles.tooltipAnchor, { left: `${row.userPosition}%` }]}>
                <View style={styles.tooltip}>
                  <Text style={styles.tooltipText}>{row.userValueText}</Text>
                </View>
              </View>
            ) : null}
          </View>
        </View>
      ))}
    </Pressable>
  );
}
