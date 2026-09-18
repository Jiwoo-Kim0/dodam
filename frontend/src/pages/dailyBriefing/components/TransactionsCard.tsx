import { Image } from 'expo-image';
import { useMemo, useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { formatCurrency } from '@/utils/currency';

import type { DailyBriefingTransaction } from '../dailyBriefing.types';
import { styles as screenStyles } from '../DailyBriefingScreen.styles';
import { styles } from './TransactionsCard.styles';

const INITIAL_TRANSACTION_COUNT = 5;

type TransactionsCardProps = {
  transactions: readonly DailyBriefingTransaction[];
  message?: string | null;
  onTransactionPress: (transactionId: string) => void;
};

export function TransactionsCard({
  message,
  onTransactionPress,
  transactions,
}: TransactionsCardProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  const visibleTransactions = useMemo(
    () => (isExpanded ? transactions : transactions.slice(0, INITIAL_TRANSACTION_COUNT)),
    [isExpanded, transactions],
  );

  return (
    <View style={[screenStyles.card, styles.transactionsCard]}>
      <View style={styles.sectionHeadingRow}>
        <Text style={screenStyles.sectionTitle}>어제 결제 내역</Text>
        <Text style={styles.transactionCount}>{message ? '—' : `${transactions.length}건`}</Text>
      </View>

      {transactions.length === 0 ? (
        <Text style={screenStyles.emptyMessage}>{message ?? '어제 결제한 내역이 없어요.'}</Text>
      ) : <View style={styles.transactionList}>
        {visibleTransactions.map((transaction, index) => (
          <Pressable
            accessibilityHint="가계부에서 이 결제 내역을 열어요"
            accessibilityRole="button"
            key={transaction.id}
            onPress={() => onTransactionPress(transaction.id)}
            style={({ pressed }) => [
              styles.transactionRow,
              index > 0 && styles.separatedRow,
              pressed && screenStyles.pressed,
            ]}>
            <Text style={styles.transactionTime}>{transaction.time}</Text>
            <View style={styles.paymentIconFrame}>
              <Image
                contentFit="contain"
                source={transaction.icon}
                style={styles.paymentIcon}
              />
            </View>
            <View style={styles.transactionInfo}>
              <Text numberOfLines={1} style={styles.transactionMerchant}>
                {transaction.merchant}
              </Text>
              <Text numberOfLines={1} style={styles.transactionCategory}>
                {transaction.category}
              </Text>
            </View>
            <Text style={styles.transactionAmount}>{formatCurrency(transaction.amount)}</Text>
          </Pressable>
        ))}
      </View>}

      {transactions.length > INITIAL_TRANSACTION_COUNT ? <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: isExpanded }}
        onPress={() => setIsExpanded((current) => !current)}
        style={({ pressed }) => [styles.expandButton, pressed && screenStyles.pressed]}>
        <Text style={styles.expandButtonText}>{isExpanded ? '접기' : '펼치기'}</Text>
      </Pressable> : null}
    </View>
  );
}
