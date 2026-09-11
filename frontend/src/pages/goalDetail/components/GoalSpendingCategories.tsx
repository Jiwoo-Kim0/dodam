import { Image } from 'expo-image';
import type { ImageSourcePropType } from 'react-native';
import { Text, View } from 'react-native';

import type { SpendingCategory } from '@/types/goal.types';
import { formatCurrency } from '@/utils/currency';

import { styles } from './GoalSpendingCategories.styles';

interface GoalSpendingCategoriesProps {
  categories: readonly SpendingCategory[];
}

const CATEGORY_ICONS: Readonly<Record<string, ImageSourcePropType>> = {
  shopping: require('@/assets/icons/category/shopping_icon.png'),
  coffee: require('@/assets/icons/category/cafeSnack_icon.png'),
  transportation: require('@/assets/icons/category/publicTransport_icon.png'),
  leisure: require('@/assets/icons/category/hobbyLeisure_icon.png'),
};

const FALLBACK_CATEGORY_ICON = require('@/assets/icons/category/etc_icon.png');

export function GoalSpendingCategories({
  categories,
}: GoalSpendingCategoriesProps) {
  return (
    <View style={styles.section}>
      <Text style={styles.title}>카테고리별 이달의 누적 소비</Text>

      <View style={styles.card}>
        {categories.map((category, index) => {
          const icon = CATEGORY_ICONS[category.id] ?? FALLBACK_CATEGORY_ICON;

          return (
            <View
              accessible
              accessibilityLabel={`${category.label}, ${formatCurrency(category.amount)}`}
              key={category.id}
              style={[
                styles.categoryRow,
                index < categories.length - 1 && styles.categoryDivider,
              ]}
            >
              <Image
                accessibilityIgnoresInvertColors
                contentFit="contain"
                source={icon}
                style={styles.categoryIcon}
              />

              <Text style={styles.categoryLabel}>{category.label}</Text>
              <Text style={styles.amount}>{formatCurrency(category.amount)}</Text>
            </View>
          );
        })}
      </View>
    </View>
  );
}
