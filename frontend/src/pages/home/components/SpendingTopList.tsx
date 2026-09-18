import { Image } from 'expo-image';
import { SymbolView } from 'expo-symbols';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { getTransactionCategoryIcon } from '@/constants/transactionCategoryIcons';
import { colors, componentSize } from '@/styles';
import { formatCurrency } from '@/utils/currency';
import type { HomeSpendingCategory } from '../hooks/useHomeSummary';

import { CardDetailButton } from './CardDetailButton';
import { styles } from './SpendingTopList.styles';

interface SpendingTopListProps {
  hasError: boolean;
  isLoading: boolean;
  items: readonly HomeSpendingCategory[];
  monthLabel: string;
  onDetailPress: () => void;
}

export function SpendingTopList({
  hasError,
  isLoading,
  items,
  monthLabel,
  onDetailPress,
}: SpendingTopListProps) {
  return (
    <Pressable
      accessibilityLabel={`${monthLabel} 소비 TOP 3 자세히 보기`}
      accessibilityRole="button"
      onPress={onDetailPress}
      style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}
    >
      <Text style={styles.title}>{`${monthLabel} 소비 TOP 3`}</Text>
      <CardDetailButton />

      {isLoading ? (
        <ActivityIndicator color={colors.primary.green} style={styles.status} />
      ) : items.length === 0 ? (
        <Text style={styles.statusText}>
          {hasError ? '소비 순위를 불러오지 못했어요' : '소비 내역이 없어요'}
        </Text>
      ) : items.map((item, index) => (
        <View
          key={item.rank}
          style={[
            styles.row,
            index === 0 && styles.firstRow,
            index > 0 && styles.separatedRow,
          ]}>
          {/* 순위는 목록 순서가 이미 말해 주므로, 자리는 무엇에 썼는지 알려 주는 아이콘에 준다. */}
          <View style={styles.categoryIconFrame}>
            <Image
              contentFit="contain"
              source={getTransactionCategoryIcon(item.code)}
              style={styles.categoryIcon}
            />
          </View>

          <Text numberOfLines={1} style={styles.categoryLabel}>{item.label}</Text>
          <View style={styles.amountGroup}>
            <Text style={styles.categoryAmount}>{formatCurrency(item.amount)}</Text>
            {item.direction === 'none' ? (
              <Text style={styles.changeLabel}>지난 달과 같아요</Text>
            ) : (
              <View style={styles.changeGroup}>
                <Text style={styles.changeLabel}>{`지난 달 대비 ${item.changePercent}%`}</Text>
                <SymbolView
                  name={
                    item.direction === 'down'
                      ? { android: 'arrow_downward', ios: 'arrow.down', web: 'arrow_downward' }
                      : { android: 'arrow_upward', ios: 'arrow.up', web: 'arrow_upward' }
                  }
                  size={componentSize.icon.trend}
                  tintColor={
                    item.direction === 'down' ? colors.primary.green : colors.primary.orange
                  }
                />
              </View>
            )}
          </View>
        </View>
      ))}
    </Pressable>
  );
}
