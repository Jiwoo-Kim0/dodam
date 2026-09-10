import { Image } from 'expo-image';
import { useMemo } from 'react';

import type { AssetComposition, AssetCompositionTone } from '@/mocks/home';
import { colors } from '@/styles';

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

function createChartSource(composition: readonly AssetComposition[]) {
  const total = composition.reduce((sum, item) => sum + Math.max(0, item.amount), 0);
  let offset = 0;

  const segments = composition
    .map((item) => {
      if (total === 0 || item.amount <= 0) {
        return '';
      }

      const segmentLength = (item.amount / total) * CIRCUMFERENCE;
      const segment = `<circle cx="40" cy="40" r="${RADIUS}" fill="none" stroke="${chartColors[item.tone]}" stroke-width="${STROKE_WIDTH}" stroke-dasharray="${segmentLength} ${CIRCUMFERENCE - segmentLength}" stroke-dashoffset="${-offset}" transform="rotate(-90 40 40)" />`;
      offset += segmentLength;
      return segment;
    })
    .join('');

  const svg = `<svg width="80" height="80" viewBox="0 0 80 80" xmlns="http://www.w3.org/2000/svg"><circle cx="40" cy="40" r="${RADIUS}" fill="none" stroke="${colors.divider}" stroke-width="${STROKE_WIDTH}" />${segments}</svg>`;

  return {
    total,
    uri: `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`,
  };
}

export function AssetDonutChart({ composition }: AssetDonutChartProps) {
  const chart = useMemo(() => createChartSource(composition), [composition]);
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
    <Image
      accessibilityLabel={accessibilityLabel}
      accessible
      contentFit="contain"
      source={{ uri: chart.uri }}
      style={styles.chart}
    />
  );
}
