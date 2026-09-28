import { Image } from 'expo-image';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { colors } from '@/styles';
import type { HomeBriefing } from '../hooks/useHomeSummary';
import { formatCurrency } from '@/utils/currency';

import { styles } from './BriefingCard.styles';
import { CardDetailButton } from './CardDetailButton';

interface BriefingCardProps {
  briefing: HomeBriefing | null;
  hasError: boolean;
  isLoading: boolean;
  onDetailPress: () => void;
}

export function BriefingCard({
  briefing,
  hasError,
  isLoading,
  onDetailPress,
}: BriefingCardProps) {
  return (
    <Pressable
      accessibilityLabel="도담 브리핑 자세히 보기"
      accessibilityRole="button"
      onPress={onDetailPress}
      style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}
    >
      <Image
        contentFit="contain"
        source={require('@/assets/characters/tori/tori_daily_briefing.png')}
        style={styles.characterImage}
      />

      <Text style={styles.title}>오늘의 도담 브리핑</Text>
      <CardDetailButton
        tintColor={colors.white}
      />

      <View style={styles.body}>
        {isLoading ? (
          <ActivityIndicator color={colors.white} style={styles.loadingIndicator} />
        ) : briefing ? (
          <>
            <View style={styles.spendingSummary}>
              <Text style={styles.label}>어제 하루 쓴 돈</Text>
              <Text style={styles.amount}>{formatCurrency(briefing.yesterdaySpentAmount)}</Text>
            </View>

            <View style={styles.messageBubble}>
              <Image
                contentFit="contain"
                source={require('@/assets/icons/common/calendar.svg')}
                style={styles.calendarIcon}
                tintColor={colors.primary.brown}
              />
              {briefing.nextOutflow ? (
                <View style={styles.outflowContent}>
                  <Text numberOfLines={1} style={styles.outflowLabel}>
                    {briefing.nextOutflow.daysLeft === 0 ? '오늘' : `D-${briefing.nextOutflow.daysLeft}`}
                    {' · '}
                    {briefing.nextOutflow.name}
                  </Text>
                  <Text adjustsFontSizeToFit minimumFontScale={0.8} numberOfLines={1} style={styles.outflowAmount}>
                    {formatCurrency(briefing.nextOutflow.amount)}
                  </Text>
                </View>
              ) : (
                <Text adjustsFontSizeToFit minimumFontScale={0.65} numberOfLines={1} style={styles.messageText}>
                  {briefing.message}
                </Text>
              )}
            </View>
          </>
        ) : (
          <Text style={styles.unavailableText}>
            {hasError ? '브리핑을 불러오지 못했어요' : '브리핑할 내역이 없어요'}
          </Text>
        )}
      </View>
    </Pressable>
  );
}
