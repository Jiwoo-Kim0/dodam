import { Pressable, Text, TextInput, View } from 'react-native';

import { colors } from '@/styles';

import { styles } from './SavingAmountStep.styles';

const AMOUNT_OPTIONS = [
  { label: '+10만', value: 100_000 },
  { label: '+50만', value: 500_000 },
  { label: '+100만', value: 1_000_000 },
  { label: '+1,000만', value: 10_000_000 },
] as const;

interface SavingAmountStepProps {
  value: number;
  onChange: (value: number) => void;
}

const getAmountFromText = (value: string) => {
  const digits = value.replace(/\D/g, '');

  if (!digits) {
    return 0;
  }

  const amount = Number(digits);

  return Number.isSafeInteger(amount) ? amount : Number.MAX_SAFE_INTEGER;
};

export function SavingAmountStep({
  value,
  onChange,
}: SavingAmountStepProps) {
  const formattedAmount = value > 0 ? value.toLocaleString('ko-KR') : '';

  const addAmount = (amount: number) => {
    onChange(Math.min(value + amount, Number.MAX_SAFE_INTEGER));
  };

  return (
    <View style={styles.container}>
      <View style={styles.amountRow}>
        <TextInput
          value={formattedAmount}
          onChangeText={(text) => onChange(getAmountFromText(text))}
          placeholder="0"
          placeholderTextColor={colors.textTertiary}
          keyboardType="number-pad"
          accessibilityLabel="목표 금액"
          style={styles.amountInput}
        />

        <Text style={styles.currency}>원</Text>

        {value > 0 ? (
          <Pressable
            onPress={() => onChange(0)}
            accessibilityRole="button"
            accessibilityLabel="목표 금액 지우기"
            hitSlop={8}
            style={styles.clearButton}>
            <Text style={styles.clearIcon}>×</Text>
          </Pressable>
        ) : null}
      </View>

      <View style={styles.optionList}>
        {AMOUNT_OPTIONS.map((option) => (
          <Pressable
            key={option.value}
            onPress={() => addAmount(option.value)}
            accessibilityRole="button"
            accessibilityLabel={`${option.label}원 추가`}
            style={({ pressed }) => [
              styles.option,
              pressed && styles.pressedOption,
            ]}>
            <Text style={styles.optionLabel}>{option.label}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}
