import { Text, View } from 'react-native';
import Svg, { Circle, Defs, LinearGradient, Path, Stop } from 'react-native-svg';

import { colors } from '@/styles';
import type { LedgerDailyTotal } from '@/types/ledger.types';
import { formatCurrency } from '@/utils/currency';

import { styles } from './SpendingTrendSparkline.styles';

interface SpendingTrendSparklineProps {
  year: number;
  month: number;
  dailyTotals: readonly LedgerDailyTotal[];
  previousDailyTotals: readonly LedgerDailyTotal[];
}

interface ChartPoint {
  x: number;
  y: number;
}

const CHART_WIDTH = 112;
const CHART_HEIGHT = 52;
const CHART_PADDING = 4;

function getVisibleDayCount(year: number, month: number) {
  const now = new Date();
  const isCurrentMonth = year === now.getFullYear() && month === now.getMonth() + 1;

  return isCurrentMonth ? now.getDate() : new Date(year, month, 0).getDate();
}

function createCumulativeValues(
  dailyTotals: readonly LedgerDailyTotal[],
  dayCount: number,
) {
  const expenseByDay = new Map<number, number>();

  dailyTotals.forEach((dailyTotal) => {
    const day = Number(dailyTotal.date.slice(8, 10));
    if (Number.isInteger(day) && day >= 1 && day <= dayCount) {
      expenseByDay.set(day, (expenseByDay.get(day) ?? 0) + (dailyTotal.expense ?? 0));
    }
  });

  let cumulativeExpense = 0;
  return [
    0,
    ...Array.from({ length: dayCount }, (_, index) => {
      cumulativeExpense += expenseByDay.get(index + 1) ?? 0;
      return cumulativeExpense;
    }),
  ];
}

function createPoints(values: readonly number[], maximum: number): ChartPoint[] {
  const plotWidth = CHART_WIDTH - CHART_PADDING * 2;
  const plotHeight = CHART_HEIGHT - CHART_PADDING * 2;
  const horizontalStep = values.length > 1 ? plotWidth / (values.length - 1) : 0;

  return values.map((value, index) => ({
    x: CHART_PADDING + index * horizontalStep,
    y: CHART_HEIGHT - CHART_PADDING - (value / maximum) * plotHeight,
  }));
}

function createSmoothPath(points: readonly ChartPoint[]) {
  if (points.length === 0) return '';

  return points.slice(1).reduce((path, point, index) => {
    const previous = points[index];
    const controlX = (previous.x + point.x) / 2;
    return `${path} C ${controlX} ${previous.y}, ${controlX} ${point.y}, ${point.x} ${point.y}`;
  }, `M ${points[0].x} ${points[0].y}`);
}

export function SpendingTrendSparkline({
  year,
  month,
  dailyTotals,
  previousDailyTotals,
}: SpendingTrendSparklineProps) {
  const dayCount = getVisibleDayCount(year, month);
  const currentValues = createCumulativeValues(dailyTotals, dayCount);
  const previousValues = createCumulativeValues(previousDailyTotals, dayCount);
  const currentTotal = currentValues.at(-1) ?? 0;
  const previousTotal = previousValues.at(-1) ?? 0;
  const maximum = Math.max(currentTotal, previousTotal);

  if (maximum === 0) return null;

  const hasPreviousData = previousDailyTotals.length > 0;
  const currentLineColor = !hasPreviousData
    ? colors.primary.green
    : currentTotal > previousTotal
      ? colors.ledger.expense
      : currentTotal < previousTotal
        ? colors.ledger.income
        : colors.primary.green;
  const currentPoints = createPoints(currentValues, maximum);
  const previousPoints = createPoints(previousValues, maximum);
  const currentPath = createSmoothPath(currentPoints);
  const previousPath = createSmoothPath(previousPoints);
  const firstPoint = currentPoints[0];
  const lastPoint = currentPoints.at(-1);
  const currentAreaPath = lastPoint
    ? `${currentPath} L ${lastPoint.x} ${CHART_HEIGHT - CHART_PADDING} L ${firstPoint.x} ${CHART_HEIGHT - CHART_PADDING} Z`
    : '';
  const accessibilityLabel = hasPreviousData
    ? `이번 달 누적 소비 ${formatCurrency(currentTotal)}, 지난달 같은 기간 누적 소비 ${formatCurrency(previousTotal)}`
    : `이번 달 누적 소비 ${formatCurrency(currentTotal)}`;

  return (
    <View style={styles.container}>
      <Text style={styles.caption}>지난달 같은 기간</Text>
      <Svg
        accessibilityLabel={accessibilityLabel}
        accessible
        height={CHART_HEIGHT}
        viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
        width={CHART_WIDTH}>
        <Defs>
          <LinearGradient id="currentSpendingArea" x1="0" x2="0" y1="0" y2="1">
            <Stop offset="0" stopColor={currentLineColor} stopOpacity={0.16} />
            <Stop offset="1" stopColor={currentLineColor} stopOpacity={0} />
          </LinearGradient>
        </Defs>
        <Path d={currentAreaPath} fill="url(#currentSpendingArea)" />
        {hasPreviousData ? (
          <Path
            d={previousPath}
            fill="none"
            stroke={colors.textTertiary}
            strokeDasharray="2.5 3.5"
            strokeLinecap="round"
            strokeOpacity={0.38}
            strokeWidth={1}
          />
        ) : null}
        <Path
          d={currentPath}
          fill="none"
          stroke={currentLineColor}
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2.25}
        />
        {lastPoint ? (
          <>
            <Circle cx={lastPoint.x} cy={lastPoint.y} fill={colors.white} r={4} />
            <Circle cx={lastPoint.x} cy={lastPoint.y} fill={currentLineColor} r={2.5} />
          </>
        ) : null}
      </Svg>
      <View style={styles.legend}>
        <View style={styles.legendItem}>
          <View style={[styles.legendLine, { backgroundColor: currentLineColor }]} />
          <Text style={styles.legendLabel}>이번 달</Text>
        </View>
        {hasPreviousData ? (
          <View style={styles.legendItem}>
            <View style={[styles.legendLine, styles.previousLegendLine]} />
            <Text style={styles.legendLabel}>지난달</Text>
          </View>
        ) : null}
      </View>
    </View>
  );
}
