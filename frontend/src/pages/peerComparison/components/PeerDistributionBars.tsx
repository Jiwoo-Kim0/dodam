import { useMemo } from 'react';
import { Text, View } from 'react-native';

import type { ReportPeerFeature } from '@/types/report.types';

import { styles } from './PeerDistributionBars.styles';

type PeerDistributionBarsProps = {
  features: readonly ReportPeerFeature[];
};

/** 값이 축 끝에 딱 붙지 않도록 남기는 여유다. */
const DOMAIN_HEADROOM = 1.25;

type BarRow = {
  key: string;
  label: string;
  userValueText: string;
  peerMedianText: string | null;
  /** 0~100 퍼센트 위치. 트랙 왼쪽 끝이 0이다. */
  userPosition: number;
  /** 또래 25~75분위 구간. 값이 없으면 null이다. */
  peerBand: { left: number; width: number } | null;
  medianPosition: number | null;
};

function toRow(feature: ReportPeerFeature): BarRow {
  // 축마다 값의 크기가 달라(소비율은 1을 넘고 저축률은 0.2대) 행마다 자기 도메인을 쓴다.
  const candidates = [feature.userValue, feature.peerP75 ?? 0, feature.peerMedian ?? 0];
  const domain = Math.max(...candidates) * DOMAIN_HEADROOM;
  const toPosition = (value: number) =>
    domain === 0 ? 0 : Math.min(100, Math.max(0, (value / domain) * 100));

  const p25 = feature.peerP25;
  const p75 = feature.peerP75;

  return {
    key: feature.key,
    label: feature.label,
    userValueText: feature.userValueText,
    peerMedianText: feature.peerMedianText,
    userPosition: toPosition(feature.userValue),
    peerBand:
      p25 === null || p75 === null
        ? null
        : { left: toPosition(p25), width: Math.max(2, toPosition(p75) - toPosition(p25)) },
    medianPosition: feature.peerMedian === null ? null : toPosition(feature.peerMedian),
  };
}

export function PeerDistributionBars({ features }: PeerDistributionBarsProps) {
  const rows = useMemo(() => features.map(toRow), [features]);

  return (
    <View style={styles.list}>
      {rows.map((row) => (
        <View key={row.key} style={styles.row}>
          <View style={styles.rowHeader}>
            <Text style={styles.rowLabel}>{row.label}</Text>
            <Text style={styles.rowValue}>{row.userValueText}</Text>
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
            <View style={[styles.userDot, { left: `${row.userPosition}%` }]} />
          </View>

          {row.peerMedianText === null ? null : (
            <Text style={styles.rowCaption}>{`또래 중간 ${row.peerMedianText}`}</Text>
          )}
        </View>
      ))}
    </View>
  );
}
