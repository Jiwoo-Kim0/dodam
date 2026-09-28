import { Image } from 'expo-image';
import { useEffect, useRef, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import ReanimatedSwipeable, {
  type SwipeableMethods,
} from 'react-native-gesture-handler/ReanimatedSwipeable';

import type { NotificationApiResponse } from '@/types/notificationApi.types';
import {
  formatNotificationTime,
  getNotificationCategoryLabel,
  getNotificationIcon,
} from '@/utils/notification';

import { styles } from './NotificationCard.styles';

type NotificationCardProps = {
  isOpen: boolean;
  notification: NotificationApiResponse;
  onClose: () => void;
  onDelete: () => void;
  onOpen: () => void;
  onPress: () => void;
};

export function NotificationCard({
  isOpen,
  notification,
  onClose,
  onDelete,
  onOpen,
  onPress,
}: NotificationCardProps) {
  const swipeableRef = useRef<SwipeableMethods>(null);
  const [isVisuallyOpen, setIsVisuallyOpen] = useState(false);

  useEffect(() => {
    if (!isOpen) swipeableRef.current?.close();
  }, [isOpen]);

  return (
    <ReanimatedSwipeable
        containerStyle={styles.swipeable}
        friction={1.5}
        onSwipeableClose={() => {
          setIsVisuallyOpen(false);
          onClose();
        }}
        onSwipeableOpenStartDrag={() => {
          setIsVisuallyOpen(true);
          onOpen();
        }}
        onSwipeableWillOpen={onOpen}
        overshootRight={false}
        ref={swipeableRef}
        renderRightActions={(_progress, _translation, swipeableMethods) => (
          <Pressable
            accessibilityLabel={`${notification.title} 삭제`}
            accessibilityRole="button"
            onPress={() => {
              swipeableMethods.close();
              onDelete();
            }}
            style={({ pressed }) => [
              styles.deleteAction,
              pressed && styles.deleteActionPressed,
            ]}>
            <Text style={styles.deleteActionLabel}>삭제</Text>
          </Pressable>
        )}
        rightThreshold={40}>
        <Pressable
          accessibilityActions={[{ label: '알림 삭제', name: 'delete' }]}
          accessibilityLabel={`${getNotificationCategoryLabel(notification.type)}, ${notification.title}, ${notification.isRead ? '읽음' : '읽지 않음'}`}
          accessibilityRole="button"
          onAccessibilityAction={(event) => {
            if (event.nativeEvent.actionName === 'delete') onDelete();
          }}
          onPress={() => {
            swipeableRef.current?.close();
            onPress();
          }}
          style={({ pressed }) => [
            styles.card,
            isVisuallyOpen && styles.cardOpen,
            pressed && styles.pressed,
          ]}>
          {!notification.isRead ? <View style={styles.unreadIndicator} /> : null}
          <Image
            contentFit="contain"
            source={getNotificationIcon(notification.type)}
            style={styles.icon}
          />

          <View style={styles.content}>
            <Text numberOfLines={1} style={styles.title}>
              {getNotificationCategoryLabel(notification.type)}
            </Text>
            <Text numberOfLines={1} style={styles.description}>
              {notification.title}
            </Text>
          </View>

          <Text style={styles.timeLabel}>
            {formatNotificationTime(notification.createdAt)}
          </Text>
        </Pressable>
    </ReanimatedSwipeable>
  );
}
