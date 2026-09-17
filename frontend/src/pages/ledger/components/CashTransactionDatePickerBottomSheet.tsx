import { useCallback, useMemo, useState } from 'react';
import { Modal, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { toDateKeyFromParts } from '@/utils/date';

import { styles } from './YearMonthPickerBottomSheet.styles';
import { WheelPickerColumn } from './WheelPickerColumn';

interface CashTransactionDatePickerBottomSheetProps {
  visible: boolean;
  value: string;
  onClose: () => void;
  onConfirm: (dateKey: string) => void;
}

interface PickerDate {
  year: number;
  month: number;
  day: number;
}

const YEARS_BACK = 10;

function daysInMonth(year: number, month: number) {
  return new Date(year, month, 0).getDate();
}

function CashDatePickerContent({
  initialValue,
  onConfirm,
}: {
  initialValue: string;
  onConfirm: (dateKey: string) => void;
}) {
  const now = new Date();
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1;
  const currentDay = now.getDate();

  const [selected, setSelected] = useState<PickerDate>(() => {
    const [rawYear, rawMonth, rawDay] = initialValue.split('-').map(Number);
    const hasValidParts =
      Number.isInteger(rawYear) &&
      Number.isInteger(rawMonth) &&
      Number.isInteger(rawDay) &&
      rawMonth >= 1 &&
      rawMonth <= 12 &&
      rawDay >= 1 &&
      rawDay <= daysInMonth(rawYear, rawMonth);
    const initYear = hasValidParts ? rawYear : currentYear;
    const initMonth = hasValidParts ? rawMonth : currentMonth;
    const initDay = hasValidParts ? rawDay : currentDay;
    const clampedYear = Math.max(
      currentYear - YEARS_BACK,
      Math.min(initYear, currentYear),
    );
    const monthCap = clampedYear === currentYear ? currentMonth : 12;
    const clampedMonth = Math.min(initMonth, monthCap);
    const dayCap =
      clampedYear === currentYear && clampedMonth === currentMonth
        ? currentDay
        : daysInMonth(clampedYear, clampedMonth);
    return { year: clampedYear, month: clampedMonth, day: Math.min(initDay, dayCap) };
  });
  const { year: selectedYear, month: selectedMonth, day: selectedDay } = selected;

  const years = useMemo(
    () => Array.from({ length: YEARS_BACK + 1 }, (_, index) => currentYear - index),
    [currentYear],
  );

  const monthCount = selectedYear === currentYear ? currentMonth : 12;
  const months = useMemo(
    () => Array.from({ length: monthCount }, (_, index) => monthCount - index),
    [monthCount],
  );

  const dayCount =
    selectedYear === currentYear && selectedMonth === currentMonth
      ? currentDay
      : daysInMonth(selectedYear, selectedMonth);
  const days = useMemo(
    () => Array.from({ length: dayCount }, (_, index) => dayCount - index),
    [dayCount],
  );

  const handleYearSelect = useCallback(
    (year: number) => {
      setSelected((current) => {
        const nextMonthCap = year === currentYear ? currentMonth : 12;
        const nextMonth = Math.min(current.month, nextMonthCap);
        const nextDayCap =
          year === currentYear && nextMonth === currentMonth
            ? currentDay
            : daysInMonth(year, nextMonth);
        return { year, month: nextMonth, day: Math.min(current.day, nextDayCap) };
      });
    },
    [currentDay, currentMonth, currentYear],
  );

  const handleMonthSelect = useCallback(
    (month: number) => {
      setSelected((current) => {
        const nextDayCap =
          current.year === currentYear && month === currentMonth
            ? currentDay
            : daysInMonth(current.year, month);
        return { ...current, month, day: Math.min(current.day, nextDayCap) };
      });
    },
    [currentDay, currentMonth, currentYear],
  );

  const handleDaySelect = useCallback((day: number) => {
    setSelected((current) => ({ ...current, day }));
  }, []);

  return (
    <>
      <Text style={styles.title}>
        {selectedYear}년 {selectedMonth}월 {selectedDay}일
      </Text>

      <View style={styles.picker}>
        <View pointerEvents="none" style={styles.selectionRow}>
          <View style={styles.selectionCell} />
          <View style={styles.selectionCell} />
          <View style={styles.selectionCell} />
        </View>

        <WheelPickerColumn
          options={years}
          selectedValue={selectedYear}
          suffix="년"
          onSelect={handleYearSelect}
        />

        <WheelPickerColumn
          options={months}
          selectedValue={selectedMonth}
          suffix="월"
          onSelect={handleMonthSelect}
        />

        <WheelPickerColumn
          options={days}
          selectedValue={selectedDay}
          suffix="일"
          onSelect={handleDaySelect}
        />
      </View>

      <Pressable
        onPress={() => onConfirm(toDateKeyFromParts(selectedYear, selectedMonth, selectedDay))}
        accessibilityRole="button"
        style={styles.confirmButton}>
        <Text style={styles.confirmLabel}>완료</Text>
      </Pressable>
    </>
  );
}

export function CashTransactionDatePickerBottomSheet({
  visible,
  value,
  onClose,
  onConfirm,
}: CashTransactionDatePickerBottomSheetProps) {
  const contentKey = visible ? `open-${value}` : 'hidden';

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      statusBarTranslucent
      onRequestClose={onClose}>
      <View style={styles.overlay}>
        <Pressable
          onPress={onClose}
          accessibilityRole="button"
          accessibilityLabel="날짜 선택 닫기"
          style={styles.backdrop}
        />

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <CashDatePickerContent
            key={contentKey}
            initialValue={value}
            onConfirm={onConfirm}
          />
        </SafeAreaView>
      </View>
    </Modal>
  );
}
