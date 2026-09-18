import { Pressable, Text, View } from 'react-native';

import type { ReportPeerComparison } from '@/types/report.types';

import { styles as screenStyles } from '../ReportScreen.styles';
import { PeerComparisonChart } from './PeerComparisonChart';
import { styles } from './PeerComparisonCard.styles';

type PeerComparisonCardProps = {
  peerComparison: ReportPeerComparison;
  onPress: () => void;
};

export function PeerComparisonCard({ onPress, peerComparison }: PeerComparisonCardProps) {
  if (peerComparison.status === 'unavailable') {
    return (
      <View style={[screenStyles.card, styles.peerCard]}>
        <View style={styles.header}>
          <Text style={screenStyles.cardTitle}>나의 소비 유형</Text>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>K-Means 분석</Text>
          </View>
        </View>

        <Text style={styles.emptyMessage}>{peerComparison.message}</Text>
      </View>
    );
  }

  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        screenStyles.card,
        styles.peerCard,
        pressed && styles.peerCardPressed,
      ]}>
      <View style={styles.header}>
        <Text style={screenStyles.cardTitle}>나의 소비 유형</Text>
        <View style={styles.badge}>
          <Text style={styles.badgeText}>K-Means 분석</Text>
        </View>
      </View>

      {peerComparison.clusterLabel === null ? null : (
        <Text style={styles.clusterLabel}>{peerComparison.clusterLabel}</Text>
      )}

      <View style={styles.chartRow}>
        <PeerComparisonChart features={peerComparison.features} />
      </View>

      <View style={styles.legend}>
        <View style={styles.legendItem}>
          <View style={styles.legendUserMark} />
          <Text style={styles.legendText}>나</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={styles.legendPeerMark} />
          <Text style={styles.legendText}>또래 중간값</Text>
        </View>
      </View>

      {/* 축마다 좋은 방향이 달라 값을 맞춰 뒤집었으므로 읽는 법을 함께 알려 준다. */}
      <Text style={styles.chartGuide}>바깥쪽일수록 좋아요</Text>

      {/* 비교 집단·표본 수와 분석 기간은 상세 화면에서 보여 준다. */}
      <View style={styles.moreRow}>
        <Text style={styles.moreText}>항목별로 비교해 보기</Text>
        <Text style={styles.moreChevron}>›</Text>
      </View>
    </Pressable>
  );
}
