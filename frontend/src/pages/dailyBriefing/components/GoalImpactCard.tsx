import { Image } from 'expo-image';
import { Text, View } from 'react-native';

import type { DailyBriefingGoalImpact } from '@/mocks/dailyBriefing';

import { styles as screenStyles } from '../DailyBriefingScreen.styles';
import { styles } from './GoalImpactCard.styles';

type GoalImpactCardProps = {
  impacts: readonly DailyBriefingGoalImpact[];
};

export function GoalImpactCard({ impacts }: GoalImpactCardProps) {
  return (
    <View style={[screenStyles.card, styles.goalCard]}>
      <Text style={screenStyles.sectionTitle}>목표에는 이렇게 반영됐어요</Text>
      <View style={styles.goalImpactRow}>
        {impacts.map((impact, index) => (
          <View key={impact.id} style={styles.goalImpactItem}>
            {index > 0 ? <View style={styles.goalDivider} /> : null}
            <Image contentFit="contain" source={impact.icon} style={styles.goalIcon} />
            <View style={styles.goalTextContent}>
              <Text numberOfLines={1} style={styles.goalLabel}>
                {impact.label}
              </Text>
              <Text numberOfLines={1} style={styles.goalValue}>
                {impact.value}
              </Text>
            </View>
          </View>
        ))}
      </View>
    </View>
  );
}
