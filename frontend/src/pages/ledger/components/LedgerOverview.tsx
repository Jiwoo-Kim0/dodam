import { useEffect, useRef, useState } from 'react';
import type { LayoutChangeEvent } from 'react-native';
import { Pressable, Text, TextInput, View } from 'react-native';
import { SymbolView } from 'expo-symbols';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import type { BouncyScrollViewHandle } from '@/components/bouncyScrollView/BouncyScrollView';
import { colors, componentSize } from '@/styles';
import type {
  LedgerCategory,
  LedgerCategorySummary,
  LedgerDailyTotal,
  LedgerMonthlySummary,
  LedgerTransaction,
} from '@/types/ledger.types';
import { formatCurrency, formatNumber } from '@/utils/currency';
import { dateKeyDaysAgo, toDateKeyFromParts } from '@/utils/date';

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
  hiddenTransactions: readonly LedgerTransaction[];
  onBackPress: () => void;
  onPreviousMonth: () => void;
  onNextMonth: () => void;
  onCreateTransaction: () => void;
  onOpenTrend: () => void;
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

function getRecentDateLabel(dateKey: string) {
  if (dateKey === dateKeyDaysAgo(0)) return '오늘';
  if (dateKey === dateKeyDaysAgo(1)) return '어제';

  const [, month, day] = dateKey.split('-').map(Number);
  return `${month}월 ${day}일`;
}

