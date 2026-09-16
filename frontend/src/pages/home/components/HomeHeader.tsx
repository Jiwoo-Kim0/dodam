import { Image } from 'expo-image';
import { SymbolView } from 'expo-symbols';
import { Pressable, Text, View } from 'react-native';

import { colors, componentSize } from '@/styles';

import { styles } from './HomeHeader.styles';

interface HomeHeaderProps {
  onLogoPress: () => void;
  onMenuPress: () => void;
  onNotificationPress: () => void;
  unreadNotificationCount: number | null;
}

export function HomeHeader({
  onLogoPress,
  onMenuPress,
  onNotificationPress,
  unreadNotificationCount,
}: HomeHeaderProps) {
  return (
    <View style={styles.container}>
      <Pressable
        accessibilityLabel="홈 새로고침"
        accessibilityRole="button"
        hitSlop={8}
        onPress={onLogoPress}>
        <Image
          contentFit="contain"
          source={require('@/assets/logos/dodam_logo_image.png')}
          style={styles.logo}
        />
      </Pressable>

      <View style={styles.actions}>
        <Pressable
          accessibilityLabel="알림 화면으로 이동"
          accessibilityRole="button"
          hitSlop={8}
          onPress={onNotificationPress}
          style={({ pressed }) => [styles.actionButton, pressed && styles.actionButtonPressed]}>
          <SymbolView
            name={{ android: 'notifications', ios: 'bell', web: 'notifications' }}
            size={componentSize.icon.small + 4}
            tintColor={colors.textPrimary}
          />
          {unreadNotificationCount !== null && unreadNotificationCount > 0 ? (
            <View style={styles.notificationBadge}>
              <Text style={styles.notificationBadgeLabel}>
                {unreadNotificationCount > 99 ? '99+' : unreadNotificationCount}
              </Text>
            </View>
          ) : null}
        </Pressable>

        <Pressable
          accessibilityLabel="메뉴 열기"
          accessibilityRole="button"
          hitSlop={8}
          onPress={onMenuPress}
          style={({ pressed }) => [styles.actionButton, pressed && styles.actionButtonPressed]}>
          <SymbolView
            name={{ android: 'menu', ios: 'line.horizontal.3', web: 'menu' }}
            size={componentSize.icon.small + 4}
            tintColor={colors.textPrimary}
          />
        </Pressable>
      </View>
    </View>
  );
}
