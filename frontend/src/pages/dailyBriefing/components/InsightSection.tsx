import { Image } from 'expo-image';
import { Fragment } from 'react';
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
      ) : <View style={styles.insightList}>
        {insights.map((insight, index) => {
          const isIncrease = insight.type === 'increase';

          return (
            <Fragment key={insight.id}>
              {index > 0 ? <View style={styles.insightDivider} /> : null}

              <View style={styles.insightRow}>
                {/* 카테고리 아이콘이 원색이라 회색 원 안에 넣어 채도를 눌러 준다. */}
                <View style={styles.insightIconFrame}>
                  <Image contentFit="contain" source={insight.icon} style={styles.insightIcon} />
                </View>

                <View style={styles.insightTextContent}>
                  <View style={styles.insightHeadingRow}>
                    <Text style={styles.insightTitle}>{insight.title}</Text>
                    <View
                      style={[
                        styles.insightTag,
                        isIncrease ? styles.insightTagIncrease : styles.insightTagDecrease,
                      ]}>
                      <Text
                        style={[
                          styles.insightTagText,
                          isIncrease
                            ? styles.insightTagTextIncrease
                            : styles.insightTagTextDecrease,
                        ]}>
                        {insight.label}
                      </Text>
                    </View>
                  </View>

                  <Text style={styles.insightDescription}>{insight.description}</Text>
                </View>
              </View>
            </Fragment>
          );
        })}
      </View>}
    </View>
  );
}
