import { useCallback, useMemo, useState } from 'react';
import { Modal, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { toDateKey, toTimeKeyFromParts } from '@/utils/date';

import { styles } from './YearMonthPickerBottomSheet.styles';
import { WheelPickerColumn } from './WheelPickerColumn';

interface CashTransactionTimePickerBottomSheetProps {
  visible: boolean;
  dateKey: string;
  value: string;
  onClose: () => void;
  onConfirm: (time: string) => void;
}

interface PickerTime {
  hour: number;
  minute: number;
}

function TimePickerContent({
  initialValue,
  maxHour,
  maxMinuteForMaxHour,
  onConfirm,
}: {
  initialValue: string;
  maxHour: number;
  maxMinuteForMaxHour: number;
  onConfirm: (time: string) => void;
}) {
  const [selected, setSelected] = useState<PickerTime>(() => {
    const [rawHour, rawMinute] = initialValue.split(':').map(Number);
    const hasValidParts =
      Number.isInteger(rawHour) &&
      Number.isInteger(rawMinute) &&
      rawHour >= 0 &&
      rawHour <= 23 &&
      rawMinute >= 0 &&
      rawMinute <= 59;
    const initHour = hasValidParts ? rawHour : maxHour;
    const initMinute = hasValidParts ? rawMinute : maxMinuteForMaxHour;
    const clampedHour = Math.min(initHour, maxHour);
    const minuteCap = clampedHour === maxHour ? maxMinuteForMaxHour : 59;
    return { hour: clampedHour, minute: Math.min(initMinute, minuteCap) };
  });
  const { hour: selectedHour, minute: selectedMinute } = selected;

  const hours = useMemo(
    () => Array.from({ length: maxHour + 1 }, (_, index) => maxHour - index),
    [maxHour],
  );

  const minuteCount = selectedHour === maxHour ? maxMinuteForMaxHour + 1 : 60;
  const minutes = useMemo(
    () => Array.from({ length: minuteCount }, (_, index) => minuteCount - 1 - index),
    [minuteCount],
  );

  const handleHourSelect = useCallback(
    (hour: number) => {
      setSelected((current) => {
        const nextMinuteCap = hour === maxHour ? maxMinuteForMaxHour : 59;
        return { hour, minute: Math.min(current.minute, nextMinuteCap) };
      });
    },
    [maxHour, maxMinuteForMaxHour],
  );

  const handleMinuteSelect = useCallback((minute: number) => {
    setSelected((current) => ({ ...current, minute }));
  }, []);

  return (
    <>
      <Text style={styles.title}>{toTimeKeyFromParts(selectedHour, selectedMinute)}</Text>

      <View style={styles.picker}>
        <View pointerEvents="none" style={styles.selectionRow}>
          <View style={styles.selectionCell} />
          <View style={styles.selectionCell} />
        </View>

        <WheelPickerColumn
          options={hours}
          selectedValue={selectedHour}
          suffix="시"
          onSelect={handleHourSelect}
        />

        <WheelPickerColumn
          options={minutes}
          selectedValue={selectedMinute}
          suffix="분"
          onSelect={handleMinuteSelect}
        />
      </View>

      <Pressable
        onPress={() => onConfirm(toTimeKeyFromParts(selectedHour, selectedMinute))}
        accessibilityRole="button"
        style={styles.confirmButton}>
        <Text style={styles.confirmLabel}>완료</Text>
      </Pressable>
    </>
  );
}

export function CashTransactionTimePickerBottomSheet({
  visible,
  dateKey,
  value,
  onClose,
  onConfirm,
}: CashTransactionTimePickerBottomSheetProps) {
  const now = new Date();
  const isToday = dateKey === toDateKey(now);
  const maxHour = isToday ? now.getHours() : 23;
  const maxMinuteForMaxHour = isToday ? now.getMinutes() : 59;

  const contentKey = visible ? `open-${dateKey}-${value}` : 'hidden';

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
          accessibilityLabel="시간 선택 닫기"
          style={styles.backdrop}
        />

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <TimePickerContent
            key={contentKey}
            initialValue={value}
            maxHour={maxHour}
            maxMinuteForMaxHour={maxMinuteForMaxHour}
            onConfirm={onConfirm}
          />
        </SafeAreaView>
      </View>
    </Modal>
  );
}
