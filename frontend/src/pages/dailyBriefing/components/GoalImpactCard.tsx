import { Image } from 'expo-image';
import { Text, View } from 'react-native';

import type { DailyBriefingGoalImpact } from '../dailyBriefing.types';
import { styles as screenStyles } from '../DailyBriefingScreen.styles';
import { styles } from './GoalImpactCard.styles';

type GoalImpactCardProps = {
  impacts: readonly DailyBriefingGoalImpact[];
  message?: string | null;
};

export function GoalImpactCard({ impacts, message }: GoalImpactCardProps) {
  return (
    <View style={[screenStyles.card, styles.goalCard]}>
      <Text style={screenStyles.sectionTitle}>목표 흐름도 확인했어요</Text>
      {impacts.length === 0 ? (
        <Text style={screenStyles.emptyMessage}>{message ?? '진행 중인 목표가 없어요.'}</Text>
      ) : <View style={styles.goalImpactRow}>
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
      </View>}
    </View>
  );
}
