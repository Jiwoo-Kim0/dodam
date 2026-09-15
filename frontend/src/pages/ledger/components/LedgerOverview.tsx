import { Pressable, Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import type {
  LedgerCategory,
  LedgerCategorySummary,
  LedgerMonthlySummary,
  LedgerTransaction,
} from '@/types/ledger.types';
import { formatCurrency, formatNumber } from '@/utils/currency';
import { dateKeyDaysAgo } from '@/utils/date';

import { styles } from '../LedgerScreen.styles';
import {
  MonthSelector,
  TransactionRow,
} from './LedgerShared';

interface LedgerOverviewProps {
  year: number;
  month: number;
  summary: LedgerMonthlySummary;
  categorySummaries: readonly LedgerCategorySummary[];
  categories: readonly LedgerCategory[];
  transactions: readonly LedgerTransaction[];
  onBackPress: () => void;
  onPreviousMonth: () => void;
  onNextMonth: () => void;
  onOpenCalendar: () => void;
  onOpenTransaction: (transactionId: string) => void;
  onRefresh: () => void;
  refreshing: boolean;
}

export function LedgerOverview({
  year,
  month,
  summary,
  categorySummaries,
  categories,
  transactions,
  onBackPress,
  onPreviousMonth,
  onNextMonth,
  onOpenCalendar,
  onOpenTransaction,
  onRefresh,
  refreshing,
}: LedgerOverviewProps) {
  const categoryById = new Map(categories.map((category) => [category.id, category]));
  const visibleTransactions = transactions.filter((transaction) => !transaction.hidden);
  const todayTransactions = visibleTransactions.filter(
    (transaction) => transaction.transactionDate === dateKeyDaysAgo(0),
  );
  const yesterdayTransactions = visibleTransactions
    .filter((transaction) => transaction.transactionDate === dateKeyDaysAgo(1))
    .slice(0, 1);

  return (
    <View style={styles.fill}>
      <AppHeader
        onBackPress={onBackPress}
        showBackButton
        title="가계부"
      />

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={onRefresh}
        refreshing={refreshing}>
        <MonthSelector
          month={month}
          onNext={onNextMonth}
          onPrevious={onPreviousMonth}
          year={year}
        />

        <View style={[styles.card, styles.expenseSummaryCard]}>
          <Text style={styles.cardEyebrow}>이번 달 쓴 돈</Text>
          <Text style={styles.expenseAmount}>
            {formatNumber(summary.expense)}
            <Text style={styles.expenseAmountUnit}>원</Text>
          </Text>
          <Text style={styles.expenseComparison}>
            {`지난달보다 ${formatCurrency(summary.previousMonthDifference)} 더 썼어요`}
          </Text>
        </View>

        <View style={[styles.card, styles.sectionCard]}>
          <Text style={styles.categorySectionTitle}>카테고리별 소비</Text>
          <View style={styles.categoryBar}>
            {categorySummaries.map((item) => (
              <View
                key={item.categoryId}
                style={[
                  styles.categoryBarSegment,
                  { backgroundColor: item.color, flex: item.ratio },
                ]}
              />
            ))}
          </View>

          <View style={styles.categoryLegend}>
            {categorySummaries.map((item) => (
              <View key={item.categoryId} style={styles.categoryLegendRow}>
                <View style={[styles.categoryLegendDot, { backgroundColor: item.color }]} />
                <Text style={styles.categoryLegendLabel}>
                  {categoryById.get(item.categoryId)?.label ?? '기타'}
                </Text>
                <Text style={styles.categoryLegendAmount}>
                  {`${formatCurrency(item.amount)} (${item.ratio}%)`}
                </Text>
              </View>
            ))}
          </View>
        </View>

        <View style={[styles.card, styles.activityCard]}>
          <View style={styles.glanceSection}>
            <Text style={styles.sectionTitle}>이번 달 한눈에</Text>
            <View style={styles.glanceOuterCard}>
              <View style={styles.glanceGrid}>
                <View style={styles.glanceItem}>
                  <Text style={styles.glanceLabel}>일 평균 소비</Text>
                  <Text style={styles.glanceValue}>
                    <Text style={styles.glanceValueNumber}>
                      {formatNumber(summary.dailyAverage)}
                    </Text>
                    원
                  </Text>
                </View>
                <View style={styles.glanceItem}>
                  <Text style={styles.glanceLabel}>남은 일 수</Text>
                  <Text style={styles.glanceValue}>
                    <Text style={styles.glanceValueNumber}>{summary.remainingDays}</Text>일
                  </Text>
                </View>
                <View style={styles.glanceItem}>
                  <Text style={styles.glanceLabel}>저번달 대비</Text>
                  <Text style={[styles.glanceValue, styles.expenseText]}>
                    <Text style={[styles.glanceValueNumber, styles.expenseText]}>
                      {`-${formatNumber(summary.previousMonthDifference)}`}
                    </Text>
                    원
                  </Text>
                </View>
              </View>
            </View>
          </View>

          <View style={styles.recentSection}>
            <View style={styles.sectionTitleRow}>
              <Text style={styles.sectionTitle}>최근 내역</Text>
              <Pressable
                accessibilityRole="button"
                hitSlop={8}
                onPress={onOpenCalendar}>
                <Text style={styles.textButton}>전체보기 ›</Text>
              </Pressable>
            </View>

            <View style={styles.transactionList}>
              <View style={styles.recentGroup}>
                <Text style={styles.groupLabel}>오늘</Text>
                {todayTransactions.map((transaction) => (
                  <TransactionRow
                    amount={transaction.amount}
                    compact
                    key={transaction.id}
                    merchantName={transaction.merchantName}
                    onPress={() => onOpenTransaction(transaction.id)}
                    transactionType={transaction.transactionType}
                  />
                ))}
              </View>

              <View style={[styles.recentGroup, styles.recentGroupSeparated]}>
                <Text style={styles.groupLabel}>어제</Text>
                {yesterdayTransactions.map((transaction) => (
                  <TransactionRow
                    amount={transaction.amount}
                    compact
                    key={transaction.id}
                    merchantName={transaction.merchantName}
                    onPress={() => onOpenTransaction(transaction.id)}
                    transactionType={transaction.transactionType}
                  />
                ))}
              </View>
            </View>
          </View>
        </View>
      </RefreshableBouncyScrollView>
    </View>
  );
}
