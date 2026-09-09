import { useState } from 'react';
import { View } from 'react-native';

import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';

import {
  GoalTypeStep,
  type GoalType,
} from './components/GoalTypeStep';
import { styles } from './GoalSetupScreen.styles';

export function GoalSetupScreen() {
  const [selectedType, setSelectedType] = useState<GoalType | null>(null);

  return (
    <AuthScreenLayout
      title="목표를 설정하세요"
      description="도담과 함께 이루고 싶은 목표를 골라보세요"
      showBackButton>
      <View style={styles.content}>
        <GoalTypeStep
          selectedType={selectedType}
          onSelect={setSelectedType}
        />
      </View>
    </AuthScreenLayout>
  );
}