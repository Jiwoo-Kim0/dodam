import { useMemo, useState } from 'react';
import { Text, View } from 'react-native';
import Svg, { Circle, G, Line, Path, Text as SvgText } from 'react-native-svg';

import { colors, fontFamily } from '@/styles';
import type { SpendingForecast } from '@/types/forecastApi.types';
import { formatNumber } from '@/utils/currency';

import { styles as screenStyles } from '../ReportScreen.styles';
import {
  CHART_HEIGHT,
  CHART_WIDTH,
  GRID_LINE_COUNT,
  PLOT_BOTTOM,
  PLOT_LEFT,
  PLOT_RIGHT,
  PLOT_TOP,
  styles,
} from './SpendingForecastCard.styles';

type SpendingForecastCardProps = {
  forecast: SpendingForecast;
};

type ChartPoint = {
  month: string;
  label: string;
  amount: number;
  lowerBound: number;
  upperBound: number;
  predicted: boolean;
  x: number;
  y: number;
  lowerY: number;
  upperY: number;
};

function monthLabel(month: string) {
  return `${Number(month.slice(5, 7))}월`;
}

/** 축 최댓값. 상한 띠까지 담아야 띠가 잘리지 않는다. */
function axisMaximumOf(amounts: readonly number[]) {
  const maximum = amounts.reduce((current, amount) => Math.max(current, amount), 0);
  if (maximum === 0) {
    return 1;
  }
  const unit = maximum >= 1_000_000 ? 500_000 : 100_000;
  return Math.ceil(maximum / unit) * unit;
}

