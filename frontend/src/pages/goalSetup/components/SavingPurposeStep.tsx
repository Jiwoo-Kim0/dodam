import { Pressable, Text, TextInput, View } from 'react-native';

import { colors } from '@/styles';

import { styles } from './SavingPurposeStep.styles';

interface SavingPurposeStepProps {
  value: string;
  onChange: (value: string) => void;
}

const GOAL_SUGGESTIONS = [
  { label: '비상금', value: '비상금 모으기' },
  { label: '여행', value: '여행 자금 모으기' },
  { label: '이사', value: '이사 자금 모으기' },
  { label: '결혼', value: '결혼 자금 모으기' },
] as const;

export function SavingPurposeStep({
  value,
  onChange,
}: SavingPurposeStepProps) {
  return (
    <View style={styles.container}>
      <TextInput
        value={value}
        onChangeText={onChange}
        placeholder="비상금 모으기"
        placeholderTextColor={colors.textTertiary}
        maxLength={20}
        returnKeyType="done"
        accessibilityLabel="목표 이름"
        style={styles.input}
      />

      <View style={styles.suggestionList}>
        {GOAL_SUGGESTIONS.map((suggestion) => {
          const isSelected = value === suggestion.value;

          return (
            <Pressable
              key={suggestion.value}
              onPress={() => onChange(suggestion.value)}
              accessibilityRole="button"
              accessibilityState={{ selected: isSelected }}
              style={[
                styles.suggestion,
                isSelected && styles.selectedSuggestion,
              ]}>
              <Text
                style={[
                  styles.suggestionLabel,
                  isSelected && styles.selectedSuggestionLabel,
                ]}>
                {suggestion.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}
