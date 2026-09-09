import { Pressable, Text, TextInput, View } from 'react-native';

import { colors } from '@/styles';

import { styles } from './ReductionAmountStep.styles';

const AMOUNT_ADJUSTMENTS = [
  { label: '-10만', value: -100_000 },
  { label: '-5만', value: -50_000 },
  { label: '+5만', value: 50_000 },
  { label: '+10만', value: 100_000 },
] as const;

interface ReductionAmountStepProps {
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

export function ReductionAmountStep({
  value,
  onChange,
}: ReductionAmountStepProps) {
  const formattedAmount = value > 0 ? value.toLocaleString('ko-KR') : '';

  const adjustAmount = (adjustment: number) => {
    onChange(
      Math.min(
        Math.max(value + adjustment, 0),
        Number.MAX_SAFE_INTEGER,
      ),
    );
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
          accessibilityLabel="이번 달 목표 소비 금액"
          style={styles.amountInput}
        />

        <Text style={styles.currency}>원</Text>

        {value > 0 ? (
          <Pressable
            onPress={() => onChange(0)}
            accessibilityRole="button"
            accessibilityLabel="목표 소비 금액 지우기"
            hitSlop={8}
            style={styles.clearButton}>
            <Text style={styles.clearIcon}>×</Text>
          </Pressable>
        ) : null}
      </View>

      <View style={styles.adjustmentList}>
        {AMOUNT_ADJUSTMENTS.map((adjustment) => {
          const isDisabled = value + adjustment.value < 0;

          return (
            <Pressable
              key={adjustment.value}
              onPress={() => adjustAmount(adjustment.value)}
              disabled={isDisabled}
              accessibilityRole="button"
              accessibilityLabel={`${adjustment.label}원 조절`}
              accessibilityState={{ disabled: isDisabled }}
              style={({ pressed }) => [
                styles.adjustment,
                isDisabled && styles.disabledAdjustment,
                pressed && !isDisabled && styles.pressedAdjustment,
              ]}>
              <Text
                style={[
                  styles.adjustmentLabel,
                  isDisabled && styles.disabledAdjustmentLabel,
                ]}>
                {adjustment.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}