export function SpendingForecastCard({ forecast }: SpendingForecastCardProps) {
  const { history, forecasts } = forecast;

  const points = useMemo<ChartPoint[]>(() => {
    const merged = [
      ...history.map((point) => ({
        month: point.month,
        amount: point.amount,
        lowerBound: point.amount,
        upperBound: point.amount,
        predicted: false,
      })),
      ...forecasts.map((point) => ({
        month: point.month,
        amount: point.predicted,
        lowerBound: point.lowerBound,
        upperBound: point.upperBound,
        predicted: true,
      })),
    ];
    const axisMaximum = axisMaximumOf(merged.map((point) => point.upperBound));
    const step = merged.length > 1 ? (PLOT_RIGHT - PLOT_LEFT) / (merged.length - 1) : 0;
    const toY = (amount: number) =>
      PLOT_BOTTOM - (Math.max(0, amount) / axisMaximum) * (PLOT_BOTTOM - PLOT_TOP);

    return merged.map((point, index) => ({
      ...point,
      label: monthLabel(point.month),
      x: PLOT_LEFT + step * index,
      y: toY(point.amount),
      lowerY: toY(point.lowerBound),
      upperY: toY(point.upperBound),
    }));
  }, [forecasts, history]);

  // 기본 선택은 마지막 예측 달이다. 손대지 않아도 화면이 말하려는 숫자가 먼저 보인다.
  const [selectedMonth, setSelectedMonth] = useState<string | null>(null);
  const selected = points.find((point) => point.month === selectedMonth) ?? points.at(-1);

  if (points.length === 0 || forecasts.length === 0) {
    return (
      <View style={screenStyles.card}>
        <View style={styles.header}>
          <Text style={screenStyles.cardTitle}>예상 소비</Text>
        </View>
        <Text style={styles.emptyBody}>
          예측을 만들려면 최근 거래 내역이 더 필요해요. 거래가 쌓이면 다음 달 소비를 알려드릴게요.
        </Text>
      </View>
    );
  }

  const axisMaximum = axisMaximumOf(points.map((point) => point.upperBound));
  const lastActual = points.filter((point) => !point.predicted).at(-1) ?? points[0];
  const actualPoints = points.filter((point) => !point.predicted);
  // 예측선은 마지막 실측에서 이어 그린다. 끊어 그리면 두 선이 다른 이야기로 읽힌다.
  const predictedPoints = [lastActual, ...points.filter((point) => point.predicted)];

  const toPath = (list: readonly ChartPoint[]) =>
    list.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ');
  const bandPath = [
    ...predictedPoints.map(
      (point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.upperY}`,
    ),
    ...[...predictedPoints].reverse().map((point) => `L ${point.x} ${point.lowerY}`),
    'Z',
  ].join(' ');

  const accessibilityLabel = `예상 소비. ${points
    .map(
      (point) =>
        `${point.label} ${point.predicted ? '예상' : '실제'} ${formatNumber(point.amount)}원`,
    )
    .join(', ')}`;

  return (
    <View style={screenStyles.card}>
      <View style={styles.header}>
        <Text style={screenStyles.cardTitle}>예상 소비</Text>
        <Text style={styles.headerCaption}>{`앞으로 ${forecasts.length}개월`}</Text>
      </View>

      <Svg
        accessibilityLabel={accessibilityLabel}
        accessible
        height={CHART_HEIGHT}
        style={styles.chart}
        viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
        width="100%">
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
                fontSize={9}
                textAnchor="end"
                x={PLOT_LEFT - 6}
                y={y + 3}>
                {amount >= 10_000 ? `${Math.round(amount / 10_000)}만` : `${amount}`}
              </SvgText>
            </G>
          );
        })}

        <Path d={bandPath} fill={colors.report.forecastLiving} opacity={0.75} />

        <Line
          stroke={colors.border}
          strokeDasharray="3 3"
          strokeWidth={1}
          x1={lastActual.x}
          x2={lastActual.x}
          y1={PLOT_TOP - 6}
          y2={PLOT_BOTTOM}
        />
        <SvgText
          fill={colors.textTertiary}
          fontFamily={fontFamily.medium}
          fontSize={9}
          textAnchor="middle"
          x={lastActual.x}
          y={PLOT_TOP - 10}>
          오늘
        </SvgText>

        <Path
          d={toPath(actualPoints)}
          fill="none"
          stroke={colors.report.scoreStrong}
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
        />
        <Path
          d={toPath(predictedPoints)}
          fill="none"
          stroke={colors.report.scoreStrong}
          strokeDasharray="5 4"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
        />

        {points.map((point) => {
          const isSelected = point.month === selected?.month;
          if (!point.predicted && !isSelected) {
            return null;
          }
          return (
            <Circle
              cx={point.x}
              cy={point.y}
              fill={colors.report.scoreStrong}
              key={`point-${point.month}`}
              r={isSelected ? 5 : 4}
              stroke={colors.white}
              strokeWidth={2}
            />
          );
        })}

        {points.map((point, index) =>
          index % 3 === 0 || index === points.length - 1 ? (
            <SvgText
              fill={colors.textTertiary}
              fontFamily={fontFamily.regular}
              fontSize={9}
              key={`label-${point.month}`}
              textAnchor="middle"
              x={point.x}
              y={PLOT_BOTTOM + 14}>
              {point.label}
            </SvgText>
          ) : null,
        )}

        {points.map((point) => (
          <Circle
            cx={point.x}
            cy={point.y}
            fill="transparent"
            key={`touch-${point.month}`}
            onPress={() => setSelectedMonth(point.month)}
            r={14}
          />
        ))}

        {selected ? (
          <SvgText
            fill={colors.textPrimary}
            fontFamily={fontFamily.semiBold}
            fontSize={12}
            textAnchor="middle"
            x={Math.min(CHART_WIDTH - 46, Math.max(46, selected.x))}
            y={Math.max(PLOT_TOP - 12, selected.y - 12)}>
            {`${selected.label} ${formatNumber(selected.amount)}원`}
          </SvgText>
        ) : null}
      </Svg>

      <View style={styles.legend}>
        <View style={styles.legendItem}>
          <View style={styles.legendSolid} />
          <Text style={styles.legendLabel}>실제 소비</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={styles.legendDashed} />
          <Text style={styles.legendLabel}>예상 소비</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={styles.legendBand} />
          <Text style={styles.legendLabel}>예상 범위</Text>
        </View>
      </View>

      <Text style={styles.note}>
        지금까지의 소비 흐름으로 계산한 예상치예요. 실제 소비는 달라질 수 있어요.
      </Text>
    </View>
  );
}
