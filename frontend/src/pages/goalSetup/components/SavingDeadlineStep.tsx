import { useState } from 'react';

import { SymbolView } from 'expo-symbols';
import { Pressable, Text, View } from 'react-native';

import { colors } from '@/styles';

import { DatePickerBottomSheet } from './DatePickerBottomSheet';
import { styles } from './SavingDeadlineStep.styles';

interface SavingDeadlineStepProps {
  value: Date | null;
  onChange: (value: Date) => void;
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const;

const formatDate = (date: Date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const weekday = WEEKDAYS[date.getDay()];

  return `${year}.${month}.${day} (${weekday})`;
};

export function SavingDeadlineStep({
  value,
  onChange,
}: SavingDeadlineStepProps) {
  const [isPickerVisible, setIsPickerVisible] = useState(false);

  return (
    <View style={styles.container}>
      <Pressable
        onPress={() => setIsPickerVisible(true)}
        accessibilityRole="button"
        accessibilityLabel="목표 날짜 선택"
        style={styles.dateField}>
        <Text style={[styles.dateText, !value && styles.placeholder]}>
          {value ? formatDate(value) : '날짜를 선택해주세요'}
        </Text>

        <SymbolView
          name={{
            android: 'calendar_month',
            ios: 'calendar',
            web: 'calendar_month',
          }}
          size={20}
          tintColor={colors.textSecondary}
        />
      </Pressable>

      <DatePickerBottomSheet
        visible={isPickerVisible}
        value={value}
        onClose={() => setIsPickerVisible(false)}
        onConfirm={(date) => {
          onChange(date);
          setIsPickerVisible(false);
        }}
      />
    </View>
  );
}