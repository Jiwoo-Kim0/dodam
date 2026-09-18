import { Image } from 'expo-image';
import { useMemo, useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import type { ReportPastSpending } from '@/types/report.types';
import { formatCurrency, formatNumber } from '@/utils/currency';

import { styles as screenStyles } from '../ReportScreen.styles';
import {
  BAR_AREA_HEIGHT,
  MONTH_LABEL_BLOCK_HEIGHT,
  styles,
} from './MonthlySpendingCard.styles';

type MonthlySpendingCardProps = {
  pastSpending: ReportPastSpending;
};

export function MonthlySpendingCard({ pastSpending }: MonthlySpendingCardProps) {
  const { differenceFromAverage, latestAmount, monthlyAverage, months, topCategories } =
    pastSpending;

  const maxAmount = useMemo(
    () => Math.max(monthlyAverage, ...months.map((month) => Math.max(0, month.amount)), 0),
    [monthlyAverage, months],
  );
  const lastIndex = months.length - 1;
  const spendsMore = differenceFromAverage > 0;
  const isSameAsAverage = differenceFromAverage === 0;
  // 막대와 평균선이 같은 최댓값을 쓰므로 평균선이 항상 막대 영역 안에 들어온다.
  const averageRatio = maxAmount === 0 ? 0 : monthlyAverage / maxAmount;

  const [selectedYearMonth, setSelectedYearMonth] = useState<string | null>(null);
  const selectedMonth = months.find((month) => month.yearMonth === selectedYearMonth) ?? null;

  return (
    // 막대가 아닌 곳을 누르면 선택이 풀린다. 막대 Pressable이 터치를 먼저 잡아 여기까지 오지 않는다.
    <Pressable
      onPress={() => setSelectedYearMonth(null)}
      style={[screenStyles.card, styles.spendingCard]}>
      <View style={styles.header}>
        <Text style={screenStyles.cardTitle}>지난 소비</Text>
        <Text style={styles.headerCaption}>{`최근 ${months.length}개월`}</Text>
      </View>

      <View style={styles.heroValueRow}>
        <Text style={styles.heroValue}>{formatNumber(latestAmount)}</Text>
        <Text style={styles.heroValueUnit}>원</Text>
      </View>
      {/* 지출이 늘면 나쁜 방향이라 주요 금융 지표와 같은 색 규칙을 따른다. */}
      <Text
        style={[
          styles.heroComparison,
          isSameAsAverage
            ? styles.heroComparisonNeutral
            : spendsMore
              ? styles.heroComparisonBad
              : styles.heroComparisonGood,
        ]}>
        {isSameAsAverage
          ? '6개월 평균과 같아요'
          : `6개월 평균보다 ${spendsMore ? '↑' : '↓'} ${formatCurrency(
              Math.abs(differenceFromAverage),
            )} ${spendsMore ? '많아요' : '적어요'}`}
      </Text>

      {/* 높이를 고정해 둬서 막대를 눌러도 아래 차트가 밀리지 않는다. */}
      <View style={styles.selectionSlot}>
        {selectedMonth === null ? null : (
          <Text style={styles.selectionText}>
            <Text style={styles.selectionMonth}>{`${selectedMonth.monthLabel} `}</Text>
            {formatCurrency(selectedMonth.amount)}
          </Text>
        )}
      </View>

      <View style={styles.chart}>
        {/*
         * 평균선은 막대보다 먼저 그려서 뒤로 보낸다. 앞에 두면 막대를 가로질러 지저분해진다.
         * `bottom`은 차트 전체(막대 + 월 라벨) 기준이라 라벨 높이를 더해야 막대 바닥에 맞는다.
         */}
        <View
          accessibilityLabel={`${months.length}개월 평균 ${formatCurrency(monthlyAverage)}`}
          pointerEvents="none"
          style={[
            styles.averageLine,
            { bottom: MONTH_LABEL_BLOCK_HEIGHT + averageRatio * BAR_AREA_HEIGHT },
          ]}
        />

        {months.map((month, index) => {
          const isReportMonth = index === lastIndex;
          const isSelected = month.yearMonth === selectedYearMonth;
          const barRatio = maxAmount === 0 ? 0 : Math.max(0, month.amount) / maxAmount;

          return (
            <Pressable
              accessibilityLabel={`${month.monthLabel} 지출 ${formatCurrency(month.amount)}`}
              accessibilityRole="button"
              accessibilityState={{ selected: isSelected }}
              key={month.yearMonth}
              // 같은 막대를 다시 누르면 선택이 풀린다.
              onPress={() => setSelectedYearMonth(isSelected ? null : month.yearMonth)}
              style={styles.column}>
              <View style={styles.barTrack}>
                <View
                  style={[
                    styles.bar,
                    { height: `${barRatio * 100}%` },
                    isReportMonth && styles.barReportMonth,
                    isSelected &&
                      (isReportMonth ? styles.barReportMonthSelected : styles.barSelected),
                  ]}
                />
              </View>

              <Text
                style={[
                  styles.monthLabel,
                  isReportMonth && styles.monthLabelReport,
                  isSelected && styles.monthLabelSelected,
                ]}>
                {month.monthLabel}
              </Text>
            </Pressable>
          );
        })}
      </View>

      <View style={styles.legend}>
        <View style={styles.legendLine} />
        <Text style={styles.legendLabel}>
          {`${months.length}개월 평균 ${formatCurrency(monthlyAverage)}`}
        </Text>
      </View>

      {topCategories.length === 0 ? null : (
        <>
          <View style={styles.divider} />

          <Text style={styles.sectionLabel}>많이 쓴 곳</Text>
          <View style={styles.categoryList}>
            {topCategories.map((category, index) => (
              <View key={category.id} style={styles.categoryRow}>
                <View style={styles.categoryIconFrame}>
                  <Image
                    contentFit="contain"
                    source={category.icon}
                    style={styles.categoryIcon}
                  />
                </View>

                <View style={styles.categoryContent}>
                  <View style={styles.categoryTextRow}>
                    <Text numberOfLines={1} style={styles.categoryName}>
                      {category.name}
                    </Text>
                    <Text style={styles.categoryAmount}>
                      {formatCurrency(category.amount)}
                    </Text>
                  </View>

                  <View style={styles.categoryBarTrack}>
                    <View
                      style={[
                        styles.categoryBar,
                        { width: `${category.sharePercent}%` },
                        index > 0 && styles.categoryBarMuted,
                      ]}
                    />
                  </View>
                </View>

                <Text style={styles.categoryShare}>{`${category.sharePercent}%`}</Text>
              </View>
            ))}
          </View>
        </>
      )}
    </Pressable>
  );
}
