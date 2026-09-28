import { Image } from 'expo-image';
import { Text, View } from 'react-native';

import { colors } from '@/styles';
import type { HomeGoal } from '@/types/goal.types';
import { formatCurrency } from '@/utils/currency';
import { calculateProgressPercent } from '@/utils/goal';

import { styles } from './GoalCard.styles';

type GoalCardProps = {
  goal: HomeGoal;
};

export function GoalCard({ goal }: GoalCardProps) {
  const isSavingGoal = goal.type === 'saving';
  const progressColor = isSavingGoal ? colors.primary.yellow : colors.primary.orange;
  const goalIconBackgroundColor = isSavingGoal
    ? colors.goal.savingCharacterBackground
    : colors.goal.reducingCharacterBackground;
  const insightBackgroundColor = isSavingGoal
    ? colors.goal.savingCharacterBackground
    : colors.goal.reducingCharacterBackground;
  const progressPercent = calculateProgressPercent(goal.currentAmount, goal.targetAmount);
  const goalIconSource = isSavingGoal
    ? require('@/assets/icons/goal/saving_money_bag.png')
    : require('@/assets/icons/goal/acorn_icon.png');
  const singleLineMessage = goal.message.replace(/\s*\n\s*/g, ' ');

  return (
    <View style={styles.card}>
      <View style={styles.header}>
        <View style={[styles.goalIconBackground, { backgroundColor: goalIconBackgroundColor }]}>
          <Image
            contentFit="contain"
            source={goalIconSource}
            style={styles.goalIcon}
          />
        </View>
        <View style={styles.goalIdentity}>
          <Text numberOfLines={1} style={styles.title}>{goal.title}</Text>
          <Text style={styles.dueDate}>{goal.dueDateLabel}</Text>
        </View>
      </View>

      <View style={styles.statusRow}>
        <View style={styles.statusCopy}>
          <Text style={styles.dDay}>{`D-${goal.dDay}`}</Text>
          {isSavingGoal ? (
            <View style={styles.amountRow}>
              <Text style={[styles.currentAmount, { color: progressColor }]}>
                {formatCurrency(goal.currentAmount)}
              </Text>
              <Text style={styles.targetAmount}>{` / ${formatCurrency(goal.targetAmount)}`}</Text>
            </View>
          ) : (
            <View style={styles.amountRow}>
              <Text style={styles.reducingLabel}>이번 달 절약 목표 </Text>
              <Text style={styles.reducingAmount}>{formatCurrency(goal.targetAmount)}</Text>
            </View>
          )}
        </View>

        <Image contentFit="contain" source={goal.characterImage} style={styles.characterImage} />
      </View>

      <View style={styles.progressRow}>
        <View style={styles.progressTrack}>
          <View
            style={[
              styles.progressFill,
              { backgroundColor: progressColor, width: `${progressPercent}%` },
            ]}
          />
        </View>
        <Text style={styles.progressPercent}>{`${progressPercent}%`}</Text>
      </View>

      <View style={[styles.insightBanner, { backgroundColor: insightBackgroundColor }]}>
        <Image
          contentFit="contain"
          source={require('@/assets/images/home/goal_card_leaf.png')}
          style={styles.leafIcon}
        />
        <Text numberOfLines={1} style={styles.messageText}>{singleLineMessage}</Text>
        <Text style={styles.chevron}>›</Text>
      </View>
    </View>
  );
}
