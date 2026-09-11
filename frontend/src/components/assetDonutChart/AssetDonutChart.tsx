import { useMemo } from 'react';
import Svg, { Circle } from 'react-native-svg';

import { colors } from '@/styles';
import type {
  AssetComposition,
  AssetCompositionTone,
} from '@/types/asset.types';

import { styles } from './AssetDonutChart.styles';

type AssetDonutChartProps = {
  composition: readonly AssetComposition[];
};

const CHART_SIZE = 80;
const STROKE_WIDTH = 19;
const RADIUS = (CHART_SIZE - STROKE_WIDTH) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

const chartColors: Record<AssetCompositionTone, string> = {
  strong: colors.home.assetChart.strong,
  medium: colors.home.assetChart.medium,
  light: colors.home.assetChart.light,
};

function createChartSegments(composition: readonly AssetComposition[]) {
  const total = composition.reduce((sum, item) => sum + Math.max(0, item.amount), 0);
  let offset = 0;

  const segments = composition
    .map((item) => {
      if (total === 0 || item.amount <= 0) {
        return null;
      }

      const segmentLength = (item.amount / total) * CIRCUMFERENCE;
      const segment = {
        color: chartColors[item.tone],
        dashArray: `${segmentLength} ${CIRCUMFERENCE - segmentLength}`,
        dashOffset: -offset,
        key: `${item.label}-${item.tone}`,
      };
      offset += segmentLength;
      return segment;
    })
    .filter((segment) => segment !== null);

  return {
    segments,
    total,
  };
}

export function AssetDonutChart({ composition }: AssetDonutChartProps) {
  const chart = useMemo(() => createChartSegments(composition), [composition]);
  const accessibilityLabel = composition
    .map((item) => {
      const percentage =
        chart.total === 0
          ? 0
          : Math.round((Math.max(0, item.amount) / chart.total) * 100);
      return `${item.label} ${percentage}%`;
    })
    .join(', ');

  return (
    <Svg
      accessibilityLabel={accessibilityLabel}
      accessible
      height={CHART_SIZE}
      style={styles.chart}
      viewBox={`0 0 ${CHART_SIZE} ${CHART_SIZE}`}
      width={CHART_SIZE}
    >
      <Circle
        cx={CHART_SIZE / 2}
        cy={CHART_SIZE / 2}
        fill="none"
        r={RADIUS}
        stroke={colors.divider}
        strokeWidth={STROKE_WIDTH}
      />
      {chart.segments.map((segment) => (
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
  );
}
