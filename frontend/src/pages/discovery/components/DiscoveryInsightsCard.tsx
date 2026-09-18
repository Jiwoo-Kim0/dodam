import { Image } from 'expo-image';
import { SymbolView } from 'expo-symbols';
import { Pressable, Text, View } from 'react-native';

import type { DiscoveryInsight } from '../discoveryModel';
import { colors } from '@/styles';
import { formatRoundedAmount } from '@/utils/currency';

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
        {/* 순위마다 색을 바꾸면 의미 없는 색이 늘어난다. 순위는 숫자만으로 충분하다. */}
        <View style={styles.rankBadge}>
          <Text style={styles.rankBadgeText}>{insight.rank}순위</Text>
        </View>
        <View style={styles.categoryBadge}>
          <Text style={styles.categoryBadgeText}>{insight.category}</Text>
        </View>
      </View>

      {/* 발견 홈 목록과 같은 카테고리 아이콘이다. 글자만 있던 카드에 시각 앵커를 준다. */}
      <View style={styles.titleRow}>
        <View style={styles.iconFrame}>
          <Image contentFit="contain" source={insight.icon} style={styles.icon} />
        </View>
        <Text style={styles.title}>{insight.detailedTitle}</Text>
      </View>

      <View style={styles.divider} />

      <View style={styles.amountRow}>
        <Text style={styles.amountLabel}>예상 절감액</Text>
        <View style={styles.amountValueRow}>
          <Text style={styles.amountValue}>
            {insight.annualSavingAmount === null
              ? '산정 전'
              : formatRoundedAmount(insight.annualSavingAmount)}
          </Text>
          {insight.annualSavingAmount !== null ? <Text style={styles.amountUnit}> 원/연</Text> : null}
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
