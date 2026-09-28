import { Image } from 'expo-image';
import { Text, View } from 'react-native';

import { styles as screenStyles } from '../DailyBriefingScreen.styles';
import { styles } from './UpcomingCard.styles';

type UpcomingCardProps = {
  description: string;
  title: string;
};

export function UpcomingCard({ description, title }: UpcomingCardProps) {
  return (
    <View style={[screenStyles.card, styles.upcomingCard]}>
      <Text style={screenStyles.sectionTitle}>오늘 체크할 것</Text>

      <View style={styles.upcomingRow}>
        <View style={styles.upcomingIconFrame}>
          <Image
            contentFit="contain"
            source={require('@/assets/icons/common/check.png')}
            style={styles.upcomingIcon}
          />
        </View>

        <View style={styles.upcomingContent}>
          <Text style={styles.upcomingTitle}>{title}</Text>
          <Text style={styles.upcomingDescription}>{description}</Text>
        </View>
      </View>
    </View>
  );
}
