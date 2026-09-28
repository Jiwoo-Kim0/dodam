import { useState } from 'react';
import { Text, View } from 'react-native';
import Svg, {
  Circle,
  G,
  Line,
  Path,
  Rect,
  Text as SvgText,
} from 'react-native-svg';

import { colors, fontFamily } from '@/styles';
import type { GoalDetail, GoalHistoryPoint } from '@/types/goal.types';
import { formatNumber } from '@/utils/currency';

import { styles } from './GoalProgressChart.styles';

interface GoalProgressChartProps {
  goal: GoalDetail;
}

const CHART_WIDTH = 320;
const CHART_HEIGHT = 180;
const PLOT_LEFT = 48;
const PLOT_RIGHT = 316;
const PLOT_TOP = 64;
const PLOT_BOTTOM = 144;
const GRID_LINE_COUNT = 5;
const BAR_WIDTH = 14;

function calculateAxisMaximum(history: readonly GoalHistoryPoint[]) {
  const maximum = history.reduce(
    (currentMaximum, point) =>
      Math.max(currentMaximum, point.actualAmount, point.plannedAmount),
    0,
  );
  const roundingUnit = maximum >= 1_000_000 ? 100_000 : 50_000;
  const step = Math.max(
    roundingUnit,
    Math.ceil(maximum / (GRID_LINE_COUNT - 1) / roundingUnit) * roundingUnit,
  );

  return step * (GRID_LINE_COUNT - 1);
}

function createPointCoordinates(
  history: readonly GoalHistoryPoint[],
  axisMaximum: number,
) {
  const horizontalStep =
    history.length > 1
      ? (PLOT_RIGHT - PLOT_LEFT) / (history.length - 1)
      : 0;
  const getY = (amount: number) =>
    PLOT_BOTTOM - (Math.max(0, amount) / axisMaximum) * (PLOT_BOTTOM - PLOT_TOP);

  return history.map((point, index) => ({
    ...point,
    x: PLOT_LEFT + horizontalStep * index,
    actualY: getY(point.actualAmount),
    plannedY: getY(point.plannedAmount),
  }));
}

function formatPointDate(label: string) {
  const [month, day] = label.split('.');
  return `${Number(month)}월 ${Number(day)}일 기준`;
}

