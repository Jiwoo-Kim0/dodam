import { Fragment } from 'react';

import { Image } from 'expo-image';
import type { ImageSourcePropType } from 'react-native';
import { Text, View } from 'react-native';

import type { ReportFinancePattern } from '@/types/report.types';

import { styles as screenStyles } from '../ReportScreen.styles';
import { styles } from './FinancePatternCard.styles';

type FinancePatternCardProps = {
  patterns: readonly ReportFinancePattern[];
  coachingMessage: string;
  characterImage: ImageSourcePropType;
  disclaimer: string | null;
};

const statusLabels = {
  good: '좋은 습관',
  check: '확인 필요',
} as const;

export function FinancePatternCard({
  characterImage,
  coachingMessage,
  disclaimer,
  patterns,
}: FinancePatternCardProps) {
  return (
    <View style={[screenStyles.card, styles.patternCard]}>
      <Text style={screenStyles.cardTitle}>AI가 발견한 이번 달 금융 패턴</Text>
      <Text style={screenStyles.cardDescription}>
        나의 소비와 자산 흐름을 분석해 이런 점을 발견했어요
      </Text>

      <View style={styles.patternList}>
        {patterns.map((pattern, index) => (
          <Fragment key={pattern.id}>
            {index > 0 ? <View style={styles.patternDivider} /> : null}

            <View style={styles.patternRow}>
              <View style={styles.patternIconFrame}>
                <Image contentFit="contain" source={pattern.icon} style={styles.patternIcon} />
              </View>

              <View style={styles.patternTextContent}>
                <Text style={styles.patternTitle}>{pattern.title}</Text>
                <Text style={styles.patternDescription}>{pattern.description}</Text>
              </View>

              <View
                style={[
                  styles.statusBadge,
                  pattern.status === 'check' && styles.statusBadgeCheck,
                ]}>
                <Text
                  numberOfLines={1}
                  style={[
                    styles.statusBadgeText,
                    pattern.status === 'check' && styles.statusBadgeTextCheck,
                  ]}>
                  {statusLabels[pattern.status]}
                </Text>
              </View>
            </View>
          </Fragment>
        ))}
      </View>

      <View style={styles.coachingRow}>
        <Image
          contentFit="contain"
          source={characterImage}
          style={styles.coachingCharacter}
        />
        <Text style={styles.coachingMessage}>{coachingMessage}</Text>
      </View>
      {disclaimer ? <Text style={styles.disclaimer}>{disclaimer}</Text> : null}
    </View>
  );
}
