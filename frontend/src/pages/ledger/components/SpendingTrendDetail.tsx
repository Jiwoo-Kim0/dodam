import { useState } from 'react';
import { Text, View } from 'react-native';
import Svg, {
  Circle,
  Defs,
  Line,
  LinearGradient,
  Path,
  Stop,
  Text as SvgText,
} from 'react-native-svg';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { colors } from '@/styles';
import type {
  LedgerCategory,
  LedgerDailyTotal,
  LedgerMonthlySummary,
  LedgerTransaction,
} from '@/types/ledger.types';
import { formatCurrency, formatNumber } from '@/utils/currency';

import { styles } from './SpendingTrendDetail.styles';
import { TransactionCategoryIcon } from './LedgerShared';

interface SpendingTrendDetailProps {
  dailyTotals: readonly LedgerDailyTotal[];
  categories: readonly LedgerCategory[];
  month: number;
  onBackPress: () => void;
  onRefresh: () => void;
  previousDailyTotals: readonly LedgerDailyTotal[];
  refreshing: boolean;
  summary: LedgerMonthlySummary;
  transactions: readonly LedgerTransaction[];
  year: number;
}

const WIDTH = 340;
const HEIGHT = 220;
const LEFT = 24;
const RIGHT = 316;
const TOP = 34;
const BOTTOM = 184;

function cumulativeValues(totals: readonly LedgerDailyTotal[], dayCount: number) {
  const byDay = new Map(totals.map((item) => [Number(item.date.slice(8)), item.expense]));
  let sum = 0;
  return Array.from({ length: dayCount }, (_, index) => {
    sum += byDay.get(index + 1) ?? 0;
    return sum;
  });
}

