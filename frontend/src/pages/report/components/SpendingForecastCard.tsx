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
  monthsFromStart: number;
  x: number;
  y: number;
  lowerY: number;
  upperY: number;
};

function monthLabel(month: string) {
  return `${Number(month.slice(5, 7))}월`;
}

/**
 * 축 최댓값. 상한 띠까지 담아야 띠가 잘리지 않는다.
 *
 * 눈금 간격을 먼저 반올림 단위에 맞춘 뒤 구간 수를 곱한다. 최댓값만 올림하면 200만을 셋으로 나눠
 * 67만·133만 같은 눈금이 나온다.
 */
function axisMaximumOf(amounts: readonly number[]) {
  const maximum = amounts.reduce((current, amount) => Math.max(current, amount), 0);
  if (maximum === 0) {
    return 1;
  }
  const unit = maximum >= 1_000_000 ? 500_000 : 100_000;
  const intervals = GRID_LINE_COUNT - 1;
  const step = Math.max(unit, Math.ceil(maximum / intervals / unit) * unit);
  return step * intervals;
}

/** 증감 금액. 0은 부호 없이 둔다 */
function signedWon(amount: number) {
  const sign = amount > 0 ? '+' : amount < 0 ? '-' : '';
  return `${sign}${formatNumber(Math.abs(amount))}원`;
}

/** `2026-09` -> 연속된 월 번호. x좌표를 배열 순서가 아니라 시간으로 잡는 데 쓴다 */
function monthOrdinal(month: string) {
  return Number(month.slice(0, 4)) * 12 + Number(month.slice(5, 7));
}

export function SpendingForecastCard({ forecast }: SpendingForecastCardProps) {
  const { baseMonth, explanation, history, forecasts } = forecast;

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
    // x는 배열 순서가 아니라 첫 달로부터의 개월 수다. 순서로 잡으면 기준 달(9월)처럼 값이 없는 달이
    // 축에서 사라져 8월과 10월이 한 칸 간격으로 붙고, 결측이 있는 사용자도 같은 왜곡을 겪는다.
    const firstOrdinal = monthOrdinal(merged[0].month);
    const span = monthOrdinal(merged[merged.length - 1].month) - firstOrdinal;
    const toY = (amount: number) =>
      PLOT_BOTTOM - (Math.max(0, amount) / axisMaximum) * (PLOT_BOTTOM - PLOT_TOP);

    return merged.map((point) => ({
      ...point,
      label: monthLabel(point.month),
      monthsFromStart: monthOrdinal(point.month) - firstOrdinal,
      x:
        span === 0
          ? PLOT_LEFT
          : PLOT_LEFT + ((monthOrdinal(point.month) - firstOrdinal) / span) * (PLOT_RIGHT - PLOT_LEFT),
      y: toY(point.amount),
      lowerY: toY(point.lowerBound),
      upperY: toY(point.upperBound),
    }));
  }, [forecasts, history]);

  // 기본 선택은 첫 예측 달, 즉 다음 달이다. 이 카드가 답하는 질문이 "다음 달에 얼마나 쓸까"인데
  // 마지막 예측 달을 먼저 띄우면 9월에 보는 사람이 12월 예상치를 다음 달 예상으로 읽는다.
  const [selectedMonth, setSelectedMonth] = useState<string | null>(null);
  const selected =
    points.find((point) => point.month === selectedMonth) ??
    points.find((point) => point.predicted) ??
    points.at(-1);

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

  // 근거는 예측 달을 골랐을 때만 보여 준다. 실측 달에는 분해할 모델 값이 없다.
  const basis = selected?.predicted
    ? forecasts.find((point) => point.month === selected.month)
    : undefined;

  const axisMaximum = axisMaximumOf(points.map((point) => point.upperBound));
  const lastActual = points.filter((point) => !point.predicted).at(-1) ?? points[0];
  const actualPoints = points.filter((point) => !point.predicted);
  // 예측선은 마지막 실측에서 이어 그린다. 끊어 그리면 두 선이 다른 이야기로 읽힌다.
  const predictedPoints = [lastActual, ...points.filter((point) => point.predicted)];
  // "오늘" 기준선은 기준 달(진행 중인 이번 달)에 세운다. 마지막 실측 달에 세우면 9월에 열어도 선이 8월에
  // 붙어 이번 달이 이미 끝난 것처럼 읽힌다. 기준 달은 실측에도 예측에도 없으므로 x를 직접 잰다.
  const firstOrdinal = monthOrdinal(points[0].month);
  const span = monthOrdinal(points[points.length - 1].month) - firstOrdinal;
  const baseRatio = span === 0 ? 0 : (monthOrdinal(baseMonth) - firstOrdinal) / span;
  const todayX = PLOT_LEFT + Math.min(1, Math.max(0, baseRatio)) * (PLOT_RIGHT - PLOT_LEFT);

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
        <View style={styles.titleGroup}>
          <Text style={screenStyles.cardTitle}>예상 소비</Text>
          <Text style={styles.unit}>(단위: 원)</Text>
        </View>
        <Text style={styles.headerCaption}>{`앞으로 ${forecasts.length}개월`}</Text>
      </View>

      {selected ? (
        <Text style={styles.summary}>
          {`${selected.label} ${selected.predicted ? '예상' : '실제'} `}
          <Text style={styles.summaryAmount}>{`${formatNumber(selected.amount)}원`}</Text>
        </Text>
      ) : null}

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
          x1={todayX}
          x2={todayX}
          y1={PLOT_TOP - 6}
          y2={PLOT_BOTTOM}
        />
        <SvgText
          fill={colors.textTertiary}
          fontFamily={fontFamily.medium}
          fontSize={9}
          textAnchor="middle"
          x={todayX}
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
          point.monthsFromStart % 3 === 0 || index === points.length - 1 ? (
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

      {basis && explanation && selected ? (
        // 서버가 평소 수준 + 추세 영향 + 계절 영향 = 예상이 원 단위로 맞도록 내려 준다. 화면에서 다시 계산하지 않는다.
        <View style={styles.basis}>
          <Text style={styles.basisTitle}>{`${selected.label} 예상 근거`}</Text>
          <View style={styles.basisRow}>
            <Text style={styles.basisLabel}>평소 한 달 소비</Text>
            <Text style={styles.basisValue}>{`${formatNumber(explanation.level)}원`}</Text>
          </View>
          <View style={styles.basisRow}>
            <Text style={styles.basisLabel}>
              {basis.trendEffect >= 0 ? '늘어나는 추세' : '줄어드는 추세'}
            </Text>
            <Text style={styles.basisValue}>{signedWon(basis.trendEffect)}</Text>
          </View>
          {explanation.seasonalityApplied ? (
            <View style={styles.basisRow}>
              <Text style={styles.basisLabel}>{`${selected.label} 계절 영향`}</Text>
              <Text style={styles.basisValue}>{signedWon(basis.seasonalEffect)}</Text>
            </View>
          ) : null}
          {basis.lastYearAmount !== null ? (
            <Text style={styles.basisCaption}>
              {`작년 ${selected.label}에는 ${formatNumber(basis.lastYearAmount)}원을 썼어요.`}
            </Text>
          ) : null}
        </View>
      ) : null}

      <Text style={styles.note}>
        지금까지의 소비 흐름으로 계산한 예상치예요. 실제 소비는 달라질 수 있어요.
      </Text>
    </View>
  );
}
