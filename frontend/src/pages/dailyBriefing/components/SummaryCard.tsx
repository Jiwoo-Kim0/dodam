import { Image } from 'expo-image';
import { Text, View } from 'react-native';

import { formatCurrency, formatNumber } from '@/utils/currency';

import { styles as screenStyles } from '../DailyBriefingScreen.styles';
import { styles } from './SummaryCard.styles';

type SummaryCardProps = {
  previousMonthDailyAverage: number;
  yesterdaySpentAmount: number;
};

export function SummaryCard({ previousMonthDailyAverage, yesterdaySpentAmount }: SummaryCardProps) {
  const averageDifference = previousMonthDailyAverage - yesterdaySpentAmount;
  const spentLessThanAverage = averageDifference >= 0;
  const progressRatio = Math.min(yesterdaySpentAmount / previousMonthDailyAverage, 1);
  const progressWidth = `${progressRatio * 100}%` as `${number}%`;

  return (
    <View style={[screenStyles.card, styles.summaryCard]}>
      <View style={styles.summaryCharacterFrame}>
        <Image
          contentFit="contain"
          source={require('@/assets/characters/dori/dori_dudung.png')}
          style={styles.summaryCharacter}
        />
        <View pointerEvents="none" style={styles.summaryCharacterCornerMask} />
      </View>

      <View style={styles.summaryContent}>
        <View style={styles.summaryHeadingRow}>
          <Text style={styles.summaryLabel}>어제 하루 쓴 돈</Text>
          <View style={styles.statusBadge}>
            <Text style={styles.statusBadgeText}>
              {spentLessThanAverage ? '평소보다 적어요' : '평소보다 많아요'}
            </Text>
          </View>
        </View>

        <Text style={styles.summaryAmount}>
          {formatNumber(yesterdaySpentAmount)}
          <Text style={styles.summaryAmountUnit}>원</Text>
        </Text>
        <Text style={styles.averageText}>
          저번달 기준 하루 평균 {formatCurrency(previousMonthDailyAverage)}
        </Text>
        <View style={styles.progressTrack}>
          <View style={[styles.progressValue, { width: progressWidth }]} />
        </View>
      </View>
    </View>
  );
}
