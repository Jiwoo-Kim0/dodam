import { Image } from 'expo-image';
import { Text, View } from 'react-native';

import type { ReportKeyMetric, ReportMetricChangeTone } from '@/types/report.types';
import { formatNumber } from '@/utils/currency';

import { styles as screenStyles } from '../ReportScreen.styles';
import { styles } from './KeyMetricsCard.styles';

type KeyMetricsCardProps = {
  metrics: readonly ReportKeyMetric[];
};

const changeToneStyles: Record<ReportMetricChangeTone, object> = {
  good: styles.metricChangeGood,
  bad: styles.metricChangeBad,
  neutral: styles.metricChangeNeutral,
};

export function KeyMetricsCard({ metrics }: KeyMetricsCardProps) {
  return (
    <View style={[screenStyles.card, styles.metricsCard]}>
      <Text style={screenStyles.cardTitle}>주요 금융 지표</Text>

      <View style={styles.metricGrid}>
        {metrics.map((metric) => (
          <View key={metric.id} style={styles.metricTile}>
            <View style={styles.metricHeader}>
              <Text style={styles.metricLabel}>{metric.label}</Text>
              <View style={styles.metricIconFrame}>
                <Image
                  contentFit="contain"
                  source={metric.icon}
                  style={[styles.metricIcon, { transform: [{ scale: metric.iconScale }] }]}
                />
              </View>
            </View>

            <View style={styles.metricValueRow}>
              <Text adjustsFontSizeToFit numberOfLines={1} style={styles.metricValue}>
                {metric.format === 'percent'
                  ? `${metric.value}%`
                  : formatNumber(metric.value)}
              </Text>
              {metric.format === 'currency' ? (
                <Text style={styles.metricValueUnit}>원</Text>
              ) : null}
            </View>

            <Text
              numberOfLines={1}
              style={[styles.metricChange, changeToneStyles[metric.change.tone]]}>
              {metric.change.direction === 'none'
                ? `· ${metric.change.label}`
                : `${metric.change.direction === 'up' ? '↑' : '↓'} ${metric.change.label}`}
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
}
