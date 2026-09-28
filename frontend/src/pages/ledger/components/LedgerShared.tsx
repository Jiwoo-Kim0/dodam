import { SymbolView } from 'expo-symbols';
import { Image, Pressable, Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { getTransactionCategoryIcon } from '@/constants/transactionCategoryIcons';
import { colors } from '@/styles';
import { formatCurrency } from '@/utils/currency';

import { styles } from '../LedgerScreen.styles';

interface LedgerHeaderProps {
  title: string;
  onBackPress?: () => void;
  backDisabled?: boolean;
  rightLabel?: string;
  rightDisabled?: boolean;
  onRightPress?: () => void;
}

export function LedgerHeader({
  title,
  onBackPress,
  backDisabled = false,
  rightLabel,
  rightDisabled = false,
  onRightPress,
}: LedgerHeaderProps) {
  const right = rightLabel ? (
    <Pressable
      accessibilityRole="button"
      disabled={rightDisabled}
      hitSlop={8}
      onPress={onRightPress}>
      <Text style={[styles.headerAction, rightDisabled && styles.headerActionDisabled]}>
        {rightLabel}
      </Text>
    </Pressable>
  ) : undefined;

  return (
    <AppHeader
      backDisabled={backDisabled}
      onBackPress={onBackPress}
      right={right}
      showBackButton={Boolean(onBackPress)}
      title={title}
    />
  );
}

const MONTH_ARROW_SIZE = 18;

interface MonthSelectorProps {
  year: number;
  month: number;
  onPrevious: () => void;
  onNext: () => void;
  onPressLabel?: () => void;
}

export function MonthSelector({
  year,
  month,
  onPrevious,
  onNext,
  onPressLabel,
}: MonthSelectorProps) {
  const now = new Date();
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1;
  const isNextDisabled = year > currentYear || (year === currentYear && month >= currentMonth);
  const label = <Text style={styles.monthLabel}>{`${year}년 ${month}월`}</Text>;

  return (
    <View style={styles.monthSelector}>
      <Pressable
        accessibilityLabel="이전 달"
        accessibilityRole="button"
        hitSlop={12}
        onPress={onPrevious}
        style={styles.monthArrowButton}>
        {/* 화살표는 보조 조작이라 월 이름보다 뒤로 물러나게 작고 연하게 그린다. */}
        <SymbolView
          name={{ android: 'chevron_left', ios: 'chevron.left', web: 'chevron_left' }}
          size={MONTH_ARROW_SIZE}
          tintColor={colors.textSecondary}
        />
      </Pressable>
      {onPressLabel ? (
        <Pressable
          accessibilityLabel="연도와 월 선택"
          accessibilityRole="button"
          hitSlop={8}
          onPress={onPressLabel}>
          {label}
        </Pressable>
      ) : (
        label
      )}
      <Pressable
        accessibilityLabel="다음 달"
        accessibilityRole="button"
        accessibilityState={{ disabled: isNextDisabled }}
        disabled={isNextDisabled}
        hitSlop={12}
        onPress={onNext}
        style={styles.monthArrowButton}>
        <SymbolView
          name={{ android: 'chevron_right', ios: 'chevron.right', web: 'chevron_right' }}
          size={MONTH_ARROW_SIZE}
          tintColor={isNextDisabled ? colors.textTertiary : colors.textSecondary}
        />
      </Pressable>
    </View>
  );
}

interface TransactionCategoryIconProps {
  categoryCode?: string | null;
  categoryLabel?: string;
  compact?: boolean;
}

export function TransactionCategoryIcon({
  categoryCode,
  categoryLabel,
  compact = false,
}: TransactionCategoryIconProps) {
  return (
    <Image
      accessibilityLabel={`${categoryLabel ?? '기타'} 카테고리`}
      resizeMode="contain"
      source={getTransactionCategoryIcon(categoryCode)}
      style={[
        styles.transactionCategoryIcon,
        compact && styles.transactionCategoryIconCompact,
      ]}
    />
  );
}

interface TransactionRowProps {
  merchantName: string;
  amount: number;
  transactionType: 'expense' | 'income';
  categoryCode?: string | null;
  categoryLabel?: string;
  onPress?: () => void;
  compact?: boolean;
}

export function TransactionRow({
  merchantName,
  amount,
  transactionType,
  categoryCode,
  categoryLabel,
  onPress,
  compact = false,
}: TransactionRowProps) {
  const amountLabel =
    transactionType === 'expense' ? `-${formatCurrency(amount)}` : formatCurrency(amount);

  return (
    <Pressable
      accessibilityRole={onPress ? 'button' : undefined}
      onPress={onPress}
      style={({ pressed }) => [
        styles.transactionRow,
        compact && styles.transactionRowCompact,
        pressed && onPress && styles.rowPressed,
      ]}>
      <TransactionCategoryIcon
        categoryCode={categoryCode}
        categoryLabel={categoryLabel}
        compact={compact}
      />
      <View style={[styles.transactionCopy, compact && styles.transactionCopyCompact]}>
        <Text
          numberOfLines={1}
          style={[
            styles.transactionAmount,
            compact && styles.transactionAmountCompact,
            transactionType === 'income' && styles.incomeText,
          ]}>
          {amountLabel}
        </Text>
        <Text
          numberOfLines={1}
          style={[
            styles.transactionMerchant,
            compact && styles.transactionMerchantCompact,
          ]}>
          {merchantName}
        </Text>
      </View>
    </Pressable>
  );
}