function getOverviewCalendarDays(year: number, month: number) {
  const firstWeekday = new Date(year, month - 1, 1).getDay();
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(year, month - 1, index - firstWeekday + 1);
    return {
      dateKey: toDateKeyFromParts(date.getFullYear(), date.getMonth() + 1, date.getDate()),
      day: date.getDate(),
      isCurrentMonth: date.getMonth() === month - 1,
    };
  });
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
  hiddenTransactions,
  onBackPress,
  onPreviousMonth,
  onNextMonth,
  onCreateTransaction,
  onOpenTrend,
  onSelectMonth,
  onOpenTransaction,
  onRefresh,
  refreshing,
}: LedgerOverviewProps) {
  const scrollRef = useRef<BouncyScrollViewHandle>(null);
  const [groupOffsets, setGroupOffsets] = useState<Map<string, number>>(
    () => new Map(),
  );
  const [pendingScrollDate, setPendingScrollDate] = useState<string | null>(null);
  const [isMonthPickerVisible, setIsMonthPickerVisible] = useState(false);
  const [isActionsMenuVisible, setIsActionsMenuVisible] = useState(false);
  const [isCalendarExpanded, setIsCalendarExpanded] = useState(false);
  const [isSearchVisible, setIsSearchVisible] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategoryId, setSelectedCategoryId] = useState<string | null>(null);
  const [showHiddenTransactions, setShowHiddenTransactions] = useState(false);
  const [visibleDateCount, setVisibleDateCount] = useState(7);
  const [calendarY, setCalendarY] = useState(0);
  const [feedY, setFeedY] = useState(0);
  const [transactionListY, setTransactionListY] = useState(0);
  const [collapsedStickyCalendarHeight, setCollapsedStickyCalendarHeight] = useState(0);
  const [scrollOffset, setScrollOffset] = useState(0);
  const categoryById = new Map(categories.map((category) => [category.id, category]));
  const visibleTransactions = (
    showHiddenTransactions ? hiddenTransactions : transactions
  ).filter((transaction) => showHiddenTransactions || !transaction.hidden);
  const displayCategorySummaries = categorySummaries
    .filter((summaryItem) =>
      transactions.some(
        (transaction) => transaction.categoryId === summaryItem.categoryId,
      ),
    )
    .slice(0, 3);
  const totalsByDate = new Map(dailyTotals.map((total) => [total.date, total]));
  const calendarDays = getOverviewCalendarDays(year, month);
  const now = new Date();
  const focusDay =
    year === now.getFullYear() && month === now.getMonth() + 1
      ? now.getDate()
      : new Date(year, month, 0).getDate();
  const initialSelectedDate = toDateKeyFromParts(year, month, focusDay);
  const [selectedDate, setSelectedDate] = useState(initialSelectedDate);
  const selectedIndex = calendarDays.findIndex(
    (day) => day.dateKey === selectedDate,
  );
  const weekStart = Math.max(0, Math.floor(selectedIndex / 7) * 7);
  const displayedCalendarDays = isCalendarExpanded
    ? calendarDays
    : calendarDays.slice(weekStart, weekStart + 7);
  const filteredTransactions = visibleTransactions.filter((transaction) => {
    if (selectedCategoryId && transaction.categoryId !== selectedCategoryId) return false;
    const query = searchQuery.trim().toLocaleLowerCase('ko-KR');
    if (!query) return true;
    const categoryLabel = categoryById.get(transaction.categoryId)?.label ?? '';
    return `${transaction.merchantName} ${categoryLabel}`
      .toLocaleLowerCase('ko-KR')
      .includes(query);
  });
  const recentTransactions = [...filteredTransactions]
    .sort((left, right) =>
      `${right.transactionDate} ${right.transactionTime}`.localeCompare(
        `${left.transactionDate} ${left.transactionTime}`,
      ),
    )
  ;
  const allGroups = Array.from(
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
  const recentGroups = allGroups.slice(0, visibleDateCount);
  const hasMoreGroups = allGroups.length > visibleDateCount;
  const showStickyCalendar = calendarY > 0 && scrollOffset >= calendarY;
  const selectCalendarDate = (dateKey: string) => {
    const groupIndex = allGroups.findIndex(([date]) => date === dateKey);
    if (groupIndex < 0) return;

    setSelectedDate(dateKey);
    setVisibleDateCount((count) => Math.max(count, groupIndex + 1));
    setIsCalendarExpanded(false);
    setPendingScrollDate(dateKey);
  };
  const handleGroupLayout = (date: string, event: LayoutChangeEvent) => {
    const groupOffset = event.nativeEvent.layout.y;
    setGroupOffsets((current) => {
      const next = new Map(current);
      next.set(date, groupOffset);
      return next;
    });
  };

  useEffect(() => {
    if (!pendingScrollDate) return;
    const groupOffset = groupOffsets.get(pendingScrollDate);
    if (groupOffset === undefined) return;

    const frame = requestAnimationFrame(() => {
      scrollRef.current?.scrollToOffset(
        Math.max(
          0,
          calendarY +
            feedY +
            transactionListY +
            groupOffset -
            collapsedStickyCalendarHeight,
        ),
      );
      setPendingScrollDate(null);
    });
    return () => cancelAnimationFrame(frame);
  }, [
    calendarY,
    collapsedStickyCalendarHeight,
    feedY,
    groupOffsets,
    pendingScrollDate,
    transactionListY,
  ]);
  return (
    <View style={styles.fill}>
      <AppHeader
        onBackPress={onBackPress}
        right={
          <View style={styles.headerActions}>
            <Pressable
              accessibilityLabel="거래 검색"
              accessibilityRole="button"
              onPress={() => setIsSearchVisible((visible) => !visible)}
              style={styles.headerIconAction}>
              <SymbolView
                name={{ android: 'search', ios: 'magnifyingglass', web: 'search' }}
                size={componentSize.icon.small + 5}
                tintColor={colors.textPrimary}
              />
            </Pressable>
            <Pressable
              accessibilityLabel="가계부 메뉴"
              accessibilityRole="button"
              hitSlop={8}
              onPress={() => setIsActionsMenuVisible((visible) => !visible)}>
              <Text style={styles.headerMore}>•••</Text>
            </Pressable>
          </View>
        }
        sideWidth={84}
        showBackButton
        title="가계부"
      />
      {isActionsMenuVisible ? (
        <View style={styles.headerMenu}>
          <Pressable
            accessibilityRole="button"
            onPress={() => {
              setIsActionsMenuVisible(false);
              onCreateTransaction();
            }}
              style={styles.headerMenuItem}>
            <Text style={styles.headerMenuIcon}>＋</Text>
              <Text style={styles.headerMenuLabel}>거래 추가</Text>
            </Pressable>
            <Pressable
              accessibilityRole="button"
              onPress={() => {
                setIsActionsMenuVisible(false);
                setShowHiddenTransactions((current) => !current);
                setSelectedCategoryId(null);
                setVisibleDateCount(7);
              }}
              style={styles.headerMenuItem}>
              <Text style={styles.headerMenuIcon}>◉</Text>
              <Text style={styles.headerMenuLabel}>
                {showHiddenTransactions ? '전체 거래 보기' : '숨긴 거래 내역 보기'}
              </Text>
            </Pressable>
        </View>
      ) : null}

      <View style={styles.fixedMonthSelector}>
        <MonthSelector
          month={month}
          onNext={onNextMonth}
          onPressLabel={() => setIsMonthPickerVisible(true)}
          onPrevious={onPreviousMonth}
          year={year}
        />
      </View>

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onEndReached={() => {
          if (hasMoreGroups) setVisibleDateCount((count) => count + 7);
        }}
        onRefresh={onRefresh}
        onScrollOffsetChange={(offset) => {
          setScrollOffset(offset);
          if (offset > 72 && isCalendarExpanded) setIsCalendarExpanded(false);
        }}
        ref={scrollRef}
        refreshing={refreshing}>
        {isSearchVisible ? (
          <View style={styles.searchBox}>
            <TextInput
              autoFocus
              onChangeText={setSearchQuery}
              placeholder="지출·수입 검색"
              placeholderTextColor={colors.textTertiary}
              style={styles.searchInput}
              value={searchQuery}
            />
            <Pressable onPress={() => {
              setSearchQuery('');
              setIsSearchVisible(false);
            }}>
              <Text style={styles.searchClearIcon}>×</Text>
            </Pressable>
          </View>
        ) : null}

        <Pressable
          accessibilityHint="월 소비 흐름 상세 화면으로 이동합니다"
          accessibilityRole="button"
          onPress={onOpenTrend}
          style={({ pressed }) => [
            styles.card,
            styles.expenseSummaryCard,
            pressed && styles.rowPressed,
          ]}>
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
        </Pressable>

        <View style={[styles.card, styles.sectionCard]}>
          <Text style={styles.categorySectionTitle}>{`${month}월 소비 TOP 3`}</Text>
          {displayCategorySummaries.length > 0 ? (
            <View style={styles.categorySummaryList}>
              {displayCategorySummaries.map((item) => (
                <Pressable
                  accessibilityRole="button"
                  key={item.categoryId}
                  onPress={() => {
                    setSelectedCategoryId((current) =>
                      current === item.categoryId ? null : item.categoryId,
                    );
                    setVisibleDateCount(7);
                  }}
                  style={({ pressed }) => [
                    styles.categoryItem,
                    pressed && styles.rowPressed,
                  ]}>
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
                </Pressable>
              ))}
            </View>
          ) : (
            <View style={styles.sectionEmptyState}>
              <Text style={styles.emptyStateText}>{`${month}월 소비 내역이 없어요.`}</Text>
            </View>
          )}
        </View>

        <View
          onLayout={(event) => setCalendarY(event.nativeEvent.layout.y)}
          style={[styles.card, styles.activityCard]}>
          <View
            style={styles.overviewCalendar}>
            <View style={styles.weekdayRow}>
              {['일', '월', '화', '수', '목', '금', '토'].map((weekday) => (
                <Text key={weekday} style={styles.weekdayLabel}>{weekday}</Text>
              ))}
            </View>
            <View style={styles.calendarGrid}>
              {displayedCalendarDays.map((day) => {
                const total = totalsByDate.get(day.dateKey);
                const isSelected = day.dateKey === selectedDate;
                const hasTransactions = visibleTransactions.some(
                  (transaction) => transaction.transactionDate === day.dateKey,
                );
                return (
                  <View key={day.dateKey} style={styles.overviewCalendarCell}>
                    <Pressable
                      accessibilityRole="button"
                      accessibilityState={{ disabled: !day.isCurrentMonth || !hasTransactions }}
                      disabled={!day.isCurrentMonth || !hasTransactions}
                      onPress={() => selectCalendarDate(day.dateKey)}
                      style={[
                        styles.calendarDayButton,
                        isSelected && styles.calendarDaySelected,
                      ]}>
                      <Text style={[
                        styles.calendarDay,
                        isSelected && styles.calendarDaySelectedText,
                        !hasTransactions && styles.calendarDayDisabled,
                        !day.isCurrentMonth && styles.calendarDayOutside,
                      ]}>{day.day}</Text>
                    </Pressable>
                    {day.isCurrentMonth && total?.income ? (
                      <Text numberOfLines={1} style={[styles.calendarTotal, styles.calendarIncome]}>
                        {`+${formatNumber(total.income)}`}
                      </Text>
                    ) : null}
                    {day.isCurrentMonth && total?.expense ? (
                      <Text numberOfLines={1} style={[styles.calendarTotal, styles.calendarNeutral]}>
                        {`-${formatNumber(total.expense)}`}
                      </Text>
                    ) : null}
                  </View>
                );
              })}
            </View>
            <Pressable
              accessibilityLabel={isCalendarExpanded ? '달력 접기' : '달력 펼치기'}
              accessibilityRole="button"
              onPress={() => setIsCalendarExpanded((expanded) => !expanded)}
              style={styles.calendarToggle}>
              <SymbolView
                name={{
                  android: isCalendarExpanded ? 'expand_less' : 'expand_more',
                  ios: isCalendarExpanded ? 'chevron.up' : 'chevron.down',
                  web: isCalendarExpanded ? 'expand_less' : 'expand_more',
                }}
                size={18}
                tintColor={colors.textSecondary}
              />
            </Pressable>
            <View style={styles.calendarDivider} />
          </View>
          <View
            onLayout={(event) => setFeedY(event.nativeEvent.layout.y)}
            style={styles.recentSection}>
            <View
              onLayout={(event) => setTransactionListY(event.nativeEvent.layout.y)}
              style={styles.transactionList}>
              {recentGroups.length > 0 ? (
                recentGroups.map(([date, groupTransactions], index) => (
                  <View
                    key={date}
                    onLayout={(event) => handleGroupLayout(date, event)}
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

      {showStickyCalendar ? (
        <View
          onLayout={(event) => {
            if (!isCalendarExpanded) {
              setCollapsedStickyCalendarHeight(event.nativeEvent.layout.height);
            }
          }}
          style={styles.stickyCalendar}>
          <View style={styles.weekdayRow}>
            {['일', '월', '화', '수', '목', '금', '토'].map((weekday) => (
              <Text key={weekday} style={styles.weekdayLabel}>{weekday}</Text>
            ))}
          </View>
          <View style={styles.calendarGrid}>
            {displayedCalendarDays.map((day) => {
              const total = totalsByDate.get(day.dateKey);
              const isSelected = day.dateKey === selectedDate;
              const hasTransactions = visibleTransactions.some(
                (transaction) => transaction.transactionDate === day.dateKey,
              );
              return (
                <View key={day.dateKey} style={styles.overviewCalendarCell}>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityState={{ disabled: !day.isCurrentMonth || !hasTransactions }}
                    disabled={!day.isCurrentMonth || !hasTransactions}
                    onPress={() => selectCalendarDate(day.dateKey)}
                    style={[
                      styles.calendarDayButton,
                      isSelected && styles.calendarDaySelected,
                    ]}>
                    <Text style={[
                      styles.calendarDay,
                      isSelected && styles.calendarDaySelectedText,
                      !hasTransactions && styles.calendarDayDisabled,
                      !day.isCurrentMonth && styles.calendarDayOutside,
                    ]}>{day.day}</Text>
                  </Pressable>
                  {day.isCurrentMonth && total?.income ? (
                    <Text numberOfLines={1} style={[styles.calendarTotal, styles.calendarIncome]}>
                      {`+${formatNumber(total.income)}`}
                    </Text>
                  ) : null}
                  {day.isCurrentMonth && total?.expense ? (
                    <Text numberOfLines={1} style={[styles.calendarTotal, styles.calendarNeutral]}>
                      {`-${formatNumber(total.expense)}`}
                    </Text>
                  ) : null}
                </View>
              );
            })}
          </View>
          <Pressable
            accessibilityLabel={isCalendarExpanded ? '달력 접기' : '달력 펼치기'}
            accessibilityRole="button"
            onPress={() => setIsCalendarExpanded((expanded) => !expanded)}
            style={styles.calendarToggle}>
            <SymbolView
              name={{
                android: isCalendarExpanded ? 'expand_less' : 'expand_more',
                ios: isCalendarExpanded ? 'chevron.up' : 'chevron.down',
                web: isCalendarExpanded ? 'expand_less' : 'expand_more',
              }}
              size={18}
              tintColor={colors.textSecondary}
            />
          </Pressable>
          <View style={styles.calendarDivider} />
        </View>
      ) : null}

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
