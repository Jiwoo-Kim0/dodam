import { Pressable, Text, View } from 'react-native';

import type { ReportEfficiencyScore } from '@/types/report.types';

import { styles as screenStyles } from '../ReportScreen.styles';
import { styles } from './EfficiencyScoreCard.styles';

type EfficiencyScoreCardProps = {
  efficiencyScore: ReportEfficiencyScore;
  onPress: () => void;
};

function differenceLabel(difference: number) {
  if (difference === 0) return '지난달과 같아요';
  return `지난달보다 ${Math.abs(difference)}점 ${difference > 0 ? '올랐어요' : '내렸어요'}`;
}

export function EfficiencyScoreCard({ efficiencyScore, onPress }: EfficiencyScoreCardProps) {
  const { maxScore, previousMonthDifference, score, summary, unavailableMessage } = efficiencyScore;

  if (score === null) {
    return (
      <View style={[screenStyles.card, styles.scoreCard]}>
        <Text style={screenStyles.cardTitle}>금융 효율 점수</Text>
        <Text style={styles.emptyMessage}>{unavailableMessage}</Text>
      </View>
    );
  }

  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        screenStyles.card,
        styles.scoreCard,
        pressed && styles.scoreCardPressed,
      ]}>
      <Text style={screenStyles.cardTitle}>금융 효율 점수</Text>

      <View style={styles.scoreRow}>
        <Text style={styles.score}>{score}</Text>
        <Text style={styles.scoreDivider}>/</Text>
        <Text style={styles.maxScore}>{maxScore}</Text>
      </View>

      {previousMonthDifference === null ? null : (
        <Text style={styles.difference}>{differenceLabel(previousMonthDifference)}</Text>
      )}

      <Text style={styles.summary}>{summary}</Text>

      <View style={styles.moreRow}>
        <Text style={styles.moreText}>항목별 점수 보기</Text>
        <Text style={styles.moreChevron}>›</Text>
      </View>
    </Pressable>
  );
}
