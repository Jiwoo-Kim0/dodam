import { Text, View } from 'react-native';

import { colors } from '@/styles';
import type { ReportForecastTone, ReportNextMonthForecast } from '@/types/report.types';
import { formatCurrency, formatNumber } from '@/utils/currency';

import { styles as screenStyles } from '../ReportScreen.styles';
import { styles } from './NextMonthForecastCard.styles';

type NextMonthForecastCardProps = {
  forecast: ReportNextMonthForecast;
};

const toneColors: Record<ReportForecastTone, string> = {
  fixed: colors.primary.green,
  living: colors.report.forecastLiving,
  card: colors.report.forecastCard,
};

export function NextMonthForecastCard({ forecast }: NextMonthForecastCardProps) {
  const { breakdown, description, differenceFromThisMonth, expectedExpense } = forecast;
  const breakdownTotal = breakdown.reduce((sum, item) => sum + Math.max(0, item.amount), 0);
  const spendsMore = differenceFromThisMonth >= 0;

  return (
    <View style={[screenStyles.card, styles.forecastCard]}>
      <Text style={screenStyles.cardTitle}>다음 달 예상 금융 생활</Text>
      <Text style={screenStyles.cardDescription}>{description}</Text>

      <Text style={styles.expenseLabel}>예상 지출</Text>
      <Text style={styles.expenseAmount}>
        {formatNumber(expectedExpense)}
        <Text style={styles.expenseAmountUnit}>원</Text>
      </Text>
      <Text style={styles.expenseComparison}>
        이번 달보다{' '}
        <Text style={styles.expenseComparisonValue}>
          {formatCurrency(Math.abs(differenceFromThisMonth))}
        </Text>{' '}
        {spendsMore ? '많을' : '적을'} 것으로 예상돼요
      </Text>

      <View style={styles.breakdownBar}>
        {breakdown.map((item) => (
          <View
            key={item.id}
            style={[
              styles.breakdownBarSegment,
              { backgroundColor: toneColors[item.tone], flex: Math.max(0, item.amount) },
            ]}
          />
        ))}
      </View>

      <View style={styles.breakdownLegend}>
        {breakdown.map((item) => (
          <View key={item.id} style={styles.breakdownItem}>
            <View style={styles.breakdownLabelRow}>
              <View style={[styles.breakdownDot, { backgroundColor: toneColors[item.tone] }]} />
              <Text style={styles.breakdownLabel}>{item.label}</Text>
            </View>
            <Text numberOfLines={1} style={styles.breakdownAmount}>
              {formatCurrency(item.amount)}
            </Text>
            <Text style={styles.breakdownRatio}>
              {breakdownTotal === 0 ? '0%' : `${Math.round((item.amount / breakdownTotal) * 100)}%`}
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
}
