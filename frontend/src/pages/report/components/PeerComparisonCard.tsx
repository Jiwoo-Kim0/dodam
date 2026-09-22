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
            <Text style={styles.badgeText}>소비 유형 분석</Text>
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
          <Text style={styles.badgeText}>소비 유형 분석</Text>
        </View>
      </View>

      {peerComparison.clusterLabel === null ? null : (
        <Text style={styles.clusterLabel}>{peerComparison.clusterLabel}</Text>
      )}

      {/*
        또래 중앙값은 리포트 달이 아니라 최근 완료 분석 구간 기준이다(BE 670 회신).
        기간을 적지 않으면 이 달 숫자로 읽히므로 그래프를 보기 전에 알려 준다.
      */}
      {peerComparison.analysisPeriodLabel === null ? null : (
        <Text style={styles.periodText}>{peerComparison.analysisPeriodLabel}</Text>
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
          <Text style={styles.legendText}>또래 중앙값</Text>
        </View>
      </View>

      {/*
        축마다 좋은 방향이 달라 값을 맞춰 뒤집었으므로 읽는 법을 함께 알려 준다.
        점선은 또래를 줄 세웠을 때 딱 가운데 사람의 자리다.
      */}
      <Text style={styles.chartGuide}>또래 안에서 내 위치예요 · 바깥쪽일수록 좋아요</Text>

      {/* 비교 집단과 표본 수는 상세 화면에서 보여 준다. */}
      <View style={styles.moreRow}>
        <Text style={styles.moreText}>항목별로 비교해 보기</Text>
        <Text style={styles.moreChevron}>›</Text>
      </View>
    </Pressable>
  );
}
