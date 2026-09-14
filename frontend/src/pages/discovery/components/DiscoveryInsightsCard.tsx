import { SymbolView } from 'expo-symbols';
import { Pressable, Text, View } from 'react-native';

import type { DiscoveryInsight } from '@/mocks/discovery';
import { colors } from '@/styles';
import { formatNumber } from '@/utils/currency';

import { styles } from './DiscoveryInsightsCard.styles';

type DiscoveryInsightsCardProps = {
  insight: DiscoveryInsight;
  onPress: () => void;
};

export function DiscoveryInsightsCard({ insight, onPress }: DiscoveryInsightsCardProps) {
  return (
    <Pressable
      accessibilityLabel={`${insight.detailedTitle} 상세 보기`}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}>
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
          <SymbolView
            name={{ android: 'chevron_right', ios: 'chevron.right', web: 'chevron_right' }}
            size={16}
            style={styles.chevron}
            tintColor={colors.textSecondary}
          />
        </View>
      </View>
    </Pressable>
  );
}
