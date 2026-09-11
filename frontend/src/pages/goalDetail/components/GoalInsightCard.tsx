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
  const accentColor = isSavingGoal
    ? colors.primary.green
    : colors.primary.orange;

  const leftMetric = isSavingGoal
    ? { label: '예상 달성일', value: goal.expectedAchievementDate }
    : {
        label: '예상 절약액',
        value: formatCurrency(goal.expectedSavingAmount),
      };
  const rightMetric = isSavingGoal
    ? {
        label: '월 필요 저축액',
        value: formatCurrency(goal.monthlyRequiredAmount),
      }
    : {
        label: '하루 필요 절약액',
        value: formatCurrency(goal.dailyBudget),
      };

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
            {isSavingGoal ? '현재 속도라면' : '목표를 달성하려면'}
          </Text>
          <Text style={styles.highlight}>
            <Text style={{ color: accentColor }}>{goal.statusAccentText}</Text>
            {isSavingGoal ? null : ' 더 아껴요'}
          </Text>
          <Text style={styles.description}>{goal.statusDescription}</Text>
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