export function SpendingTrendDetail({
  categories,
  dailyTotals,
  month,
  onBackPress,
  onRefresh,
  previousDailyTotals,
  refreshing,
  summary,
  transactions,
  year,
}: SpendingTrendDetailProps) {
  const now = new Date();
  const dayCount =
    year === now.getFullYear() && month === now.getMonth() + 1
      ? now.getDate()
      : new Date(year, month, 0).getDate();
  const current = cumulativeValues(dailyTotals, dayCount);
  const previous = cumulativeValues(previousDailyTotals, dayCount);
  const maximum = Math.max(1, ...current, ...previous);
  const [selectedIndex, setSelectedIndex] = useState(Math.max(0, dayCount - 1));
  const point = (value: number, index: number) => ({
    x: LEFT + (index / Math.max(1, dayCount - 1)) * (RIGHT - LEFT),
    y: BOTTOM - (value / maximum) * (BOTTOM - TOP),
  });
  const currentPoints = current.map(point);
  const previousPoints = previous.map(point);
  const path = (points: readonly { x: number; y: number }[]) =>
    points.slice(1).reduce((result, item, index) => {
      const previousPoint = points[index];
      const controlX = (previousPoint.x + item.x) / 2;
      return `${result} C ${controlX} ${previousPoint.y}, ${controlX} ${item.y}, ${item.x} ${item.y}`;
    }, points[0] ? `M ${points[0].x} ${points[0].y}` : '');
  const selected = currentPoints[selectedIndex];
  const highestDay = dailyTotals.reduce<LedgerDailyTotal | null>(
    (highest, item) =>
      !highest || (item.expense ?? 0) > (highest.expense ?? 0) ? item : highest,
    null,
  );
  const previousTotal = previous.at(-1) ?? 0;
  const currentTotal = current.at(-1) ?? 0;
  const difference = currentTotal - previousTotal;
  const hasPreviousData = previousDailyTotals.length > 0;
  const currentLineColor = !hasPreviousData
    ? colors.primary.green
    : currentTotal > previousTotal
      ? colors.ledger.expense
      : currentTotal < previousTotal
        ? colors.ledger.income
        : colors.primary.green;
  const lastPoint = currentPoints.at(-1);
  const areaPath = currentPoints[0] && lastPoint
    ? `${path(currentPoints)} L ${lastPoint.x} ${BOTTOM} L ${currentPoints[0].x} ${BOTTOM} Z`
    : '';
  const categoryById = new Map(categories.map((category) => [category.id, category]));
  const categoryAmounts = Array.from(
    transactions
      .filter(
        (transaction) =>
          !transaction.hidden &&
          transaction.transactionType === 'expense' &&
          transaction.includedInExpense,
      )
      .reduce<Map<string, number>>((amounts, transaction) => {
        amounts.set(
          transaction.categoryId,
          (amounts.get(transaction.categoryId) ?? 0) + transaction.amount,
        );
        return amounts;
      }, new Map()),
  )
    .map(([categoryId, amount]) => ({ categoryId, amount }))
    .sort((left, right) => right.amount - left.amount);
  const categoryTotal = categoryAmounts.reduce((sum, item) => sum + item.amount, 0);

  return (
    <View style={styles.screen}>
      <AppHeader onBackPress={onBackPress} showBackButton title={`${month}월 소비 흐름`} />
      <RefreshableBouncyScrollView
        contentContainerStyle={styles.content}
        onRefresh={onRefresh}
        refreshing={refreshing}>
        <View>
          <Text style={styles.eyebrow}>현재까지 쓴 돈</Text>
          <Text style={styles.total}>{formatCurrency(summary.expense)}</Text>
          <Text style={styles.comparison}>
            {difference === 0
              ? '지난달 같은 기간과 같아요'
              : `지난달 같은 기간보다 ${formatCurrency(Math.abs(difference))} ${difference > 0 ? '더 썼어요' : '덜 썼어요'}`}
          </Text>
        </View>

        <View style={styles.chartCard}>
          <View style={styles.tooltip}>
            <Text style={styles.tooltipDay}>{`${selectedIndex + 1}일 누적`}</Text>
            <Text style={styles.tooltipAmount}>{formatCurrency(current[selectedIndex] ?? 0)}</Text>
          </View>
          <Svg height={HEIGHT} viewBox={`0 0 ${WIDTH} ${HEIGHT}`} width="100%">
            <Defs>
              <LinearGradient id="detailSpendingArea" x1="0" x2="0" y1="0" y2="1">
                <Stop offset="0" stopColor={currentLineColor} stopOpacity={0.2} />
                <Stop offset="1" stopColor={currentLineColor} stopOpacity={0} />
              </LinearGradient>
            </Defs>
            {[0, 0.5, 1].map((ratio) => {
              const y = TOP + ratio * (BOTTOM - TOP);
              return <Line key={ratio} stroke={colors.divider} x1={LEFT} x2={RIGHT} y1={y} y2={y} />;
            })}
            <Path d={areaPath} fill="url(#detailSpendingArea)" />
            {previous.length ? (
              <Path d={path(previousPoints)} fill="none" stroke={colors.textTertiary} strokeDasharray="4 4" strokeWidth={1.5} />
            ) : null}
            <Path d={path(currentPoints)} fill="none" stroke={currentLineColor} strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} />
            {selected ? <Line stroke={currentLineColor} x1={selected.x} x2={selected.x} y1={TOP} y2={BOTTOM} /> : null}
            {currentPoints.map((item, index) => (
              <Circle
                cx={item.x}
                cy={item.y}
                fill={index === selectedIndex ? currentLineColor : 'transparent'}
                key={index}
                onPress={() => setSelectedIndex(index)}
                r={index === selectedIndex ? 6 : 10}
              />
            ))}
            <SvgText fill={colors.textTertiary} fontSize={10} x={LEFT} y={BOTTOM + 22}>1일</SvgText>
            <SvgText fill={colors.textTertiary} fontSize={10} textAnchor="end" x={RIGHT} y={BOTTOM + 22}>{`${dayCount}일`}</SvgText>
          </Svg>
          <View style={styles.legend}>
            <Text style={[styles.currentLegend, { color: currentLineColor }]}>● 이번 달</Text>
            <Text style={styles.previousLegend}>┄ 지난달 같은 기간</Text>
          </View>
        </View>

        <View style={styles.summaryCard}>
          <View style={styles.summaryItem}>
            <Text style={styles.summaryLabel}>하루 평균</Text>
            <Text style={styles.summaryValue}>{formatCurrency(summary.dailyAverage)}</Text>
          </View>
          <View style={styles.summaryDivider} />
          <View style={styles.summaryItem}>
            <Text style={styles.summaryLabel}>가장 많이 쓴 날</Text>
            <Text style={styles.summaryValue}>
              {highestDay ? `${Number(highestDay.date.slice(8))}일 · ${formatNumber(highestDay.expense ?? 0)}원` : '-'}
            </Text>
          </View>
        </View>

        <View style={styles.categoryCard}>
          <Text style={styles.categoryTitle}>{`${month}월 카테고리별 소비`}</Text>
          {categoryAmounts.map((item, index) => {
            const ratio = categoryTotal > 0 ? Math.round((item.amount / categoryTotal) * 100) : 0;
            const category = categoryById.get(item.categoryId);
            const barColor = [
              colors.primary.green,
              colors.primary.yellow,
              colors.ledger.categorySecondary,
              colors.ledger.categoryTertiary,
              colors.ledger.categoryOther,
            ][index % 5];
            return (
              <View key={item.categoryId} style={styles.categoryRow}>
                <TransactionCategoryIcon
                  categoryCode={item.categoryId}
                  categoryLabel={category?.label}
                  compact
                />
                <View style={styles.categoryBody}>
                  <View style={styles.categoryHeader}>
                    <View style={styles.categoryNameRow}>
                      <Text style={styles.categoryName}>{category?.label ?? '기타'}</Text>
                      <Text style={styles.categoryRatio}>{`${ratio}%`}</Text>
                    </View>
                    <Text style={styles.categoryAmount}>{formatCurrency(item.amount)}</Text>
                  </View>
                  <View style={styles.categoryTrack}>
                    <View style={[styles.categoryFill, { backgroundColor: barColor, width: `${ratio}%` }]} />
                  </View>
                </View>
              </View>
            );
          })}
        </View>
      </RefreshableBouncyScrollView>
    </View>
  );
}
