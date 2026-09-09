import { Image } from 'expo-image';
import { Pressable, Text, View } from 'react-native';

import { styles } from './GoalTypeStep.styles';

export type GoalType = 'saving' | 'spendingReduction';

interface GoalTypeStepProps {
  selectedType: GoalType | null;
  onSelect: (type: GoalType) => void;
}

const GOAL_OPTIONS = [
  {
    type: 'saving',
    title: '모으기',
    description: '돈을 모아 목표를 달성하고 싶어요',
    image: require('@/assets/characters/dori/dori_saving.png'),
  },
  {
    type: 'spendingReduction',
    title: '줄이기',
    description: '소비를 줄여 목표를 달성하고 싶어요',
    image: require('@/assets/characters/tori/tori_spending_reduction.png'),
  },
] as const;

export function GoalTypeStep({
  selectedType,
  onSelect,
}: GoalTypeStepProps) {
  return (
    <View style={styles.container}>
      <Image
        source={require('@/assets/images/goal/piggy_bank.png')}
        contentFit="contain"
        style={styles.heroImage}
      />

      <View style={styles.optionList}>
        {GOAL_OPTIONS.map((option) => {
          const isSelected = selectedType === option.type;

          return (
            <Pressable
              key={option.type}
              onPress={() => onSelect(option.type)}
              accessibilityRole="radio"
              accessibilityState={{ selected: isSelected }}
              style={({ pressed }) => [
                styles.option,
                isSelected && styles.selectedOption,
                pressed && styles.pressedOption,
              ]}>
              <Image
                source={option.image}
                contentFit="contain"
                style={styles.optionImage}
              />

              <View style={styles.optionText}>
                <Text style={styles.optionTitle}>{option.title}</Text>
                <Text style={styles.optionDescription}>
                  {option.description}
                </Text>
              </View>

              <Text style={styles.chevron}>›</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}