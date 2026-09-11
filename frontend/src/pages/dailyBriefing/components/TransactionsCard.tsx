import { Image } from 'expo-image';
import { useMemo, useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import type { DailyBriefingTransaction } from '@/mocks/dailyBriefing';
import { formatCurrency } from '@/utils/currency';

import { styles as screenStyles } from '../DailyBriefingScreen.styles';
import { styles } from './TransactionsCard.styles';

const INITIAL_TRANSACTION_COUNT = 5;

type TransactionsCardProps = {
  transactions: readonly DailyBriefingTransaction[];
};

export function TransactionsCard({ transactions }: TransactionsCardProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  const visibleTransactions = useMemo(
    () => (isExpanded ? transactions : transactions.slice(0, INITIAL_TRANSACTION_COUNT)),
    [isExpanded, transactions],
  );

  return (
    <View style={[screenStyles.card, styles.transactionsCard]}>
      <View style={styles.sectionHeadingRow}>
        <Text style={screenStyles.sectionTitle}>어제 결제 내역</Text>
        <Text style={styles.transactionCount}>{transactions.length}건</Text>
      </View>

      <View style={styles.transactionList}>
        {visibleTransactions.map((transaction) => (
          <View key={transaction.id} style={styles.transactionRow}>
            <Text style={styles.transactionTime}>{transaction.time}</Text>
            <View style={styles.paymentIconFrame}>
              <Image
                contentFit="contain"
                source={transaction.icon}
                style={[styles.paymentIcon, { transform: [{ scale: transaction.iconScale }] }]}
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
          </View>
        ))}
      </View>

      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: isExpanded }}
        onPress={() => setIsExpanded((current) => !current)}
        style={({ pressed }) => [styles.expandButton, pressed && screenStyles.pressed]}>
        <Text style={styles.expandButtonText}>{isExpanded ? '접기' : '펼치기'}</Text>
      </Pressable>
    </View>
  );
}
