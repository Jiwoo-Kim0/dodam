import { Text, View } from 'react-native';

import type { ReportEfficiencyScore } from '@/types/report.types';

import { styles as screenStyles } from '../ReportScreen.styles';
import { EfficiencyScoreDonut } from './EfficiencyScoreDonut';
import { styles } from './EfficiencyScoreCard.styles';

type EfficiencyScoreCardProps = {
  efficiencyScore: ReportEfficiencyScore;
};

export function EfficiencyScoreCard({ efficiencyScore }: EfficiencyScoreCardProps) {
  const {
    composition,
    maxScore,
    score,
    summary,
  } = efficiencyScore;

  return (
    <View style={[screenStyles.card, styles.scoreCard]}>
      <View style={styles.scoreContent}>
        <Text style={screenStyles.cardTitle}>참고 금융 효율 점수</Text>

        <View style={styles.scoreRow}>
          <Text style={styles.score}>{score}</Text>
          <Text style={styles.scoreDivider}>/</Text>
          <Text style={styles.maxScore}>{maxScore}</Text>
        </View>

        <Text style={styles.summary}>{summary}</Text>
      </View>

      <EfficiencyScoreDonut composition={composition} score={score} />
    </View>
  );
}
