import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { DISCOVERY_INSIGHTS } from '@/mocks/discovery';

import { BasicSimulationSection } from './components/BasicSimulationSection';
import { styles } from './DiscoveryInsightDetailScreen.styles';

type DiscoveryInsightDetailScreenProps = {
  insightId?: string;
};

export function DiscoveryInsightDetailScreen({
  insightId,
}: DiscoveryInsightDetailScreenProps) {
  const router = useRouter();
  const insight = DISCOVERY_INSIGHTS.find((item) => item.id === insightId);

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <AppHeader showBackButton title="발견한 점" />

      <RefreshableBouncyScrollView contentContainerStyle={styles.scrollContent}>
        {insight ? (
          <>
            <View style={styles.titleSection}>
              <View style={styles.categoryBadge}>
                <Text style={styles.categoryBadgeText}>{insight.category}</Text>
              </View>
              <Text style={styles.title}>{insight.detailedTitle}</Text>
              <Text style={styles.description}>{insight.description}</Text>
            </View>

            <View style={styles.adviceSection}>
              <Image
                accessibilityLabel="발견 근거를 설명하는 도리"
                contentFit="contain"
                source={require('@/assets/images/discovery/discovery_detail_advice.png')}
                style={styles.adviceImage}
              />
              <View style={styles.adviceBubble}>
                <Text style={styles.adviceText}>{insight.evidence}</Text>
              </View>
            </View>

            <View style={styles.recommendationSection}>
              <Text style={styles.sectionTitle}>이렇게 바꿔볼 수 있어요</Text>
              <Text style={styles.sectionDescription}>{insight.recommendationSummary}</Text>

              <View style={styles.recommendationList}>
                {insight.recommendations.map((recommendation) => (
                  <View
                    key={recommendation.title}
                    style={[
                      styles.recommendationCard,
                      recommendation.recommended && styles.recommendedCard,
                    ]}>
                    <View style={styles.recommendationCopy}>
                      <Text style={styles.recommendationTitle}>{recommendation.title}</Text>
                      <Text style={styles.recommendationDetail}>{recommendation.detail}</Text>
                    </View>
                    {recommendation.recommended ? (
                      <View style={styles.recommendedBadge}>
                        <Text style={styles.recommendedBadgeText}>추천</Text>
                      </View>
                    ) : null}
                  </View>
                ))}
              </View>
            </View>

            <BasicSimulationSection insightId={insight.id} key={insight.id} />
          </>
        ) : (
          <View style={styles.emptyState}>
            <Text style={styles.emptyTitle}>상세 정보를 찾을 수 없어요</Text>
            <Text onPress={() => router.back()} style={styles.emptyAction}>
              이전 화면으로 돌아가기
            </Text>
          </View>
        )}
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
