import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useRef, useState } from 'react';
import {
  Animated,
  type LayoutChangeEvent,
  Pressable,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';

import type { BouncyScrollViewHandle } from '@/components/bouncyScrollView/BouncyScrollView';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';

import { styles } from './DiscoveryLanding.styles';

type DiscoveryLandingProps = {
  onBack: () => void;
  onComplete: () => void;
};

const diagnosisItems = [
  ['정기구독을 모아봤어요', '구독을 확인하고\n사용하지 않는 구독료를 줄여봐요'],
  ['이 대출, 더 낮은 금리로\n갈아탈 수 있어요', '연 126,000원을 아낄 수 있어요'],
  ['지금 쓰는 카드, 혜택을\n잘 활용하고 있나요?', '연 60,000원을 더 받을 수 있어요'],
] as const;

const priorityItems = [
  ['고금리 대출', '이자를 줄일 수 있어요', '연 126,000원'],
  ['사용하지 않는 구독', '불필요한 지출이에요', '연 84,000원'],
  ['카드 혜택 최적화', '지금보다 더 받을 수 있어요', '연 60,000원'],
] as const;

const comparisonItems = [
  ['연간 절약 금액', '62,000원'],
  ['목표 달성일', '9일 더 빨라져요'],
  ['더 모을 수 있는 금액', '240,000원'],
] as const;

export function DiscoveryLanding({ onBack, onComplete }: DiscoveryLandingProps) {
  const { height } = useWindowDimensions();
  const [viewportHeight, setViewportHeight] = useState(height - 64);
  const scrollRef = useRef<BouncyScrollViewHandle>(null);

  const handleLayout = (event: LayoutChangeEvent) => {
    setViewportHeight(event.nativeEvent.layout.height);
  };

  return (
    <SafeAreaLayout style={styles.safeArea}>
      <View onLayout={handleLayout} style={styles.scrollArea}>
        <RefreshableBouncyScrollView
          contentContainerStyle={styles.scrollContent}
          pageSize={viewportHeight}
          pagingEnabled
          ref={scrollRef}>
          <LinearGradient
            colors={[colors.white, colors.primary.lightGreen]}
            locations={[0, 1]}
            style={[styles.section, styles.heroSection, { height: viewportHeight }]}>
            <View style={styles.topBar}>
              <Pressable accessibilityLabel="뒤로 가기" hitSlop={12} onPress={onBack}>
                <Text style={styles.backIcon}>‹</Text>
              </Pressable>
              <Text pointerEvents="none" style={styles.topBarTitle}>
                발견
              </Text>
              <StageSkip onPress={onComplete} />
            </View>

            <View style={styles.heroCopy}>
              <Text style={styles.heroTitle}>
                내 돈, <Text style={styles.greenText}>어디서</Text>{'\n'}새고 있을까?
              </Text>
              <Text style={styles.description}>도담이 내 금융생활을 살펴보고{`\n`}지금 바꾸면 좋은 부분을 찾아드려요.</Text>
            </View>

            <View style={styles.bubbleGroup}>
              <View style={[styles.speechBubble, styles.bubbleLeft]}>
                <Text style={styles.bubbleText}>이 대출, 이자를 줄일 수 있어요</Text>
              </View>
              <View style={[styles.speechBubble, styles.bubbleRight]}>
                <Text style={styles.bubbleText}>구독료가 계속 나가고 있어요</Text>
              </View>
              <View style={[styles.speechBubble, styles.bubbleLeft]}>
                <Text style={styles.bubbleText}>첫 신용대출 금리가 높아요</Text>
              </View>
            </View>

            <Image
              accessibilityLabel="돋보기로 금융 정보를 살펴보는 도리와 토리"
              contentFit="contain"
              source={require('@/assets/images/discovery/discovery_hero.png')}
              style={styles.heroImage}
            />
            <FloatingScrollHint
              onPress={() => scrollRef.current?.scrollToOffset(viewportHeight)}
            />
          </LinearGradient>

          <View style={[styles.section, { height: viewportHeight }]}>
            <StageSkip onPress={onComplete} />
            <StageHeader
              description={'계좌, 소비, 카드, 대출, 내 자산까지\n내 금융생활을 한눈에 살펴봐요.'}
              stage="1단계"
              title={<>흩어진 금융 정보를{`\n`}한곳에 모아요</>}
            />
            <Image
              accessibilityLabel="한 휴대폰에 모인 금융 정보"
              contentFit="contain"
              source={require('@/assets/images/discovery/discovery_finance_hub.png')}
              style={styles.financeImage}
            />
            <View style={styles.noticeBanner}>
              <View style={styles.checkCircle}><Text style={styles.checkMark}>✓</Text></View>
              <Text style={styles.noticeText}>개인정보는 안전하게 수집하고 관리해요</Text>
            </View>
          </View>

          <View style={[styles.section, styles.compactSection, { height: viewportHeight }]}>
            <StageSkip onPress={onComplete} />
            <StageHeader
              description={'불필요한 지출부터 아쉬운 금리까지,\n도담이 내 금융생활을 꼼꼼하게 살펴봐요.'}
              stage="2단계"
              title={<>지금 바꾸면{`\n`}좋은 부분을 <Text style={styles.brownText}>찾아요</Text></>}
            />
            <View style={styles.stageVisualGroup}>
              <Image
                accessibilityLabel="노트북으로 금융 정보를 분석하는 토리"
                contentFit="contain"
                source={require('@/assets/images/discovery/discovery_diagnosis.png')}
                style={styles.diagnosisImage}
              />
              <View style={styles.cardList}>
                {diagnosisItems.map(([title, description]) => (
                  <View key={title} style={styles.insightCard}>
                    <View style={styles.insightCopy}>
                      <Text style={styles.insightTitle}>{title}</Text>
                      <Text style={styles.insightDescription}>{description}</Text>
                    </View>
                    <Text style={styles.chevron}>›</Text>
                  </View>
                ))}
              </View>
            </View>
          </View>

          <View style={[styles.section, styles.compactSection, { height: viewportHeight }]}>
            <StageSkip onPress={onComplete} />
            <StageHeader
              description={'발견한 내용을 비교해\n효과가 큰 것부터 한눈에 보여드려요.'}
              stage="3단계"
              title={<>중요한 순서대로{`\n`}정리해요</>}
            />
            <View style={styles.stageVisualGroup}>
              <View style={styles.rankingList}>
                {priorityItems.map(([title, description, amount], index) => (
                  <View key={title} style={styles.rankingRow}>
                    <View style={[
                      styles.rankCircle,
                      index === 0 && styles.rankFirst,
                      index === 1 && styles.rankSecond,
                    ]}>
                      <Text style={styles.rankText}>{index + 1}</Text>
                    </View>
                    <View style={styles.rankingCopy}>
                      <Text style={styles.rankingTitle}>{title}</Text>
                      <Text style={styles.rankingDescription}>{description}</Text>
                    </View>
                    <View style={styles.amountWrap}>
                      <Text style={styles.amountLabel}>예상 절약액</Text>
                      <Text style={styles.amountText}>{amount}</Text>
                    </View>
                    <Text style={styles.chevron}>›</Text>
                  </View>
                ))}
              </View>
              <Image
                accessibilityLabel="금융 정보를 함께 확인하는 도리와 토리"
                contentFit="contain"
                source={require('@/assets/images/discovery/discovery_priority.png')}
                style={styles.priorityImage}
              />
            </View>
          </View>

          <View style={[styles.section, styles.compactSection, styles.lastSection, { height: viewportHeight }]}>
            <StageSkip onPress={onComplete} />
            <StageHeader
              description={'대안을 비교하고,\n나에게 맞는 대안과 변화까지 보여드려요.'}
              stage="4단계"
              title={<>더 나은 선택을{`\n`}비교해드려요</>}
            />
            <View style={styles.comparisonShowcase}>
              <View style={styles.comparisonIntro}>
                <Image
                  accessibilityLabel="돋보기로 금융 상품을 비교하는 토리"
                  contentFit="contain"
                  source={require('@/assets/images/discovery/discovery_comparison.png')}
                  style={styles.comparisonImage}
                />
                <View style={styles.changeBubble}>
                  <Text style={styles.changeBubbleText}>작은 변화가{`\n`}큰 차이를 만들어요!</Text>
                </View>
              </View>
              <View style={styles.comparisonCards}>
                <ComparisonCard amount="320,000원" label="지금 그대로라면" />
                <ComparisonCard amount="258,000원" highlighted label="이렇게 바꾸면" />
              </View>
            </View>
            <View style={styles.resultList}>
              {comparisonItems.map(([label, value]) => (
                <View key={label} style={styles.resultRow}>
                  <View style={styles.resultCopy}>
                    <Text style={styles.resultLabel}>{label}</Text>
                    <Text style={styles.resultValue}>{value}</Text>
                  </View>
                  <Text style={styles.chevron}>›</Text>
                </View>
              ))}
            </View>
            <Pressable accessibilityRole="button" onPress={onComplete} style={styles.startButton}>
              <Text style={styles.startButtonText}>발견 시작하기</Text>
            </Pressable>
          </View>
        </RefreshableBouncyScrollView>
      </View>
    </SafeAreaLayout>
  );
}

type StageHeaderProps = {
  stage: string;
  title: React.ReactNode;
  description: string;
};

function StageHeader({ stage, title, description }: StageHeaderProps) {
  return (
    <View style={styles.stageHeader}>
      <Text style={styles.stageLabel}>{stage}</Text>
      <Text style={styles.stageTitle}>{title}</Text>
      <Text style={styles.stageDescription}>{description}</Text>
    </View>
  );
}

function StageSkip({ onPress }: { onPress: () => void }) {
  return (
    <Pressable hitSlop={12} onPress={onPress} style={styles.stageSkip}>
      <Text style={styles.stageSkipText}>건너뛰기</Text>
    </Pressable>
  );
}

type ComparisonCardProps = {
  label: string;
  amount: string;
  highlighted?: boolean;
};

function ComparisonCard({ label, amount, highlighted = false }: ComparisonCardProps) {
  return (
    <View style={[styles.comparisonCard, highlighted && styles.highlightedComparisonCard]}>
      <Text style={styles.comparisonCardTitle}>{label}</Text>
      <View style={styles.barChart}>
        <View style={[styles.chartBar, styles.shortBar, highlighted && styles.highlightedBar]} />
        <View style={[styles.chartBar, styles.mediumBar, highlighted && styles.highlightedBar]} />
        <View style={[styles.chartBar, styles.tallBar, highlighted && styles.highlightedBar]} />
      </View>
      <Text style={styles.comparisonCardLabel}>예상 이자(연)</Text>
      <Text style={styles.comparisonCardAmount}>{amount}</Text>
    </View>
  );
}

function FloatingScrollHint({ onPress }: { onPress: () => void }) {
  const [translateY] = useState(() => new Animated.Value(0));

  useEffect(() => {
    const animation = Animated.loop(
      Animated.sequence([
        Animated.timing(translateY, {
          duration: 700,
          toValue: 7,
          useNativeDriver: true,
        }),
        Animated.timing(translateY, {
          duration: 700,
          toValue: 0,
          useNativeDriver: true,
        }),
      ]),
    );

    animation.start();
    return () => animation.stop();
  }, [translateY]);

  return (
    <Pressable
      accessibilityHint="1단계로 이동합니다"
      accessibilityLabel="아래로 스크롤하세요"
      accessibilityRole="button"
      hitSlop={12}
      onPress={onPress}
      style={styles.scrollHint}>
      <Text style={styles.scrollHintText}>아래로 스크롤하세요</Text>
      <Animated.Text style={[styles.scrollArrow, { transform: [{ translateY }] }]}>⌄</Animated.Text>
    </Pressable>
  );
}
