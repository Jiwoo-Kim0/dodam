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

function getSpendingComparisonText(difference: number) {
  if (difference > 0) {
    return `지난달보다 ${formatCurrency(difference)} 더 썼어요`;
  }

  if (difference < 0) {
    return `지난달보다 ${formatCurrency(Math.abs(difference))} 덜 썼어요`;
  }

  return '지난달과 같은 금액을 썼어요';
}

function getSignedDifference(difference: number) {
  if (difference === 0) return '0';

  const sign = difference > 0 ? '+' : '-';
  return `${sign}${formatNumber(Math.abs(difference))}`;
}

function getRecentDateLabel(dateKey: string) {
  if (dateKey === dateKeyDaysAgo(0)) return '오늘';
  if (dateKey === dateKeyDaysAgo(1)) return '어제';

  const [, month, day] = dateKey.split('-').map(Number);
  return `${month}월 ${day}일`;
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
  const recentTransactions = [...visibleTransactions]
    .sort((left, right) =>
      `${right.transactionDate} ${right.transactionTime}`.localeCompare(
        `${left.transactionDate} ${left.transactionTime}`,
      ),
    )
    .slice(0, 3);
  const recentGroups = Array.from(
    recentTransactions.reduce<Map<string, LedgerTransaction[]>>(
      (groups, transaction) => {
        const group = groups.get(transaction.transactionDate) ?? [];
        group.push(transaction);
        groups.set(transaction.transactionDate, group);
        return groups;
      },
      new Map(),
    ),
  );
  const comparisonValueStyle =
    summary.previousMonthDifference > 0
      ? styles.expenseText
      : summary.previousMonthDifference < 0
        ? styles.spendingDecreaseText
        : undefined;

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
            {getSpendingComparisonText(summary.previousMonthDifference)}
          </Text>
        </View>

        <View style={[styles.card, styles.sectionCard]}>
          <Text style={styles.categorySectionTitle}>카테고리별 소비</Text>
          {categorySummaries.length > 0 ? (
            <>
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
                    <View
                      style={[
                        styles.categoryLegendDot,
                        { backgroundColor: item.color },
                      ]}
                    />
                    <Text style={styles.categoryLegendLabel}>
                      {categoryById.get(item.categoryId)?.label ?? '기타'}
                    </Text>
                    <Text style={styles.categoryLegendAmount}>
                      {`${formatCurrency(item.amount)} (${item.ratio}%)`}
                    </Text>
                  </View>
                ))}
              </View>
            </>
          ) : (
            <View style={styles.sectionEmptyState}>
              <Text style={styles.emptyStateText}>이번 달 소비 내역이 없어요.</Text>
            </View>
          )}
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
                  <Text style={[styles.glanceValue, comparisonValueStyle]}>
                    <Text style={[styles.glanceValueNumber, comparisonValueStyle]}>
                      {getSignedDifference(summary.previousMonthDifference)}
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
              {recentGroups.length > 0 ? (
                recentGroups.map(([date, groupTransactions], index) => (
                  <View
                    key={date}
                    style={[
                      styles.recentGroup,
                      index > 0 && styles.recentGroupSeparated,
                    ]}>
                    <Text style={styles.groupLabel}>{getRecentDateLabel(date)}</Text>
                    {groupTransactions.map((transaction) => (
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
                ))
              ) : (
                <View style={styles.sectionEmptyState}>
                  <Text style={styles.emptyStateText}>이번 달 거래 내역이 없어요.</Text>
                </View>
              )}
            </View>
          </View>
        </View>
      </RefreshableBouncyScrollView>
    </View>
  );
}
