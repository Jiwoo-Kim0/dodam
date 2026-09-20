import { useMemo } from 'react';
import { Text, View } from 'react-native';

import type { DiagnosisClusterContextApiResponse } from '@/types/discoveryApi.types';

import {
  clusterRankBandLabel,
  formatClusterRange,
  formatClusterValue,
} from '../clusterComparisonModel';
import { styles } from './ClusterComparisonCard.styles';

type ClusterComparisonCardProps = {
  cluster: DiagnosisClusterContextApiResponse;
};

/** 값이 축 끝에 딱 붙지 않도록 남기는 여유다. 또래 비교 분포 막대와 같은 규칙을 쓴다. */
const DOMAIN_HEADROOM = 1.25;

export function ClusterComparisonCard({ cluster }: ClusterComparisonCardProps) {
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
          {` · 표본 ${cluster.sampleSize.toLocaleString('ko-KR')}명`}
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
          {`기준 범위 ${formatClusterRange(cluster.feature, cluster.p5, cluster.p95)}`}
        </Text>
        {rankLabel === null ? null : <Text style={styles.rankText}>{rankLabel}</Text>}
      </View>

      <Text style={styles.footnote}>
        {`${cluster.window} 기준 · ${cluster.modelVersion}`}
      </Text>
    </View>
  );
}
