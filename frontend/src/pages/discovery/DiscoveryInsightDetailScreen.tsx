import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { coachApi, diagnosisApi, recommendationApi } from '@/api';
import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { ScreenState } from '@/components/screenState/ScreenState';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';
import type { CoachMessageResponse } from '@/types/coachApi.types';
import type {
  DiagnosisGoalImpactApiResponse,
  DiagnosisIssueApiResponse,
  RecommendationApiResponse,
} from '@/types/discoveryApi.types';
import { formatNumber } from '@/utils/currency';

import {
  categorySpendingDiagnosisComparison,
  categorySpendingSummary,
  recentPeriodLabel,
} from './categorySpendingCopy';
import { CategoryMonthlySpendingSection } from './components/CategoryMonthlySpendingSection';
import { ClusterComparisonCard } from './components/ClusterComparisonCard';
import { LoanRateComparisonCard } from './components/LoanRateComparisonCard';
import { LoanRefinanceSection } from './components/LoanRefinanceSection';
import { SpendingDiagnosisComparisonCard } from './components/SpendingDiagnosisComparisonCard';
import {
  discoveryDomainLabel,
  hasClusterEvidence,
  isCategorySpendingIssue,
  parseIssueRouteId,
  recommendationsForIssue,
} from './discoveryModel';
import { styles } from './DiscoveryInsightDetailScreen.styles';
import { mostDelayedGoal } from './goalDelay';
import { formatRate, isLoanHighInterestIssue, loanRateDiagnosis } from './loanRateCopy';
import { useCategoryMonthlySpending } from './hooks/useCategoryMonthlySpending';
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
  const [explanation, setExplanation] = useState<CoachMessageResponse | null>(
    null,
  );
  const [explanationError, setExplanationError] = useState<string | null>(null);
  const [isExplanationLoading, setIsExplanationLoading] = useState(false);
  const [selectedRecommendationId, setSelectedRecommendationId] = useState<
    number | null
  >(null);
  const [recommendationExplanation, setRecommendationExplanation] =
    useState<CoachMessageResponse | null>(null);
  const [recommendationError, setRecommendationError] = useState<string | null>(
    null,
  );
  const [goalImpact, setGoalImpact] =
    useState<DiagnosisGoalImpactApiResponse | null>(null);
  const [isLoading, setIsLoading] = useState(Boolean(routeIds));
  const [error, setError] = useState<string | null>(null);
  const requestIdRef = useRef(0);
  const explanationRequestIdRef = useRef(0);
  const recommendationRequestIdRef = useRef(0);
  const recommendationExplanationCacheRef = useRef(
    new Map<number, CoachMessageResponse>(),
  );
  const loadingRouteKeyRef = useRef<string | null>(null);
  const loadingRecommendationIdRef = useRef<number | null>(null);
  const isCategorySpending = issue !== null && isCategorySpendingIssue(issue);
  const monthlySpending = useCategoryMonthlySpending(
    isCategorySpending ? issue : null,
  );
  const spendingSummary = isCategorySpending
    ? categorySpendingSummary(recommendations)
    : null;
  const spendingComparison =
    isCategorySpending && issue
      ? categorySpendingDiagnosisComparison(issue)
      : null;
  const rawCategoryCode = issue?.currentValue?.categoryCode;
  const spendingCategoryCode =
    typeof rawCategoryCode === 'string' && rawCategoryCode.trim()
      ? rawCategoryCode.trim()
      : null;
  const spendingRecommendation = recommendations.find(
    (recommendation) => recommendation.sourceIssueId === issue?.issueId,
  );
  const spendingAnnualSaving = spendingRecommendation?.effect.annualCostSaving;
  const loanDiagnosis =
    issue !== null && isLoanHighInterestIssue(issue) ? loanRateDiagnosis(issue) : null;
  const clusterContext = issue !== null && hasClusterEvidence(issue) ? issue.clusterContext : null;
  const goalDelay = isCategorySpending ? mostDelayedGoal(goalImpact) : null;
  // 목표 지연을 우선 보여주고, 목표가 없거나 스냅샷에 예상 달성일이 없으면 연간 금액으로 대신한다.
  const spendingImpact = !isCategorySpending
    ? null
    : goalDelay
      ? ({ kind: 'goalDelay', goalName: goalDelay.name, days: goalDelay.days } as const)
      : spendingAnnualSaving !== null &&
          spendingAnnualSaving !== undefined &&
          spendingAnnualSaving > 0
        ? ({ kind: 'annualCost', annualAmount: spendingAnnualSaving } as const)
        : null;
  const handleBackPress = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace(routes.discoveryInsights);
  };

  const loadExplanation = useCallback(async () => {
    if (!diagnosisId || !issueId) return;

    const requestId = ++explanationRequestIdRef.current;
    setIsExplanationLoading(true);
    setExplanationError(null);

    try {
      const coach = await coachApi.getInefficiencyExplanation({
        diagnosisId,
        issueId,
      });
      if (requestId === explanationRequestIdRef.current) setExplanation(coach);
    } catch {
      if (requestId === explanationRequestIdRef.current) {
        setExplanation(null);
        setExplanationError('AI 설명을 불러오지 못했어요. 다시 시도해 주세요.');
      }
    } finally {
      if (requestId === explanationRequestIdRef.current) {
        setIsExplanationLoading(false);
      }
    }
  }, [diagnosisId, issueId]);

  const load = useCallback(async () => {
    if (!diagnosisId || !issueId) return;
    const routeKey = `${diagnosisId}:${issueId}`;
    if (loadingRouteKeyRef.current === routeKey) return;

    loadingRouteKeyRef.current = routeKey;
    const requestId = ++requestIdRef.current;
    explanationRequestIdRef.current += 1;
    recommendationRequestIdRef.current += 1;
    loadingRecommendationIdRef.current = null;
    setIsLoading(true);
    setError(null);
    setIssue(null);
    setRecommendations([]);
    setGoalImpact(null);
    setExplanation(null);
    setIsExplanationLoading(false);
    setExplanationError(null);
    setSelectedRecommendationId(null);
    setRecommendationExplanation(null);
    setRecommendationError(null);
    recommendationExplanationCacheRef.current.clear();

    try {
      const [nextIssue, allRecommendations, impact] = await Promise.all([
        diagnosisApi.getIssue(diagnosisId, issueId),
        loadAllPages((page) => recommendationApi.getRecommendations(page)),
        // 목표 지연 배너는 부가 정보다. 실패해도 나머지 상세는 그대로 보여준다.
        diagnosisApi.getGoalImpact(diagnosisId).catch(() => null),
      ]);
      if (requestId !== requestIdRef.current) return;
      setIssue(nextIssue);
      setRecommendations(recommendationsForIssue(allRecommendations, nextIssue));
      setGoalImpact(impact);
      void loadExplanation();
    } catch {
      if (requestId === requestIdRef.current) {
        setError('발견 상세를 불러오지 못했어요. 다시 시도해 주세요.');
      }
    } finally {
      if (loadingRouteKeyRef.current === routeKey) {
        loadingRouteKeyRef.current = null;
      }
      if (requestId === requestIdRef.current) setIsLoading(false);
    }
  }, [diagnosisId, issueId, loadExplanation]);

  useEffect(() => {
    void Promise.resolve().then(load);

    return () => {
      requestIdRef.current += 1;
      explanationRequestIdRef.current += 1;
      recommendationRequestIdRef.current += 1;
      loadingRouteKeyRef.current = null;
      loadingRecommendationIdRef.current = null;
    };
  }, [load]);

  const showRecommendationExplanation = async (
    recommendationId: number,
    retry = false,
  ) => {
    if (loadingRecommendationIdRef.current === recommendationId) return;
    if (!retry && selectedRecommendationId === recommendationId) {
      setSelectedRecommendationId(null);
      return;
    }

    const requestId = ++recommendationRequestIdRef.current;
    const cached = recommendationExplanationCacheRef.current.get(recommendationId);
    loadingRecommendationIdRef.current = cached ? null : recommendationId;
    setSelectedRecommendationId(recommendationId);
    setRecommendationExplanation(cached ?? null);
    setRecommendationError(null);
    if (cached) return;

    try {
      const coach = await coachApi.getRecommendationExplanation({
        recommendationId,
      });
      if (requestId === recommendationRequestIdRef.current) {
        recommendationExplanationCacheRef.current.set(recommendationId, coach);
        setRecommendationExplanation(coach);
      }
    } catch {
      if (requestId === recommendationRequestIdRef.current) {
        setRecommendationError('추천 설명을 불러오지 못했어요. 다시 눌러 주세요.');
      }
    } finally {
      if (requestId === recommendationRequestIdRef.current) {
        loadingRecommendationIdRef.current = null;
      }
    }
  };

  const explanationText = coachMessage(explanation);
  const coachSection = (
    <View style={styles.coachSection}>
      <Text style={styles.coachLabel}>도담의 조언</Text>
      {/*
        접기·펴기를 두지 않는다. 글자 수(90자)로 길이를 재던 방식은 실제 줄 수와 달라서,
        세 줄 안에 들어가는 조언에도 "전체 보기"가 떠 눌러도 아무것도 바뀌지 않았다.
        조언은 길어야 네댓 줄이라 처음부터 다 보여 주는 편이 낫다.
      */}
      <Text style={styles.adviceText}>
        {isExplanationLoading
          ? '조언을 불러오고 있어요.'
          : explanationText ?? explanationError ?? '표시할 조언이 없어요.'}
      </Text>
      {explanationError ? (
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ disabled: isExplanationLoading }}
          disabled={isExplanationLoading}
          onPress={() => {
            void loadExplanation();
          }}>
          <Text style={styles.emptyAction}>다시 시도하기</Text>
        </Pressable>
      ) : null}
      {spendingSummary ? (
        <View style={styles.coachSummary}>
          <Text style={styles.spendingSummaryLabel}>예상 절약 효과</Text>
          <Text style={styles.spendingSummaryText}>
            {spendingSummary.isComplete
              ? '이전 평균 수준으로 줄이면 연 약 '
              : '계산 가능한 항목만 줄이면 연 약 '}
            <Text style={styles.spendingSummaryAmount}>
              {spendingSummary.amount}원
            </Text>
            {' 절약 예상'}
          </Text>
        </View>
      ) : null}
    </View>
  );

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <AppHeader onBackPress={handleBackPress} showBackButton title="발견한 점" />

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
                  {isCategorySpending
                    ? '소비 진단'
                    : loanDiagnosis
                      ? '대출 진단'
                      : discoveryDomainLabel(issue.domain)}
                </Text>
              </View>
              {spendingComparison ? (
                <>
                  <Text style={styles.headlineTitle}>
                    {spendingComparison.categoryName.endsWith('비')
                      ? spendingComparison.categoryName
                      : `${spendingComparison.categoryName}비`}
                    가 평소보다
                  </Text>
                  <Text style={styles.headlineAmount}>
                    {formatNumber(spendingComparison.increaseAmount)}원 늘었어요
                  </Text>
                  <Text style={styles.headlineDescription}>
                    직전 {spendingComparison.baselinePeriod.months}개월 평균과{' '}
                    {recentPeriodLabel(spendingComparison.recentPeriod)} 결제 내역을
                    비교했어요.
                  </Text>
                </>
              ) : loanDiagnosis ? (
                <>
                  <Text style={styles.headlineTitle}>
                    ‘{loanDiagnosis.accountName}’ 금리가
                  </Text>
                  <Text style={styles.headlineAmount}>
                    연 {formatRate(loanDiagnosis.rateDifference)}%p 높아요
                  </Text>
                  <Text style={styles.headlineDescription}>
                    같은 조건 상품의 공시 최저금리 중앙값과 비교했어요.
                  </Text>
                </>
              ) : (
                <Text style={styles.title}>{issue.title}</Text>
              )}
            </View>

            {!isCategorySpending && !loanDiagnosis ? (
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
            ) : null}

            {spendingComparison ? (
              <SpendingDiagnosisComparisonCard comparison={spendingComparison} />
            ) : null}

            {loanDiagnosis ? (
              <LoanRateComparisonCard diagnosis={loanDiagnosis} />
            ) : null}

            {/* 규칙만으로 찾은 문제는 군집 필드가 통째로 없어 카드를 그리지 않는다. */}
            {clusterContext ? <ClusterComparisonCard cluster={clusterContext} /> : null}

            {spendingImpact ? (
              <View style={styles.impactCard}>
                <View style={styles.impactIconWrap}>
                  <Image
                    accessibilityLabel=""
                    contentFit="contain"
                    source={require('@/assets/icons/common/calendar.png')}
                    style={styles.impactIcon}
                  />
                </View>
                {spendingImpact.kind === 'goalDelay' ? (
                  <Text style={styles.impactText}>
                    지금 속도면 ‘{spendingImpact.goalName}’ 목표가{' '}
                    <Text style={styles.impactEmphasis}>
                      약 {formatNumber(spendingImpact.days)}일
                    </Text>
                    {' 늦어져요.'}
                  </Text>
                ) : (
                  <Text style={styles.impactText}>
                    이 소비가 계속되면 1년에{' '}
                    <Text style={styles.impactEmphasis}>
                      약 {formatNumber(spendingImpact.annualAmount)}원
                    </Text>
                    을 더 쓰게 돼요.
                  </Text>
                )}
              </View>
            ) : null}

            {monthlySpending.hasQuery && spendingComparison && spendingCategoryCode ? (
              <CategoryMonthlySpendingSection
                categoryCode={spendingCategoryCode}
                categoryName={spendingComparison.categoryName}
                error={monthlySpending.error}
                isLoading={monthlySpending.isLoading}
                merchants={monthlySpending.merchants}
                onRetry={monthlySpending.retry}
                periodLabel={recentPeriodLabel(spendingComparison.recentPeriod)}
              />
            ) : null}

            {loanDiagnosis?.refinance ? (
              <View style={styles.impactCard}>
                <View style={styles.impactIconWrap}>
                  <Image
                    accessibilityLabel=""
                    contentFit="contain"
                    source={require('@/assets/icons/common/money.png')}
                    style={styles.impactIcon}
                  />
                </View>
                <Text style={styles.impactText}>
                  대환하면 만기까지 이자{' '}
                  <Text style={styles.impactEmphasis}>
                    약 {formatNumber(loanDiagnosis.refinance.interestSaving)}원
                  </Text>
                  을 아낄 수 있어요.
                </Text>
              </View>
            ) : null}

            {loanDiagnosis ? (
              <LoanRefinanceSection diagnosis={loanDiagnosis} />
            ) : null}

            {isCategorySpending ? coachSection : null}

            {!isCategorySpending && !loanDiagnosis ? (
              <View style={styles.recommendationSection}>
                <Text style={styles.sectionTitle}>이렇게 바꿔볼 수 있어요</Text>

                <View style={styles.recommendationList}>
                  {recommendations.length === 0 ? (
                    <Text style={styles.sectionDescription}>
                      연결된 추천이 아직 없어요.
                    </Text>
                  ) : null}

                  {recommendations.map((recommendation, index) => (
                    <View key={recommendation.recommendationId}>
                      <Pressable
                        accessibilityRole="button"
                        accessibilityState={{
                          expanded:
                            selectedRecommendationId === recommendation.recommendationId,
                          disabled:
                            selectedRecommendationId === recommendation.recommendationId &&
                            recommendationExplanation === null &&
                            recommendationError === null,
                        }}
                        disabled={
                          selectedRecommendationId === recommendation.recommendationId &&
                          recommendationExplanation === null &&
                          recommendationError === null
                        }
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
                            {recommendation.title}
                          </Text>
                          {index === 0 ? (
                            <View style={styles.recommendedBadge}>
                              <Text style={styles.recommendedBadgeText}>
                                우선 추천
                              </Text>
                            </View>
                          ) : null}
                        </View>
                        <Text style={styles.recommendationDetail}>
                          {recommendation.description}
                        </Text>
                        {/*
                          소비 패턴 검토(REVIEW_SPENDING_PATTERN)는 줄일 금액을 사용자가 정하는
                          추천이라 서버 효과가 전부 null이다. 행을 숨기면 추천마다 높이가 달라져
                          빠진 것처럼 보이므로, 자리를 두고 산정 전이라고 알린다.
                        */}
                        <View style={styles.savingRow}>
                          <Text style={styles.savingLabel}>예상 절약액</Text>
                          <Text
                            style={[
                              styles.savingValue,
                              recommendation.effect.annualCostSaving === null &&
                                styles.savingValuePending,
                            ]}>
                            {recommendation.effect.annualCostSaving === null
                              ? '절감액 산정 전'
                              : `연 ${formatNumber(recommendation.effect.annualCostSaving)}원 예상`}
                          </Text>
                        </View>
                        <View style={styles.recommendationAction}>
                          <Text style={styles.recommendationActionText}>
                            {selectedRecommendationId === recommendation.recommendationId
                              ? '추천 이유 접기'
                              : '추천 이유 보기'}
                          </Text>
                          {/* 글자 `⌄`는 폰트마다 크기·위치가 달라 글자와 중심이 안 맞는다. */}
                          <SymbolView
                            name={
                              selectedRecommendationId === recommendation.recommendationId
                                ? {
                                    android: 'keyboard_arrow_up',
                                    ios: 'chevron.up',
                                    web: 'keyboard_arrow_up',
                                  }
                                : {
                                    android: 'keyboard_arrow_down',
                                    ios: 'chevron.down',
                                    web: 'keyboard_arrow_down',
                                  }
                            }
                            size={16}
                            tintColor={colors.textSecondary}
                          />
                        </View>
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
                            <>
                              <Text style={styles.recommendationDetail}>
                                {recommendationError ??
                                  '추천 설명을 확인하고 있어요.'}
                              </Text>
                              {recommendationError ? (
                                <Pressable
                                  accessibilityRole="button"
                                  onPress={() => {
                                    void showRecommendationExplanation(
                                      recommendation.recommendationId,
                                      true,
                                    );
                                  }}>
                                  <Text style={styles.inlineRetry}>다시 시도</Text>
                                </Pressable>
                              ) : null}
                            </>
                          )}
                        </View>
                      ) : null}
                    </View>
                  ))}
                </View>
              </View>
            ) : null}

            {!isCategorySpending ? coachSection : null}
          </>
        ) : (
          <ScreenState
            description={
              isLoading
                ? '발견 상세를 불러오고 있어요'
                : routeIds
                  ? error ?? '연결된 상세 정보가 없어요.'
                  : '발견 번호를 확인하거나 이전 화면에서 다시 선택해주세요.'
            }
            isLoading={isLoading}
            onRetry={!isLoading && error && routeIds ? () => void load() : undefined}
            title={
              isLoading
                ? undefined
                : routeIds
                  ? error
                    ? '발견 상세를 불러오지 못했어요.'
                    : '상세 정보가 없어요.'
                  : '발견 내용을 찾을 수 없어요.'
            }
          />
        )}
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
