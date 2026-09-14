import { useMemo } from 'react';
import { Text, View } from 'react-native';
import Svg, { Line } from 'react-native-svg';

import { colors } from '@/styles';
import type { ReportMonthlySpending } from '@/types/report.types';
import { formatCurrency } from '@/utils/currency';

import { styles as screenStyles } from '../ReportScreen.styles';
import { BAR_AREA_HEIGHT, MONTH_LABEL_BLOCK_HEIGHT, styles } from './MonthlySpendingCard.styles';

type MonthlySpendingCardProps = {
  monthlySpending: readonly ReportMonthlySpending[];
};

function formatManwon(amount: number): string {
  return `${Math.round(amount / 10000)}만`;
}

export function MonthlySpendingCard({ monthlySpending }: MonthlySpendingCardProps) {
  const chart = useMemo(() => {
    const amounts = monthlySpending.map((item) => Math.max(0, item.amount));
    const maxAmount = Math.max(...amounts, 0);
    const total = amounts.reduce((sum, amount) => sum + amount, 0);
    const averageAmount = amounts.length === 0 ? 0 : total / amounts.length;
    const lastAmount = amounts.at(-1) ?? 0;

    return {
      averageAmount,
      averageRatio: maxAmount === 0 ? 0 : averageAmount / maxAmount,
      differenceFromAverage: lastAmount - averageAmount,
      maxAmount,
    };
  }, [monthlySpending]);

  const lastIndex = monthlySpending.length - 1;
  const spendsMore = chart.differenceFromAverage >= 0;

  return (
    <View style={[screenStyles.card, styles.spendingCard]}>
      <View style={styles.header}>
        <Text style={screenStyles.cardTitle}>지난 소비</Text>
        <Text style={styles.headerCaption}>{`${monthlySpending.length}개월 추이`}</Text>
      </View>

      <View style={styles.chart}>
        {monthlySpending.map((item, index) => {
          const isCurrentMonth = index === lastIndex;
          const barRatio =
            chart.maxAmount === 0 ? 0 : Math.max(0, item.amount) / chart.maxAmount;

          return (
            <View key={item.yearMonth} style={styles.column}>
              <Text
                style={[styles.amountLabel, isCurrentMonth && styles.amountLabelCurrent]}>
                {formatManwon(item.amount)}
              </Text>

              <View style={styles.barTrack}>
                <View
                  style={[
                    styles.bar,
                    { height: `${barRatio * 100}%` },
                    isCurrentMonth && styles.barCurrent,
                  ]}
                />
              </View>

              <Text style={[styles.monthLabel, isCurrentMonth && styles.monthLabelCurrent]}>
                {item.monthLabel}
              </Text>
            </View>
          );
        })}

        <View
          accessibilityLabel={`6개월 평균 ${formatCurrency(Math.round(chart.averageAmount))}`}
          pointerEvents="none"
          style={[
            styles.averageLine,
            { bottom: MONTH_LABEL_BLOCK_HEIGHT + chart.averageRatio * BAR_AREA_HEIGHT },
          ]}>
          <Svg height={1} width="100%">
            <Line
              stroke={colors.textPrimary}
              strokeDasharray="4 4"
              strokeWidth={1}
              x1="0"
              x2="100%"
              y1="0.5"
              y2="0.5"
            />
          </Svg>
        </View>
      </View>

      <Text style={styles.summary}>
        {`저번달, 평균 대비 ${formatCurrency(Math.abs(Math.round(chart.differenceFromAverage)))} ${
          spendsMore ? '많이' : '적게'
        } 썼어요.`}
      </Text>
    </View>
  );
}
