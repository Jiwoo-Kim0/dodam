import { Image } from 'expo-image';
import { SymbolView } from 'expo-symbols';
import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors } from '@/styles';
import type { NotificationApiResponse } from '@/types/notificationApi.types';
import {
  formatNotificationDateTime,
  getNotificationIcon,
  hasNotificationDestination,
} from '@/utils/notification';

import { styles } from './NotificationDetailSheet.styles';

interface NotificationDetailSheetProps {
  errorMessage: string;
  isDeleting: boolean;
  isLoading: boolean;
  isMoving: boolean;
  notification: NotificationApiResponse | null;
  visible: boolean;
  onClose: () => void;
  onDelete: () => void;
  onMoveToDestination: () => void;
  onRetry: () => void;
}

export function NotificationDetailSheet({
  errorMessage,
  isDeleting,
  isLoading,
  isMoving,
  notification,
  visible,
  onClose,
  onDelete,
  onMoveToDestination,
  onRetry,
}: NotificationDetailSheetProps) {
  return (
    <Modal
      animationType="fade"
      onRequestClose={onClose}
      statusBarTranslucent
      transparent
      visible={visible}>
      <View style={styles.overlay}>
        <Pressable
          accessibilityLabel="알림 상세 닫기"
          accessibilityRole="button"
          onPress={onClose}
          style={styles.backdrop}
        />

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <View style={styles.header}>
            <Text style={styles.sheetTitle}>알림 상세</Text>
            <Pressable
              accessibilityLabel="닫기"
              accessibilityRole="button"
              hitSlop={8}
              onPress={onClose}
              style={styles.headerButton}>
              <SymbolView
                name={{ android: 'close', ios: 'xmark', web: 'close' }}
                size={22}
                tintColor={colors.textPrimary}
              />
            </Pressable>
          </View>

          {isLoading ? (
            <View style={styles.status}>
              <ActivityIndicator color={colors.primary.green} size="large" />
              <Text style={styles.statusText}>알림을 불러오고 있어요</Text>
            </View>
          ) : errorMessage || !notification ? (
            <View style={styles.status}>
              <Text accessibilityRole="alert" style={styles.statusText}>
                {errorMessage || '알림을 불러오지 못했어요.'}
              </Text>
              <Pressable
                accessibilityRole="button"
                onPress={onRetry}
                style={({ pressed }) => [
                  styles.retryButton,
                  pressed && styles.pressed,
                ]}>
                <Text style={styles.retryLabel}>다시 시도</Text>
              </Pressable>
            </View>
          ) : (
            <>
              <ScrollView
                contentContainerStyle={styles.content}
                showsVerticalScrollIndicator={false}>
                <Image
                  contentFit="contain"
                  source={getNotificationIcon(notification.type)}
                  style={styles.icon}
                />
                <Text style={styles.title}>{notification.title}</Text>
                <Text style={styles.createdAt}>
                  {formatNotificationDateTime(notification.createdAt)}
                </Text>
                <Text style={styles.message}>{notification.message}</Text>
              </ScrollView>

              <View style={styles.actions}>
                <Pressable
                  accessibilityRole="button"
                  accessibilityState={{ disabled: isDeleting || isMoving }}
                  disabled={isDeleting || isMoving}
                  onPress={onDelete}
                  style={({ pressed }) => [
                    styles.deleteButton,
                    (pressed || isDeleting || isMoving) && styles.pressed,
                  ]}>
                  {isDeleting ? (
                    <ActivityIndicator color={colors.textSecondary} size="small" />
                  ) : (
                    <Text style={styles.deleteLabel}>삭제</Text>
                  )}
                </Pressable>

                {hasNotificationDestination(notification) ? (
                  <Pressable
                    accessibilityRole="button"
                    accessibilityState={{ disabled: isDeleting || isMoving }}
                    disabled={isDeleting || isMoving}
                    onPress={onMoveToDestination}
                    style={({ pressed }) => [
                      styles.moveButton,
                      (pressed || isMoving) && styles.pressed,
                    ]}>
                    {isMoving ? (
                      <ActivityIndicator color={colors.white} size="small" />
                    ) : (
                      <Text style={styles.moveLabel}>바로가기</Text>
                    )}
                  </Pressable>
                ) : null}
              </View>
            </>
          )}
        </SafeAreaView>
      </View>
    </Modal>
  );
}
