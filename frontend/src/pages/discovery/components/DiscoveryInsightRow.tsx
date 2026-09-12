import { Image } from 'expo-image';
import { SymbolView } from 'expo-symbols';
import { Text, View } from 'react-native';

import type { DiscoveryInsight } from '@/mocks/discovery';
import { colors } from '@/styles';
import { formatNumber } from '@/utils/currency';

import { styles } from './DiscoveryInsightRow.styles';

type DiscoveryInsightRowProps = {
  insight: DiscoveryInsight;
  isLast: boolean;
};

export function DiscoveryInsightRow({ insight, isLast }: DiscoveryInsightRowProps) {
  return (
    <View style={[styles.row, !isLast && styles.divider]}>
      <View style={styles.rankBadge}>
        <View
          style={[
            styles.rankAccent,
            insight.rank === 1 && styles.rankFirst,
            insight.rank === 2 && styles.rankSecond,
            insight.rank === 3 && styles.rankThird,
            insight.rank === 4 && styles.rankFourth,
          ]}
        />
        <Text style={styles.rankText}>{insight.rank}</Text>
      </View>

      <View style={styles.iconFrame}>
        <Image contentFit="contain" source={insight.icon} style={styles.icon} />
      </View>

      <View style={styles.content}>
        <Text style={styles.category}>{insight.category}</Text>
        <Text numberOfLines={1} style={styles.title}>
          {insight.title}
        </Text>
        <Text numberOfLines={1} style={styles.description}>
          {insight.description}
        </Text>
      </View>

      <View style={styles.amountChevronRow}>
        <View style={styles.amountWrap}>
          <Text numberOfLines={1} style={styles.amount}>
            <Text style={styles.amountUnit}>연 </Text>
            {formatNumber(insight.annualSavingAmount)}원
          </Text>
          <Text numberOfLines={1} style={styles.amountLabel}>
            절약 예상
          </Text>
        </View>

        <SymbolView
          name={{ android: 'chevron_right', ios: 'chevron.right', web: 'chevron_right' }}
          size={16}
          tintColor={colors.textSecondary}
        />
      </View>
    </View>
  );
}
