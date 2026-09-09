import { View } from 'react-native';

import { styles } from './GoalStepProgress.styles';

interface GoalStepProgressProps {
  currentStep: number;
  totalSteps: number;
}

export function GoalStepProgress({
  currentStep,
  totalSteps,
}: GoalStepProgressProps) {
  return (
    <View
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 1, max: totalSteps, now: currentStep }}
      style={styles.container}>
      {Array.from({ length: totalSteps }, (_, index) => (
        <View
          key={index}
          style={[
            styles.bar,
            index === currentStep - 1 && styles.activeBar,
          ]}
        />
      ))}
    </View>
  );
}
