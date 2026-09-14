import { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

import {
  type BouncyScrollViewHandle,
} from '@/components/bouncyScrollView/BouncyScrollView';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import type {
  LedgerCategory,
  LedgerDailyTotal,
  LedgerMonthlySummary,
  LedgerTransaction,
} from '@/types/ledger.types';
import { colors } from '@/styles';
import { formatNumber } from '@/utils/currency';
import { toDateKeyFromParts } from '@/utils/date';

import { styles } from '../LedgerScreen.styles';
import {
  LedgerHeader,
  MonthSelector,
  TransactionRow,
} from './LedgerShared';
import { YearMonthPickerBottomSheet } from './YearMonthPickerBottomSheet';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const;
const FEED_PAGE_SIZE = 7;

interface CalendarDay {
  dateKey: string;
  day: number;
  isCurrentMonth: boolean;
}

function createCalendarDays(year: number, month: number): CalendarDay[] {
  const firstWeekday = new Date(year, month - 1, 1).getDay();
  const currentMonthDays = new Date(year, month, 0).getDate();
  const previousMonthDays = new Date(year, month - 1, 0).getDate();
  const days: CalendarDay[] = [];

  for (let index = firstWeekday - 1; index >= 0; index -= 1) {
    const date = new Date(year, month - 2, previousMonthDays - index);
    days.push({
      dateKey: toDateKeyFromParts(date.getFullYear(), date.getMonth() + 1, date.getDate()),
      day: date.getDate(),
      isCurrentMonth: false,
    });
  }

  for (let day = 1; day <= currentMonthDays; day += 1) {
    days.push({ dateKey: toDateKeyFromParts(year, month, day), day, isCurrentMonth: true });
  }

  let nextDay = 1;
  while (days.length < 42) {
    const date = new Date(year, month, nextDay);
    days.push({
      dateKey: toDateKeyFromParts(date.getFullYear(), date.getMonth() + 1, date.getDate()),
      day: nextDay,
      isCurrentMonth: false,
    });
    nextDay += 1;
  }

  return days;
}

function getSelectedDateLabel(dateKey: string) {
  const date = new Date(`${dateKey}T00:00:00`);
  return `${date.getDate()}일 ${WEEKDAYS[date.getDay()]}요일`;
}

interface LedgerCalendarProps {
  year: number;
  month: number;
  summary: LedgerMonthlySummary;
  dailyTotals: readonly LedgerDailyTotal[];
  transactions: readonly LedgerTransaction[];
  categories: readonly LedgerCategory[];
  onBackPress: () => void;
  onPreviousMonth: () => void;
  onNextMonth: () => void;
  onSelectMonth: (year: number, month: number) => void;
  onOpenTransaction: (transactionId: string) => void;
  onCreateTransaction: () => void;
}

export function LedgerCalendar({
  year,
  month,
  summary,
  dailyTotals,
  transactions,
  categories,
  onBackPress,
  onPreviousMonth,
  onNextMonth,
  onSelectMonth,
  onOpenTransaction,
  onCreateTransaction,
}: LedgerCalendarProps) {
  const scrollRef = useRef<BouncyScrollViewHandle>(null);
  const [selectedDate, setSelectedDate] = useState(() => {
    const now = new Date();
    return toDateKeyFromParts(now.getFullYear(), now.getMonth() + 1, now.getDate());
  });
  const [visibleFeedDateCount, setVisibleFeedDateCount] = useState(FEED_PAGE_SIZE);
  const [searchQuery, setSearchQuery] = useState('');
  const [isMonthPickerVisible, setIsMonthPickerVisible] = useState(false);
  const [calendarCardY, setCalendarCardY] = useState(0);
  const [calendarWidgetBottomY, setCalendarWidgetBottomY] = useState(0);
  const [scrollOffset, setScrollOffset] = useState(0);
  const calendarDays = useMemo(() => createCalendarDays(year, month), [month, year]);
  const totalsByDate = useMemo(
    () => new Map(dailyTotals.map((total) => [total.date, total])),
    [dailyTotals],
  );
  const categoryById = useMemo(
    () => new Map(categories.map((category) => [category.id, category])),
    [categories],
  );

  const handleSelectDay = (dateKey: string) => {
    setSelectedDate(dateKey);
    setVisibleFeedDateCount(FEED_PAGE_SIZE);
  };

  const searchedTransactions = useMemo(
    () =>
      transactions
        .filter((transaction) => !transaction.hidden)
        .filter((transaction) => {
          const query = searchQuery.trim().toLocaleLowerCase('ko-KR');
          if (!query) return true;

          const categoryLabel = categoryById.get(transaction.categoryId)?.label ?? '';
          return `${transaction.merchantName} ${categoryLabel}`
            .toLocaleLowerCase('ko-KR')
            .includes(query);
        }),
    [categoryById, searchQuery, transactions],
  );

  const groupsByDateDesc = useMemo(() => {
    const upToSelected = searchedTransactions.filter(
      (transaction) => transaction.transactionDate <= selectedDate,
    );

    const byDate = new Map<string, LedgerTransaction[]>();
    upToSelected.forEach((transaction) => {
      const group = byDate.get(transaction.transactionDate) ?? [];
      group.push(transaction);
      byDate.set(transaction.transactionDate, group);
    });

    return Array.from(byDate.entries())
      .sort(([left], [right]) => right.localeCompare(left))
      .map(([date, dayTransactions]) => ({
        date,
        transactions: [...dayTransactions].sort((left, right) =>
          right.transactionTime.localeCompare(left.transactionTime),
        ),
      }));
  }, [searchedTransactions, selectedDate]);

  const feedGroups = groupsByDateDesc.slice(0, visibleFeedDateCount);
  const hasMoreFeedDays = groupsByDateDesc.length > visibleFeedDateCount;

  const handleLoadMoreFeed = () => {
    if (!hasMoreFeedDays) return;
    setVisibleFeedDateCount((count) => count + FEED_PAGE_SIZE);
  };

  const calendarBottomY = calendarCardY + calendarWidgetBottomY;
  const showToTopButton = calendarBottomY > 0 && scrollOffset >= calendarBottomY;

  const toTopVisible = useSharedValue(0);

  useEffect(() => {
    toTopVisible.value = withTiming(showToTopButton ? 1 : 0, { duration: 220 });
  }, [showToTopButton, toTopVisible]);

  const toTopAnimatedStyle = useAnimatedStyle(() => ({
    opacity: toTopVisible.value,
    transform: [{ translateY: (1 - toTopVisible.value) * 48 }],
  }));

  return (
    <View style={styles.fill}>
      <LedgerHeader
        onBackPress={onBackPress}
        onRightPress={onCreateTransaction}
        rightLabel="추가"
        title="전체 내역"
      />
      <RefreshableBouncyScrollView
        contentContainerStyle={styles.calendarContent}
        onEndReached={handleLoadMoreFeed}
        onScrollOffsetChange={setScrollOffset}
        ref={scrollRef}>
        <MonthSelector
          month={month}
          onNext={onNextMonth}
          onPressLabel={() => setIsMonthPickerVisible(true)}
          onPrevious={onPreviousMonth}
          year={year}
        />

        <View style={[styles.card, styles.totalsCard]}>
          <View style={styles.totalItem}>
            <Text style={styles.totalLabel}>수입</Text>
            <Text style={styles.totalValue}>{`${formatNumber(summary.income)}원`}</Text>
          </View>
          <View style={styles.totalItem}>
            <Text style={styles.totalLabel}>지출</Text>
            <Text style={styles.totalValue}>{`${formatNumber(summary.expense)}원`}</Text>
          </View>
          <View style={styles.totalItem}>
            <Text style={styles.totalLabel}>잔액</Text>
            <Text style={styles.totalValue}>{`${formatNumber(summary.balance)}원`}</Text>
          </View>
        </View>

        <View style={styles.searchBox}>
          <Text importantForAccessibility="no" style={styles.searchIcon}>⌕</Text>
          <TextInput
            accessibilityLabel="지출 및 수입 검색"
            onChangeText={setSearchQuery}
            placeholder="지출 · 수입 검색"
            placeholderTextColor={colors.textTertiary}
            returnKeyType="search"
            style={styles.searchInput}
            value={searchQuery}
          />
        </View>

        <View
          onLayout={(event) => setCalendarCardY(event.nativeEvent.layout.y)}
          style={[styles.card, styles.calendarCard]}>
          <View
            onLayout={(event) =>
              setCalendarWidgetBottomY(
                event.nativeEvent.layout.y + event.nativeEvent.layout.height,
              )
            }>
            <View style={styles.weekdayRow}>
              {WEEKDAYS.map((weekday) => (
                <Text key={weekday} style={styles.weekdayLabel}>
                  {weekday}
                </Text>
              ))}
            </View>

            <View style={styles.calendarGrid}>
              {calendarDays.map((calendarDay) => {
                const total = totalsByDate.get(calendarDay.dateKey);
                const isSelected = calendarDay.dateKey === selectedDate;
                const hasBothTypes = Boolean(total?.income) && Boolean(total?.expense);

                return (
                  <View key={calendarDay.dateKey} style={styles.calendarCell}>
                    <Pressable
                      accessibilityLabel={`${calendarDay.dateKey} 거래 보기`}
                      accessibilityRole="button"
                      onPress={() => handleSelectDay(calendarDay.dateKey)}
                      style={[
                        styles.calendarDayButton,
                        isSelected && styles.calendarDaySelected,
                      ]}>
                      <Text
                        style={[
                          styles.calendarDay,
                          !calendarDay.isCurrentMonth && styles.calendarDayOutside,
                        ]}>
                        {calendarDay.day}
                      </Text>
                    </Pressable>
                    {total?.income ? (
                      <Text
                        adjustsFontSizeToFit
                        minimumFontScale={0.6}
                        numberOfLines={1}
                        style={[
                          styles.calendarTotal,
                          hasBothTypes ? styles.calendarIncome : styles.calendarNeutral,
                        ]}>
                        {`+${formatNumber(total.income)}`}
                      </Text>
                    ) : null}
                    {total?.expense ? (
                      <Text
                        adjustsFontSizeToFit
                        minimumFontScale={0.6}
                        numberOfLines={1}
                        style={[
                          styles.calendarTotal,
                          hasBothTypes ? styles.calendarExpense : styles.calendarNeutral,
                        ]}>
                        {`-${formatNumber(total.expense)}`}
                      </Text>
                    ) : null}
                  </View>
                );
              })}
            </View>
          </View>

          {feedGroups.length > 0 ? (
            feedGroups.map((group) => (
              <View key={group.date} style={styles.calendarFeedGroup}>
                <View style={styles.sectionTitleRow}>
                  <Text style={styles.selectedDateTitle}>{getSelectedDateLabel(group.date)}</Text>
                </View>
                {group.transactions.map((transaction) => (
                  <TransactionRow
                    amount={transaction.amount}
                    key={transaction.id}
                    merchantName={transaction.merchantName}
                    onPress={() => onOpenTransaction(transaction.id)}
                    transactionType={transaction.transactionType}
                  />
                ))}
              </View>
            ))
          ) : (
            <View style={styles.emptyState}>
              <Text style={styles.emptyStateText}>거래 내역이 없어요.</Text>
            </View>
          )}
        </View>
      </RefreshableBouncyScrollView>

      <Animated.View
        pointerEvents={showToTopButton ? 'auto' : 'none'}
        style={[styles.toTopButton, toTopAnimatedStyle]}>
        <Pressable
          accessibilityRole="button"
          onPress={() => scrollRef.current?.scrollToTop()}
          style={styles.toTopButtonInner}>
          <Text style={styles.toTopLabel}>위로 올라가기</Text>
        </Pressable>
      </Animated.View>

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
