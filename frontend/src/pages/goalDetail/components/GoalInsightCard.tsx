import { Image } from 'expo-image';
import { Text, View } from 'react-native';

import { colors } from '@/styles';
import type { GoalDetail } from '@/types/goal.types';
import { formatCurrency } from '@/utils/currency';

import { styles } from './GoalInsightCard.styles';

interface GoalInsightCardProps {
  goal: GoalDetail;
}

export function GoalInsightCard({ goal }: GoalInsightCardProps) {
  const isSavingGoal = goal.type === 'saving';
  const isSpendingExceeded =
    goal.type === 'reducing' && goal.currentAmount > goal.targetAmount;
  const remainingBudget = Math.max(goal.targetAmount - goal.currentAmount, 0);
  const exceededAmount = Math.max(goal.currentAmount - goal.targetAmount, 0);
  const accentColor = isSavingGoal
    ? colors.primary.green
    : colors.primary.orange;

  const leftMetric = isSavingGoal
    ? { label: '예상 달성일', value: goal.expectedAchievementDate }
    : {
        label: '전월 대비 예상 절약',
        value: formatCurrency(goal.expectedSavingAmount),
      };
  const rightMetric = isSavingGoal
    ? {
        label: '월 필요 저축액',
        value: formatCurrency(goal.monthlyRequiredAmount),
      }
    : {
        label: '하루 사용 가능 금액',
        value: formatCurrency(goal.dailyBudget),
      };
  const eyebrow = isSavingGoal
    ? '현재 속도라면'
    : isSpendingExceeded
      ? '이번 달 소비 현황'
      : '이번 달 남은 예산';
  const highlight = isSavingGoal
    ? goal.statusAccentText
    : isSpendingExceeded
      ? `${formatCurrency(exceededAmount)} 초과했어요`
      : `${formatCurrency(remainingBudget)} 남았어요`;
  const description = isSavingGoal
    ? goal.statusDescription
    : isSpendingExceeded
      ? '이번 달 목표 금액을 넘었어요.\n남은 소비 계획을 다시 확인해보세요.'
      : `하루 ${formatCurrency(goal.dailyBudget)} 안에서 사용하면\n목표를 지킬 수 있어요.`;

  return (
    <View style={styles.card}>
      <View style={styles.summary}>
        <Image
          contentFit="contain"
          source={require('@/assets/images/goal/piggy_bank.png')}
          style={styles.illustration}
        />

        <View style={styles.message}>
          <Text style={styles.eyebrow}>
            {eyebrow}
          </Text>
          <Text style={styles.highlight}>
            <Text style={{ color: accentColor }}>{highlight}</Text>
          </Text>
          <Text style={styles.description}>{description}</Text>
        </View>
      </View>

      <View style={styles.divider} />

      <View style={styles.metrics}>
        <View style={styles.metric}>
          <Text style={styles.metricLabel}>{leftMetric.label}</Text>
          <Text style={styles.metricValue}>{leftMetric.value}</Text>
        </View>

        <View style={styles.metricDivider} />

        <View style={styles.metric}>
          <Text style={styles.metricLabel}>{rightMetric.label}</Text>
          <Text style={styles.metricValue}>{rightMetric.value}</Text>
        </View>
      </View>
    </View>
  );
}
