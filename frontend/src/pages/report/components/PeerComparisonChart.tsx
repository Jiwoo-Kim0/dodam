import { useMemo } from 'react';
import { View } from 'react-native';
import Svg, { Circle, Polygon, Text as SvgText } from 'react-native-svg';

import { colors, fontFamily, fontSize } from '@/styles';
import type { ReportPeerFeature } from '@/types/report.types';

import { CHART_SIZE, styles } from './PeerComparisonChart.styles';

type PeerComparisonChartProps = {
  features: readonly ReportPeerFeature[];
};

const CENTER = CHART_SIZE / 2;
/** 축 라벨이 들어갈 자리를 남기고 남은 반지름이다. */
const MAX_RADIUS = 66;
const LABEL_RADIUS = MAX_RADIUS + 20;
/** 또래 중앙값은 백분위 정의상 항상 50이고, 방향을 뒤집어도 50이라 기준선이 정오각형으로 그려진다. */
const MEDIAN_VALUE = 50;

type Point = { x: number; y: number };

/** 12시 방향에서 시작해 시계 방향으로 축을 배치한다. */
function pointAt(index: number, count: number, radius: number): Point {
  const angle = -Math.PI / 2 + (index * 2 * Math.PI) / count;
  return {
    x: CENTER + radius * Math.cos(angle),
    y: CENTER + radius * Math.sin(angle),
  };
}

function toPolygon(points: readonly Point[]) {
  return points.map(({ x, y }) => `${x},${y}`).join(' ');
}

function radiusFor(value: number) {
  return (MAX_RADIUS * Math.min(100, Math.max(0, value))) / 100;
}


export function PeerComparisonChart({ features }: PeerComparisonChartProps) {
  const chart = useMemo(() => {
    const count = features.length;

    return {
      labels: features.map((feature, index) => ({
        key: feature.key,
        label: feature.label,
        point: pointAt(index, count, LABEL_RADIUS),
      })),
      medianPolygon: toPolygon(
        features.map((_, index) => pointAt(index, count, radiusFor(MEDIAN_VALUE))),
      ),
      outerPolygon: toPolygon(features.map((_, index) => pointAt(index, count, MAX_RADIUS))),
      userPolygon: toPolygon(
        features.map((feature, index) => pointAt(index, count, radiusFor(feature.axisValue))),
      ),
      userPoints: features.map((feature, index) => ({
        key: feature.key,
        ...pointAt(index, count, radiusFor(feature.axisValue)),
      })),
    };
  }, [features]);

  return (
    <View style={styles.chartFrame}>
      <Svg height={CHART_SIZE} viewBox={`0 0 ${CHART_SIZE} ${CHART_SIZE}`} width={CHART_SIZE}>
        {/* 바깥 테두리만 남기고 격자와 축선은 두지 않는다. 선이 많을수록 도형이 묻힌다. */}
        <Polygon
          fill="none"
          points={chart.outerPolygon}
          stroke={colors.divider}
          strokeLinejoin="round"
          strokeWidth={1}
        />

        <Polygon
          fill="none"
          points={chart.medianPolygon}
          stroke={colors.textTertiary}
          strokeDasharray="2.5 3.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeOpacity={0.38}
          strokeWidth={1}
        />

        <Polygon
          fill={colors.primary.green}
          fillOpacity={0.2}
          points={chart.userPolygon}
          stroke={colors.primary.green}
          strokeLinejoin="round"
          strokeWidth={2.25}
        />

        {chart.userPoints.map((point) => (
          <Circle cx={point.x} cy={point.y} fill={colors.white} key={`halo-${point.key}`} r={4} />
        ))}
        {chart.userPoints.map((point) => (
          <Circle
            cx={point.x}
            cy={point.y}
            fill={colors.primary.green}
            key={`point-${point.key}`}
            r={2.5}
          />
        ))}

        {chart.labels.map((axis) => (
          <SvgText
            fill={colors.textSecondary}
            fontFamily={fontFamily.regular}
            fontSize={fontSize.detail}
            key={`label-${axis.key}`}
            textAnchor="middle"
            x={axis.point.x}
            y={axis.point.y + 4}>
            {axis.label}
          </SvgText>
        ))}
      </Svg>
    </View>
  );
}
