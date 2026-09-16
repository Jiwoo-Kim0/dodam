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

function splitCoachingMessage(message: string) {
  const sentenceEnd = message.indexOf('. ');

  if (sentenceEnd === -1) {
    return { detail: null, headline: message };
  }

  return {
    detail: message.slice(sentenceEnd + 2),
    headline: message.slice(0, sentenceEnd + 1),
  };
}

export function FinancePatternCard({
  characterImage,
  coachingMessage,
  disclaimer,
  patterns,
}: FinancePatternCardProps) {
  const coaching = splitCoachingMessage(coachingMessage);

  return (
    <View style={[screenStyles.card, styles.patternCard]}>
      <Text style={screenStyles.cardTitle}>이번 달 금융 패턴</Text>
      <Text style={screenStyles.cardDescription}>
        소비·저축·현금흐름에서 찾은 핵심 3가지예요
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
        <View style={styles.coachingContent}>
          <Text style={styles.coachingLabel}>도담의 한마디</Text>
          <Text style={styles.coachingMessage}>{coaching.headline}</Text>
          {coaching.detail ? (
            <Text style={styles.coachingDetail}>{coaching.detail}</Text>
          ) : null}
          {disclaimer ? (
            <Text numberOfLines={1} style={styles.disclaimer}>
              회원님의 금융 데이터로 계산한 참고 정보이며, 금융 자문은 아니에요.
            </Text>
          ) : null}
        </View>
      </View>
    </View>
  );
}