export function GoalProgressChart({ goal }: GoalProgressChartProps) {
  const [selectedPointLabel, setSelectedPointLabel] = useState<string | null>(
    null,
  );
  const title =
    goal.type === 'saving'
      ? '지금까지 이렇게 모았어요'
      : '이번 달 이렇게 줄였어요';
  const lineColor =
    goal.type === 'saving' ? colors.primary.yellow : colors.primary.orange;
  const barColor =
    goal.type === 'saving'
      ? colors.goal.savingCharacterBackground
      : colors.primary.yellow;
  const axisMaximum = calculateAxisMaximum(goal.history);
  const points = createPointCoordinates(goal.history, axisMaximum);
  const linePath = points
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.actualY}`)
    .join(' ');
  const selectedPoint =
    points.find((point) => point.label === selectedPointLabel) ?? points.at(-1);
  const tooltipWidth = 108;
  const tooltipHeight = 40;
  const tooltipX = selectedPoint
    ? Math.min(
        CHART_WIDTH - tooltipWidth - 4,
        Math.max(4, selectedPoint.x - tooltipWidth / 2),
      )
    : 0;
  const tooltipY = 8;
  const accessibilityLabel = `${title}. ${goal.history
    .map(
      (point) =>
        `${point.label} 실제 누적 금액 ${formatNumber(point.actualAmount)}원, 계획 누적 금액 ${formatNumber(point.plannedAmount)}원`,
    )
    .join(', ')}`;

  return (
    <View style={styles.card}>
      <View style={styles.header}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.unit}>(단위: 원)</Text>
      </View>

      <Svg
        accessibilityLabel={accessibilityLabel}
        accessible
        height={CHART_HEIGHT}
        style={styles.chart}
        viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
        width="100%"
      >
        {Array.from({ length: GRID_LINE_COUNT }, (_, index) => {
          const ratio = index / (GRID_LINE_COUNT - 1);
          const y = PLOT_TOP + ratio * (PLOT_BOTTOM - PLOT_TOP);
          const amount = Math.round(axisMaximum * (1 - ratio));

          return (
            <G key={`grid-${amount}`}>
              <Line
                stroke={colors.divider}
                strokeWidth={1}
                x1={PLOT_LEFT}
                x2={PLOT_RIGHT}
                y1={y}
                y2={y}
              />
              <SvgText
                fill={colors.textTertiary}
                fontFamily={fontFamily.regular}
                fontSize={8}
                textAnchor="end"
                x={PLOT_LEFT - 8}
                y={y + 3}
              >
                {formatNumber(amount)}
              </SvgText>
            </G>
          );
        })}

        {points.map((point, index) => (
          <Rect
            fill={
              index === points.length - 1
                ? colors.home.secondaryRank
                : barColor
            }
            height={PLOT_BOTTOM - point.plannedY}
            key={`bar-${point.label}`}
            rx={3}
            width={BAR_WIDTH}
            x={point.x - BAR_WIDTH / 2}
            y={point.plannedY}
          />
        ))}

        {linePath ? (
          <Path
            d={linePath}
            fill="none"
            stroke={lineColor}
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
          />
        ) : null}

        {points.map((point) => (
          <Circle
            cx={point.x}
            cy={point.actualY}
            fill={lineColor}
            key={`point-${point.label}`}
            r={point.label === selectedPoint?.label ? 5 : 3.5}
            stroke={colors.white}
            strokeWidth={point.label === selectedPoint?.label ? 2 : 0}
          />
        ))}

        {points.map((point) => (
          <Circle
            cx={point.x}
            cy={point.actualY}
            fill="transparent"
            key={`touch-${point.label}`}
            onPress={() => setSelectedPointLabel(point.label)}
            r={14}
          />
        ))}

        {points.map((point) => (
          <SvgText
            fill={colors.textTertiary}
            fontFamily={fontFamily.regular}
            fontSize={9}
            key={`label-${point.label}`}
            textAnchor="middle"
            x={point.x}
            y={PLOT_BOTTOM + 18}
          >
            {point.label}
          </SvgText>
        ))}

        {selectedPoint ? (
          <>
            <Line
              stroke={lineColor}
              strokeWidth={1}
              x1={selectedPoint.x}
              x2={selectedPoint.x}
              y1={tooltipY + tooltipHeight}
              y2={selectedPoint.actualY - 6}
            />
            <Rect
              fill={colors.primary.lightGreen}
              height={tooltipHeight}
              rx={8}
              width={tooltipWidth}
              x={tooltipX}
              y={tooltipY}
            />
            <SvgText
              fill={colors.textSecondary}
              fontFamily={fontFamily.regular}
              fontSize={8}
              textAnchor="middle"
              x={tooltipX + tooltipWidth / 2}
              y={tooltipY + 14}
            >
              {selectedPoint.tooltipLabel ?? formatPointDate(selectedPoint.label)}
            </SvgText>
            <SvgText
              fill={lineColor}
              fontFamily={fontFamily.semiBold}
              fontSize={11}
              textAnchor="middle"
              x={tooltipX + tooltipWidth / 2}
              y={tooltipY + 30}
            >
              {`${formatNumber(selectedPoint.actualAmount)}원`}
            </SvgText>
          </>
        ) : null}
      </Svg>

      <View style={styles.legend}>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: lineColor }]} />
          <Text style={styles.legendLabel}>실제 금액</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendBar, { backgroundColor: barColor }]} />
          <Text style={styles.legendLabel}>계획 금액</Text>
        </View>
      </View>
    </View>
  );
}
