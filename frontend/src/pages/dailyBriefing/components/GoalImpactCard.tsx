import { Image } from 'expo-image';
import { Fragment } from 'react';
import { Text, View } from 'react-native';

import type { DailyBriefingGoalImpact } from '../dailyBriefing.types';
import { styles as screenStyles } from '../DailyBriefingScreen.styles';
import { styles } from './GoalImpactCard.styles';

type GoalImpactCardProps = {
  impacts: readonly DailyBriefingGoalImpact[];
  message?: string | null;
};

const valueToneStyles: Record<DailyBriefingGoalImpact['tone'], object | undefined> = {
  good: styles.goalValueGood,
  bad: styles.goalValueBad,
  neutral: undefined,
};

export function GoalImpactCard({ impacts, message }: GoalImpactCardProps) {
  return (
    <View style={[screenStyles.card, styles.goalCard]}>
      <Text style={screenStyles.sectionTitle}>목표 흐름도 확인했어요</Text>
      {impacts.length === 0 ? (
        <Text style={screenStyles.emptyMessage}>{message ?? '진행 중인 목표가 없어요.'}</Text>
      ) : <View style={styles.goalList}>
        {impacts.map((impact, index) => (
          <Fragment key={impact.id}>
            {index > 0 ? <View style={styles.goalDivider} /> : null}

            <View style={styles.goalRow}>
              {/* 아이콘이 원색이라 회색 원 안에 넣어 채도를 눌러 준다. */}
              <View style={styles.goalIconFrame}>
                <Image contentFit="contain" source={impact.icon} style={styles.goalIcon} />
              </View>

              <Text numberOfLines={1} style={styles.goalLabel}>
                {impact.label}
              </Text>
              <Text numberOfLines={1} style={[styles.goalValue, valueToneStyles[impact.tone]]}>
                {impact.value}
              </Text>
            </View>
          </Fragment>
        ))}
      </View>}
    </View>
  );
}
