import { Text, View } from 'react-native';

import { HOME_SUMMARY } from '@/mocks/home';
import { formatNumber } from '@/utils/currency';

import { CardDetailButton } from './CardDetailButton';
import { styles } from './ExpenseSummaryCard.styles';

type ExpenseSummaryCardProps = {
  onDetailPress: () => void;
};

export function ExpenseSummaryCard({ onDetailPress }: ExpenseSummaryCardProps) {
  return (
    <View style={styles.card}>
      <Text style={styles.title}>{`${HOME_SUMMARY.monthLabel} 지출`}</Text>
      <Text
        adjustsFontSizeToFit
        minimumFontScale={0.8}
        numberOfLines={1}
        style={styles.amount}
      >
        {formatNumber(HOME_SUMMARY.monthlyExpense)}
        <Text style={styles.currencyUnit}> 원</Text>
      </Text>
      <CardDetailButton accessibilityLabel="이번 달 지출 자세히 보기" onPress={onDetailPress} />
    </View>
  );
}
