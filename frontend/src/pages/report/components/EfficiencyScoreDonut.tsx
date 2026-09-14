import { useMemo } from 'react';
import { Text, View } from 'react-native';
import Svg, { Circle } from 'react-native-svg';

import { colors } from '@/styles';
import type { ReportScoreSegment, ReportScoreTone } from '@/types/report.types';

import { CHART_SIZE, STROKE_WIDTH, styles } from './EfficiencyScoreDonut.styles';

type EfficiencyScoreDonutProps = {
  composition: readonly ReportScoreSegment[];
  percentile: number;
};

const RADIUS = (CHART_SIZE - STROKE_WIDTH) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

const segmentColors: Record<ReportScoreTone, string> = {
  strong: colors.report.scoreStrong,
  medium: colors.primary.green,
  light: colors.report.scoreLight,
};

function createSegments(composition: readonly ReportScoreSegment[]) {
  const total = composition.reduce((sum, segment) => sum + Math.max(0, segment.ratio), 0);
  let offset = 0;

  return composition
    .map((segment) => {
      if (total === 0 || segment.ratio <= 0) {
        return null;
      }

      const length = (segment.ratio / total) * CIRCUMFERENCE;
      const drawn = {
        color: segmentColors[segment.tone],
        dashArray: `${length} ${CIRCUMFERENCE - length}`,
        dashOffset: -offset,
        key: segment.id,
      };
      offset += length;
      return drawn;
    })
    .filter((segment) => segment !== null);
}

export function EfficiencyScoreDonut({ composition, percentile }: EfficiencyScoreDonutProps) {
  const segments = useMemo(() => createSegments(composition), [composition]);

  return (
    <View
      accessibilityLabel={`금융 효율 점수 상위 ${percentile}%`}
      accessible
      style={styles.chartFrame}>
      <Svg
        height={CHART_SIZE}
        viewBox={`0 0 ${CHART_SIZE} ${CHART_SIZE}`}
        width={CHART_SIZE}>
        {segments.map((segment) => (
          <Circle
            cx={CHART_SIZE / 2}
            cy={CHART_SIZE / 2}
            fill="none"
            key={segment.key}
            origin={`${CHART_SIZE / 2}, ${CHART_SIZE / 2}`}
            r={RADIUS}
            rotation={-90}
            stroke={segment.color}
            strokeDasharray={segment.dashArray}
            strokeDashoffset={segment.dashOffset}
            strokeWidth={STROKE_WIDTH}
          />
        ))}
      </Svg>

      <View pointerEvents="none" style={styles.center}>
        <Text style={styles.centerLabel}>상위</Text>
        <Text style={styles.centerValue}>{percentile}%</Text>
      </View>
    </View>
  );
}
