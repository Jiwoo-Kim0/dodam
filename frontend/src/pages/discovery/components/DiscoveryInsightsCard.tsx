import { SymbolView } from 'expo-symbols';
import { Text, View } from 'react-native';

import type { DiscoveryInsight } from '@/mocks/discovery';
import { colors } from '@/styles';
import { formatNumber } from '@/utils/currency';

import { styles } from './DiscoveryInsightsCard.styles';

type DiscoveryInsightsCardProps = {
  insight: DiscoveryInsight;
};

export function DiscoveryInsightsCard({ insight }: DiscoveryInsightsCardProps) {
  return (
    <View style={styles.card}>
      <View style={styles.badgeRow}>
        <View
          style={[
            styles.rankBadge,
            insight.rank === 1 && styles.rankFirst,
            insight.rank === 2 && styles.rankSecond,
            insight.rank === 3 && styles.rankThird,
            insight.rank === 4 && styles.rankFourth,
          ]}>
          <Text style={styles.rankBadgeText}>{insight.rank}순위</Text>
        </View>
        <View style={styles.categoryBadge}>
          <Text style={styles.categoryBadgeText}>{insight.category}</Text>
        </View>
      </View>

      <Text style={styles.title}>{insight.detailedTitle}</Text>

      <View style={styles.divider} />

      <View style={styles.amountRow}>
        <Text style={styles.amountLabel}>예상 절감액</Text>
        <View style={styles.amountValueRow}>
          <Text style={styles.amountValue}>{formatNumber(insight.annualSavingAmount)}</Text>
          <Text style={styles.amountUnit}> 원/연</Text>
          {insight.rank !== 1 ? (
            <SymbolView
              name={{ android: 'chevron_right', ios: 'chevron.right', web: 'chevron_right' }}
              size={16}
              style={styles.chevron}
              tintColor={colors.textSecondary}
            />
          ) : null}
        </View>
      </View>
    </View>
  );
}
