import {
  memo,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  FlatList,
  Modal,
  Pressable,
  Text,
  View,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { componentSize } from '@/styles';

import { styles } from './DatePickerBottomSheet.styles';

interface DatePickerBottomSheetProps {
  visible: boolean;
  value: Date | null;
  onClose: () => void;
  onConfirm: (date: Date) => void;
}

interface PickerColumnProps {
  options: readonly number[];
  selectedValue: number;
  suffix: string;
  onSelect: (value: number) => void;
}

interface DatePickerContentProps {
  initialDate: Date;
  onConfirm: (date: Date) => void;
}

interface PickerDate {
  year: number;
  month: number;
  day: number;
}

const ITEM_HEIGHT = componentSize.touchTarget;

function getMinimumGoalDate() {
  const minimum = new Date();
  minimum.setHours(0, 0, 0, 0);
  minimum.setDate(minimum.getDate() + 1);
  return minimum;
}

const PickerColumn = memo(function PickerColumn({
  options,
  selectedValue,
  suffix,
  onSelect,
}: PickerColumnProps) {
  const listRef = useRef<FlatList<number>>(null);
  const selectedIndex = Math.max(options.indexOf(selectedValue), 0);

  useEffect(() => {
    listRef.current?.scrollToOffset({
      offset: selectedIndex * ITEM_HEIGHT,
      animated: false,
    });
  }, [selectedIndex]);

  const handleScrollEnd = (
    event: NativeSyntheticEvent<NativeScrollEvent>,
  ) => {
    const index = Math.round(
      event.nativeEvent.contentOffset.y / ITEM_HEIGHT,
    );
    const boundedIndex = Math.min(
      Math.max(index, 0),
      options.length - 1,
    );
    const nextValue = options[boundedIndex];

    if (nextValue !== undefined && nextValue !== selectedValue) {
      onSelect(nextValue);
    }
  };

  return (
    <FlatList
      ref={listRef}
      data={options}
      extraData={selectedValue}
      initialScrollIndex={selectedIndex}
      keyExtractor={(option) => option.toString()}
      getItemLayout={(_, index) => ({
        length: ITEM_HEIGHT,
        offset: ITEM_HEIGHT * index,
        index,
      })}
      renderItem={({ item }) => {
        const isSelected = item === selectedValue;

        return (
          <View
            accessibilityRole="radio"
            accessibilityState={{ selected: isSelected }}
            style={styles.option}>
            <Text
              style={[
                styles.optionLabel,
                isSelected && styles.selectedOptionLabel,
              ]}>
              {item}
              {suffix}
            </Text>
          </View>
        );
      }}
      snapToInterval={ITEM_HEIGHT}
      decelerationRate="fast"
      bounces={false}
      overScrollMode="never"
      showsVerticalScrollIndicator={false}
      contentContainerStyle={styles.columnContent}
      onMomentumScrollEnd={handleScrollEnd}
      style={styles.column}
    />
  );
});

function DatePickerContent({
  initialDate,
  onConfirm,
}: DatePickerContentProps) {
  const minimumDate = getMinimumGoalDate();
  const minimumYear = minimumDate.getFullYear();
  const minimumMonth = minimumDate.getMonth() + 1;
  const minimumDay = minimumDate.getDate();
  const [selectedDate, setSelectedDate] = useState<PickerDate>(() => ({
    year: initialDate.getFullYear(),
    month: initialDate.getMonth() + 1,
    day: initialDate.getDate(),
  }));
  const { year: selectedYear, month: selectedMonth, day: selectedDay } =
    selectedDate;

  const years = useMemo(
    () => Array.from({ length: 11 }, (_, index) => minimumYear + index),
    [minimumYear],
  );
  const firstMonth = selectedYear === minimumYear ? minimumMonth : 1;
  const months = useMemo(
    () => Array.from(
      { length: 13 - firstMonth },
      (_, index) => firstMonth + index,
    ),
    [firstMonth],
  );
  const daysInMonth = new Date(selectedYear, selectedMonth, 0).getDate();
  const firstDay =
    selectedYear === minimumYear && selectedMonth === minimumMonth
      ? minimumDay
      : 1;
  const days = useMemo(
    () => Array.from(
      { length: daysInMonth - firstDay + 1 },
      (_, index) => firstDay + index,
    ),
    [daysInMonth, firstDay],
  );

  const handleYearSelect = useCallback((year: number) => {
    setSelectedDate((currentDate) => {
      const month = year === minimumYear
        ? Math.max(currentDate.month, minimumMonth)
        : currentDate.month;
      const firstValidDay =
        year === minimumYear && month === minimumMonth ? minimumDay : 1;
      const lastValidDay = new Date(year, month, 0).getDate();

      return {
        year,
        month,
        day: Math.max(firstValidDay, Math.min(currentDate.day, lastValidDay)),
      };
    });
  }, [minimumDay, minimumMonth, minimumYear]);

  const handleMonthSelect = useCallback((month: number) => {
    setSelectedDate((currentDate) => {
      const firstValidDay =
        currentDate.year === minimumYear && month === minimumMonth
          ? minimumDay
          : 1;
      const lastValidDay = new Date(currentDate.year, month, 0).getDate();

      return {
        ...currentDate,
        month,
        day: Math.max(firstValidDay, Math.min(currentDate.day, lastValidDay)),
      };
    });
  }, [minimumDay, minimumMonth, minimumYear]);

  const handleDaySelect = useCallback((day: number) => {
    setSelectedDate((currentDate) => ({ ...currentDate, day }));
  }, []);

  return (
    <>
      <Text style={styles.title}>
        {selectedYear}년 {selectedMonth}월 {selectedDay}일
      </Text>

      <View style={styles.picker}>
        <View
          pointerEvents="none"
          style={styles.selectionRow}>
          <View style={styles.selectionCell} />
          <View style={styles.selectionCell} />
          <View style={styles.selectionCell} />
        </View>

        <PickerColumn
          options={years}
          selectedValue={selectedYear}
          suffix="년"
          onSelect={handleYearSelect}
        />

        <PickerColumn
          options={months}
          selectedValue={selectedMonth}
          suffix="월"
          onSelect={handleMonthSelect}
        />

        <PickerColumn
          options={days}
          selectedValue={selectedDay}
          suffix="일"
          onSelect={handleDaySelect}
        />
      </View>

      <Pressable
        onPress={() =>
          onConfirm(
            new Date(
              selectedYear,
              selectedMonth - 1,
              selectedDay,
            ),
          )
        }
        accessibilityRole="button"
        style={styles.confirmButton}>
        <Text style={styles.confirmLabel}>완료</Text>
      </Pressable>
    </>
  );
}

export function DatePickerBottomSheet({
  visible,
  value,
  onClose,
  onConfirm,
}: DatePickerBottomSheetProps) {
  const minimumDate = getMinimumGoalDate();
  const initialDate = value && value.getTime() >= minimumDate.getTime()
    ? value
    : minimumDate;
  const contentKey = visible
    ? `open-${value?.getTime() ?? 'today'}`
    : 'hidden';

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
          accessibilityLabel="날짜 선택 닫기"
          style={styles.backdrop}
        />

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <DatePickerContent
            key={contentKey}
            initialDate={initialDate}
            onConfirm={onConfirm}
          />
        </SafeAreaView>
      </View>
    </Modal>
  );
}
