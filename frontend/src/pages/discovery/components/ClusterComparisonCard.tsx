import { SymbolView } from 'expo-symbols';
import { useMemo, useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { colors } from '@/styles';
import type { DiagnosisClusterContextApiResponse } from '@/types/discoveryApi.types';

import {
  clusterRankBandLabel,
  formatClusterRange,
  formatClusterValue,
  formatClusterWindow,
} from '../clusterComparisonModel';
import { styles } from './ClusterComparisonCard.styles';

type ClusterComparisonCardProps = {
  cluster: DiagnosisClusterContextApiResponse;
};

/** 값이 축 끝에 딱 붙지 않도록 남기는 여유다. 또래 비교 분포 막대와 같은 규칙을 쓴다. */
const DOMAIN_HEADROOM = 1.25;

export function ClusterComparisonCard({ cluster }: ClusterComparisonCardProps) {
  const [isBasisOpen, setIsBasisOpen] = useState(false);

  const bar = useMemo(() => {
    // 특징마다 값의 크기가 달라(비중은 0~1, 변동성은 그 밖) 이 카드 안에서만 쓰는 축을 만든다.
    const domain = Math.max(cluster.userValue, cluster.p95, cluster.mean) * DOMAIN_HEADROOM;
    const toPosition = (value: number) =>
      domain === 0 ? 0 : Math.min(100, Math.max(0, (value / domain) * 100));

    const left = toPosition(cluster.p5);
    return {
      bandLeft: left,
      // 기준 범위가 아주 좁아도 보이도록 최소 폭을 준다.
      bandWidth: Math.max(2, toPosition(cluster.p95) - left),
      userPosition: toPosition(cluster.userValue),
    };
  }, [cluster]);

  const rankLabel = clusterRankBandLabel(cluster.rankBand, cluster.percentileInCluster);

  return (
    <View style={styles.card}>
      <Text style={styles.title}>비슷한 소비 유형과 비교</Text>

      {/*
        문구는 clusterContext 값만 쓴다. "과하다" 같은 판정은 넣지 않는다 —
        서버가 판정한 것은 이탈 여부까지이고, 해석은 문제 제목·설명이 이미 갖고 있다.
      */}
      <Text style={styles.summary}>
        {cluster.clusterLabel}
        <Text style={styles.summaryMuted}>
          {` · 같은 유형 ${cluster.sampleSize.toLocaleString('ko-KR')}명과 비교했어요`}
        </Text>
      </Text>

      <View style={styles.valueRow}>
        <Text style={styles.featureLabel}>{cluster.displayName}</Text>
        <Text style={styles.userValue}>{formatClusterValue(cluster.feature, cluster.userValue)}</Text>
      </View>

      {/* 평균 눈금은 두지 않는다. 띠가 끊긴 틈처럼 보이고, 문구에서도 평균을 쓰지 않는다. */}
      <View style={styles.track}>
        <View
          style={[styles.band, { left: `${bar.bandLeft}%`, width: `${bar.bandWidth}%` }]}
        />
        <View style={[styles.userDot, { left: `${bar.userPosition}%` }]} />
      </View>

      <View style={styles.legendRow}>
        <Text style={styles.legendText}>
          {`비슷한 유형 대부분 ${formatClusterRange(cluster.feature, cluster.p5, cluster.p95)}`}
        </Text>
        {rankLabel === null ? null : <Text style={styles.rankText}>{rankLabel}</Text>}
      </View>

      {/*
        띠가 5~95분위라는 걸 분위수라는 말 없이 알려 준다. 이 설명이 없으면 띠가 '정상 범위'처럼
        읽혀서, 밖으로 나간 것이 곧 잘못이라는 뜻으로 오해된다.
      */}
      <Text style={styles.guideText}>
        가운데 띠는 같은 유형 100명 중 90명이 있는 구간이에요
      </Text>

      <Text style={styles.footnote}>{`${formatClusterWindow(cluster.window)} 소비 기준`}</Text>

      {/* 모델 버전은 값을 대조할 때만 필요해 접어 둔다. 평소에는 기간만 보이면 된다. */}
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: isBasisOpen }}
        onPress={() => setIsBasisOpen((isOpen) => !isOpen)}
        style={({ pressed }) => [styles.basisToggle, pressed && styles.basisTogglePressed]}>
        <Text style={styles.basisToggleText}>분석 정보</Text>
        <SymbolView
          name={
            isBasisOpen
              ? { android: 'keyboard_arrow_up', ios: 'chevron.up', web: 'keyboard_arrow_up' }
              : { android: 'keyboard_arrow_down', ios: 'chevron.down', web: 'keyboard_arrow_down' }
          }
          size={14}
          tintColor={colors.textTertiary}
        />
      </Pressable>

      {isBasisOpen ? (
        <Text style={styles.basisText}>
          {`분석 모델 ${cluster.modelVersion} · 비교 항목 ${cluster.displayName}`}
        </Text>
      ) : null}
    </View>
  );
}
