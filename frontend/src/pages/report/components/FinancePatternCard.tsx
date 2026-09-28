import { Fragment } from 'react';

import { Image } from 'expo-image';
import { Text, View } from 'react-native';

import type { ReportFinancePattern } from '@/types/report.types';

import { styles as screenStyles } from '../ReportScreen.styles';
import { styles } from './FinancePatternCard.styles';

type FinancePatternCardProps = {
  patterns: readonly ReportFinancePattern[];
};

export function FinancePatternCard({ patterns }: FinancePatternCardProps) {
  return (
    <View style={[screenStyles.card, styles.patternCard]}>
      <Text style={screenStyles.cardTitle}>이번 달 금융 패턴</Text>

      <View style={styles.patternList}>
        {patterns.map((pattern, index) => (
          <Fragment key={pattern.id}>
            {index > 0 ? <View style={styles.patternDivider} /> : null}

            <View style={styles.patternRow}>
              {/*
                상태는 배지 대신 아이콘 배경색으로 알린다. 제목 문장이 이미 좋고 나쁨을 말한다.
                자료가 없을 때는 색을 빼고 아이콘을 흐리게 해서 판정과 구분한다.
              */}
              <View
                style={[
                  styles.patternIconFrame,
                  pattern.status === 'check' && styles.patternIconFrameCheck,
                  pattern.status === 'unavailable' && styles.patternIconFrameUnavailable,
                ]}>
                <Image
                  contentFit="contain"
                  source={pattern.icon}
                  style={[
                    styles.patternIcon,
                    pattern.status === 'unavailable' && styles.patternIconUnavailable,
                  ]}
                />
              </View>

              <View style={styles.patternTextContent}>
                <Text style={styles.patternTitle}>{pattern.title}</Text>
                <Text style={styles.patternDescription}>{pattern.description}</Text>
                {/* 어느 기간을 본 판정인지. 리포트 달과 다를 수 있어 서버 값을 그대로 적는다. */}
                {pattern.evidencePeriod === null ? null : (
                  <Text style={styles.patternPeriod}>{pattern.evidencePeriod}</Text>
                )}
              </View>
            </View>
          </Fragment>
        ))}
      </View>
    </View>
  );
}
