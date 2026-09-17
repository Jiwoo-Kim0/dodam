import { Text, View } from 'react-native';

import { formatNumber } from '@/utils/currency';

import {
  recentPeriodLabel,
  type categorySpendingDiagnosisComparison,
} from '../categorySpendingCopy';
import { styles } from './SpendingDiagnosisComparisonCard.styles';

type Comparison = NonNullable<
  ReturnType<typeof categorySpendingDiagnosisComparison>
>;

function barWidth(amount: number, maximum: number): `${number}%` {
  return `${maximum > 0 ? (amount / maximum) * 100 : 0}%`;
}

export function SpendingDiagnosisComparisonCard({
  comparison,
}: {
  comparison: Comparison;
}) {
  const {
    categoryName,
    recentAmount,
    previousAverage,
    increaseRatePercent,
    recentPeriod,
  } = comparison;
  const maximum = Math.max(recentAmount, previousAverage);
  // 진단은 진행 중인 달을 빼고 마지막 완료 월을 본다. "이번 달"이라고 쓰면 사실과 어긋나 월을 밝힌다.
  const recentLabel = recentPeriodLabel(recentPeriod);

  return (
    <View style={styles.card}>
      <Text style={styles.categoryLabel}>
        {recentLabel} {categoryName}
      </Text>
      <View style={styles.amountRow}>
        <Text style={styles.amount}>{formatNumber(recentAmount)}원</Text>
        <Text style={styles.changePercent}>
          평소보다 {formatNumber(increaseRatePercent)}% ↑
        </Text>
      </View>

      <View style={styles.bars}>
        <View style={styles.barRow}>
          <Text style={styles.barLabel}>평소 평균</Text>
          <View style={styles.barTrack}>
            <View
              style={[
                styles.previousBar,
                { width: barWidth(previousAverage, maximum) },
              ]}
            />
          </View>
          <Text style={styles.barAmount}>{formatNumber(previousAverage)}원</Text>
        </View>
        <View style={styles.barRow}>
          <Text style={styles.barLabel}>{recentLabel}</Text>
          <View style={styles.barTrack}>
            <View
              style={[styles.recentBar, { width: barWidth(recentAmount, maximum) }]}
            />
          </View>
          <Text style={styles.barAmount}>{formatNumber(recentAmount)}원</Text>
        </View>
      </View>
    </View>
  );
}
