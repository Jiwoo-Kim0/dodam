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
const CHART_HEIGHT = 204;
const PLOT_LEFT = 48;
const PLOT_RIGHT = 270;
const PLOT_TOP = 76;
const PLOT_BOTTOM = 164;
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

export function GoalProgressChart({ goal }: GoalProgressChartProps) {
  const title =
    goal.type === 'saving'
      ? '지금까지 이렇게 모았어요'
      : '이번 달 이렇게 줄였어요';
  const lineColor =
    goal.type === 'saving' ? colors.primary.green : colors.primary.orange;
  const barColor =
    goal.type === 'saving' ? colors.home.secondaryRank : colors.primary.yellow;
  const axisMaximum = calculateAxisMaximum(goal.history);
  const points = createPointCoordinates(goal.history, axisMaximum);
  const linePath = points
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.actualY}`)
    .join(' ');
  const lastPoint = points.at(-1);
  const tooltipWidth = 88;
  const tooltipHeight = 40;
  const tooltipX = lastPoint
    ? Math.min(
        CHART_WIDTH - tooltipWidth - 4,
        Math.max(4, lastPoint.x - tooltipWidth / 2),
      )
    : 0;
  const tooltipY = 12;
  const accessibilityLabel = `${title}. ${goal.history
    .map(
      (point) =>
        `${point.label} 누적 금액 ${formatNumber(point.actualAmount)}원, 월별 금액 ${formatNumber(point.plannedAmount)}원`,
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
              goal.type === 'reducing' && index === points.length - 1
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
            r={3.5}
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

        {lastPoint ? (
          <>
            <Line
              stroke={lineColor}
              strokeWidth={1}
              x1={lastPoint.x}
              x2={lastPoint.x}
              y1={tooltipY + tooltipHeight}
              y2={lastPoint.actualY - 5}
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
              현재까지
            </SvgText>
            <SvgText
              fill={lineColor}
              fontFamily={fontFamily.semiBold}
              fontSize={11}
              textAnchor="middle"
              x={tooltipX + tooltipWidth / 2}
              y={tooltipY + 29}
            >
              {`${formatNumber(lastPoint.actualAmount)}원`}
            </SvgText>
          </>
        ) : null}
      </Svg>

      <View style={styles.legend}>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: lineColor }]} />
          <Text style={styles.legendLabel}>누적 금액</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendBar, { backgroundColor: barColor }]} />
          <Text style={styles.legendLabel}>월별 금액</Text>
        </View>
      </View>
    </View>
  );
}
