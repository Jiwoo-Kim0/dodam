import { Image } from 'expo-image';
import { Text, View } from 'react-native';

import type { NotificationItem } from '@/mocks/notifications';

import { styles } from './NotificationCard.styles';

type NotificationCardProps = {
  notification: NotificationItem;
};

export function NotificationCard({ notification }: NotificationCardProps) {
  return (
    <View style={styles.card}>
      <Image contentFit="contain" source={notification.icon} style={styles.icon} />

      <View style={styles.content}>
        <Text numberOfLines={1} style={styles.title}>
          {notification.title}
        </Text>
        <Text numberOfLines={1} style={styles.description}>
          {notification.description}
        </Text>
      </View>

      <Text style={styles.timeLabel}>{notification.timeLabel}</Text>
    </View>
  );
}
