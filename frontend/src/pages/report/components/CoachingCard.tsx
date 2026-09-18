import { Image } from 'expo-image';
import type { ImageSourcePropType } from 'react-native';
import { Text, View } from 'react-native';

import { styles as screenStyles } from '../ReportScreen.styles';
import { styles } from './CoachingCard.styles';

type CoachingCardProps = {
  message: string;
  characterImage: ImageSourcePropType;
  /** 서버가 준 안내 문구다. 없으면 면책 문구를 보여 주지 않는다. */
  disclaimer: string | null;
};

export function CoachingCard({ characterImage, disclaimer, message }: CoachingCardProps) {
  return (
    <View style={[screenStyles.card, styles.coachingCard]}>
      <Text style={screenStyles.cardTitle}>도담의 한마디</Text>

      <View style={styles.coachingRow}>
        <Image contentFit="contain" source={characterImage} style={styles.character} />
        <Text style={styles.message}>{message}</Text>
      </View>

      {disclaimer === null ? null : <Text style={styles.disclaimer}>{disclaimer}</Text>}
    </View>
  );
}
