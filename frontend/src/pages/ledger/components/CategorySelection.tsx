import { Image, Pressable, Text, View } from 'react-native';

import { BouncyScrollView } from '@/components/bouncyScrollView/BouncyScrollView';
import type { LedgerCategory } from '@/types/ledger.types';
import { formatCurrency } from '@/utils/currency';

import { styles } from '../LedgerScreen.styles';
import { LedgerHeader, MerchantLogoPlaceholder } from './LedgerShared';

interface CategorySelectionProps {
  categories: readonly LedgerCategory[];
  selectedCategoryId: string | null;
  amount: number;
  merchantName: string;
  transactionType: 'expense' | 'income';
  onBackPress: () => void;
  onSelect: (categoryId: string) => void;
}

export function CategorySelection({
  categories,
  selectedCategoryId,
  amount,
  merchantName,
  transactionType,
  onBackPress,
  onSelect,
}: CategorySelectionProps) {
  return (
    <View style={styles.fill}>
      <LedgerHeader onBackPress={onBackPress} title="카테고리" />

      <BouncyScrollView contentContainerStyle={styles.categoryScreenContent}>
        <View style={styles.categoryTransactionPreview}>
          <MerchantLogoPlaceholder />
          <View style={styles.categoryPreviewCopy}>
            <Text style={styles.categoryPreviewAmount}>
              {amount === 0
                ? formatCurrency(0)
                : `${transactionType === 'expense' ? '-' : '+'}${formatCurrency(amount)}`}
            </Text>
            <Text style={styles.categoryPreviewMerchant}>
              {merchantName || '새 거래 내역'}
            </Text>
          </View>
        </View>

        <View style={styles.categoryList}>
          {categories.map((category) => {
            const isSelected = category.id === selectedCategoryId;

            return (
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ selected: isSelected }}
                key={category.id}
                onPress={() => onSelect(category.id)}
                style={({ pressed }) => [
                  styles.categoryRow,
                  isSelected && styles.categoryRowSelected,
                  pressed && styles.rowPressed,
                ]}>
                <Image resizeMode="contain" source={category.icon} style={styles.categoryIcon} />
                <Text style={styles.categoryLabel}>{category.label}</Text>
                {isSelected ? <Text style={styles.categoryCheck}>✓</Text> : null}
              </Pressable>
            );
          })}
        </View>
      </BouncyScrollView>
    </View>
  );
}
