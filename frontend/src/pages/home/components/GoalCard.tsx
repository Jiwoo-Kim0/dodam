import { Image } from 'expo-image';
import { Text, View } from 'react-native';

import type { HomeGoal } from '@/mocks/home';
import { colors } from '@/styles';
import { formatCurrency } from '@/utils/currency';

import { styles } from './GoalCard.styles';

type GoalCardProps = {
  goal: HomeGoal;
};

function calculateProgressPercent(currentAmount: number, targetAmount: number) {
  if (
    !Number.isFinite(currentAmount) ||
    !Number.isFinite(targetAmount) ||
    targetAmount <= 0
  ) {
    return 0;
  }

  const percentage = Math.floor((currentAmount / targetAmount) * 100);
  return Math.min(100, Math.max(0, percentage));
}

export function GoalCard({ goal }: GoalCardProps) {
  const progressColor = goal.type === 'saving' ? colors.primary.yellow : colors.primary.orange;
  const progressPercent = calculateProgressPercent(goal.currentAmount, goal.targetAmount);
  const isSavingGoal = goal.type === 'saving';

  return (
    <View style={styles.card}>
      <View style={styles.header}>
        <Text style={styles.title}>{goal.title}</Text>
        <Text style={styles.dueDate}>{goal.dueDateLabel}</Text>
      </View>

      <View style={styles.goalStatus}>
        <Text style={styles.dDay}>{`D-${goal.dDay}`}</Text>

        <View style={styles.messageBubble}>
          <Image
            contentFit="contain"
            source={require('@/assets/images/home/goal_card_leaf.png')}
            style={styles.leafDecoration}
          />
          <Text style={styles.messageText}>{goal.message}</Text>
        </View>

        <Image contentFit="contain" source={goal.characterImage} style={styles.characterImage} />
      </View>

      {isSavingGoal ? (
        <View style={styles.amountRow}>
          <Text style={styles.currentAmount}>{formatCurrency(goal.currentAmount)}</Text>
          <Text style={styles.targetAmount}>{` / ${formatCurrency(goal.targetAmount)}`}</Text>
        </View>
      ) : (
        <Text style={styles.reducingAmount}>
          {`이번 달 절약 목표 ${formatCurrency(goal.targetAmount)}`}
        </Text>
      )}

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
    </View>
  );
}
