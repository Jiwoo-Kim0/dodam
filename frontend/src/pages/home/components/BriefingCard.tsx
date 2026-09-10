import { Image } from 'expo-image';
import { Text, View } from 'react-native';

import { HOME_BRIEFING } from '@/mocks/home';
import { colors } from '@/styles';
import { formatCurrency } from '@/utils/currency';

import { styles } from './BriefingCard.styles';
import { CardDetailButton } from './CardDetailButton';

interface BriefingCardProps {
  onDetailPress: () => void;
}

export function BriefingCard({ onDetailPress }: BriefingCardProps) {
  return (
    <View style={styles.card}>
      <Image
        contentFit="contain"
        source={require('@/assets/characters/tori/tori_daily_briefing.png')}
        style={styles.characterImage}
      />

      <Text style={styles.title}>오늘의 도담 브리핑</Text>
      <CardDetailButton
        accessibilityLabel="도담 브리핑 자세히 보기"
        onPress={onDetailPress}
        tintColor={colors.white}
      />

      <View style={styles.body}>
        <View style={styles.spendingSummary}>
          <Text style={styles.label}>어제 하루 쓴 돈</Text>
          <Text style={styles.amount}>{formatCurrency(HOME_BRIEFING.yesterdaySpentAmount)}</Text>
        </View>

        <View style={styles.messageBubble}>
          <Image
            contentFit="contain"
            source={require('@/assets/icons/common/calendar.svg')}
            style={styles.calendarIcon}
            tintColor={colors.primary.brown}
          />
          <Text
            adjustsFontSizeToFit
            minimumFontScale={0.65}
            numberOfLines={1}
            style={styles.messageText}
          >
            {HOME_BRIEFING.message}
          </Text>
        </View>
      </View>
    </View>
  );
}
