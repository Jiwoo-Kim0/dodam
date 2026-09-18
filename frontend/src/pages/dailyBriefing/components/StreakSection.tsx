import { Image } from 'expo-image';
import { Fragment } from 'react';
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
      ) : <View style={styles.streakList}>
        {streaks.map((streak, index) => (
          <Fragment key={streak.id}>
            {index > 0 ? <View style={styles.streakDivider} /> : null}

            <View style={styles.streakRow}>
              <View style={styles.streakIconFrame}>
                <Image contentFit="contain" source={streak.icon} style={styles.streakIcon} />
              </View>

              <Text numberOfLines={1} style={styles.streakLabel}>
                {streak.label}
              </Text>
              {/* 이어가고 있다는 건 좋은 신호라 값에 초록을 준다. */}
              <Text numberOfLines={1} style={styles.streakValue}>
                {streak.value}
              </Text>
            </View>
          </Fragment>
        ))}
      </View>}
    </View>
  );
}
