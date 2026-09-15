import { ActivityIndicator, Text, View } from 'react-native';

import { colors } from '@/styles';
import { formatNumber } from '@/utils/currency';

import { CardDetailButton } from './CardDetailButton';
import { styles } from './ExpenseSummaryCard.styles';

type ExpenseSummaryCardProps = {
  hasError: boolean;
  isLoading: boolean;
  monthLabel: string;
  monthlyExpense: number | null;
  onDetailPress: () => void;
};

export function ExpenseSummaryCard({
  hasError,
  isLoading,
  monthLabel,
  monthlyExpense,
  onDetailPress,
}: ExpenseSummaryCardProps) {
  return (
    <View style={styles.card}>
      <Text style={styles.title}>{`${monthLabel} 지출`}</Text>
      {isLoading ? (
        <ActivityIndicator
          color={colors.primary.green}
          size="small"
          style={styles.loadingIndicator}
        />
      ) : monthlyExpense !== null ? (
        <Text
          adjustsFontSizeToFit
          minimumFontScale={0.8}
          numberOfLines={1}
          style={styles.amount}
        >
          {formatNumber(monthlyExpense)}
          <Text style={styles.currencyUnit}> 원</Text>
        </Text>
      ) : (
        <Text style={styles.errorText}>
          {hasError ? '지출을 불러오지 못했어요' : '지출 내역이 없어요'}
        </Text>
      )}
      <CardDetailButton accessibilityLabel="이번 달 지출 자세히 보기" onPress={onDetailPress} />
    </View>
  );
}
