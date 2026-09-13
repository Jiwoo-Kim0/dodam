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
  size?: number;
  strokeWidth?: number;
};

const DEFAULT_CHART_SIZE = 80;
const DEFAULT_STROKE_WIDTH = 19;

const chartColors: Record<AssetCompositionTone, string> = {
  strong: colors.home.assetChart.strong,
  medium: colors.home.assetChart.medium,
  light: colors.home.assetChart.light,
};

function createChartSegments(
  composition: readonly AssetComposition[],
  circumference: number,
) {
  const total = composition.reduce((sum, item) => sum + Math.max(0, item.amount), 0);
  let offset = 0;

  const segments = composition
    .map((item) => {
      if (total === 0 || item.amount <= 0) {
        return null;
      }

      const segmentLength = (item.amount / total) * circumference;
      const segment = {
        color: chartColors[item.tone],
        dashArray: `${segmentLength} ${circumference - segmentLength}`,
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

export function AssetDonutChart({
  composition,
  size = DEFAULT_CHART_SIZE,
  strokeWidth = DEFAULT_STROKE_WIDTH,
}: AssetDonutChartProps) {
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const chart = useMemo(
    () => createChartSegments(composition, circumference),
    [composition, circumference],
  );
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
      height={size}
      style={[styles.chart, { height: size, width: size }]}
      viewBox={`0 0 ${size} ${size}`}
      width={size}
    >
      <Circle
        cx={size / 2}
        cy={size / 2}
        fill="none"
        r={radius}
        stroke={colors.divider}
        strokeWidth={strokeWidth}
      />
      {chart.segments.map((segment) => (
        <Circle
          cx={size / 2}
          cy={size / 2}
          fill="none"
          key={segment.key}
          origin={`${size / 2}, ${size / 2}`}
          r={radius}
          rotation={-90}
          stroke={segment.color}
          strokeDasharray={segment.dashArray}
          strokeDashoffset={segment.dashOffset}
          strokeWidth={strokeWidth}
        />
      ))}
    </Svg>
  );
}
