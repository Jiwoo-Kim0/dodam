import { Text, View } from 'react-native';

import { styles as screenStyles } from '../DailyBriefingScreen.styles';
import { styles } from './StreakSection.styles';

type Streak = {
  id: string;
  label: string;
  value: string;
};

type StreakSectionProps = {
  streaks: readonly Streak[];
};

export function StreakSection({ streaks }: StreakSectionProps) {
  return (
    <View style={[screenStyles.card, styles.streakSection]}>
      <Text style={screenStyles.sectionTitle}>이어가고 있는 기록</Text>
      <View style={styles.streakRow}>
        {streaks.map((streak) => (
          <View key={streak.id} style={styles.streakCard}>
            <Text style={styles.streakLabel}>{streak.label}</Text>
            <Text style={styles.streakValue}>{streak.value}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}
