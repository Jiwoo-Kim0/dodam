import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { coachApi, diagnosisApi, recommendationApi } from '@/api';
import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import type { CoachMessageResponse } from '@/types/coachApi.types';
import type {
  DiagnosisIssueApiResponse,
  RecommendationApiResponse,
} from '@/types/discoveryApi.types';
import { formatNumber } from '@/utils/currency';

import { BasicSimulationSection } from './components/BasicSimulationSection';
import {
  categorySpendingCopy,
  categorySpendingSummary,
} from './categorySpendingCopy';
import {
  discoveryDomainLabel,
  isCategorySpendingIssue,
  parseIssueRouteId,
  recommendationsForDetail,
} from './discoveryModel';
import { styles } from './DiscoveryInsightDetailScreen.styles';
import { loadAllPages } from './hooks/useDiscoveryData';

type DiscoveryInsightDetailScreenProps = { insightId?: string };

/** AI 설명의 본문. 모델이 쓰는 유일한 부분이라 빈 문자열로 올 수 있다. */
function coachMessage(response: CoachMessageResponse | null) {
  return response?.message.trim() || null;
}

export function DiscoveryInsightDetailScreen({
  insightId,
}: DiscoveryInsightDetailScreenProps) {
  const router = useRouter();
  const routeIds = parseIssueRouteId(insightId);
  const diagnosisId = routeIds?.diagnosisId;
  const issueId = routeIds?.issueId;
  const [issue, setIssue] = useState<DiagnosisIssueApiResponse | null>(null);
  const [recommendations, setRecommendations] = useState<
    RecommendationApiResponse[]
  >([]);
  const [sourceIssues, setSourceIssues] = useState<DiagnosisIssueApiResponse[]>([]);
  const [explanation, setExplanation] = useState<CoachMessageResponse | null>(
    null,
  );
  const [explanationError, setExplanationError] = useState<string | null>(null);
  const [selectedRecommendationId, setSelectedRecommendationId] = useState<
    number | null
  >(null);
  const [recommendationExplanation, setRecommendationExplanation] =
    useState<CoachMessageResponse | null>(null);
  const [recommendationError, setRecommendationError] = useState<string | null>(
    null,
  );
  const [isLoading, setIsLoading] = useState(Boolean(routeIds));
  const [error, setError] = useState<string | null>(null);
  const [relatedIssuesError, setRelatedIssuesError] = useState(false);
  const requestIdRef = useRef(0);
  const recommendationRequestIdRef = useRef(0);
  const isCategorySpending = issue !== null && isCategorySpendingIssue(issue);
  const spendingSummary = isCategorySpending
    ? categorySpendingSummary(recommendations)
    : null;
  const recommendationCards = recommendations.map((recommendation) => {
    const sourceIssue = sourceIssues.find(
      (candidate) => candidate.issueId === recommendation.sourceIssueId,
    );
    return {
      recommendation,
      copy:
        isCategorySpending && sourceIssue
          ? categorySpendingCopy(sourceIssue, recommendation)
          : { title: recommendation.title, description: recommendation.description },
    };
  });

  const load = useCallback(async () => {
    if (!diagnosisId || !issueId) return;
    const requestId = ++requestIdRef.current;
    setIsLoading(true);
    setError(null);
    setRelatedIssuesError(false);
    setIssue(null);
    setRecommendations([]);
    setSourceIssues([]);
    setExplanation(null);
    setExplanationError(null);
    setSelectedRecommendationId(null);
    setRecommendationExplanation(null);

    try {
      const [nextIssue, allRecommendations] = await Promise.all([
        diagnosisApi.getIssue(diagnosisId, issueId),
        loadAllPages((page) => recommendationApi.getRecommendations(page)),
      ]);
      if (requestId !== requestIdRef.current) return;
      let relatedIssues = [nextIssue];
      if (isCategorySpendingIssue(nextIssue)) {
        try {
          relatedIssues = await loadAllPages((page) =>
            diagnosisApi.getIssues(diagnosisId, page),
          );
        } catch {
          if (requestId === requestIdRef.current) setRelatedIssuesError(true);
        }
      }
      if (requestId !== requestIdRef.current) return;
      setIssue(nextIssue);
      setSourceIssues(relatedIssues);
      setRecommendations(
        recommendationsForDetail(allRecommendations, relatedIssues, nextIssue),
      );

      try {
        const coach = await coachApi.getInefficiencyExplanation({
          diagnosisId,
          issueId,
        });
        if (requestId === requestIdRef.current) setExplanation(coach);
      } catch {
        if (requestId === requestIdRef.current) {
          setExplanationError('AI 설명을 불러오지 못했어요. 다시 시도해 주세요.');
        }
      }
    } catch {
      if (requestId === requestIdRef.current) {
        setError('발견 상세를 불러오지 못했어요. 다시 시도해 주세요.');
      }
    } finally {
      if (requestId === requestIdRef.current) setIsLoading(false);
    }
  }, [diagnosisId, issueId]);

  useEffect(() => {
    void Promise.resolve().then(load);

    return () => {
      requestIdRef.current += 1;
      recommendationRequestIdRef.current += 1;
    };
  }, [load]);

  const showRecommendationExplanation = async (recommendationId: number) => {
    const requestId = ++recommendationRequestIdRef.current;
    setSelectedRecommendationId(recommendationId);
    setRecommendationExplanation(null);
    setRecommendationError(null);

    try {
      const coach = await coachApi.getRecommendationExplanation({
        recommendationId,
      });
      if (requestId === recommendationRequestIdRef.current) {
        setRecommendationExplanation(coach);
      }
    } catch {
      if (requestId === recommendationRequestIdRef.current) {
        setRecommendationError('추천 설명을 불러오지 못했어요. 다시 눌러 주세요.');
      }
    }
  };

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <AppHeader showBackButton title="발견한 점" />

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={() => {
          void load();
        }}
        refreshing={isLoading}>
        {issue ? (
          <>
            <View style={styles.titleSection}>
              <View style={styles.categoryBadge}>
                <Text style={styles.categoryBadgeText}>
                  {discoveryDomainLabel(issue.domain)}
                </Text>
              </View>
              <Text style={styles.title}>
                {isCategorySpending && recommendations.length > 1
                  ? `소비가 늘어난 항목 ${recommendations.length}개`
                  : issue.title}
              </Text>
            </View>

            {isCategorySpending ? (
              <View style={styles.spendingAdviceSection}>
                <Image
                  accessibilityLabel="예상 절약 효과를 안내하는 도리"
                  contentFit="contain"
                  source={require('@/assets/images/discovery/discovery_detail_advice.png')}
                  style={styles.spendingAdviceImage}
                />
                <View style={styles.spendingSpeechBubble}>
                  <View pointerEvents="none" style={styles.spendingSpeechTail} />
                  <Text style={styles.spendingAdviceText}>
                    {spendingSummary ? (
                      <>
                        {spendingSummary.isComplete
                          ? '전부 줄이면 1년간 약 '
                          : '절약액이 계산된 항목을 줄이면 1년간 약 '}
                        <Text style={styles.spendingAdviceAmount}>
                          {spendingSummary.amount}원
                        </Text>
                        {' 아낄 수 있어요.'}
                      </>
                    ) : (
                      '예상 절약액을 아직 계산할 수 없어요.'
                    )}
                  </Text>
                </View>
              </View>
            ) : (
              <View style={styles.adviceSection}>
                <View style={styles.adviceHeader}>
                  <Image
                    accessibilityLabel="발견 근거를 설명하는 도리"
                    contentFit="contain"
                    source={require('@/assets/images/discovery/discovery_detail_advice.png')}
                    style={styles.adviceImage}
                  />
                  <View style={styles.adviceHeaderCopy}>
                    <Text style={styles.adviceEyebrow}>도리가 발견했어요</Text>
                    <Text style={styles.adviceHeading}>이런 변화가 보여요</Text>
                  </View>
                </View>
                <Text style={styles.adviceText}>{issue.description}</Text>
              </View>
            )}

            <View style={styles.recommendationSection}>
              <Text style={styles.sectionTitle}>
                {isCategorySpending
                  ? '소비를 이렇게 줄여볼 수 있어요'
                  : '이렇게 바꿔볼 수 있어요'}
              </Text>
              {isCategorySpending ? (
                <Text style={styles.sectionDescription}>
                  같은 진단에서 늘어난 소비 항목을 최대 3개 보여드려요.
                </Text>
              ) : null}
              {relatedIssuesError ? (
                <Text style={styles.sectionDescription}>
                  다른 소비 항목을 불러오지 못했어요. 아래로 당겨 다시 시도해 주세요.
                </Text>
              ) : null}

              <View style={styles.recommendationList}>
                {recommendations.length === 0 ? (
                  <Text style={styles.sectionDescription}>
                    연결된 추천이 아직 없어요.
                  </Text>
                ) : null}

                {recommendationCards.map(({ recommendation, copy }, index) => (
                  <View key={recommendation.recommendationId}>
                    <Pressable
                      accessibilityRole="button"
                      onPress={() => {
                        void showRecommendationExplanation(
                          recommendation.recommendationId,
                        );
                      }}
                      style={[
                        styles.recommendationCard,
                        index === 0 && styles.recommendedCard,
                      ]}>
                      <View style={styles.recommendationHeader}>
                        <Text style={styles.recommendationTitle}>
                          {copy.title}
                        </Text>
                        {index === 0 &&
                        (!isCategorySpending || recommendations.length > 1) ? (
                          <View style={styles.recommendedBadge}>
                            <Text style={styles.recommendedBadgeText}>
                              {isCategorySpending ? '가장 추천' : '우선 추천'}
                            </Text>
                          </View>
                        ) : null}
                      </View>
                      <Text style={styles.recommendationDetail}>
                        {copy.description}
                      </Text>
                      {recommendation.effect.annualCostSaving !== null ? (
                        <View style={styles.savingRow}>
                          <Text style={styles.savingLabel}>예상 절약</Text>
                          <Text style={styles.savingValue}>
                            연 {formatNumber(recommendation.effect.annualCostSaving)}원
                          </Text>
                        </View>
                      ) : null}
                    </Pressable>

                    {selectedRecommendationId ===
                    recommendation.recommendationId ? (
                      <View style={styles.recommendationExplanation}>
                        {recommendationExplanation ? (
                          <Text style={styles.adviceText}>
                            {coachMessage(recommendationExplanation) ??
                              '이 추천을 왜 권하는지 설명을 준비하지 못했어요.'}
                          </Text>
                        ) : (
                          <Text style={styles.recommendationDetail}>
                            {recommendationError ??
                              '추천 설명을 확인하고 있어요.'}
                          </Text>
                        )}
                      </View>
                    ) : null}
                  </View>
                ))}
              </View>
            </View>

            <View style={styles.coachSection}>
              <Text style={styles.coachLabel}>도담의 조언</Text>
              <Text style={styles.adviceText}>
                {coachMessage(explanation) ??
                  explanationError ??
                  '조언을 준비하고 있어요.'}
              </Text>
              {explanation ? (
                <View style={styles.disclaimerSection}>
                  <Text style={styles.disclaimer}>{explanation.disclaimer}</Text>
                </View>
              ) : null}
              {explanationError ? (
                <Pressable
                  accessibilityRole="button"
                  onPress={() => {
                    void load();
                  }}>
                  <Text style={styles.emptyAction}>다시 시도하기</Text>
                </Pressable>
              ) : null}
            </View>

            {recommendations.length > 0 ? (
              <BasicSimulationSection
                key={recommendations[0].recommendationId}
                preset={recommendations[0].simulation ?? null}
              />
            ) : null}
          </>
        ) : (
          <View style={styles.emptyState}>
            <Text style={styles.emptyTitle}>
              {isLoading
                ? '발견 상세를 불러오고 있어요'
                : (error ?? '상세 정보를 찾을 수 없어요')}
            </Text>
            {error && routeIds ? (
              <Text
                onPress={() => {
                  void load();
                }}
                style={styles.emptyAction}>
                다시 시도하기
              </Text>
            ) : (
              <Text onPress={() => router.back()} style={styles.emptyAction}>
                이전 화면으로 돌아가기
              </Text>
            )}
          </View>
        )}
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
