import { Text, View } from 'react-native';

import { formatNumber } from '@/utils/currency';

import {
  formatRate,
  refinanceUnavailableMessage,
  type LoanRateDiagnosis,
} from '../loanRateCopy';
import { styles } from './LoanRefinanceSection.styles';

export function LoanRefinanceSection({
  diagnosis,
}: {
  diagnosis: LoanRateDiagnosis;
}) {
  const { refinance, refinanceUnavailableReason } = diagnosis;

  if (!refinance) {
    return (
      <View style={styles.section}>
        <Text style={styles.title}>대환하면 어떻게 달라질까요?</Text>
        <View style={styles.card}>
          <Text style={styles.unavailableText}>
            {refinanceUnavailableMessage(refinanceUnavailableReason)}
          </Text>
        </View>
      </View>
    );
  }

  const targetLabel = [refinance.targetCompanyName, refinance.targetProductName]
    .filter((value): value is string => Boolean(value))
    .join(' ');

  return (
    <View style={styles.section}>
      <Text style={styles.title}>대환하면 어떻게 달라질까요?</Text>
      <Text style={styles.description}>
        만기까지 낼 이자를 지금 조건과 비교했어요.
      </Text>
      <View style={styles.card}>
        <View style={styles.row}>
          <Text style={styles.rowLabel}>지금 그대로</Text>
          <Text style={styles.rowValue}>
            {formatNumber(refinance.beforeInterest)}원
          </Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.rowLabel}>대환 후</Text>
          <Text style={styles.rowValueStrong}>
            {formatNumber(refinance.afterInterest)}원
          </Text>
        </View>

        <View style={styles.targetBlock}>
          <Text style={styles.targetLabel}>
            {targetLabel || '공시 최저금리 상품'}
          </Text>
          <Text style={styles.targetRate}>연 {formatRate(refinance.appliedRate)}%</Text>
        </View>

        {refinance.monthlySaving !== null && refinance.monthlySaving > 0 ? (
          <Text style={styles.monthlyNote}>
            한 회차에 약 {formatNumber(refinance.monthlySaving)}원씩 덜 내요
            {refinance.remainingInstallments !== null
              ? ` · ${refinance.remainingInstallments}회 남음`
              : ''}
          </Text>
        ) : null}
      </View>
      <Text style={styles.caption}>
        중도상환수수료와 부대비용은 포함하지 않았어요.
      </Text>
    </View>
  );
}
