import { Image } from 'expo-image';
import { SymbolView } from 'expo-symbols';
import { Pressable, Text, View } from 'react-native';

import type { DiscoveryInsight } from '../discoveryModel';
import { colors } from '@/styles';
import { formatRoundedAmount } from '@/utils/currency';

import { styles } from './DiscoveryInsightRow.styles';

type DiscoveryInsightRowProps = {
  insight: DiscoveryInsight;
  onPress: () => void;
};

export function DiscoveryInsightRow({ insight, onPress }: DiscoveryInsightRowProps) {
  return (
    <View style={styles.cardShadow}>
      <Pressable
        accessibilityLabel={`${insight.title} 상세 보기`}
        accessibilityRole="button"
        onPress={onPress}
        style={({ pressed }) => [
          styles.row,
          pressed && styles.rowPressed,
        ]}>
        {/* 순위 숫자는 목록 순서가 이미 말해 준다. 왼쪽 시각 앵커를 아이콘 하나로 줄인다. */}
        <View style={styles.iconFrame}>
          <Image contentFit="contain" source={insight.icon} style={styles.icon} />
        </View>

        <View style={styles.content}>
          <View style={styles.categoryRow}>
            <Text style={styles.category}>{insight.category}</Text>
            {/* 군집 이탈이 근거에 섞인 문제만. 규칙 진단은 값이 null이라 배지가 없다. */}
            {insight.hasClusterEvidence ? (
              <View style={styles.clusterBadge}>
                <Text style={styles.clusterBadgeText}>또래 비교</Text>
              </View>
            ) : null}
          </View>
          <Text numberOfLines={1} style={styles.title}>
            {insight.title}
          </Text>
        </View>

        <View style={styles.amountChevronRow}>
          <View style={styles.amountWrap}>
            <Text numberOfLines={1} style={styles.amount}>
              {insight.annualSavingAmount === null ? (
                '산정 전'
              ) : (
                <>
                  <Text style={styles.amountUnit}>연 </Text>
                  {formatRoundedAmount(insight.annualSavingAmount)}원
                </>
              )}
            </Text>
            <Text numberOfLines={1} style={styles.amountLabel}>
              {insight.annualSavingAmount === null ? '예상 효과' : '절약 예상'}
            </Text>
          </View>

          <SymbolView
            name={{ android: 'chevron_right', ios: 'chevron.right', web: 'chevron_right' }}
            size={16}
            tintColor={colors.textSecondary}
          />
        </View>
      </Pressable>
    </View>
  );
}
