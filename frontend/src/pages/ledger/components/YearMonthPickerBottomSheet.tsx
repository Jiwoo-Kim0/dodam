import { useCallback, useMemo, useState } from 'react';
import { Modal, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { styles } from './YearMonthPickerBottomSheet.styles';
import { WheelPickerColumn } from './WheelPickerColumn';

interface YearMonthPickerBottomSheetProps {
  visible: boolean;
  year: number;
  month: number;
  onClose: () => void;
  onConfirm: (year: number, month: number) => void;
}

interface PickerYearMonth {
  year: number;
  month: number;
}

const YEARS_BACK = 10;

function YearMonthPickerContent({
  initialYear,
  initialMonth,
  onConfirm,
}: {
  initialYear: number;
  initialMonth: number;
  onConfirm: (year: number, month: number) => void;
}) {
  const now = new Date();
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1;

  const [selected, setSelected] = useState<PickerYearMonth>(() => {
    const clampedYear = Math.min(initialYear, currentYear);
    const monthCap = clampedYear === currentYear ? currentMonth : 12;
    return { year: clampedYear, month: Math.min(initialMonth, monthCap) };
  });
  const { year: selectedYear, month: selectedMonth } = selected;

  const years = useMemo(
    () => Array.from({ length: YEARS_BACK + 1 }, (_, index) => currentYear - index),
    [currentYear],
  );

  const monthCount = selectedYear === currentYear ? currentMonth : 12;
  const months = useMemo(
    () => Array.from({ length: monthCount }, (_, index) => monthCount - index),
    [monthCount],
  );

  const handleYearSelect = useCallback(
    (year: number) => {
      setSelected((current) => {
        const nextMonthCap = year === currentYear ? currentMonth : 12;
        return { year, month: Math.min(current.month, nextMonthCap) };
      });
    },
    [currentMonth, currentYear],
  );

  const handleMonthSelect = useCallback((month: number) => {
    setSelected((current) => ({ ...current, month }));
  }, []);

  return (
    <>
      <Text style={styles.title}>
        {selectedYear}년 {selectedMonth}월
      </Text>

      <View style={styles.picker}>
        <View pointerEvents="none" style={styles.selectionRow}>
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
      </View>

      <Pressable
        onPress={() => onConfirm(selectedYear, selectedMonth)}
        accessibilityRole="button"
        style={styles.confirmButton}>
        <Text style={styles.confirmLabel}>완료</Text>
      </Pressable>
    </>
  );
}

export function YearMonthPickerBottomSheet({
  visible,
  year,
  month,
  onClose,
  onConfirm,
}: YearMonthPickerBottomSheetProps) {
  const contentKey = visible ? `open-${year}-${month}` : 'hidden';

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      statusBarTranslucent
      onRequestClose={onClose}>
      <View style={styles.overlay}>
        <Pressable
          onPress={onClose}
          accessibilityRole="button"
          accessibilityLabel="년월 선택 닫기"
          style={styles.backdrop}
        />

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <YearMonthPickerContent
            key={contentKey}
            initialYear={year}
            initialMonth={month}
            onConfirm={onConfirm}
          />
        </SafeAreaView>
      </View>
    </Modal>
  );
}
