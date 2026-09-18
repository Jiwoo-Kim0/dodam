import { Text, View } from 'react-native';

import type { ReportScoreDimension, ReportScoreDimensionStatus } from '@/types/report.types';

import { styles } from './EfficiencyDimensionList.styles';

type EfficiencyDimensionListProps = {
  dimensions: readonly ReportScoreDimension[];
};

const statusFillStyles: Record<ReportScoreDimensionStatus, object> = {
  good: styles.fillGood,
  normal: styles.fillNormal,
  check: styles.fillCheck,
  unavailable: styles.fillUnavailable,
};

const statusTextStyles: Record<ReportScoreDimensionStatus, object> = {
  good: styles.statusGood,
  normal: styles.statusNormal,
  check: styles.statusCheck,
  unavailable: styles.statusUnavailable,
};

const statusLabels: Record<ReportScoreDimensionStatus, string> = {
  good: '좋아요',
  normal: '보통이에요',
  check: '점검이 필요해요',
  unavailable: '계산 불가',
};

export function EfficiencyDimensionList({ dimensions }: EfficiencyDimensionListProps) {
  return (
    <View style={styles.list}>
      {dimensions.map((dimension) => (
        <View key={dimension.key} style={styles.row}>
          <View style={styles.rowHeader}>
            <Text style={styles.rowLabel}>{dimension.label}</Text>

            <View style={styles.rowValueGroup}>
              <Text style={[styles.rowStatus, statusTextStyles[dimension.status]]}>
                {statusLabels[dimension.status]}
              </Text>
              <Text
                style={[
                  styles.rowScore,
                  dimension.score === null && styles.rowScoreUnavailable,
                ]}>
                {dimension.score === null ? '-' : `${dimension.score}점`}
              </Text>
            </View>
          </View>

          <View style={styles.track}>
            {dimension.score === null ? null : (
              <View
                style={[
                  styles.fill,
                  statusFillStyles[dimension.status],
                  { width: `${Math.min(100, Math.max(0, dimension.score))}%` },
                ]}
              />
            )}
          </View>

          <Text style={styles.rowDescription}>{dimension.description}</Text>
        </View>
      ))}
    </View>
  );
}
