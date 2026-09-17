import { Image } from 'expo-image';
import { Pressable, Text, View } from 'react-native';

import { getTransactionCategoryIcon } from '@/constants/transactionCategoryIcons';
import { formatNumber } from '@/utils/currency';

import type { CategoryMerchantSpending } from '../categoryMonthlySpending';
import { styles } from './CategoryMonthlySpendingSection.styles';

interface CategoryMonthlySpendingSectionProps {
  categoryCode: string;
  categoryName: string;
  /** 진단이 본 기간의 이름. "8월" 또는 "최근 3개월". */
  periodLabel: string;
  isLoading: boolean;
  error: boolean;
  merchants: readonly CategoryMerchantSpending[];
  onRetry: () => void;
}

export function CategoryMonthlySpendingSection({
  categoryCode,
  categoryName,
  periodLabel,
  isLoading,
  error,
  merchants,
  onRetry,
}: CategoryMonthlySpendingSectionProps) {
  // 지출처별 아이콘은 없다. 이 진단이 다루는 카테고리의 아이콘을 모든 줄에 함께 쓴다.
  const icon = getTransactionCategoryIcon(categoryCode);

  return (
    <View style={styles.section}>
      <Text style={styles.title}>
        {periodLabel}에는 어디에 썼을까요?
      </Text>
      <Text style={styles.description}>
        {periodLabel} {categoryName} 결제를 금액이 큰 순서로 모았어요.
      </Text>
      <View style={styles.card}>
        {isLoading ? (
          <Text style={styles.stateText}>결제 내역을 불러오고 있어요.</Text>
        ) : error ? (
          <View style={styles.stateContent}>
            <Text style={styles.stateText}>결제 내역을 불러오지 못했어요.</Text>
            <Pressable
              accessibilityRole="button"
              onPress={onRetry}
              style={styles.retryRow}>
              <Text style={styles.retryText}>다시 시도</Text>
            </Pressable>
          </View>
        ) : merchants.length === 0 ? (
          <Text style={styles.stateText}>
            {periodLabel} {categoryName} 결제 내역이 없어요.
          </Text>
        ) : (
          merchants.map((merchant, index) => (
            <View
              key={merchant.name}
              style={[styles.row, index > 0 && styles.rowDivider]}>
              <View style={styles.iconChip}>
                <Image
                  accessibilityLabel=""
                  contentFit="contain"
                  source={icon}
                  style={styles.icon}
                />
              </View>
              <View style={styles.merchantCopy}>
                <Text numberOfLines={1} style={styles.merchantName}>
                  {merchant.name}
                </Text>
                <Text style={styles.count}>{merchant.count}건 결제</Text>
              </View>
              <Text style={styles.amount}>{formatNumber(merchant.amount)}원</Text>
            </View>
          ))
        )}
      </View>
    </View>
  );
}
