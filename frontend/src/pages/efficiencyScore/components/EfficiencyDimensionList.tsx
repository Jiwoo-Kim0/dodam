import { Text, View } from 'react-native';
import Svg, { Circle } from 'react-native-svg';

import { colors } from '@/styles';
import type { ReportScoreDimension, ReportScoreDimensionStatus } from '@/types/report.types';

import { styles } from './EfficiencyDimensionList.styles';

type EfficiencyDimensionListProps = {
  dimensions: readonly ReportScoreDimension[];
};

const statusTextStyles: Record<ReportScoreDimensionStatus, object> = {
  good: styles.statusGood,
  normal: styles.statusNormal,
  check: styles.statusCheck,
  unavailable: styles.statusUnavailable,
};

const statusColors: Record<ReportScoreDimensionStatus, string> = {
  good: colors.home.assetChart.medium,
  normal: colors.report.scoreLight,
  check: colors.primary.orange,
  unavailable: colors.divider,
};

const GAUGE_SIZE = 52;
const GAUGE_STROKE_WIDTH = 5;
const GAUGE_CENTER = GAUGE_SIZE / 2;
const GAUGE_RADIUS = (GAUGE_SIZE - GAUGE_STROKE_WIDTH) / 2;
const GAUGE_CIRCUMFERENCE = 2 * Math.PI * GAUGE_RADIUS;

const statusLabels: Record<ReportScoreDimensionStatus, string> = {
  good: '좋아요',
  normal: '보통이에요',
  check: '점검이 필요해요',
  unavailable: '계산 불가',
};

export function EfficiencyDimensionList({ dimensions }: EfficiencyDimensionListProps) {
  return (
    <View style={styles.list}>
      {dimensions.map((dimension, index) => {
        const normalizedScore = Math.min(100, Math.max(0, dimension.score ?? 0));
        const dashOffset = GAUGE_CIRCUMFERENCE * (1 - normalizedScore / 100);

        return (
          <View
            key={dimension.key}
            style={[styles.row, index < dimensions.length - 1 && styles.rowDivider]}>
            <View style={styles.gauge}>
              <Svg height={GAUGE_SIZE} width={GAUGE_SIZE}>
                <Circle
                  cx={GAUGE_CENTER}
                  cy={GAUGE_CENTER}
                  fill="none"
                  r={GAUGE_RADIUS}
                  stroke={colors.ledger.categoryTrack}
                  strokeWidth={GAUGE_STROKE_WIDTH}
                />
                {dimension.score === null ? null : (
                  <Circle
                    cx={GAUGE_CENTER}
                    cy={GAUGE_CENTER}
                    fill="none"
                    r={GAUGE_RADIUS}
                    rotation={-90}
                    origin={`${GAUGE_CENTER}, ${GAUGE_CENTER}`}
                    stroke={statusColors[dimension.status]}
                    strokeDasharray={`${GAUGE_CIRCUMFERENCE} ${GAUGE_CIRCUMFERENCE}`}
                    strokeDashoffset={dashOffset}
                    strokeLinecap="round"
                    strokeWidth={GAUGE_STROKE_WIDTH}
                  />
                )}
              </Svg>
              <Text
                style={[
                  styles.gaugeScore,
                  dimension.score === null && styles.gaugeScoreUnavailable,
                ]}>
                {dimension.score ?? '-'}
              </Text>
            </View>

            <View style={styles.rowContent}>
              <View style={styles.rowHeader}>
                <Text style={styles.rowLabel}>{dimension.label}</Text>
                <Text style={[styles.rowStatus, statusTextStyles[dimension.status]]}>
                  {statusLabels[dimension.status]}
                </Text>
              </View>
              <Text style={styles.rowDescription}>{dimension.description}</Text>
            </View>
          </View>
        );
      })}
    </View>
  );
}
