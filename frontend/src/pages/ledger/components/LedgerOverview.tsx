import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import type {
  LedgerCategory,
  LedgerCategorySummary,
  LedgerDailyTotal,
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
import { SpendingTrendSparkline } from './SpendingTrendSparkline';
import { YearMonthPickerBottomSheet } from './YearMonthPickerBottomSheet';

interface LedgerOverviewProps {
  year: number;
  month: number;
  summary: LedgerMonthlySummary;
  dailyTotals: readonly LedgerDailyTotal[];
  previousDailyTotals: readonly LedgerDailyTotal[];
  categorySummaries: readonly LedgerCategorySummary[];
  categories: readonly LedgerCategory[];
  transactions: readonly LedgerTransaction[];
  onBackPress: () => void;
  onPreviousMonth: () => void;
  onNextMonth: () => void;
  onOpenCalendar: () => void;
  onSelectMonth: (year: number, month: number) => void;
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

function getSpendingDifferenceValue(difference: number) {
  if (difference === 0) return '비슷하게 썼어요';

  return `${formatCurrency(Math.abs(difference))} ${difference > 0 ? '더 썼어요' : '덜 썼어요'}`;
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
  dailyTotals,
  previousDailyTotals,
  categorySummaries,
  categories,
  transactions,
  onBackPress,
  onPreviousMonth,
  onNextMonth,
  onOpenCalendar,
  onSelectMonth,
  onOpenTransaction,
  onRefresh,
  refreshing,
}: LedgerOverviewProps) {
  const [isMonthPickerVisible, setIsMonthPickerVisible] = useState(false);
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
  const comparisonSurfaceStyle =
    summary.previousMonthDifference > 0
      ? styles.spendingIncreaseSurface
      : summary.previousMonthDifference < 0
        ? styles.spendingDecreaseSurface
        : styles.spendingNeutralSurface;

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
          onPressLabel={() => setIsMonthPickerVisible(true)}
          onPrevious={onPreviousMonth}
          year={year}
        />

        <View style={[styles.card, styles.expenseSummaryCard]}>
          <View style={styles.expenseSummaryContent}>
            <View style={styles.expenseSummaryCopy}>
              <Text style={styles.cardEyebrow}>{`${month}월 쓴 돈`}</Text>
              <Text adjustsFontSizeToFit numberOfLines={1} style={styles.expenseAmount}>
                {formatNumber(summary.expense)}
                <Text style={styles.expenseAmountUnit}>원</Text>
              </Text>
              <Text numberOfLines={2} style={styles.expenseComparison}>
                {getSpendingComparisonText(summary.previousMonthDifference)}
              </Text>
            </View>
            <SpendingTrendSparkline
              dailyTotals={dailyTotals}
              month={month}
              previousDailyTotals={previousDailyTotals}
              year={year}
            />
          </View>
        </View>

        <View style={[styles.card, styles.sectionCard]}>
          <Text style={styles.categorySectionTitle}>{`${month}월 소비 TOP 3`}</Text>
          {categorySummaries.length > 0 ? (
            <View style={styles.categorySummaryList}>
              {categorySummaries.map((item) => (
                <View key={item.categoryId} style={styles.categoryItem}>
                  <View style={styles.categoryItemHeader}>
                    <View style={styles.categoryNameRow}>
                      <Text style={styles.categoryLegendLabel}>
                        {categoryById.get(item.categoryId)?.label ?? '기타'}
                      </Text>
                      <Text style={styles.categoryLegendRatio}>{`${item.ratio}%`}</Text>
                    </View>
                    <Text style={styles.categoryLegendAmount}>
                      {formatCurrency(item.amount)}
                    </Text>
                  </View>
                  <View style={styles.categoryProgressTrack}>
                    <View
                      style={[
                        styles.categoryProgressFill,
                        { backgroundColor: item.color, flex: Math.max(item.ratio, 0) },
                      ]}
                    />
                    <View style={{ flex: Math.max(100 - item.ratio, 0) }} />
                  </View>
                </View>
              ))}
            </View>
          ) : (
            <View style={styles.sectionEmptyState}>
              <Text style={styles.emptyStateText}>{`${month}월 소비 내역이 없어요.`}</Text>
            </View>
          )}
        </View>

        <View style={[styles.card, styles.activityCard]}>
          <View style={styles.glanceSection}>
            <Text style={styles.sectionTitle}>{`${month}월 한눈에`}</Text>
            <View style={styles.glanceStats}>
              <View style={styles.glanceStat}>
                <Text style={styles.glanceLabel}>하루 평균</Text>
                <Text style={styles.glanceStatValue}>
                  {formatCurrency(summary.dailyAverage)}
                </Text>
              </View>
              <View style={styles.glanceStatDivider} />
              <View style={[styles.glanceStat, styles.glanceStatTrailing]}>
                <Text style={styles.glanceLabel}>소비 건수</Text>
                <Text style={styles.glanceStatValue}>{`${summary.transactionCount}건`}</Text>
              </View>
            </View>
            <View style={[styles.glanceComparison, comparisonSurfaceStyle]}>
              <Text style={styles.glanceComparisonLabel}>지난달보다</Text>
              <Text style={[styles.glanceComparisonValue, comparisonValueStyle]}>
                {getSpendingDifferenceValue(summary.previousMonthDifference)}
              </Text>
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
                        categoryCode={transaction.categoryId}
                        categoryLabel={categoryById.get(transaction.categoryId)?.label}
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
                  <Text style={styles.emptyStateText}>{`${month}월 거래 내역이 없어요.`}</Text>
                </View>
              )}
            </View>
          </View>
        </View>
      </RefreshableBouncyScrollView>

      <YearMonthPickerBottomSheet
        month={month}
        onClose={() => setIsMonthPickerVisible(false)}
        onConfirm={(nextYear, nextMonth) => {
          setIsMonthPickerVisible(false);
          onSelectMonth(nextYear, nextMonth);
        }}
        visible={isMonthPickerVisible}
        year={year}
      />
    </View>
  );
}
