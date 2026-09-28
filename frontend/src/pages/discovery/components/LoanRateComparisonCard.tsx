import { Text, View } from 'react-native';

import { formatNumber } from '@/utils/currency';

import { formatRate, type LoanRateDiagnosis } from '../loanRateCopy';
import { styles } from './LoanRateComparisonCard.styles';

function barWidth(rate: number, maximum: number): `${number}%` {
  return `${maximum > 0 ? (rate / maximum) * 100 : 0}%`;
}

export function LoanRateComparisonCard({
  diagnosis,
}: {
  diagnosis: LoanRateDiagnosis;
}) {
  const { accountName, institutionName, interestRate, referenceRate, rateDifference, balance } =
    diagnosis;
  const maximum = Math.max(interestRate, referenceRate);

  return (
    <View style={styles.card}>
      <Text style={styles.loanLabel}>
        {accountName}
        {institutionName ? ` · ${institutionName}` : ''}
      </Text>
      <View style={styles.rateRow}>
        <Text style={styles.rate}>연 {formatRate(interestRate)}%</Text>
        <Text style={styles.rateDifference}>
          공시보다 {formatRate(rateDifference)}%p ↑
        </Text>
      </View>

      <View style={styles.bars}>
        <View style={styles.barRow}>
          <Text style={styles.barLabel}>공시 중앙값</Text>
          <View style={styles.barTrack}>
            <View
              style={[styles.referenceBar, { width: barWidth(referenceRate, maximum) }]}
            />
          </View>
          <Text style={styles.barValue}>연 {formatRate(referenceRate)}%</Text>
        </View>
        <View style={styles.barRow}>
          <Text style={styles.barLabel}>내 금리</Text>
          <View style={styles.barTrack}>
            <View
              style={[styles.currentBar, { width: barWidth(interestRate, maximum) }]}
            />
          </View>
          <Text style={styles.barValue}>연 {formatRate(interestRate)}%</Text>
        </View>
      </View>

      {balance !== null ? (
        <Text style={styles.balanceNote}>
          남은 잔액 {formatNumber(balance)}원
        </Text>
      ) : null}
    </View>
  );
}
