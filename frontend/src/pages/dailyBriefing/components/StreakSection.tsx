import { Text, View } from 'react-native';

import { styles as screenStyles } from '../DailyBriefingScreen.styles';
import type { DailyBriefingStreak } from '../dailyBriefing.types';
import { styles } from './StreakSection.styles';

type StreakSectionProps = {
  streaks: readonly DailyBriefingStreak[];
  message?: string | null;
};

export function StreakSection({ streaks, message }: StreakSectionProps) {
  return (
    <View style={[screenStyles.card, styles.streakSection]}>
      <Text style={screenStyles.sectionTitle}>이어가고 있는 기록</Text>
      {streaks.length === 0 ? (
        <Text style={screenStyles.emptyMessage}>{message ?? '현재 이어지는 기록이 없어요.'}</Text>
      ) : <View style={styles.streakRow}>
        {streaks.map((streak) => (
          <View key={streak.id} style={styles.streakCard}>
            <Text style={styles.streakLabel}>{streak.label}</Text>
            <Text style={styles.streakValue}>{streak.value}</Text>
          </View>
        ))}
      </View>}
    </View>
  );
}
