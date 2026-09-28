import { Text, View } from 'react-native';

import type { AssetAnalysis } from '@/types/asset.types';
import { formatCurrency } from '@/utils/currency';

import { styles } from './AssetAnalysisCard.styles';

interface AssetAnalysisCardProps {
  analysis: AssetAnalysis;
}

export function AssetAnalysisCard({ analysis }: AssetAnalysisCardProps) {
  return (
    <View style={styles.card}>
      <Text style={styles.title}>자산 점검</Text>

      <View style={styles.row}>
        <View style={styles.labelGroup}>
          <Text style={styles.label}>바로 쓸 수 있는 자산</Text>
          <Text style={styles.description}>
            입출금 계좌 {analysis.cashAccountCount}개
          </Text>
        </View>
        <Text style={styles.amount}>{formatCurrency(analysis.cashTotal)}</Text>
      </View>

      <View style={styles.divider} />

      <View style={styles.row}>
        <View style={styles.labelGroup}>
          <Text style={styles.label}>오래 쓰지 않은 계좌</Text>
          <Text style={styles.description}>
            최근 {analysis.inactiveMonths}개월 기준 {analysis.inactiveAccountCount}개
          </Text>
        </View>
        <Text style={styles.amount}>{formatCurrency(analysis.inactiveBalance)}</Text>
      </View>
    </View>
  );
}
