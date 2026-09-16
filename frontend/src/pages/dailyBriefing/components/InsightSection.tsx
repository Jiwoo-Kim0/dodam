import { Image } from 'expo-image';
import { Text, View } from 'react-native';

import type { DailyBriefingInsight } from '../dailyBriefing.types';
import { styles as screenStyles } from '../DailyBriefingScreen.styles';
import { styles } from './InsightSection.styles';

type InsightSectionProps = {
  insights: readonly DailyBriefingInsight[];
  message?: string | null;
};

export function InsightSection({ insights, message }: InsightSectionProps) {
  return (
    <View style={[screenStyles.card, styles.insightSection]}>
      <Text style={screenStyles.sectionTitle}>도담이 짚어 봤어요</Text>
      {insights.length === 0 ? (
        <Text style={screenStyles.emptyMessage}>{message ?? '비교할 소비 기록이 없어요.'}</Text>
      ) : <View style={styles.insightRow}>
        {insights.map((insight) => (
          <View key={insight.id} style={styles.insightCard}>
            <View style={styles.insightTag}>
              <Text
                style={[
                  styles.insightTagText,
                  insight.type === 'increase' && styles.insightIncreaseText,
                ]}>
                {insight.label}
              </Text>
            </View>
            <View style={styles.insightBody}>
              <View style={styles.insightIconFrame}>
                <Image contentFit="contain" source={insight.icon} style={styles.insightIcon} />
              </View>
              <View style={styles.insightTextContent}>
                <Text style={styles.insightTitle}>{insight.title}</Text>
                <Text style={styles.insightDescription}>{insight.description}</Text>
              </View>
            </View>
          </View>
        ))}
      </View>}
    </View>
  );
}
